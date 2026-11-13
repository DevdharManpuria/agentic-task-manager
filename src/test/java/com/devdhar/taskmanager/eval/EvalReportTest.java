package com.devdhar.taskmanager.eval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EvalReportTest {

    private CaseResult result(String id, String category, boolean passed, long latencyMs, int calls) {
        return new CaseResult(id, category, "cmd " + id, passed,
                passed ? List.of() : List.of("status is PENDING, expected COMPLETED"),
                "reply", latencyMs, calls, 100, 20);
    }

    @Test
    void markdownReportSummarisesRateByCategoryAndFailures() {
        List<CaseResult> results = List.of(
                result("a-1", "create", true, 100, 1),
                result("a-2", "create", false, 300, 1),
                result("b-1", "delete", true, 200, 2),
                result("b-2", "delete", true, 400, 2));

        String md = EvalReport.toMarkdown("baseline", "some-model", results);

        assertTrue(md.contains("3 / 4 (75%)"), md);
        assertTrue(md.contains("Avg LLM calls per command: 1.50"), md);
        assertTrue(md.contains("| create | 1 | 2 |"), md);
        assertTrue(md.contains("| delete | 2 | 2 |"), md);
        assertTrue(md.contains("**a-2**"), md);
        assertTrue(md.contains("status is PENDING, expected COMPLETED"), md);
        // p50 of [100,200,300,400] -> 200, p95 -> 400
        assertTrue(md.contains("p50 200 ms, p95 400 ms"), md);
    }
}
