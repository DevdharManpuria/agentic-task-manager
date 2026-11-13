package com.devdhar.taskmanager.eval;

import java.util.List;

/** The outcome of running one eval case. */
public record CaseResult(
        String id,
        String category,
        String command,
        boolean passed,
        List<String> failures,
        String reply,
        long latencyMs,
        int llmCalls,
        long promptTokens,
        long completionTokens
) {}
