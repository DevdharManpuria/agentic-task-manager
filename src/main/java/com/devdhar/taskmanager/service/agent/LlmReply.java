package com.devdhar.taskmanager.service.agent;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One model reply: the assistant "message" object (content and/or tool_calls) plus the
 * token usage the provider reported for that call. The agent loop appends {@code message}
 * to the conversation as-is, so we keep it as raw JSON instead of mapping it to a class.
 */
public record LlmReply(JsonNode message, long promptTokens, long completionTokens) {

    /** Builds a reply from the provider's full chat-completions response body. */
    public static LlmReply fromResponse(JsonNode response) {
        return new LlmReply(
                response.path("choices").path(0).path("message"),
                response.path("usage").path("prompt_tokens").asLong(0),
                response.path("usage").path("completion_tokens").asLong(0));
    }
}
