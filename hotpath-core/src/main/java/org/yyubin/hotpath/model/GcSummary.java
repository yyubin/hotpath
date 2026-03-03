package org.yyubin.hotpath.model;

import java.util.List;

public record GcSummary(
        int totalCount,
        long totalPauseMs,
        long maxPauseMs,
        double avgPauseMs,
        List<GcEvent> events
) {
    public record GcEvent(
            long startEpochMs,
            long pauseMs,
            String cause,
            String name,
            long heapBeforeBytes,
            long heapAfterBytes
    ) {}
}
