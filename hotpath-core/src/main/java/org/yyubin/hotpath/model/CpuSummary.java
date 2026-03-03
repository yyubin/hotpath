package org.yyubin.hotpath.model;

import java.util.List;

public record CpuSummary(
        double avgUser,
        double maxUser,
        double avgSystem,
        List<HotMethod> hotMethods
) {
    public record HotMethod(
            String methodName,
            String className,
            int sampleCount,
            double percent
    ) {}
}
