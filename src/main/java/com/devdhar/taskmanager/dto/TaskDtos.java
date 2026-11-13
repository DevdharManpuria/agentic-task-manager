package com.devdhar.taskmanager.dto;

import com.devdhar.taskmanager.entity.Task;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public class TaskDtos {

    public record TaskRequest(
            @NotBlank(message = "Title is required") String title,
            String description,
            Task.TaskStatus status,
            Task.TaskPriority priority,
            @Future(message = "Due date must be in the future") LocalDate dueDate
    ) {}

    // Partial update: every field is optional, null means "leave unchanged".
    // Kept separate from TaskRequest so create can require a title while update does not.
    public record TaskUpdateRequest(
            String title,
            String description,
            Task.TaskStatus status,
            Task.TaskPriority priority,
            @Future(message = "Due date must be in the future") LocalDate dueDate
    ) {}

    public record TaskResponse(
            Long id,
            String title,
            String description,
            Task.TaskStatus status,
            Task.TaskPriority priority,
            LocalDate dueDate
    ) {
        public static TaskResponse from(Task task) {
            return new TaskResponse(
                    task.getId(),
                    task.getTitle(),
                    task.getDescription(),
                    task.getStatus(),
                    task.getPriority(),
                    task.getDueDate()
            );
        }
    }
}
