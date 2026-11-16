package com.devdhar.taskmanager.eval;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One eval case: a starting set of tasks, one natural-language command, and what the
 * database (and the reply) should look like afterwards. Loaded from evals/cases.json.
 *
 * Two small conveniences keep the cases readable and stable over time:
 *  - "{{id:Some title}}" inside a command is replaced by the real DB id of the seeded
 *    task with that title (ids are generated, so they can't be hard-coded).
 *  - dates are written as "+3d" (3 days from today) or "next:MONDAY"; see EvalDates.
 */
public record EvalCase(
        String id,
        String category,
        List<SeedTask> seedTasks,
        String command,
        Expect expect,
        Boolean autoConfirm
) {

    public record SeedTask(String title, String description, String status, String priority, String dueDate) {}

    /** Every field is optional; only the ones present are checked. */
    public record Expect(
            Integer taskCount,
            List<TaskExpect> tasks,
            List<String> absent,
            List<String> replyMustContain,
            List<String> replyMustNotContain
    ) {}

    /** A task whose title contains titleContains must exist and match every other non-null field. */
    public record TaskExpect(String titleContains, String status, String priority, String dueDate,
                             String descriptionContains) {}

    private static final Pattern ID_PLACEHOLDER = Pattern.compile("\\{\\{id:([^}]+)}}");

    public static List<EvalCase> loadAll() {
        // cases.json is the original set the baseline was measured on; cases-extended.json adds
        // the cases for the new behaviour (confirmation, injection, ambiguity, more phrasing/dates).
        List<EvalCase> all = new java.util.ArrayList<>();
        for (String file : List.of("/evals/cases.json", "/evals/cases-extended.json")) {
            try (InputStream in = EvalCase.class.getResourceAsStream(file)) {
                if (in == null) {
                    throw new IllegalStateException(file + " not found on the test classpath");
                }
                all.addAll(List.of(new ObjectMapper().readValue(in, EvalCase[].class)));
            } catch (IOException e) {
                throw new IllegalStateException("Could not parse " + file, e);
            }
        }
        return List.copyOf(all);
    }

    /** Whether the simulated user clicks Confirm on proposed deletes (default yes). */
    public boolean confirmsDeletes() {
        return autoConfirm == null || autoConfirm;
    }

    public List<SeedTask> seeds() {
        return seedTasks == null ? List.of() : seedTasks;
    }

    /** Titles referenced as {{id:Title}} in the command. */
    public List<String> referencedTitles() {
        Matcher m = ID_PLACEHOLDER.matcher(command);
        List<String> titles = new java.util.ArrayList<>();
        while (m.find()) {
            titles.add(m.group(1));
        }
        return titles;
    }

    /** Replaces each {{id:Title}} with the id of the seeded task that has that title. */
    public String renderCommand(Map<String, Long> idsByTitle) {
        Matcher m = ID_PLACEHOLDER.matcher(command);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            Long id = idsByTitle.get(m.group(1));
            if (id == null) {
                throw new IllegalStateException("Case " + this.id + ": no seeded task titled '" + m.group(1) + "'");
            }
            m.appendReplacement(out, id.toString());
        }
        m.appendTail(out);
        return out.toString();
    }
}
