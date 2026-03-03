package org.yyubin.hotpath.reader;

import org.yyubin.hotpath.model.TimeBucket;
import org.yyubin.hotpath.reader.handler.CpuHandler;
import org.yyubin.hotpath.reader.handler.GcHandler;
import org.yyubin.hotpath.reader.handler.MemoryHandler;
import org.yyubin.hotpath.reader.handler.ThreadHandler;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 각 핸들러의 raw 샘플을 1초 단위 TimeBucket으로 집계한다.
 */
public class TimelineBuilder {

    public static List<TimeBucket> build(ReadResult raw) {
        Instant start = raw.recordingStart().truncatedTo(ChronoUnit.SECONDS);
        Instant end   = raw.recordingEnd().truncatedTo(ChronoUnit.SECONDS);

        if (start.equals(end) || start.isAfter(end)) return List.of();

        long bucketCount = ChronoUnit.SECONDS.between(start, end) + 1;
        int n = (int) Math.min(bucketCount, 3600); // 최대 1시간

        // 각 버킷 인덱스 → 집계값
        double[] cpuUser   = new double[n];
        double[] cpuSystem = new double[n];
        int[]    cpuCount  = new int[n];

        long[]   heapUsed  = new long[n];
        int[]    heapCnt   = new int[n];

        int[]    gcCnt     = new int[n];
        long[]   gcPause   = new long[n];

        long[]   allocBytes = new long[n];

        int[]    threadCount = new int[n];
        int[]    threadCnt   = new int[n];

        int[]    lockCnt    = new int[n];

        // CPU samples
        for (var s : raw.cpu().getSamples()) {
            int idx = idx(s.time(), start, n);
            cpuUser[idx]   += s.user();
            cpuSystem[idx] += s.system();
            cpuCount[idx]++;
        }

        // Heap samples
        for (var s : raw.memory().getHeapSamples()) {
            int idx = idx(s.time(), start, n);
            heapUsed[idx] += s.usedBytes();
            heapCnt[idx]++;
        }

        // GC events
        for (var e : raw.gc().getEvents()) {
            Instant t = Instant.ofEpochMilli(e.startEpochMs());
            int idx = idx(t, start, n);
            gcCnt[idx]++;
            gcPause[idx] += e.pauseMs();
        }

        // Alloc samples
        for (var s : raw.memory().getAllocSamples()) {
            // AllocSample에는 시간 정보가 없으므로 총량만 마지막 버킷에 누적 (추후 개선)
            allocBytes[n - 1] += s.bytes();
        }

        // Thread counts
        for (var s : raw.thread().getThreadCounts()) {
            int idx = idx(s.time(), start, n);
            threadCount[idx] += (int) s.activeCount();
            threadCnt[idx]++;
        }

        // Lock contentions
        for (var c : raw.thread().getContentions()) {
            int idx = idx(c.time(), start, n);
            lockCnt[idx]++;
        }

        // 버킷 생성
        List<TimeBucket> buckets = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            buckets.add(new TimeBucket(
                    start.plusSeconds(i),
                    cpuCount[i]  > 0 ? cpuUser[i]   / cpuCount[i]  : 0,
                    cpuCount[i]  > 0 ? cpuSystem[i] / cpuCount[i]  : 0,
                    heapCnt[i]   > 0 ? heapUsed[i]  / heapCnt[i]   : 0,
                    0,
                    gcCnt[i],
                    gcPause[i],
                    allocBytes[i],
                    threadCnt[i] > 0 ? threadCount[i] / threadCnt[i] : 0,
                    lockCnt[i]
            ));
        }
        return buckets;
    }

    private static int idx(Instant t, Instant start, int max) {
        long s = ChronoUnit.SECONDS.between(start, t);
        return (int) Math.max(0, Math.min(s, max - 1));
    }
}
