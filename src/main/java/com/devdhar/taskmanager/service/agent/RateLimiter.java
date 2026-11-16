package com.devdhar.taskmanager.service.agent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Sliding-window limit per user: at most {@code limit} agent commands per window. Every
 * command can cost several paid LLM calls, so this protects the API key and the bill.
 */
@Component
public class RateLimiter {

    private final Map<Long, Deque<Instant>> hits = new HashMap<>();
    private final int limit;
    private final Duration window;
    private final Clock clock;

    @Autowired
    public RateLimiter(@Value("${app.agent.rate-limit-per-minute:10}") int limit) {
        this(limit, Duration.ofMinutes(1), Clock.systemUTC());
    }

    RateLimiter(int limit, Duration window, Clock clock) {
        this.limit = limit;
        this.window = window;
        this.clock = clock;
    }

    /** Records a hit and returns true if the user is still within the limit. */
    public synchronized boolean tryAcquire(Long userId) {
        Instant now = clock.instant();
        Deque<Instant> recent = hits.computeIfAbsent(userId, id -> new ArrayDeque<>());
        while (!recent.isEmpty() && recent.peekFirst().isBefore(now.minus(window))) recent.removeFirst();
        if (recent.size() >= limit) return false;
        recent.addLast(now);
        return true;
    }
}
