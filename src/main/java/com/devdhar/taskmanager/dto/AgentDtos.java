package com.devdhar.taskmanager.dto;

import jakarta.validation.constraints.NotBlank;

public class AgentDtos {

    public record CommandRequest(
            @NotBlank(message = "Command text is required") String command
    ) {}

    public record CommandResponse(
            String toolUsed,
            Object result,
            String message
    ) {}
}
