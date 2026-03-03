package org.yyubin.hotpath.analyzer;

import org.yyubin.hotpath.model.Finding;
import org.yyubin.hotpath.model.Finding.Severity;
import org.yyubin.hotpath.model.MemorySummary;
import org.yyubin.hotpath.reader.handler.MemoryHandler;

import java.util.*;

public class MemoryAnalyzer {

    private static final double HIGH_HEAP_USAGE = 0.85;

    public MemorySummary buildSummary(MemoryHandler handler, long recordingDurationSeconds) {
        var heapSamples  = handler.getHeapSamples();
        var allocSamples = handler.getAllocSamples();

        long maxHeap       = 0;
        long maxCommitted  = 0;
        long sumUsed       = 0;

        for (var s : heapSamples) {
            maxHeap      = Math.max(maxHeap, s.usedBytes());
            maxCommitted = Math.max(maxCommitted, s.committedBytes());
            sumUsed     += s.usedBytes();
        }
        double avgUsage = (maxHeap > 0 && !heapSamples.isEmpty())
                ? (double) sumUsed / heapSamples.size() / maxHeap : 0;
        double maxUsage = maxCommitted > 0 ? (double) maxHeap / maxCommitted : 0;

        // 총 할당량
        long totalAlloc = allocSamples.stream().mapToLong(MemoryHandler.AllocSample::bytes).sum();
        double allocRate = recordingDurationSeconds > 0
                ? (double) totalAlloc / recordingDurationSeconds : 0;

        // Top allocators
        Map<String, Long> byClass = new HashMap<>();
        for (var s : allocSamples) {
            byClass.merge(s.className(), s.bytes(), Long::sum);
        }
        List<MemorySummary.TopAllocator> topAllocators = byClass.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(e -> new MemorySummary.TopAllocator(e.getKey(), e.getValue()))
                .toList();

        return new MemorySummary(maxHeap, maxCommitted, avgUsage, maxUsage, totalAlloc, allocRate, topAllocators);
    }

    public List<Finding> analyze(MemorySummary summary) {
        List<Finding> findings = new ArrayList<>();

        if (summary.maxHeapUsagePercent() > HIGH_HEAP_USAGE) {
            findings.add(new Finding(
                    Severity.WARNING,
                    "Memory",
                    String.format("힙 사용률 %.0f%% 도달", summary.maxHeapUsagePercent() * 100),
                    String.format("힙 사용률이 최대 %.0f%%까지 상승했습니다. OutOfMemoryError 위험이 있습니다.",
                            summary.maxHeapUsagePercent() * 100),
                    "-Xmx를 늘리거나 메모리 누수 여부를 heap dump로 확인하세요."
            ));
        }

        if (!summary.topAllocators().isEmpty()) {
            var top = summary.topAllocators().getFirst();
            long mb = top.allocatedBytes() / (1024 * 1024);
            if (mb > 100) {
                findings.add(new Finding(
                        Severity.INFO,
                        "Memory",
                        String.format("최다 할당 클래스: %s (%d MB)", top.className(), mb),
                        String.format("%s 타입 객체가 총 %d MB 할당되었습니다.", top.className(), mb),
                        "해당 클래스의 객체 생성 빈도와 생존 기간을 검토하세요."
                ));
            }
        }

        return findings;
    }
}
