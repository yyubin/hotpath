package org.yyubin.hotpath.model;

import java.util.List;

public record MemorySummary(
        long maxHeapBytes,
        long committedHeapBytes,
        double avgHeapUsagePercent,
        double maxHeapUsagePercent,
        long totalAllocatedBytes,
        double allocRatePerSec,
        List<TopAllocator> topAllocators
) {
    public record TopAllocator(
            String className,
            long allocatedBytes
    ) {}
}
