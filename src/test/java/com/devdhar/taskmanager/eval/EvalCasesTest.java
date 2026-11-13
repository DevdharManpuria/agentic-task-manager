package com.devdhar.taskmanager.eval;

import com.devdhar.taskmanager.entity.Task;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Offline sanity checks on the eval data itself (runs in the normal build, no LLM needed):
 * a typo in cases.json should fail here, not silently skew an eval score.
 */
class EvalCasesTest {

    private final List<EvalCase> cases = EvalCase.loadAll();
    private final LocalDate today = LocalDate.of(2026, 11, 13);

    @Test
    void casesFileHasEnoughCasesWithUniqueIds() {
        assertTrue(cases.size() >= 25, "expected at least 25 cases, got " + cases.size());
        Set<String> ids = new HashSet<>();
        for (EvalCase c : cases) {
            assertTrue(ids.add(c.id()), "duplicate case id: " + c.id());
        }
    }

    @Test
    void everyCaseHasCommandCategoryAndAtLeastOneCheck() {
        for (EvalCase c : cases) {
            assertTrue(c.command() != null && !c.command().isBlank(), c.id() + ": missing command");
            assertTrue(c.category() != null && !c.category().isBlank(), c.id() + ": missing category");
            EvalCase.Expect e = c.expect();
            assertTrue(e != null, c.id() + ": missing expect");
            boolean hasCheck = e.taskCount() != null || notEmpty(e.tasks()) || notEmpty(e.absent())
                    || notEmpty(e.replyMustContain()) || notEmpty(e.replyMustNotContain());
            assertTrue(hasCheck, c.id() + ": expect has no checks");
        }
    }

    @Test
    void enumsAndDatesInCasesAreValid() {
        for (EvalCase c : cases) {
            for (EvalCase.SeedTask s : c.seeds()) {
                assertTrue(s.title() != null && !s.title().isBlank(), c.id() + ": seed without title");
                if (s.status() != null) Task.TaskStatus.valueOf(s.status());
                if (s.priority() != null) Task.TaskPriority.valueOf(s.priority());
                EvalDates.resolve(s.dueDate(), today);
            }
            if (c.expect().tasks() != null) {
                for (EvalCase.TaskExpect t : c.expect().tasks()) {
                    assertTrue(t.titleContains() != null, c.id() + ": task expectation without titleContains");
                    if (t.status() != null) Task.TaskStatus.valueOf(t.status());
                    if (t.priority() != null) Task.TaskPriority.valueOf(t.priority());
                    EvalDates.resolve(t.dueDate(), today);
                }
            }
        }
    }

    @Test
    void idPlaceholdersPointAtSeededTasks() {
        for (EvalCase c : cases) {
            Set<String> seededTitles = new HashSet<>();
            c.seeds().forEach(s -> seededTitles.add(s.title()));
            for (String referenced : c.referencedTitles()) {
                assertTrue(seededTitles.contains(referenced),
                        c.id() + ": {{id:" + referenced + "}} has no matching seed task");
            }
        }
    }

    @Test
    void renderCommandSubstitutesIds() {
        EvalCase c = cases.stream().filter(x -> x.id().equals("update-id-01")).findFirst().orElseThrow();
        assertEquals("mark task 17 as completed", c.renderCommand(java.util.Map.of("Write report", 17L)));
    }

    @Test
    void dateShorthandResolvesRelativeToToday() {
        assertEquals(LocalDate.of(2026, 11, 14), EvalDates.resolve("+1d", today));
        assertEquals(LocalDate.of(2026, 11, 16), EvalDates.resolve("+3d", today));
        // 2026-11-13 is a Friday, so the next Monday is the 16th (never "today").
        assertEquals(DayOfWeek.FRIDAY, today.getDayOfWeek());
        assertEquals(LocalDate.of(2026, 11, 16), EvalDates.resolve("next:MONDAY", today));
        assertEquals(LocalDate.of(2026, 11, 20), EvalDates.resolve("next:FRIDAY", today));
        assertEquals(LocalDate.of(2030, 1, 2), EvalDates.resolve("2030-01-02", today));
        assertNull(EvalDates.resolve(null, today));
    }

    private static boolean notEmpty(List<?> list) {
        return list != null && !list.isEmpty();
    }
}
