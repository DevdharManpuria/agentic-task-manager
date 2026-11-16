package com.devdhar.taskmanager.eval;

import com.devdhar.taskmanager.config.WebClientConfig;
import com.devdhar.taskmanager.dto.AgentDtos.CommandResponse;
import com.devdhar.taskmanager.entity.Task;
import com.devdhar.taskmanager.entity.User;
import com.devdhar.taskmanager.repository.AgentInteractionLogRepository;
import com.devdhar.taskmanager.repository.TaskRepository;
import com.devdhar.taskmanager.repository.UserRepository;
import com.devdhar.taskmanager.security.AuthUtils;
import com.devdhar.taskmanager.security.UserDetailsImpl;
import com.devdhar.taskmanager.service.TaskService;
import com.devdhar.taskmanager.service.agent.AgentService;
import com.devdhar.taskmanager.service.agent.ConfirmationStore;
import com.devdhar.taskmanager.service.agent.ConversationMemory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * End-to-end eval of the agent against the REAL LLM.
 *
 * For every case in evals/cases.json: reset the DB, seed the starting tasks, run the command
 * through AgentService as a logged-in user, then check the resulting DB state (and reply text).
 * Prints and writes a report; the test only fails on infrastructure problems (e.g. no API key),
 * never on a low score, because the score IS the output.
 *
 * Run:  mvn test -Pevals            (needs app.llm.api-key, i.e. LLM_API_KEY or application-local.properties)
 * Options: -Deval.label=baseline  -Deval.delay-ms=2000
 *
 * Uses an in-memory H2 database (JPA slice only), so no MySQL, MongoDB or JWT setup is needed.
 */
@Tag("eval")
@DataJpaTest(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.format_sql=false"
})
// Let each repository call commit on its own (like production) instead of one big rolled-back transaction.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AgentEvalTest {

    @Autowired private TaskRepository taskRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private Environment env;

    private final ObjectMapper mapper = new ObjectMapper();

    private final ConversationMemory memory = new ConversationMemory();
    private MeteredLlmClient llmClient;
    private AgentService agentService;
    private User user;
    private String model;

    @BeforeEach
    void setUp() {
        String apiKey = requiredProperty("app.llm.api-key");
        String apiUrl = requiredProperty("app.llm.api-url");
        model = requiredProperty("app.llm.model");

        llmClient = new MeteredLlmClient(new WebClientConfig().webClient(60), apiKey, apiUrl, model);

        AuthUtils authUtils = new AuthUtils();
        TaskService taskService = new TaskService(taskRepository, userRepository, authUtils);
        agentService = new AgentService(llmClient, taskService,
                Mockito.mock(AgentInteractionLogRepository.class), authUtils,
                new ConfirmationStore(), memory);

        user = userRepository.save(User.builder()
                .username("eval-user").email("eval@example.com").password("not-used").build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserDetailsImpl(user), null, Collections.emptyList()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void runAllCases() throws Exception {
        List<EvalCase> cases = EvalCase.loadAll();
        String only = System.getProperty("eval.only"); // e.g. -Deval.only=create-08,dates-01 to debug single cases
        if (only != null && !only.isBlank()) {
            List<String> wanted = List.of(only.split(","));
            cases = cases.stream().filter(c -> wanted.contains(c.id())).toList();
        }
        long delayMs = Long.getLong("eval.delay-ms", 2000L);
        String label = System.getProperty("eval.label", "baseline");
        List<CaseResult> results = new ArrayList<>();

        for (EvalCase evalCase : cases) {
            CaseResult result = runCase(evalCase);
            results.add(result);
            System.out.printf("[eval] %-16s %s%n", evalCase.id(), result.passed() ? "PASS" : "FAIL " + result.failures());
            if (!result.passed() && only != null) System.out.println("[eval-reply] " + result.reply());
            Thread.sleep(delayMs); // stay under provider rate limits
        }

        EvalReport.write(Path.of("target", "eval-report"), label, label, model, results);
        System.out.println();
        System.out.println(EvalReport.toMarkdown(label, model, results));

        assertFalse(results.isEmpty());
    }

    private CaseResult runCase(EvalCase evalCase) {
        LocalDate today = LocalDate.now();

        // 1. Reset and seed.
        taskRepository.deleteAll();
        Map<String, Long> idsByTitle = new HashMap<>();
        for (EvalCase.SeedTask seed : evalCase.seeds()) {
            Task saved = taskRepository.save(Task.builder()
                    .title(seed.title())
                    .description(seed.description())
                    .status(seed.status() != null ? Task.TaskStatus.valueOf(seed.status()) : Task.TaskStatus.PENDING)
                    .priority(seed.priority() != null ? Task.TaskPriority.valueOf(seed.priority()) : Task.TaskPriority.MEDIUM)
                    .dueDate(EvalDates.resolve(seed.dueDate(), today))
                    .user(user)
                    .build());
            idsByTitle.put(seed.title(), saved.getId());
        }

        // 2. Run the agent.
        String command = evalCase.renderCommand(idsByTitle);
        llmClient.resetCounters();
        memory.clear(user.getId()); // every case starts a fresh conversation
        long start = System.nanoTime();
        String reply;
        try {
            reply = runAgent(command, evalCase.confirmsDeletes());
        } catch (RuntimeException e) {
            reply = "AGENT THREW: " + e;
        }
        long latencyMs = (System.nanoTime() - start) / 1_000_000;

        // 3. Check the end state.
        List<String> failures = check(evalCase, reply, today);
        return new CaseResult(evalCase.id(), evalCase.category(), command, failures.isEmpty(), failures,
                reply, latencyMs, llmClient.calls(), llmClient.promptTokens(), llmClient.completionTokens());
    }

    /**
     * The only place that knows the agent's response shape. When the response changes in
     * Phase 1, only this method needs updating; the cases and checks stay the same.
     */
    private String runAgent(String command, boolean confirmDeletes) {
        CommandResponse response = agentService.processCommand(command);
        StringBuilder reply = new StringBuilder(response.message());
        response.steps().forEach(step -> reply.append(' ').append(step.output()));
        // A simulated user who clicks "Confirm". Cases with confirmsDeletes=false simulate a user who
        // never confirms, which is how the injection cases prove nothing is deleted on the model's say-so.
        if (response.confirmation() != null && confirmDeletes) {
            reply.append(' ').append(agentService.confirm(response.confirmation().id()).message());
        }
        return reply.toString();
    }

    private List<String> check(EvalCase evalCase, String reply, LocalDate today) {
        List<String> failures = new ArrayList<>();
        EvalCase.Expect expect = evalCase.expect();
        List<Task> tasks = taskRepository.findAll();

        if (expect.taskCount() != null && tasks.size() != expect.taskCount()) {
            failures.add("expected " + expect.taskCount() + " tasks but found " + tasks.size()
                    + " " + titles(tasks));
        }

        if (expect.tasks() != null) {
            for (EvalCase.TaskExpect want : expect.tasks()) {
                List<Task> candidates = tasks.stream()
                        .filter(t -> containsIgnoreCase(t.getTitle(), want.titleContains()))
                        .toList();
                if (candidates.isEmpty()) {
                    failures.add("no task with title containing '" + want.titleContains() + "' " + titles(tasks));
                    continue;
                }
                List<String> mismatches = null;
                boolean matched = false;
                for (Task candidate : candidates) {
                    List<String> m = mismatches(candidate, want, today);
                    if (m.isEmpty()) {
                        matched = true;
                        break;
                    }
                    mismatches = m;
                }
                if (!matched) {
                    failures.add("task '" + candidates.get(0).getTitle() + "': " + String.join(", ", mismatches));
                }
            }
        }

        if (expect.absent() != null) {
            for (String gone : expect.absent()) {
                if (tasks.stream().anyMatch(t -> containsIgnoreCase(t.getTitle(), gone))) {
                    failures.add("a task containing '" + gone + "' should have been deleted/renamed");
                }
            }
        }

        if (expect.replyMustContain() != null) {
            for (String text : expect.replyMustContain()) {
                if (!containsIgnoreCase(reply, text)) failures.add("reply should mention '" + text + "'");
            }
        }
        if (expect.replyMustNotContain() != null) {
            for (String text : expect.replyMustNotContain()) {
                if (containsIgnoreCase(reply, text)) failures.add("reply should NOT mention '" + text + "'");
            }
        }
        return failures;
    }

    private List<String> mismatches(Task task, EvalCase.TaskExpect want, LocalDate today) {
        List<String> out = new ArrayList<>();
        if (want.status() != null && task.getStatus() != Task.TaskStatus.valueOf(want.status())) {
            out.add("status is " + task.getStatus() + ", expected " + want.status());
        }
        if (want.priority() != null && task.getPriority() != Task.TaskPriority.valueOf(want.priority())) {
            out.add("priority is " + task.getPriority() + ", expected " + want.priority());
        }
        if (want.dueDate() != null) {
            LocalDate expected = EvalDates.resolve(want.dueDate(), today);
            if (!expected.equals(task.getDueDate())) {
                out.add("dueDate is " + task.getDueDate() + ", expected " + expected);
            }
        }
        if (want.descriptionContains() != null && !containsIgnoreCase(task.getDescription(), want.descriptionContains())) {
            out.add("description is '" + task.getDescription() + "', expected it to contain '" + want.descriptionContains() + "'");
        }
        return out;
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private static List<String> titles(List<Task> tasks) {
        return tasks.stream().map(Task::getTitle).toList();
    }

    private String requiredProperty(String name) {
        try {
            String value = env.getProperty(name);
            if (value != null && !value.isBlank()) {
                return value;
            }
        } catch (IllegalArgumentException unresolvedPlaceholder) {
            // falls through to the message below
        }
        throw new IllegalStateException("Eval needs '" + name + "'. Set LLM_API_KEY (and optionally LLM_API_URL / "
                + "LLM_MODEL) or add app.llm.api-key to application-local.properties.");
    }
}
