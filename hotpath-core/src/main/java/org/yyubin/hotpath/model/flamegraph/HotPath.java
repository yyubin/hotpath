package org.yyubin.hotpath.model.flamegraph;

import java.util.List;

public record HotPath(
        List<String> frames,   // 루트 → 리프 순서
        long         samples,
        double       pct
) {}
