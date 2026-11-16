package com.devdhar.taskmanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class AgentDtos {

    public record CommandRequest(
            @NotBlank(message = "Command text is required")
            @Size(max = 500, message = "Command must be at most 500 characters") String command
    ) {}

    /** One tool the agent ran: what it was called with and what came back (ok = false means an error text). */
    public record StepTrace(String tool, Map<String, Object> arguments, boolean ok, String output) {}

    /** A delete the agent proposed. Nothing is deleted until the user confirms this id. */
    public record Confirmation(String id, List<String> taskTitles, Instant expiresAt) {}

    public record CommandResponse(
            String message,
            boolean success,
            List<StepTrace> steps,
            Confirmation confirmation
    ) {}
}
