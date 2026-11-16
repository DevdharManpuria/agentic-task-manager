package com.devdhar.taskmanager.service.agent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Remembers the last few exchanges per user so follow-ups like "make that high priority"
 * work. Only the user's text and the agent's final reply are kept, not tool traffic, so
 * the prompt stays small. Old exchanges drop out, and an idle conversation is forgotten.
 */
@Component
public class ConversationMemory {

    private record Turn(String user, String assistant) {}

    private static final class Conversation {
        final Deque<Turn> turns = new ArrayDeque<>();
        Instant lastUsed;
    }

    private final Map<Long, Conversation> conversations = new HashMap<>();
    private final Clock clock;
    private final int maxTurns;
    private final Duration idleLimit;

    @Autowired
    public ConversationMemory() {
        this(Clock.systemUTC(), 4, Duration.ofMinutes(15));
    }

    ConversationMemory(Clock clock, int maxTurns, Duration idleLimit) {
        this.clock = clock;
        this.maxTurns = maxTurns;
        this.idleLimit = idleLimit;
    }

    /** Earlier exchanges as chat messages, oldest first. */
    public synchronized List<Map<String, Object>> history(Long userId) {
        Conversation c = conversations.get(userId);
        if (c == null) return List.of();
        if (c.lastUsed.plus(idleLimit).isBefore(clock.instant())) {
            conversations.remove(userId);
            return List.of();
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        for (Turn t : c.turns) {
            messages.add(Map.of("role", "user", "content", t.user()));
            messages.add(Map.of("role", "assistant", "content", t.assistant()));
        }
        return messages;
    }

    public synchronized void remember(Long userId, String userText, String assistantText) {
        Conversation c = conversations.computeIfAbsent(userId, id -> new Conversation());
        c.turns.addLast(new Turn(userText, assistantText));
        while (c.turns.size() > maxTurns) c.turns.removeFirst();
        c.lastUsed = clock.instant();
    }

    public synchronized void clear(Long userId) {
        conversations.remove(userId);
    }
}
