package com.devdhar.taskmanager.service.agent;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

/**
 * Checks and converts the arguments the model sent for one tool call. The model can send
 * anything (wrong case, wrong types, bad dates), so nothing reaches TaskService unchecked.
 * Every problem becomes a {@link ToolArgumentException} whose message is written for the
 * model: AgentService hands it back as the tool result so the model can fix its call.
 */
final class ToolArgs {

    static class ToolArgumentException extends RuntimeException {
        ToolArgumentException(String message) {
            super(message);
        }
    }

    private final Map<String, Object> args;

    ToolArgs(Map<String, Object> args) {
        this.args = args;
    }

    String requiredString(String name) {
        String value = optString(name);
        if (value == null) throw new ToolArgumentException(name + " is required");
        return value;
    }

    /** Missing, null and blank all mean "not provided". */
    String optString(String name) {
        Object value = args.get(name);
        if (value == null) return null;
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    Long requiredLong(String name) {
        Object value = args.get(name);
        if (value == null) throw new ToolArgumentException(name + " is required");
        try {
            return value instanceof Number n ? n.longValue() : Long.parseLong(value.toString().trim());
        } catch (NumberFormatException e) {
            throw new ToolArgumentException(name + " must be a whole number (got '" + value + "')."
                    + " Use listTasks with titleContains to look up a task id.");
        }
    }

    /** Case-insensitive; "in progress" and "in-progress" are accepted for IN_PROGRESS. */
    <E extends Enum<E>> E optEnum(String name, Class<E> type) {
        String text = optString(name);
        if (text == null) return null;
        String normalised = text.toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        return Arrays.stream(type.getEnumConstants())
                .filter(c -> c.name().equals(normalised))
                .findFirst()
                .orElseThrow(() -> new ToolArgumentException(name + " must be one of "
                        + Arrays.toString(type.getEnumConstants()) + " (got '" + text + "')"));
    }

    LocalDate optDate(String name) {
        String text = optString(name);
        if (text == null) return null;
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            throw new ToolArgumentException(name + " must be a real date formatted YYYY-MM-DD (got '" + text + "')");
        }
    }
}
