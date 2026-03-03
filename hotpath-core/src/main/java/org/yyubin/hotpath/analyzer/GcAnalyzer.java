package org.yyubin.hotpath.analyzer;

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
                    String.format("GC Stop-The-World 최대 일시 정지 %d ms", summary.maxPauseMs()),
                    String.format("GC 최대 일시 정지 시간이 %d ms로 사용자 요청에 직접적인 영향을 줄 수 있습니다.", summary.maxPauseMs()),
                    "힙 크기(-Xmx)를 늘리거나 GC 알고리즘(ZGC, Shenandoah)을 검토하세요."
            ));
        } else if (summary.maxPauseMs() >= HIGH_PAUSE_MS) {
            findings.add(new Finding(
                    Severity.WARNING,
                    "GC",
                    String.format("GC 일시 정지 %d ms 감지", summary.maxPauseMs()),
                    String.format("GC 최대 일시 정지 %d ms, 평균 %.0f ms (총 %d회)",
                            summary.maxPauseMs(), summary.avgPauseMs(), summary.totalCount()),
                    "GC 로그를 상세 분석하거나 힙 할당 패턴을 확인하세요."
            ));
        }

        if (recordingDurationMs > 0) {
            double stwRatio = (double) summary.totalPauseMs() / recordingDurationMs;
            if (stwRatio > HIGH_STW_RATIO) {
                findings.add(new Finding(
                        Severity.WARNING,
                        "GC",
                        String.format("GC STW 누적 비율 %.1f%%", stwRatio * 100),
                        String.format("전체 녹화 시간 중 %.1f%%가 GC 일시 정지에 사용되었습니다.", stwRatio * 100),
                        "객체 생존 기간과 할당률을 줄이는 방향으로 코드를 개선하세요."
                ));
            }
        }

        return findings;
    }
}
