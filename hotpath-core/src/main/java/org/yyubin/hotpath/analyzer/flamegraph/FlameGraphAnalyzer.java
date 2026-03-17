package org.yyubin.hotpath.analyzer.flamegraph;

import org.yyubin.hotpath.model.Finding;
import org.yyubin.hotpath.model.Finding.Severity;
import org.yyubin.hotpath.model.flamegraph.*;

import java.util.*;

public class FlameGraphAnalyzer {

    private static final int    TOP_N              = 10;
    private static final int    TOP_PATHS          = 5;
    private static final double CRITICAL_SELF_PCT  = 50.0;
    private static final double WARNING_SELF_PCT   = 30.0;
    private static final int    MAX_DEPTH_WARNING  = 200;
    private static final double JVM_INTERNAL_INFO  = 20.0;
    private static final double LOW_APP_CODE_INFO  = 10.0;

    /** JDK 표준 라이브러리 패키지 접두사 */
    private static final List<String> JDK_PREFIXES = List.of(
            "java.", "javax.", "sun.", "jdk.", "com.sun."
    );

    /** 주요 프레임워크 패키지 접두사 */
    private static final List<String> FRAMEWORK_PREFIXES = List.of(
            "org.springframework.", "io.netty.", "org.apache.", "io.undertow.",
            "org.hibernate.", "com.zaxxer.", "reactor.", "io.micrometer.",
            "ch.qos.logback.", "org.slf4j."
    );

    /** JVM 내부 프레임 패턴 */
    private static final List<String> JVM_INTERNAL_PATTERNS = List.of(
            "[unknown]", "[JVM]", "Interpreter", "vtable stub",
            "call stub", "compiled frame"
    );

    public FlameGraphSummary buildSummary(FlameNode root) {
        long totalSamples = root.getTotal();

        // DFS로 전체 노드를 수집
        List<FlameNode> allNodes = new ArrayList<>();
        collectNodes(root, allNodes);

        // self time 기준 상위 N개 (root 제외)
        List<HotFrame> bySelf = allNodes.stream()
                .filter(n -> !n.getName().equals("root"))
                .filter(n -> n.getSelf() > 0)
                .sorted(Comparator.comparingLong(FlameNode::getSelf).reversed())
                .limit(TOP_N)
                .map(n -> toHotFrame(n, totalSamples))
                .toList();

        // total time 기준 상위 N개 (root 제외)
        List<HotFrame> byTotal = allNodes.stream()
                .filter(n -> !n.getName().equals("root"))
                .sorted(Comparator.comparingLong(FlameNode::getTotal).reversed())
                .limit(TOP_N)
                .map(n -> toHotFrame(n, totalSamples))
                .toList();

        // critical path: 각 단계에서 total이 가장 높은 자식을 따라가며 경로 추출
        List<HotPath> criticalPaths = extractCriticalPaths(root, totalSamples);

        // 패키지 분류별 비율 계산
        Map<String, Double> breakdown = buildPackageBreakdown(allNodes, totalSamples);

        int maxDepth = computeMaxDepth(root);

        return new FlameGraphSummary(totalSamples, maxDepth, bySelf, byTotal, criticalPaths, breakdown);
    }

    public List<Finding> analyze(FlameGraphSummary summary) {
        List<Finding> findings = new ArrayList<>();

        // self time 집중도 체크
        if (!summary.hotFramesBySelf().isEmpty()) {
            HotFrame top = summary.hotFramesBySelf().getFirst();
            if (top.selfPct() > CRITICAL_SELF_PCT) {
                findings.add(new Finding(
                        Severity.CRITICAL,
                        "FlameGraph",
                        String.format("심각한 CPU 병목: %s (self %.1f%%)", top.name(), top.selfPct()),
                        String.format("'%s' 메서드가 전체 샘플의 %.1f%%를 단독으로 차지합니다.", top.name(), top.selfPct()),
                        "해당 메서드의 알고리즘 복잡도와 호출 빈도를 우선적으로 점검하세요."
                ));
            } else if (top.selfPct() > WARNING_SELF_PCT) {
                findings.add(new Finding(
                        Severity.WARNING,
                        "FlameGraph",
                        String.format("특정 메서드에 CPU 집중: %s (self %.1f%%)", top.name(), top.selfPct()),
                        String.format("'%s' 메서드가 전체 샘플의 %.1f%%를 차지합니다.", top.name(), top.selfPct()),
                        "해당 메서드 내부 로직에 최적화 여지가 있는지 확인하세요."
                ));
            }
        }

        // 스택 깊이 이상
        if (summary.maxDepth() > MAX_DEPTH_WARNING) {
            findings.add(new Finding(
                    Severity.WARNING,
                    "FlameGraph",
                    String.format("비정상적으로 깊은 스택 감지 (최대 깊이 %d)", summary.maxDepth()),
                    String.format("스택 최대 깊이가 %d으로, 재귀 호출이나 지나치게 깊은 호출 체인이 의심됩니다.", summary.maxDepth()),
                    "StackOverflowError 위험이 있는지 확인하고, 재귀를 반복문으로 전환하는 것을 고려하세요."
            ));
        }

        // JVM Internal 비율
        double jvmInternalPct = summary.packageBreakdown().getOrDefault("JVM Internal", 0.0);
        if (jvmInternalPct > JVM_INTERNAL_INFO) {
            findings.add(new Finding(
                    Severity.INFO,
                    "FlameGraph",
                    String.format("JVM 인터프리터/JIT 비중 높음 (%.1f%%)", jvmInternalPct),
                    String.format("샘플의 %.1f%%가 JVM 내부 프레임([unknown], Interpreter 등)에서 수집됐습니다.", jvmInternalPct),
                    "JVM 워밍업이 충분히 이루어졌는지 확인하고, 프로파일링 시작 전 애플리케이션을 충분히 예열하세요."
            ));
        }

        // 앱 코드 비율이 낮은 경우
        double appPct = summary.packageBreakdown().getOrDefault("Application", 0.0);
        if (appPct < LOW_APP_CODE_INFO && summary.totalSamples() > 100) {
            findings.add(new Finding(
                    Severity.INFO,
                    "FlameGraph",
                    String.format("애플리케이션 코드 비중 낮음 (%.1f%%)", appPct),
                    String.format("샘플의 %.1f%%만이 애플리케이션 코드에서 수집됐습니다. 대부분의 시간이 프레임워크나 JDK에서 소비되고 있습니다.", appPct),
                    "I/O 대기, 프레임워크 오버헤드, 직렬화 비용 등을 점검하세요."
            ));
        }

        return findings;
    }

    // -------------------------------------------------------------------------
    // private helpers
    // -------------------------------------------------------------------------

    private void collectNodes(FlameNode node, List<FlameNode> acc) {
        acc.add(node);
        for (FlameNode child : node.getChildren()) {
            collectNodes(child, acc);
        }
    }

    private HotFrame toHotFrame(FlameNode node, long totalSamples) {
        double selfPct  = totalSamples > 0 ? node.getSelf()  * 100.0 / totalSamples : 0;
        double totalPct = totalSamples > 0 ? node.getTotal() * 100.0 / totalSamples : 0;
        return new HotFrame(node.getName(), node.getSelf(), node.getTotal(), selfPct, totalPct);
    }

    /**
     * 루트에서 출발해 각 단계에서 total이 가장 높은 자식을 따라가며
     * critical path를 추출한다. 상위 TOP_PATHS개의 독립 경로를 반환한다.
     */
    private List<HotPath> extractCriticalPaths(FlameNode root, long totalSamples) {
        List<HotPath> paths = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        for (int i = 0; i < TOP_PATHS; i++) {
            List<String> path = new ArrayList<>();
            FlameNode current = root;

            while (!current.getChildren().isEmpty()) {
                FlameNode next = current.getChildren().stream()
                        .max(Comparator.comparingLong(FlameNode::getTotal))
                        .orElse(null);
                if (next == null) break;

                path.add(next.getName());
                current = next;
            }

            if (path.isEmpty()) break;

            String key = String.join(";", path);
            if (visited.contains(key)) break;
            visited.add(key);

            long samples = current.getTotal();
            double pct = totalSamples > 0 ? samples * 100.0 / totalSamples : 0;
            paths.add(new HotPath(Collections.unmodifiableList(path), samples, pct));

            // 다음 경로 탐색을 위해 최상위 자식 제외 후 두 번째로 큰 경로를 탐색
            // 현재 구현은 단일 greedy path만 추출하므로 중복이 발생하면 중단
            break;
        }

        return Collections.unmodifiableList(paths);
    }

    private Map<String, Double> buildPackageBreakdown(List<FlameNode> allNodes, long totalSamples) {
        long jdkSelf      = 0;
        long frameworkSelf = 0;
        long jvmSelf      = 0;
        long appSelf      = 0;

        for (FlameNode node : allNodes) {
            if (node.getSelf() == 0) continue;
            String name = node.getName();
            long self = node.getSelf();

            if (isJvmInternal(name))      jvmSelf       += self;
            else if (isJdk(name))         jdkSelf        += self;
            else if (isFramework(name))   frameworkSelf  += self;
            else                          appSelf        += self;
        }

        long selfTotal = jdkSelf + frameworkSelf + jvmSelf + appSelf;
        if (selfTotal == 0) return Map.of();

        Map<String, Double> breakdown = new LinkedHashMap<>();
        breakdown.put("Application",  round(appSelf        * 100.0 / selfTotal));
        breakdown.put("Framework",    round(frameworkSelf  * 100.0 / selfTotal));
        breakdown.put("JDK",          round(jdkSelf        * 100.0 / selfTotal));
        breakdown.put("JVM Internal", round(jvmSelf        * 100.0 / selfTotal));
        return Collections.unmodifiableMap(breakdown);
    }

    private int computeMaxDepth(FlameNode node) {
        if (node.getChildren().isEmpty()) return 0;
        int max = 0;
        for (FlameNode child : node.getChildren()) {
            max = Math.max(max, computeMaxDepth(child));
        }
        return max + 1;
    }

    private boolean isJvmInternal(String name) {
        for (String pattern : JVM_INTERNAL_PATTERNS) {
            if (name.startsWith(pattern)) return true;
        }
        return false;
    }

    private boolean isJdk(String name) {
        for (String prefix : JDK_PREFIXES) {
            if (name.startsWith(prefix)) return true;
        }
        return false;
    }

    private boolean isFramework(String name) {
        for (String prefix : FRAMEWORK_PREFIXES) {
            if (name.startsWith(prefix)) return true;
        }
        return false;
    }

    private double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
