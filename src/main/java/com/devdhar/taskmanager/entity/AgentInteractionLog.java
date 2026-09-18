package com.devdhar.taskmanager.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * Unstructured log of every agent command, stored in MongoDB rather than MySQL.
 *
 * Why Mongo here and not another MySQL table: the "arguments" the LLM extracts
 * differ completely per tool (createTask has title/dueDate, deleteTask has just
 * an id, listTasks might have status/priority filters). Modeling that in a fixed
 * relational schema means either a wide table full of nullable columns or a
 * separate table per tool. A flexible document per interaction avoids both,
 * while giving us a clean audit trail of what the agent decided and why.
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
    private String userCommand;      // raw natural-language input, e.g. "remind me to finish the report by Friday"
    private String toolChosen;       // e.g. "createTask"
    private Map<String, Object> toolArguments; // whatever the LLM extracted for that tool
    private String resultSummary;    // what happened when we executed it
    private boolean success;
    private Instant timestamp;
}
