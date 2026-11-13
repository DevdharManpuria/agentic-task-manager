package com.devdhar.taskmanager.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Unexpected exceptions must not leak their message (SQL errors, class names, paths)
 * to the caller; the response body is a fixed, generic message.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void genericHandlerHidesTheExceptionMessage() {
        Exception boom = new RuntimeException("could not execute statement; SQL [insert into tasks ...]");

        ResponseEntity<Map<String, Object>> response = handler.handleGeneric(boom);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        String error = String.valueOf(response.getBody().get("error"));
        assertFalse(error.contains("SQL"));
        assertFalse(error.contains("tasks"));
    }
}
