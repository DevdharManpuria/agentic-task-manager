package com.devdhar.taskmanager.service;

import com.devdhar.taskmanager.dto.TaskDtos.TaskRequest;
import com.devdhar.taskmanager.dto.TaskDtos.TaskResponse;
import com.devdhar.taskmanager.entity.Task;
import com.devdhar.taskmanager.entity.User;
import com.devdhar.taskmanager.exception.ResourceNotFoundException;
import com.devdhar.taskmanager.repository.TaskRepository;
import com.devdhar.taskmanager.repository.UserRepository;
import com.devdhar.taskmanager.security.AuthUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the user-scoping rules in TaskService: a task belonging to
 * another user must be indistinguishable from one that does not exist.
 */
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    private static final Long CURRENT_USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long TASK_ID = 5L;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthUtils authUtils;

    @InjectMocks
    private TaskService taskService;

    private Task taskOwnedBy(Long ownerId) {
        return Task.builder()
                .id(TASK_ID)
                .title("Write report")
                .description("Q3 numbers")
                .status(Task.TaskStatus.PENDING)
                .priority(Task.TaskPriority.MEDIUM)
                .dueDate(LocalDate.of(2030, 1, 1))
                .user(User.builder().id(ownerId).username("owner").email("owner@example.com").password("hash").build())
                .build();
    }

    @Test
    void getTaskThrowsWhenTaskBelongsToAnotherUser() {
        when(authUtils.getCurrentUserId()).thenReturn(CURRENT_USER_ID);
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(taskOwnedBy(OTHER_USER_ID)));

        assertThrows(ResourceNotFoundException.class, () -> taskService.getTask(TASK_ID));
    }

    @Test
    void updateTaskThrowsWhenTaskBelongsToAnotherUser() {
        when(authUtils.getCurrentUserId()).thenReturn(CURRENT_USER_ID);
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(taskOwnedBy(OTHER_USER_ID)));

        TaskRequest request = new TaskRequest("Hijacked", null, null, null, null);

        assertThrows(ResourceNotFoundException.class, () -> taskService.updateTask(TASK_ID, request));
        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void deleteTaskThrowsWhenTaskBelongsToAnotherUser() {
        when(authUtils.getCurrentUserId()).thenReturn(CURRENT_USER_ID);
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(taskOwnedBy(OTHER_USER_ID)));

        assertThrows(ResourceNotFoundException.class, () -> taskService.deleteTask(TASK_ID));
        verify(taskRepository, never()).delete(any(Task.class));
    }

    @Test
    void updateTaskLeavesTitleAndDescriptionUnchangedWhenOnlyStatusIsSupplied() {
        Task existing = taskOwnedBy(CURRENT_USER_ID);
        when(authUtils.getCurrentUserId()).thenReturn(CURRENT_USER_ID);
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(existing));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Mirrors the agent flow for "mark task 5 as done": status only, everything else null.
        TaskRequest statusOnly = new TaskRequest(null, null, Task.TaskStatus.COMPLETED, null, null);

        TaskResponse response = taskService.updateTask(TASK_ID, statusOnly);

        assertEquals("Write report", response.title());
        assertEquals("Q3 numbers", response.description());
        assertEquals(Task.TaskStatus.COMPLETED, response.status());
        assertEquals(Task.TaskPriority.MEDIUM, response.priority());
        assertEquals(LocalDate.of(2030, 1, 1), response.dueDate());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listTasksPassesSpecificationAndPageableToRepositoryAndReturnsMappedResults() {
        Pageable pageable = PageRequest.of(1, 5, Sort.by("dueDate"));
        Task task = taskOwnedBy(CURRENT_USER_ID);

        when(authUtils.getCurrentUserId()).thenReturn(CURRENT_USER_ID);
        // Second page (index 1, size 5) of a six-task result set, so the last page holds one task.
        when(taskRepository.findAll(ArgumentMatchers.<Specification<Task>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(task), pageable, 6));

        Page<TaskResponse> result =
                taskService.listTasks(Task.TaskStatus.PENDING, Task.TaskPriority.MEDIUM, pageable);

        ArgumentCaptor<Specification<Task>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(taskRepository).findAll(specCaptor.capture(), pageableCaptor.capture());

        assertNotNull(specCaptor.getValue());
        assertEquals(pageable, pageableCaptor.getValue());

        assertEquals(6, result.getTotalElements());
        assertEquals(pageable, result.getPageable());
        assertEquals(1, result.getContent().size());
        TaskResponse mapped = result.getContent().get(0);
        assertEquals(TASK_ID, mapped.id());
        assertEquals("Write report", mapped.title());
        assertEquals(Task.TaskStatus.PENDING, mapped.status());
    }
}
