package com.devdhar.taskmanager.service.agent;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Describes our task CRUD operations as "tools" the LLM can choose to call, using
 * OpenAI's function-calling schema (Gemini and Anthropic use near-identical shapes,
 * so swapping providers later mostly means changing LlmClient, not this file).
 *
 * How this is used: we send these definitions alongside the user's natural-language
 * command. The model does NOT execute anything itself - it just returns which function
 * name to call and with what arguments, extracted from the user's sentence. WE then
 * execute the actual function against TaskService. This separation (model decides,
 * our code executes) is the core safety property of function calling: the LLM never
 * gets direct DB or system access.
 */
public class AgentToolDefinitions {

    public static List<Map<String, Object>> allTools() {
        return List.of(
                createTaskTool(),
                updateTaskTool(),
                deleteTaskTool(),
                listTasksTool()
        );
    }

    private static Map<String, Object> createTaskTool() {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "createTask",
                        "description", "Create a new task for the user. Use this when the user wants to add, create, or remember to do something.",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "title", Map.of("type", "string", "description", "Short title of the task"),
                                        "description", Map.of("type", List.of("string", "null"), "description", "Optional longer description"),
                                        "dueDate", Map.of("type", List.of("string", "null"), "description", "Due date in YYYY-MM-DD format, if mentioned"),
                                        "priority", Map.of("type", List.of("string", "null"), "enum", Arrays.asList("LOW", "MEDIUM", "HIGH", null))
                                ),
                                "required", List.of("title")
                        )
                )
        );
    }

    private static Map<String, Object> updateTaskTool() {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "updateTask",
                        "description", "Update an existing task's fields, such as marking it complete or changing its due date.",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "taskId", Map.of("type", "integer", "description", "The id of the task to update"),
                                        "title", Map.of("type", List.of("string", "null")),
                                        "status", Map.of("type", List.of("string", "null"), "enum", Arrays.asList("PENDING", "IN_PROGRESS", "COMPLETED", null)),
                                        "priority", Map.of("type", List.of("string", "null"), "enum", Arrays.asList("LOW", "MEDIUM", "HIGH", null)),
                                        "dueDate", Map.of("type", List.of("string", "null"), "description", "YYYY-MM-DD")
                                ),
                                "required", List.of("taskId")
                        )
                )
        );
    }

    private static Map<String, Object> deleteTaskTool() {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "deleteTask",
                        "description", "Delete a task permanently. Use when the user wants to remove or cancel a task.",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "taskId", Map.of("type", "integer", "description", "The id of the task to delete")
                                ),
                                "required", List.of("taskId")
                        )
                )
        );
    }

    private static Map<String, Object> listTasksTool() {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "listTasks",
                        "description", "List the user's tasks, optionally filtered by status or priority. Use when the user asks what tasks they have.",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "status", Map.of("type", List.of("string", "null"), "enum", Arrays.asList("PENDING", "IN_PROGRESS", "COMPLETED", null)),
                                        "priority", Map.of("type", List.of("string", "null"), "enum", Arrays.asList("LOW", "MEDIUM", "HIGH", null))
                                ),
                                "required", List.of()
                        )
                )
        );
    }
}