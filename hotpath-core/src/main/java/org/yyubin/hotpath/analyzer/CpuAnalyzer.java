package org.yyubin.hotpath.analyzer;

import org.yyubin.hotpath.model.CpuSummary;
import org.yyubin.hotpath.model.Finding;
import org.yyubin.hotpath.model.Finding.Severity;
import org.yyubin.hotpath.reader.handler.CpuHandler;

import java.util.*;

public class CpuAnalyzer {

    private static final double HIGH_CPU_THRESHOLD = 0.80;

    public CpuSummary buildSummary(CpuHandler handler) {
        var samples = handler.getSamples();
        if (samples.isEmpty()) {
            return new CpuSummary(0, 0, 0, List.of());
        }

        double sumUser   = 0, maxUser = 0;
        double sumSystem = 0;
        for (var s : samples) {
            sumUser   += s.user();
            maxUser    = Math.max(maxUser, s.user());
            sumSystem += s.system();
        }
        double avgUser   = sumUser   / samples.size();
        double avgSystem = sumSystem / samples.size();

        // Hot methods: 상위 10개
        int totalSamples = handler.getExecutionSamples().values().stream()
                .mapToInt(Integer::intValue).sum();

        List<CpuSummary.HotMethod> hotMethods = handler.getExecutionSamples().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .map(e -> {
                    String fqn = e.getKey();
                    int idx = fqn.lastIndexOf('#');
                    String className  = idx >= 0 ? fqn.substring(0, idx) : fqn;
                    String methodName = idx >= 0 ? fqn.substring(idx + 1) : "";
                    double pct = totalSamples > 0 ? (e.getValue() * 100.0 / totalSamples) : 0;
                    return new CpuSummary.HotMethod(methodName, className, e.getValue(), pct);
                })
                .toList();

        return new CpuSummary(avgUser, maxUser, avgSystem, hotMethods);
    }

    public List<Finding> analyze(CpuSummary summary) {
        List<Finding> findings = new ArrayList<>();

        if (summary.maxUser() > HIGH_CPU_THRESHOLD) {
            Severity sev = summary.avgUser() > HIGH_CPU_THRESHOLD ? Severity.CRITICAL : Severity.WARNING;
            findings.add(new Finding(
                    sev,
                    "CPU",
                    String.format("높은 CPU 사용률 (최대 %.0f%%)", summary.maxUser() * 100),
                    String.format("JVM CPU 사용률 최대 %.0f%%, 평균 %.0f%%",
                            summary.maxUser() * 100, summary.avgUser() * 100),
                    "Hot Method 상위 항목을 확인하고 불필요한 계산이나 바쁜 대기(busy-wait)가 없는지 점검하세요."
            ));
        }

        if (!summary.hotMethods().isEmpty()) {
            var top = summary.hotMethods().getFirst();
            if (top.percent() > 20) {
                findings.add(new Finding(
                        Severity.WARNING,
                        "CPU",
                        String.format("단일 메서드 CPU 집중: %s#%s (%.1f%%)", top.className(), top.methodName(), top.percent()),
                        String.format("%s#%s 메서드가 전체 CPU 샘플의 %.1f%%를 차지합니다.",
                                top.className(), top.methodName(), top.percent()),
                        "해당 메서드의 알고리즘 복잡도 또는 호출 빈도를 검토하세요."
                ));
            }
        }

        return findings;
    }
}
