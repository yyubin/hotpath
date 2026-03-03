package org.yyubin.hotpath.model;

import java.util.List;

public record ThreadSummary(
        int peakThreadCount,
        double avgThreadCount,
        long totalLockContentionMs,
        List<LockContention> topContentions
) {
    public record LockContention(
            String monitorClass,
            String blockedThread,
            long waitMs
    ) {}
}
