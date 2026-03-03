package org.yyubin.hotpath.model;

public record Finding(
        Severity severity,
        String category,
        String title,
        String description,
        String recommendation
) {
    public enum Severity {
        INFO, WARNING, CRITICAL
    }
}
