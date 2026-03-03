package org.yyubin.hotpath.model;

import java.time.Instant;

/**
 * 1초 단위 집계 슬롯.
 * 타임라인 차트의 x축 단위로 사용된다.
 */
public record TimeBucket(
        Instant timestamp,
        double cpuUser,
        double cpuSystem,
        long heapUsed,
        long heapCommitted,
        int gcCount,
        long gcPauseMs,
        long allocatedBytes,
        int threadCount,
        int lockContentionCount
) {}
