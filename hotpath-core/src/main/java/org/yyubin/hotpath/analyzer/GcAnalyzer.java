package org.yyubin.hotpath.analyzer;

import org.yyubin.hotpath.i18n.Messages;
import org.yyubin.hotpath.model.Finding;
import org.yyubin.hotpath.model.Finding.Severity;
import org.yyubin.hotpath.model.GcSummary;
import org.yyubin.hotpath.reader.handler.GcHandler;

import java.util.ArrayList;
import java.util.List;

public class GcAnalyzer {

    private static final long   HIGH_PAUSE_MS        = 200;
    private static final long   CRITICAL_PAUSE_MS    = 500;
    private static final double HIGH_STW_RATIO       = 0.05; // 전체 시간 대비 5%

    private final Messages messages;

    public GcAnalyzer(Messages messages) {
        this.messages = messages;
    }

    public GcSummary buildSummary(GcHandler handler) {
        var events = handler.getEvents();
        if (events.isEmpty()) {
            return new GcSummary(0, 0, 0, 0, List.of());
        }

        long totalPause = 0, maxPause = 0;
        for (var e : events) {
            totalPause += e.pauseMs();
            maxPause    = Math.max(maxPause, e.pauseMs());
        }
        double avgPause = (double) totalPause / events.size();

        List<GcSummary.GcEvent> gcEvents = events.stream()
                .map(e -> new GcSummary.GcEvent(
                        e.startEpochMs(), e.pauseMs(), e.cause(), e.name(),
                        e.heapBeforeBytes(), e.heapAfterBytes()))
                .toList();

        return new GcSummary(events.size(), totalPause, maxPause, avgPause, gcEvents);
    }

    public List<Finding> analyze(GcSummary summary, long recordingDurationMs) {
        List<Finding> findings = new ArrayList<>();

        if (summary.totalCount() == 0) return findings;

        if (summary.maxPauseMs() >= CRITICAL_PAUSE_MS) {
            findings.add(new Finding(
                    Severity.CRITICAL,
                    "GC",
                    messages.format("gc.critical_pause.title", summary.maxPauseMs()),
                    messages.format("gc.critical_pause.desc", summary.maxPauseMs()),
                    messages.get("gc.critical_pause.rec")
            ));
        } else if (summary.maxPauseMs() >= HIGH_PAUSE_MS) {
            findings.add(new Finding(
                    Severity.WARNING,
                    "GC",
                    messages.format("gc.high_pause.title", summary.maxPauseMs()),
                    messages.format("gc.high_pause.desc", summary.maxPauseMs(), summary.avgPauseMs(), summary.totalCount()),
                    messages.get("gc.high_pause.rec")
            ));
        }

        if (recordingDurationMs > 0) {
            double stwRatio = (double) summary.totalPauseMs() / recordingDurationMs;
            if (stwRatio > HIGH_STW_RATIO) {
                findings.add(new Finding(
                        Severity.WARNING,
                        "GC",
                        messages.format("gc.high_stw_ratio.title", stwRatio * 100),
                        messages.format("gc.high_stw_ratio.desc", stwRatio * 100),
                        messages.get("gc.high_stw_ratio.rec")
                ));
            }
        }

        return findings;
    }
}
