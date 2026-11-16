package com.devdhar.taskmanager.controller;

import com.devdhar.taskmanager.dto.AgentDtos.CommandRequest;
import com.devdhar.taskmanager.dto.AgentDtos.CommandResponse;
import com.devdhar.taskmanager.exception.TooManyRequestsException;
import com.devdhar.taskmanager.security.AuthUtils;
import com.devdhar.taskmanager.service.agent.AgentService;
import com.devdhar.taskmanager.service.agent.RateLimiter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;
    private final RateLimiter rateLimiter;
    private final AuthUtils authUtils;

    /**
     * The single entry point for natural-language task management, e.g.:
     * { "command": "remind me to finish the report by Friday" }
     * { "command": "delete my gym task" }  (returns a confirmation to approve first)
     */
    @PostMapping("/command")
    public ResponseEntity<CommandResponse> handleCommand(@Valid @RequestBody CommandRequest request) {
        if (!rateLimiter.tryAcquire(authUtils.getCurrentUserId())) {
            throw new TooManyRequestsException("Too many commands. Please wait a minute and try again.");
        }
        return ResponseEntity.ok(agentService.processCommand(request.command()));
    }

    /** Runs a delete the agent proposed earlier. */
    @PostMapping("/confirm/{confirmationId}")
    public ResponseEntity<CommandResponse> confirm(@PathVariable String confirmationId) {
        return ResponseEntity.ok(agentService.confirm(confirmationId));
    }

    /** Throws a proposed delete away without running it. */
    @DeleteMapping("/confirm/{confirmationId}")
    public ResponseEntity<CommandResponse> cancel(@PathVariable String confirmationId) {
        return ResponseEntity.ok(agentService.cancel(confirmationId));
    }
}
