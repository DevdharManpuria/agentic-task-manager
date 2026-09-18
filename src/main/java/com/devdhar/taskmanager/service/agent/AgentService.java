package com.devdhar.taskmanager.service.agent;

import com.devdhar.taskmanager.dto.AgentDtos.CommandResponse;
import com.devdhar.taskmanager.dto.TaskDtos.TaskRequest;
import com.devdhar.taskmanager.dto.TaskDtos.TaskResponse;
import com.devdhar.taskmanager.entity.AgentInteractionLog;
import com.devdhar.taskmanager.entity.Task;
import com.devdhar.taskmanager.repository.AgentInteractionLogRepository;
import com.devdhar.taskmanager.security.AuthUtils;
import com.devdhar.taskmanager.service.TaskService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * The agentic core: takes a natural-language command, asks the LLM which of our
 * 4 tools to invoke and with what arguments, executes that exact function against
 * TaskService (never anything the model "wants" beyond that), and logs the whole
 * interaction to MongoDB for audit/debugging.
 */
@Service
@RequiredArgsConstructor
public class AgentService {

    private final LlmClient llmClient;
    private final TaskService taskService;
    private final AgentInteractionLogRepository logRepository;
    private final AuthUtils authUtils;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CommandResponse processCommand(String userCommand) {
        Long userId = authUtils.getCurrentUserId();
        String toolName = null;
        Map<String, Object> arguments = new HashMap<>();
        Object result;
        boolean success = true;
        String resultSummary;

        try {
            JsonNode llmResponse = llmClient.requestToolCall(userCommand);
            JsonNode toolCallNode = extractFirstToolCall(llmResponse);

            if (toolCallNode == null) {
                // Model responded with plain text instead of calling a tool -
                // most likely the command didn't match any of our 4 operations.
                resultSummary = "No matching action found for this command.";
                logInteraction(userId, userCommand, "none", arguments, resultSummary, false);
                return new CommandResponse("none", null, resultSummary);
            }

            toolName = toolCallNode.path("function").path("name").asText();
            String rawArgs = toolCallNode.path("function").path("arguments").asText("{}");
            arguments = objectMapper.readValue(rawArgs, Map.class);

            result = dispatch(toolName, arguments);
            resultSummary = "Executed " + toolName + " successfully.";

        } catch (Exception ex) {
            success = false;
            result = null;
            resultSummary = "Failed to execute command: " + ex.getMessage();
        }

        logInteraction(userId, userCommand, toolName != null ? toolName : "unknown", arguments, resultSummary, success);
        return new CommandResponse(toolName, result, resultSummary);
    }

    // Routes to the exact same TaskService methods the REST controller uses -
    // the agent has no special/elevated path into the data layer.
    private Object dispatch(String toolName, Map<String, Object> args) {
        return switch (toolName) {
            case "createTask" -> taskService.createTask(new TaskRequest(
                    (String) args.get("title"),
                    (String) args.get("description"),
                    null,
                    parsePriority(args.get("priority")),
                    parseDate(args.get("dueDate"))
            ));
            case "updateTask" -> taskService.updateTask(
                    toLong(args.get("taskId")),
                    new TaskRequest(
                            (String) args.get("title"),
                            (String) args.get("description"),
                            parseStatus(args.get("status")),
                            parsePriority(args.get("priority")),
                            parseDate(args.get("dueDate"))
                    )
            );
            case "deleteTask" -> {
                taskService.deleteTask(toLong(args.get("taskId")));
                yield Map.of("deleted", true);
            }
            case "listTasks" -> {
                Page<TaskResponse> page = taskService.listTasks(
                        parseStatus(args.get("status")),
                        parsePriority(args.get("priority")),
                        PageRequest.of(0, 20)
                );
                yield page.getContent();
            }
            default -> throw new IllegalArgumentException("Unknown tool: " + toolName);
        };
    }

    private void logInteraction(Long userId, String command, String tool, Map<String, Object> args,
                                 String summary, boolean success) {
        AgentInteractionLog log = AgentInteractionLog.builder()
                .userId(userId)
                .userCommand(command)
                .toolChosen(tool)
                .toolArguments(args)
                .resultSummary(summary)
                .success(success)
                .timestamp(Instant.now())
                .build();
        logRepository.save(log);
    }

    private JsonNode extractFirstToolCall(JsonNode response) {
        JsonNode toolCalls = response.path("choices").path(0).path("message").path("tool_calls");
        return toolCalls.isArray() && !toolCalls.isEmpty() ? toolCalls.get(0) : null;
    }

    private Long toLong(Object val) {
        if (val == null) return null;
        return Long.valueOf(val.toString());
    }

    private LocalDate parseDate(Object val) {
        if (val == null) return null;
        return LocalDate.parse(val.toString());
    }

    private Task.TaskStatus parseStatus(Object val) {
        if (val == null) return null;
        return Task.TaskStatus.valueOf(val.toString());
    }

    private Task.TaskPriority parsePriority(Object val) {
        if (val == null) return null;
        return Task.TaskPriority.valueOf(val.toString());
    }
}
