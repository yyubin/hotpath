package org.yyubin.hotpath.model;

import java.util.List;

public record AnalysisResult(
        RecordingMeta meta,
        List<TimeBucket> timeline,
        CpuSummary cpu,
        GcSummary gc,
        MemorySummary memory,
        ThreadSummary threads,
        List<Finding> findings
) {}
