package com.devdhar.taskmanager.eval;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * Turns the date shorthand used in cases.json into a real date, relative to "today"
 * at run time, so the eval set never goes stale.
 *
 *   "+3d"          -> three days from today
 *   "next:MONDAY"  -> the first Monday strictly after today
 *   "2030-01-02"   -> an absolute date
 */
public final class EvalDates {

    private EvalDates() {}

    public static LocalDate resolve(String spec, LocalDate today) {
        if (spec == null || spec.isBlank()) {
            return null;
        }
        if (spec.startsWith("+") && spec.endsWith("d")) {
            return today.plusDays(Long.parseLong(spec.substring(1, spec.length() - 1)));
        }
        if (spec.startsWith("next:")) {
            DayOfWeek day = DayOfWeek.valueOf(spec.substring("next:".length()).toUpperCase());
            return today.with(TemporalAdjusters.next(day));
        }
        return LocalDate.parse(spec);
    }
}
