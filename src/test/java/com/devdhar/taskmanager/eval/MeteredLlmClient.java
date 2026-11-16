package com.devdhar.taskmanager.eval;

import com.devdhar.taskmanager.service.agent.LlmClient;
import com.devdhar.taskmanager.service.agent.LlmReply;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * Wraps the real LlmClient to count calls and tokens (from the provider's "usage" field)
 * per command, and retries rate-limit (429) errors so a free-tier limit doesn't get
 * counted as an agent failure. Test-only: production code is untouched.
 */
class MeteredLlmClient extends LlmClient {

    private static final int MAX_ATTEMPTS = 5;

    private int calls;
    private long promptTokens;
    private long completionTokens;

    MeteredLlmClient(WebClient webClient, String apiKey, String apiUrl, String model) {
        super(webClient);
        // LlmClient's config fields are private @Value fields; set them directly.
        ReflectionTestUtils.setField(this, LlmClient.class, "apiKey", apiKey, String.class);
        ReflectionTestUtils.setField(this, LlmClient.class, "apiUrl", apiUrl, String.class);
        ReflectionTestUtils.setField(this, LlmClient.class, "model", model, String.class);
    }

    void resetCounters() {
        calls = 0;
        promptTokens = 0;
        completionTokens = 0;
    }

    int calls() { return calls; }
    long promptTokens() { return promptTokens; }
    long completionTokens() { return completionTokens; }

    @Override
    public LlmReply chat(List<Map<String, Object>> messages) {
        for (int attempt = 1; ; attempt++) {
            try {
                LlmReply reply = super.chat(messages);
                calls++;
                promptTokens += reply.promptTokens();
                completionTokens += reply.completionTokens();
                return reply;
            } catch (RuntimeException e) {
                boolean rateLimited = String.valueOf(e.getMessage()).contains("429");
                if (!rateLimited || attempt == MAX_ATTEMPTS) {
                    throw e;
                }
                sleep(attempt * 10_000L); // 10s, 20s, 30s, 40s
            }
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
