package com.devdhar.taskmanager.controller;

import com.devdhar.taskmanager.dto.AgentDtos.CommandRequest;
import com.devdhar.taskmanager.dto.AgentDtos.CommandResponse;
import com.devdhar.taskmanager.service.agent.AgentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    /**
     * The single entry point for natural-language task management, e.g.:
     * { "command": "remind me to finish the report by Friday" }
     * { "command": "mark task 3 as completed" }
     * { "command": "what tasks do I have that are high priority?" }
     */
    @PostMapping("/command")
    public ResponseEntity<CommandResponse> handleCommand(@Valid @RequestBody CommandRequest request) {
        return ResponseEntity.ok(agentService.processCommand(request.command()));
    }
}
