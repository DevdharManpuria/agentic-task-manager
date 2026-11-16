package com.devdhar.taskmanager.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * One document per agent command, stored in MongoDB rather than MySQL.
 *
 * Why Mongo here and not another MySQL table: each command now has a variable number of
 * steps, and each step's arguments differ per tool (createTask has title/dueDate,
 * deleteTask just an id). A flexible document holding the whole trace avoids a wide table
 * of nullable columns or a table per tool, and gives a full audit trail of what the agent
 * decided. This is also where the full error detail goes: users only see a generic message.
 */
@Document(collection = "agent_interaction_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentInteractionLog {

    @Id
    private String id;

    private Long userId;
    private String userCommand;      // raw natural-language input
    private String toolChosen;       // tools used in order, comma separated, or "none"
    private Map<String, Object> toolArguments; // arguments of the first step (kept for older queries)
    private List<Map<String, Object>> steps;   // full trace: tool, arguments, ok, output
    private String resultSummary;    // the final message shown to the user
    private String errorDetail;      // internal error text, never sent to the user
    private int llmCalls;
    private long promptTokens;
    private long completionTokens;
    private boolean success;
    private Instant timestamp;
}
