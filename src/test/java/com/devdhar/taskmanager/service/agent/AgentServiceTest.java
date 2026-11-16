package com.devdhar.taskmanager.service.agent;

import com.devdhar.taskmanager.dto.AgentDtos.CommandResponse;
import com.devdhar.taskmanager.dto.TaskDtos.TaskRequest;
import com.devdhar.taskmanager.dto.TaskDtos.TaskResponse;
import com.devdhar.taskmanager.dto.TaskDtos.TaskUpdateRequest;
import com.devdhar.taskmanager.entity.AgentInteractionLog;
import com.devdhar.taskmanager.entity.Task;
import com.devdhar.taskmanager.exception.ResourceNotFoundException;
import com.devdhar.taskmanager.repository.AgentInteractionLogRepository;
import com.devdhar.taskmanager.security.AuthUtils;
import com.devdhar.taskmanager.service.TaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the agent loop. LlmClient is mocked with scripted replies in sequence,
 * so each test describes a whole conversation: what the model says, and what we do.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentServiceTest {

    private static final Long USER_ID = 42L;

    private final ObjectMapper mapper = new ObjectMapper();

    @Mock private LlmClient llmClient;
    @Mock private TaskService taskService;
    @Mock private AgentInteractionLogRepository logRepository;
    @Mock private AuthUtils authUtils;

    private AgentService agentService;

    @BeforeEach
    void setUp() {
        when(authUtils.getCurrentUserId()).thenReturn(USER_ID);
        agentService = new AgentService(llmClient, taskService, logRepository, authUtils,
                new ConfirmationStore(), new ConversationMemory());
    }

    // ---- scripted model replies ----

    private LlmReply toolCall(String name, String argumentsJson) {
        ObjectNode function = mapper.createObjectNode();
        function.put("name", name);
        function.put("arguments", argumentsJson);
        ObjectNode call = mapper.createObjectNode();
        call.put("id", "call_" + name);
        call.put("type", "function");
        call.set("function", function);
        ObjectNode message = mapper.createObjectNode();
        message.put("role", "assistant");
        message.putNull("content");
        ArrayNode calls = mapper.createArrayNode().add(call);
        message.set("tool_calls", calls);
        return new LlmReply(message, 100, 20);
    }

    private LlmReply text(String content) {
        ObjectNode message = mapper.createObjectNode();
        message.put("role", "assistant");
        message.put("content", content);
        return new LlmReply(message, 100, 20);
    }

    private TaskResponse task(long id, String title) {
        return new TaskResponse(id, title, null, Task.TaskStatus.PENDING, Task.TaskPriority.HIGH,
                LocalDate.of(2030, 1, 2));
    }

    private AgentInteractionLog capturedLog() {
        ArgumentCaptor<AgentInteractionLog> captor = ArgumentCaptor.forClass(AgentInteractionLog.class);
        verify(logRepository, times(1)).save(captor.capture());
        return captor.getValue();
    }

    // ---- tests ----

    @Test
    void createTaskThenFinalAnswer() {
        when(llmClient.chat(anyList())).thenReturn(
                toolCall("createTask", "{\"title\":\"Write report\",\"priority\":\"HIGH\",\"dueDate\":\"2030-01-02\"}"),
                text("Created the report task."));
        when(taskService.createTask(any(TaskRequest.class))).thenReturn(task(7, "Write report"));

        CommandResponse response = agentService.processCommand("add a report task");

        ArgumentCaptor<TaskRequest> captor = ArgumentCaptor.forClass(TaskRequest.class);
        verify(taskService).createTask(captor.capture());
        assertEquals("Write report", captor.getValue().title());
        assertEquals(Task.TaskPriority.HIGH, captor.getValue().priority());
        assertEquals(LocalDate.of(2030, 1, 2), captor.getValue().dueDate());
        assertEquals("Created the report task.", response.message());
        assertTrue(response.success());
        assertEquals(1, response.steps().size());
        assertTrue(response.steps().get(0).ok());
        // The task has a due date: it must serialise, or the model would be told the tool failed.
        assertTrue(response.steps().get(0).output().contains("\"ok\":true"));
        assertTrue(response.steps().get(0).output().contains("2030-01-02"));
        assertNull(response.confirmation());
    }

    @Test
    void repeatedIdenticalCreateIsRefusedSoNoDuplicatesAreMade() {
        when(llmClient.chat(anyList())).thenReturn(
                toolCall("createTask", "{\"title\":\"Pay rent\"}"),
                toolCall("createTask", "{\"title\":\"Pay rent\"}"),
                text("Created it."));
        when(taskService.createTask(any(TaskRequest.class))).thenReturn(task(1, "Pay rent"));

        CommandResponse response = agentService.processCommand("pay rent");

        verify(taskService, times(1)).createTask(any(TaskRequest.class));
        assertEquals(List.of(true, false), response.steps().stream().map(s -> s.ok()).toList());
    }

    @Test
    void lowerCaseEnumsAreAccepted() {
        when(llmClient.chat(anyList())).thenReturn(
                toolCall("updateTask", "{\"taskId\":7,\"status\":\"in progress\",\"priority\":\"high\"}"),
                text("ok"));
        when(taskService.updateTask(eq(7L), any(TaskUpdateRequest.class))).thenReturn(task(7, "x"));

        agentService.processCommand("start task 7");

        ArgumentCaptor<TaskUpdateRequest> captor = ArgumentCaptor.forClass(TaskUpdateRequest.class);
        verify(taskService).updateTask(eq(7L), captor.capture());
        assertEquals(Task.TaskStatus.IN_PROGRESS, captor.getValue().status());
        assertEquals(Task.TaskPriority.HIGH, captor.getValue().priority());
    }

    @Test
    void badArgumentIsReturnedToTheModelWhichThenCorrectsItself() {
        List<List<Map<String, Object>>> snapshots = new ArrayList<>();
        List<LlmReply> script = new ArrayList<>(List.of(
                toolCall("createTask", "{\"title\":\"Pay rent\",\"priority\":\"urgent\"}"),
                toolCall("createTask", "{\"title\":\"Pay rent\",\"priority\":\"HIGH\"}"),
                text("Created it.")));
        when(llmClient.chat(anyList())).thenAnswer(inv -> {
            snapshots.add(new ArrayList<>(inv.<List<Map<String, Object>>>getArgument(0)));
            return script.remove(0);
        });
        when(taskService.createTask(any(TaskRequest.class))).thenReturn(task(1, "Pay rent"));

        CommandResponse response = agentService.processCommand("pay rent, urgent");

        verify(taskService, times(1)).createTask(any(TaskRequest.class)); // only the corrected call ran
        assertEquals(List.of(false, true), response.steps().stream().map(s -> s.ok()).toList());
        List<Map<String, Object>> secondCall = snapshots.get(1);
        Map<String, Object> toolMessage = secondCall.get(secondCall.size() - 1);
        assertEquals("tool", toolMessage.get("role"));
        assertTrue(toolMessage.get("content").toString().contains("priority must be one of"));
        assertTrue(response.success());
    }

    @Test
    void searchThenDeleteDoesNotDeleteUntilConfirmed() {
        when(llmClient.chat(anyList())).thenReturn(
                toolCall("listTasks", "{\"titleContains\":\"report\"}"),
                toolCall("deleteTask", "{\"taskId\":7}"),
                text("Please confirm deleting the report task."));
        when(taskService.listTasks(isNull(), isNull(), eq("report"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(task(7, "Write report"))));
        when(taskService.getTask(7L)).thenReturn(task(7, "Write report"));

        CommandResponse response = agentService.processCommand("delete my report task");

        verify(taskService, never()).deleteTask(any());
        assertNotNull(response.confirmation());
        assertEquals(List.of("Write report"), response.confirmation().taskTitles());

        CommandResponse confirmed = agentService.confirm(response.confirmation().id());

        verify(taskService).deleteTask(7L);
        assertEquals("Deleted 1 task.", confirmed.message());
        assertThrows(ResourceNotFoundException.class,
                () -> agentService.confirm(response.confirmation().id())); // single use
    }

    @Test
    void cancelledDeleteNeverRuns() {
        when(llmClient.chat(anyList())).thenReturn(toolCall("deleteTask", "{\"taskId\":7}"), text("Confirm?"));
        when(taskService.getTask(7L)).thenReturn(task(7, "Write report"));

        CommandResponse response = agentService.processCommand("delete task 7");
        CommandResponse cancelled = agentService.cancel(response.confirmation().id());

        assertEquals("Cancelled. Nothing was deleted.", cancelled.message());
        verify(taskService, never()).deleteTask(any());
    }

    @Test
    void deletingATaskThatDoesNotExistProposesNothing() {
        when(llmClient.chat(anyList())).thenReturn(toolCall("deleteTask", "{\"taskId\":99}"), text("No such task."));
        when(taskService.getTask(99L)).thenThrow(new ResourceNotFoundException("Task not found: 99"));

        CommandResponse response = agentService.processCommand("delete task 99");

        assertNull(response.confirmation());
        assertFalse(response.steps().get(0).ok());
    }

    @Test
    void stopsAfterTheStepCap() {
        when(llmClient.chat(anyList())).thenReturn(toolCall("listTasks", "{}"));
        when(taskService.listTasks(isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        CommandResponse response = agentService.processCommand("loop forever");

        verify(llmClient, times(AgentService.MAX_STEPS)).chat(anyList());
        assertFalse(response.success());
        assertTrue(response.message().contains("finish that"));
        assertFalse(capturedLog().isSuccess());
    }

    @Test
    void plainTextReplyCallsNoTools() {
        when(llmClient.chat(anyList())).thenReturn(text("I can only manage tasks."));

        CommandResponse response = agentService.processCommand("how are you?");

        assertEquals("I can only manage tasks.", response.message());
        assertTrue(response.steps().isEmpty());
        verifyNoInteractions(taskService);
        assertEquals("none", capturedLog().getToolChosen());
    }

    @Test
    void unknownToolAndBrokenJsonBecomeToolErrors() {
        when(llmClient.chat(anyList())).thenReturn(
                toolCall("dropDatabase", "{}"),
                toolCall("listTasks", "not json"),
                text("Sorry."));

        CommandResponse response = agentService.processCommand("do something odd");

        assertEquals(List.of(false, false), response.steps().stream().map(s -> s.ok()).toList());
        verifyNoInteractions(taskService);
    }

    @Test
    void llmFailureGivesGenericMessageAndKeepsDetailInTheLog() {
        when(llmClient.chat(anyList())).thenThrow(new RuntimeException("LLM API error (401): secret-key-detail"));

        CommandResponse response = agentService.processCommand("anything");

        assertFalse(response.success());
        assertFalse(response.message().contains("secret-key-detail"));
        AgentInteractionLog log = capturedLog();
        assertTrue(log.getErrorDetail().contains("secret-key-detail"));
        assertFalse(log.isSuccess());
    }

    @Test
    void successfulCommandWritesOneLogWithTraceAndTokens() {
        when(llmClient.chat(anyList())).thenReturn(toolCall("listTasks", "{}"), text("You have none."));
        when(taskService.listTasks(isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        agentService.processCommand("what do I have?");

        AgentInteractionLog log = capturedLog();
        assertEquals(USER_ID, log.getUserId());
        assertEquals("what do I have?", log.getUserCommand());
        assertEquals("listTasks", log.getToolChosen());
        assertEquals(1, log.getSteps().size());
        assertEquals(2, log.getLlmCalls());
        assertEquals(200, log.getPromptTokens());
        assertEquals(40, log.getCompletionTokens());
        assertTrue(log.isSuccess());
        assertNotNull(log.getTimestamp());
    }

    @Test
    void earlierExchangesAreSentToTheModelForFollowUps() {
        List<List<Map<String, Object>>> snapshots = new ArrayList<>();
        List<LlmReply> script = new ArrayList<>(List.of(text("Created the report task."), text("Made it high.")));
        when(llmClient.chat(anyList())).thenAnswer(inv -> {
            snapshots.add(new ArrayList<>(inv.<List<Map<String, Object>>>getArgument(0)));
            return script.remove(0);
        });

        agentService.processCommand("add a report task");
        agentService.processCommand("make that high priority");

        List<Map<String, Object>> second = snapshots.get(1);
        assertEquals(4, second.size()); // system, earlier user, earlier assistant, new user
        assertEquals("add a report task", second.get(1).get("content"));
        assertEquals("Created the report task.", second.get(2).get("content"));
    }
}
