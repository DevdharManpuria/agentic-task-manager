package com.devdhar.taskmanager.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Aggregates CaseResults into a markdown summary (for humans) and JSON (for tools). */
public final class EvalReport {

    private EvalReport() {}

    public static String toMarkdown(String label, String model, List<CaseResult> results) {
        long passed = results.stream().filter(CaseResult::passed).count();
        StringBuilder md = new StringBuilder();
        md.append("# Eval report: ").append(label).append("\n\n");
        md.append("- Model: `").append(model).append("`\n");
        md.append("- Cases: ").append(results.size()).append("\n");
        md.append(String.format("- **Task success rate: %d / %d (%.0f%%)**%n", passed, results.size(), pct(passed, results.size())));
        md.append(String.format("- Avg LLM calls per command: %.2f%n", avg(results.stream().mapToLong(CaseResult::llmCalls).sum(), results.size())));
        md.append(String.format("- Avg latency: %.0f ms (p50 %d ms, p95 %d ms)%n",
                avg(results.stream().mapToLong(CaseResult::latencyMs).sum(), results.size()),
                percentile(results, 50), percentile(results, 95)));
        md.append(String.format("- Tokens: %d prompt + %d completion%n%n",
                results.stream().mapToLong(CaseResult::promptTokens).sum(),
                results.stream().mapToLong(CaseResult::completionTokens).sum()));

        md.append("## By category\n\n| Category | Passed | Total |\n| --- | --- | --- |\n");
        Map<String, List<CaseResult>> byCategory = results.stream()
                .collect(Collectors.groupingBy(CaseResult::category, LinkedHashMap::new, Collectors.toList()));
        byCategory.forEach((category, list) -> md.append(String.format("| %s | %d | %d |%n",
                category, list.stream().filter(CaseResult::passed).count(), list.size())));

        md.append("\n## Failures\n\n");
        List<CaseResult> failed = results.stream().filter(r -> !r.passed()).toList();
        if (failed.isEmpty()) {
            md.append("None.\n");
        }
        for (CaseResult r : failed) {
            md.append("- **").append(r.id()).append("** (`").append(r.command()).append("`)\n");
            for (String f : r.failures()) {
                md.append("  - ").append(f).append("\n");
            }
        }
        return md.toString();
    }

    public static void write(Path dir, String baseName, String label, String model, List<CaseResult> results)
            throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(baseName + ".md"), toMarkdown(label, model, results));
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
                .writeValue(dir.resolve(baseName + ".json").toFile(), results);
    }

    private static double pct(long part, long whole) {
        return whole == 0 ? 0 : 100.0 * part / whole;
    }

    private static double avg(long sum, long count) {
        return count == 0 ? 0 : (double) sum / count;
    }

    private static long percentile(List<CaseResult> results, int p) {
        List<Long> sorted = results.stream().map(CaseResult::latencyMs).sorted().toList();
        if (sorted.isEmpty()) return 0;
        int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }
}
