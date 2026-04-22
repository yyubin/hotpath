package org.yyubin.hotpath.analyzer;

import org.yyubin.hotpath.i18n.Messages;
import org.yyubin.hotpath.model.Finding;
import org.yyubin.hotpath.model.Finding.Severity;
import org.yyubin.hotpath.model.MemorySummary;
import org.yyubin.hotpath.reader.handler.MemoryHandler;

import java.util.*;

public class MemoryAnalyzer {

    private static final double HIGH_HEAP_USAGE = 0.85;

    private final Messages messages;

    public MemoryAnalyzer(Messages messages) {
        this.messages = messages;
    }

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
                    messages.format("memory.high_heap.title", summary.maxHeapUsagePercent() * 100),
                    messages.format("memory.high_heap.desc", summary.maxHeapUsagePercent() * 100),
                    messages.get("memory.high_heap.rec")
            ));
        }

        if (!summary.topAllocators().isEmpty()) {
            var top = summary.topAllocators().getFirst();
            long mb = top.allocatedBytes() / (1024 * 1024);
            if (mb > 100) {
                findings.add(new Finding(
                        Severity.INFO,
                        "Memory",
                        messages.format("memory.top_allocator.title", top.className(), mb),
                        messages.format("memory.top_allocator.desc", top.className(), mb),
                        messages.get("memory.top_allocator.rec")
                ));
            }
        }

        return findings;
    }
}
