package com.devdhar.taskmanager.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * Thin wrapper around the LLM provider's chat-completions endpoint with function/tool
 * calling enabled. Written against OpenAI's request/response shape - if you swap to
 * Gemini or Anthropic's native API, only this class needs to change; AgentService and
 * AgentToolDefinitions stay the same, since "tool schema in, tool_calls out" is the
 * common contract across providers (though the exact JSON field names differ slightly).
 */
@Component
public class LlmClient {

    private final WebClient webClient;

    @Value("${app.llm.api-key}")
    private String apiKey;

    @Value("${app.llm.api-url}")
    private String apiUrl;

    @Value("${app.llm.model}")
    private String model;

    public LlmClient(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Sends the user's natural-language command plus our tool schemas.
     * Returns the raw parsed JSON response so AgentService can pull out
     * whichever tool_call the model chose.
     */
    public JsonNode requestToolCall(String userCommand) {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of(
                                "role", "system",
                                "content", "You are an assistant that manages the user's tasks. "
                                        + "Always respond by calling exactly one of the provided tools "
                                        + "that best matches the user's request. Today's date context "
                                        + "should be inferred from the user's message when they mention "
                                        + "relative dates like 'Friday' or 'tomorrow'."
                        ),
                        Map.of("role", "user", "content", userCommand)
                ),
                "tools", AgentToolDefinitions.allTools(),
                "tool_choice", "auto"
        );

        return webClient.post()
                .uri(apiUrl)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(status -> status.isError(), clientResponse ->
                clientResponse.bodyToMono(String.class).map(errorBody ->
                        new RuntimeException("LLM API error (" + clientResponse.statusCode() + "): " + errorBody)
                        )
                )
                .bodyToMono(JsonNode.class)
                .block(); // simple synchronous call is fine for a single agent command endpoint
    }
}
