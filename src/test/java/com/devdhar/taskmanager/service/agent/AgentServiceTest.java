package com.devdhar.taskmanager.service.agent;

import com.devdhar.taskmanager.dto.AgentDtos.CommandResponse;
import com.devdhar.taskmanager.dto.TaskDtos.TaskRequest;
import com.devdhar.taskmanager.dto.TaskDtos.TaskResponse;
import com.devdhar.taskmanager.entity.AgentInteractionLog;
import com.devdhar.taskmanager.entity.Task;
import com.devdhar.taskmanager.exception.ResourceNotFoundException;
import com.devdhar.taskmanager.repository.AgentInteractionLogRepository;
import com.devdhar.taskmanager.security.AuthUtils;
import com.devdhar.taskmanager.service.TaskService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the agent dispatch loop: the tool the LLM picks must map to the
 * matching TaskService call, and every command must leave exactly one audit log.
 */
@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    private static final Long USER_ID = 42L;

    private final ObjectMapper mapper = new ObjectMapper();

    @Mock
    private LlmClient llmClient;

    @Mock
    private TaskService taskService;

    @Mock
    private AgentInteractionLogRepository logRepository;

    @Mock
    private AuthUtils authUtils;

    @InjectMocks
    private AgentService agentService;

    /** Builds an OpenAI-shaped response in which the model chose exactly one tool. */
    private JsonNode toolCallResponse(String toolName, String argumentsJson) {
        ObjectNode function = mapper.createObjectNode();
        function.put("name", toolName);
        function.put("arguments", argumentsJson);

        ObjectNode toolCall = mapper.createObjectNode();
        toolCall.set("function", function);

        ObjectNode message = mapper.createObjectNode();
        message.set("tool_calls", mapper.createArrayNode().add(toolCall));

        ObjectNode choice = mapper.createObjectNode();
        choice.set("message", message);

        ObjectNode root = mapper.createObjectNode();
        root.set("choices", mapper.createArrayNode().add(choice));
        return root;
    }

    /** Builds a response in which the model replied with plain text and no tool_calls. */
    private JsonNode plainTextResponse(String content) {
        ObjectNode message = mapper.createObjectNode();
        message.put("content", content);

        ObjectNode choice = mapper.createObjectNode();
        choice.set("message", message);

        ObjectNode root = mapper.createObjectNode();
        root.set("choices", mapper.createArrayNode().add(choice));
        return root;
    }

    private TaskResponse sampleTask() {
        return new TaskResponse(7L, "Write report", "Q3 numbers",
                Task.TaskStatus.PENDING, Task.TaskPriority.HIGH, LocalDate.of(2030, 1, 2));
    }

    private AgentInteractionLog capturedLog() {
        ArgumentCaptor<AgentInteractionLog> logCaptor = ArgumentCaptor.forClass(AgentInteractionLog.class);
        verify(logRepository, times(1)).save(logCaptor.capture());
        return logCaptor.getValue();
    }

    @Test
    void createTaskToolCallInvokesTaskServiceCreateTask() {
        when(authUtils.getCurrentUserId()).thenReturn(USER_ID);
        when(llmClient.requestToolCall("add a report task")).thenReturn(toolCallResponse("createTask",
                "{\"title\":\"Write report\",\"description\":\"Q3 numbers\","
                        + "\"priority\":\"HIGH\",\"dueDate\":\"2030-01-02\"}"));
        when(taskService.createTask(any(TaskRequest.class))).thenReturn(sampleTask());

        CommandResponse response = agentService.processCommand("add a report task");

        ArgumentCaptor<TaskRequest> requestCaptor = ArgumentCaptor.forClass(TaskRequest.class);
        verify(taskService).createTask(requestCaptor.capture());
        TaskRequest sent = requestCaptor.getValue();
        assertEquals("Write report", sent.title());
        assertEquals("Q3 numbers", sent.description());
        assertEquals(Task.TaskPriority.HIGH, sent.priority());
        assertEquals(LocalDate.of(2030, 1, 2), sent.dueDate());

        assertEquals("createTask", response.toolUsed());
        assertEquals("Executed createTask successfully.", response.message());
        assertEquals(sampleTask(), response.result());
    }

    @Test
    void updateTaskToolCallInvokesTaskServiceUpdateTask() {
        when(authUtils.getCurrentUserId()).thenReturn(USER_ID);
        when(llmClient.requestToolCall("mark task 7 as done")).thenReturn(
                toolCallResponse("updateTask", "{\"taskId\":7,\"status\":\"COMPLETED\"}"));
        when(taskService.updateTask(eq(7L), any(TaskRequest.class))).thenReturn(sampleTask());

        CommandResponse response = agentService.processCommand("mark task 7 as done");

        ArgumentCaptor<TaskRequest> requestCaptor = ArgumentCaptor.forClass(TaskRequest.class);
        verify(taskService).updateTask(eq(7L), requestCaptor.capture());
        TaskRequest sent = requestCaptor.getValue();
        assertEquals(Task.TaskStatus.COMPLETED, sent.status());
        assertNull(sent.title());
        assertNull(sent.description());

        assertEquals("updateTask", response.toolUsed());
        assertEquals("Executed updateTask successfully.", response.message());
    }

    @Test
    void deleteTaskToolCallInvokesTaskServiceDeleteTask() {
        when(authUtils.getCurrentUserId()).thenReturn(USER_ID);
        when(llmClient.requestToolCall("delete task 9")).thenReturn(
                toolCallResponse("deleteTask", "{\"taskId\":9}"));

        CommandResponse response = agentService.processCommand("delete task 9");

        verify(taskService).deleteTask(9L);
        assertEquals("deleteTask", response.toolUsed());
        assertEquals("Executed deleteTask successfully.", response.message());
    }

    @Test
    void listTasksToolCallInvokesTaskServiceListTasks() {
        when(authUtils.getCurrentUserId()).thenReturn(USER_ID);
        when(llmClient.requestToolCall("what is pending?")).thenReturn(
                toolCallResponse("listTasks", "{\"status\":\"PENDING\"}"));
        when(taskService.listTasks(eq(Task.TaskStatus.PENDING), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleTask())));

        CommandResponse response = agentService.processCommand("what is pending?");

        verify(taskService).listTasks(eq(Task.TaskStatus.PENDING), isNull(), any(Pageable.class));
        assertEquals("listTasks", response.toolUsed());
        assertEquals("Executed listTasks successfully.", response.message());
        assertEquals(List.of(sampleTask()), response.result());
    }

    @Test
    void responseWithoutToolCallsReturnsNoneToolAndNeverTouchesTaskService() {
        when(authUtils.getCurrentUserId()).thenReturn(USER_ID);
        when(llmClient.requestToolCall("how are you?")).thenReturn(
                plainTextResponse("I can only manage tasks."));

        CommandResponse response = agentService.processCommand("how are you?");

        assertEquals("none", response.toolUsed());
        assertNull(response.result());
        assertEquals("No matching action found for this command.", response.message());
        verifyNoInteractions(taskService);
    }

    @Test
    void successfulCommandWritesExactlyOneInteractionLog() {
        when(authUtils.getCurrentUserId()).thenReturn(USER_ID);
        when(llmClient.requestToolCall("delete task 9")).thenReturn(
                toolCallResponse("deleteTask", "{\"taskId\":9}"));

        agentService.processCommand("delete task 9");

        AgentInteractionLog log = capturedLog();
        assertEquals(USER_ID, log.getUserId());
        assertEquals("delete task 9", log.getUserCommand());
        assertEquals("deleteTask", log.getToolChosen());
        assertEquals(9, log.getToolArguments().get("taskId"));
        assertEquals("Executed deleteTask successfully.", log.getResultSummary());
        assertTrue(log.isSuccess());
        assertNotNull(log.getTimestamp());
    }

    @Test
    void commandWithoutToolCallWritesExactlyOneInteractionLog() {
        when(authUtils.getCurrentUserId()).thenReturn(USER_ID);
        when(llmClient.requestToolCall("how are you?")).thenReturn(
                plainTextResponse("I can only manage tasks."));

        agentService.processCommand("how are you?");

        AgentInteractionLog log = capturedLog();
        assertEquals(USER_ID, log.getUserId());
        assertEquals("how are you?", log.getUserCommand());
        assertEquals("none", log.getToolChosen());
        assertEquals("No matching action found for this command.", log.getResultSummary());
        assertFalse(log.isSuccess());
    }

    @Test
    void failedCommandWritesExactlyOneInteractionLog() {
        when(authUtils.getCurrentUserId()).thenReturn(USER_ID);
        when(llmClient.requestToolCall("delete task 9")).thenReturn(
                toolCallResponse("deleteTask", "{\"taskId\":9}"));
        doThrow(new ResourceNotFoundException("Task not found: 9")).when(taskService).deleteTask(9L);

        CommandResponse response = agentService.processCommand("delete task 9");

        AgentInteractionLog log = capturedLog();
        assertEquals("deleteTask", log.getToolChosen());
        assertFalse(log.isSuccess());
        assertTrue(log.getResultSummary().startsWith("Failed to execute command:"));
        assertNull(response.result());
    }
}
