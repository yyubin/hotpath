package org.yyubin.hotpath.model.flamegraph;

import java.time.Instant;

public record FlameGraphMeta(
        String  sourceFile,
        long    totalSamples,
        int     stackCount,    // 고유 스택 경로 수
        int     frameCount,    // 고유 프레임 수
        Instant generatedAt
) {}
