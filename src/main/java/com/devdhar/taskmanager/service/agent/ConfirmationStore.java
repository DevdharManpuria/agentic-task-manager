package com.devdhar.taskmanager.service.agent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds deletes the agent has proposed but the user has not approved yet. The model can
 * only ever add to this store; the delete itself runs from the confirm endpoint, so a
 * confused or tricked model cannot remove anything on its own.
 *
 * A plan is single use, expires after a few minutes, and can only be taken by the user who
 * created it. It lives in memory, which is fine for one server instance (see README limits).
 */
@Component
public class ConfirmationStore {

    public record PendingDelete(String id, Long userId, List<Long> taskIds, List<String> titles, Instant expiresAt) {}

    private final Map<String, PendingDelete> pending = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration ttl;

    @Autowired
    public ConfirmationStore() {
        this(Clock.systemUTC(), Duration.ofMinutes(5));
    }

    ConfirmationStore(Clock clock, Duration ttl) {
        this.clock = clock;
        this.ttl = ttl;
    }

    public PendingDelete create(Long userId, List<Long> taskIds, List<String> titles) {
        Instant now = clock.instant();
        pending.values().removeIf(p -> p.expiresAt().isBefore(now)); // keep the map from growing forever
        PendingDelete plan = new PendingDelete(UUID.randomUUID().toString(), userId,
                List.copyOf(taskIds), List.copyOf(titles), now.plus(ttl));
        pending.put(plan.id(), plan);
        return plan;
    }

    /** Removes and returns the plan if it exists, is not expired and belongs to this user. */
    public Optional<PendingDelete> take(String id, Long userId) {
        PendingDelete plan = pending.get(id);
        if (plan == null || !plan.userId().equals(userId)) return Optional.empty();
        if (!pending.remove(id, plan)) return Optional.empty(); // someone else took it first
        return plan.expiresAt().isBefore(clock.instant()) ? Optional.empty() : Optional.of(plan);
    }
}
