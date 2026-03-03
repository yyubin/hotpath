package org.yyubin.hotpath.analyzer;

import org.yyubin.hotpath.model.Finding;
import org.yyubin.hotpath.model.Finding.Severity;
import org.yyubin.hotpath.model.ThreadSummary;
import org.yyubin.hotpath.reader.handler.ThreadHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ThreadAnalyzer {

    private static final long HIGH_CONTENTION_MS = 100;

    public ThreadSummary buildSummary(ThreadHandler handler) {
        var counts     = handler.getThreadCounts();
        var contentions = handler.getContentions();

        int peakCount = counts.stream()
                .mapToInt(s -> (int) s.activeCount())
                .max().orElse(0);
        double avgCount = counts.stream()
                .mapToLong(s -> s.activeCount())
                .average().orElse(0);

        long totalContentionMs = contentions.stream()
                .mapToLong(ThreadHandler.ContentionEvent::waitMs)
                .sum();

        List<ThreadSummary.LockContention> top = contentions.stream()
                .filter(c -> c.waitMs() >= HIGH_CONTENTION_MS)
                .sorted(Comparator.comparingLong(ThreadHandler.ContentionEvent::waitMs).reversed())
                .limit(10)
                .map(c -> new ThreadSummary.LockContention(c.monitorClass(), c.blockedThread(), c.waitMs()))
                .toList();

        return new ThreadSummary(peakCount, avgCount, totalContentionMs, top);
    }

    public List<Finding> analyze(ThreadSummary summary) {
        List<Finding> findings = new ArrayList<>();

        if (summary.totalLockContentionMs() > 1000) {
            Severity sev = summary.totalLockContentionMs() > 5000 ? Severity.CRITICAL : Severity.WARNING;
            findings.add(new Finding(
                    sev,
                    "Thread",
                    String.format("Lock Contention 누적 %d ms", summary.totalLockContentionMs()),
                    String.format("스레드 락 경합으로 누적 %d ms가 소비되었습니다.", summary.totalLockContentionMs()),
                    "동기화 범위를 줄이거나 ConcurrentHashMap 등 non-blocking 자료구조로 교체를 검토하세요."
            ));
        }

        if (!summary.topContentions().isEmpty()) {
            var worst = summary.topContentions().getFirst();
            findings.add(new Finding(
                    Severity.INFO,
                    "Thread",
                    String.format("최장 락 대기: %s (%d ms)", worst.monitorClass(), worst.waitMs()),
                    String.format("%s 모니터에서 '%s' 스레드가 %d ms 대기했습니다.",
                            worst.monitorClass(), worst.blockedThread(), worst.waitMs()),
                    "해당 모니터를 획득하는 임계 구역(critical section)의 작업량을 줄이세요."
            ));
        }

        return findings;
    }
}
