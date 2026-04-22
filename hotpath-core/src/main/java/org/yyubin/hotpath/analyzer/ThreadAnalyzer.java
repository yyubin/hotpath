package org.yyubin.hotpath.analyzer;

import org.yyubin.hotpath.i18n.Messages;
import org.yyubin.hotpath.model.Finding;
import org.yyubin.hotpath.model.Finding.Severity;
import org.yyubin.hotpath.model.ThreadSummary;
import org.yyubin.hotpath.reader.handler.ThreadHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ThreadAnalyzer {

    private static final long HIGH_CONTENTION_MS = 100;

    private final Messages messages;

    public ThreadAnalyzer(Messages messages) {
        this.messages = messages;
    }

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
                    messages.format("thread.lock_contention.title", summary.totalLockContentionMs()),
                    messages.format("thread.lock_contention.desc", summary.totalLockContentionMs()),
                    messages.get("thread.lock_contention.rec")
            ));
        }

        if (!summary.topContentions().isEmpty()) {
            var worst = summary.topContentions().getFirst();
            findings.add(new Finding(
                    Severity.INFO,
                    "Thread",
                    messages.format("thread.worst_lock.title", worst.monitorClass(), worst.waitMs()),
                    messages.format("thread.worst_lock.desc", worst.monitorClass(), worst.blockedThread(), worst.waitMs()),
                    messages.get("thread.worst_lock.rec")
            ));
        }

        return findings;
    }
}
