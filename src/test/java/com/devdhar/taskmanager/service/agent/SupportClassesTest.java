package com.devdhar.taskmanager.service.agent;

import com.devdhar.taskmanager.service.agent.ToolArgs.ToolArgumentException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Small unit tests for the agent's helper classes. A movable clock makes time-based rules testable. */
class SupportClassesTest {

    /** A clock the test can move forward. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2030-01-01T00:00:00Z");

        void advance(Duration d) { now = now.plus(d); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }

        @Override public Clock withZone(ZoneId zone) { return this; }

        @Override public Instant instant() { return now; }
    }

    private enum Colour { RED, DARK_BLUE }

    @Test
    void toolArgsValidatesAndNormalises() {
        ToolArgs args = new ToolArgs(Map.of("n", "12", "c", "dark-blue", "d", "2030-02-03", "s", "  ", "bad", "soon"));
        assertEquals(12L, args.requiredLong("n"));
        assertEquals(Colour.DARK_BLUE, args.optEnum("c", Colour.class));
        assertEquals(LocalDate.of(2030, 2, 3), args.optDate("d"));
        assertNull(args.optString("s"));          // blank means missing
        assertNull(args.optEnum("missing", Colour.class));
        assertThrows(ToolArgumentException.class, () -> args.optDate("bad"));
        assertThrows(ToolArgumentException.class, () -> args.requiredString("s"));
        assertThrows(ToolArgumentException.class, () -> args.requiredLong("c"));
        assertThrows(ToolArgumentException.class, () -> new ToolArgs(Map.of("d", "2030-02-31")).optDate("d"));
    }

    @Test
    void confirmationIsSingleUseOwnedAndExpires() {
        MutableClock clock = new MutableClock();
        ConfirmationStore store = new ConfirmationStore(clock, Duration.ofMinutes(5));

        ConfirmationStore.PendingDelete plan = store.create(1L, List.of(5L), List.of("a"));
        assertTrue(store.take(plan.id(), 2L).isEmpty());        // another user cannot take it
        assertTrue(store.take(plan.id(), 1L).isPresent());
        assertTrue(store.take(plan.id(), 1L).isEmpty());        // already used

        ConfirmationStore.PendingDelete old = store.create(1L, List.of(6L), List.of("b"));
        clock.advance(Duration.ofMinutes(6));
        assertEquals(Optional.empty(), store.take(old.id(), 1L)); // expired
    }

    @Test
    void rateLimiterBlocksThenAllowsAgainAfterTheWindow() {
        MutableClock clock = new MutableClock();
        RateLimiter limiter = new RateLimiter(2, Duration.ofMinutes(1), clock);

        assertTrue(limiter.tryAcquire(1L));
        assertTrue(limiter.tryAcquire(1L));
        assertFalse(limiter.tryAcquire(1L));
        assertTrue(limiter.tryAcquire(2L));                     // other users are independent
        clock.advance(Duration.ofSeconds(61));
        assertTrue(limiter.tryAcquire(1L));
    }

    @Test
    void memoryKeepsOnlyRecentTurnsAndForgetsWhenIdle() {
        MutableClock clock = new MutableClock();
        ConversationMemory memory = new ConversationMemory(clock, 2, Duration.ofMinutes(10));

        memory.remember(1L, "a", "A");
        memory.remember(1L, "b", "B");
        memory.remember(1L, "c", "C");
        assertEquals(4, memory.history(1L).size());             // 2 turns x (user + assistant)
        assertEquals("b", memory.history(1L).get(0).get("content"));
        assertTrue(memory.history(2L).isEmpty());

        clock.advance(Duration.ofMinutes(11));
        assertTrue(memory.history(1L).isEmpty());
    }
}
