package org.yyubin.hotpath.analyzer;

import org.yyubin.hotpath.i18n.Messages;
import org.yyubin.hotpath.model.CpuSummary;
import org.yyubin.hotpath.model.Finding;
import org.yyubin.hotpath.model.Finding.Severity;
import org.yyubin.hotpath.reader.handler.CpuHandler;

import java.util.*;

public class CpuAnalyzer {

    private static final double HIGH_CPU_THRESHOLD = 0.80;

    private final Messages messages;

    public CpuAnalyzer(Messages messages) {
        this.messages = messages;
    }

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
                    messages.format("cpu.high_usage.title", summary.maxUser() * 100),
                    messages.format("cpu.high_usage.desc", summary.maxUser() * 100, summary.avgUser() * 100),
                    messages.get("cpu.high_usage.rec")
            ));
        }

        if (!summary.hotMethods().isEmpty()) {
            var top = summary.hotMethods().getFirst();
            if (top.percent() > 20) {
                findings.add(new Finding(
                        Severity.WARNING,
                        "CPU",
                        messages.format("cpu.hot_method.title", top.className(), top.methodName(), top.percent()),
                        messages.format("cpu.hot_method.desc", top.className(), top.methodName(), top.percent()),
                        messages.get("cpu.hot_method.rec")
                ));
            }
        }

        return findings;
    }
}
