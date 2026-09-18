package com.devdhar.taskmanager.service;

import com.devdhar.taskmanager.dto.TaskDtos.TaskRequest;
import com.devdhar.taskmanager.dto.TaskDtos.TaskResponse;
import com.devdhar.taskmanager.entity.Task;
import com.devdhar.taskmanager.entity.User;
import com.devdhar.taskmanager.exception.ResourceNotFoundException;
import com.devdhar.taskmanager.repository.TaskRepository;
import com.devdhar.taskmanager.repository.UserRepository;
import com.devdhar.taskmanager.security.AuthUtils;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final AuthUtils authUtils;

    @Transactional
    public TaskResponse createTask(TaskRequest request) {
        User user = getCurrentUser();

        Task task = Task.builder()
                .title(request.title())
                .description(request.description())
                .status(request.status() != null ? request.status() : Task.TaskStatus.PENDING)
                .priority(request.priority() != null ? request.priority() : Task.TaskPriority.MEDIUM)
                .dueDate(request.dueDate())
                .user(user)
                .build();

        return TaskResponse.from(taskRepository.save(task));
    }

    public TaskResponse getTask(Long taskId) {
        Task task = findOwnedTask(taskId);
        return TaskResponse.from(task);
    }

    // Filtering + pagination combined via Specification, so callers can pass any subset
    // of (status, priority) and still get a paged, sorted result.
    public Page<TaskResponse> listTasks(Task.TaskStatus status, Task.TaskPriority priority, Pageable pageable) {
        Long userId = authUtils.getCurrentUserId();

        Specification<Task> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("user").get("id"), userId));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (priority != null) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return taskRepository.findAll(spec, pageable).map(TaskResponse::from);
    }

    @Transactional
    public TaskResponse updateTask(Long taskId, TaskRequest request) {
        Task task = findOwnedTask(taskId);

        // Partial update: only overwrite fields that were actually provided.
        // This matters for the agent flow, where a command like "mark task 5 as done"
        // only supplies a status, and must not wipe the existing title/description.
        if (request.title() != null && !request.title().isBlank()) task.setTitle(request.title());
        if (request.description() != null) task.setDescription(request.description());
        if (request.status() != null) task.setStatus(request.status());
        if (request.priority() != null) task.setPriority(request.priority());
        if (request.dueDate() != null) task.setDueDate(request.dueDate());

        return TaskResponse.from(taskRepository.save(task));
    }

    @Transactional
    public void deleteTask(Long taskId) {
        Task task = findOwnedTask(taskId);
        taskRepository.delete(task);
    }

    // Ensures a user can only ever fetch/modify their OWN tasks -> the actual
    // enforcement point for "user-scoped" access, referenced in the resume bullet.
    private Task findOwnedTask(Long taskId) {
        Long userId = authUtils.getCurrentUserId();
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
        if (!task.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Task not found: " + taskId); // don't leak existence to other users
        }
        return task;
    }

    private User getCurrentUser() {
        Long userId = authUtils.getCurrentUserId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }
}
