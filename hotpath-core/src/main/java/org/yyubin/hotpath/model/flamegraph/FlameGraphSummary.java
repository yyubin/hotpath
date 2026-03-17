package org.yyubin.hotpath.model.flamegraph;

import java.util.List;
import java.util.Map;

public record FlameGraphSummary(
        long                totalSamples,
        int                 maxDepth,
        List<HotFrame>      hotFramesBySelf,   // self time 상위 10개
        List<HotFrame>      hotFramesByTotal,  // total time 상위 10개
        List<HotPath>       criticalPaths,     // total 기준 상위 5개 경로
        Map<String, Double> packageBreakdown   // 패키지 분류별 CPU 비율 (%)
) {}
