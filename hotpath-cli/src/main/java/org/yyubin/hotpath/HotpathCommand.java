package org.yyubin.hotpath;

import org.yyubin.hotpath.analyzer.*;
import org.yyubin.hotpath.analyzer.flamegraph.FlameGraphAnalyzer;
import org.yyubin.hotpath.i18n.Messages;
import org.yyubin.hotpath.model.*;
import org.yyubin.hotpath.model.flamegraph.*;
import org.yyubin.hotpath.reader.JfrReader;
import org.yyubin.hotpath.reader.ReadResult;
import org.yyubin.hotpath.reader.TimelineBuilder;
import org.yyubin.hotpath.reader.flamegraph.CollapsedStacksReader;
import org.yyubin.hotpath.reader.flamegraph.CollapsedStacksReader.StackEntry;
import org.yyubin.hotpath.reader.flamegraph.FlameGraphBuilder;
import org.yyubin.hotpath.renderer.FlameGraphHtmlRenderer;
import org.yyubin.hotpath.renderer.HtmlRenderer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Callable;

@Command(
        name        = "hotpath",
        description = "JFR 또는 Collapsed Stacks 파일을 분석하여 HTML 리포트를 생성합니다.",
        mixinStandardHelpOptions = true,
        version     = "0.1.0"
)
public class HotpathCommand implements Callable<Integer> {

    enum InputType { AUTO, JFR, FLAMEGRAPH }

    @Parameters(index = "0", paramLabel = "<file>", description = "분석할 파일 경로 (.jfr 또는 collapsed stacks)")
    private Path inputFile;

    @Option(names = {"-o", "--output"}, description = "출력 HTML 파일 경로 (기본값: report.html)", defaultValue = "report.html")
    private Path outputFile;

    @Option(names = {"--type"}, description = "입력 파일 타입 강제 지정: AUTO(기본), JFR, FLAMEGRAPH", defaultValue = "AUTO")
    private InputType inputType;

    @Option(names = {"--lang"}, description = "Output language tag, e.g. ko (default), en", defaultValue = "ko")
    private String lang;

    private CpuAnalyzer        cpuAnalyzer;
    private GcAnalyzer         gcAnalyzer;
    private MemoryAnalyzer     memoryAnalyzer;
    private ThreadAnalyzer     threadAnalyzer;
    private FlameGraphAnalyzer flameGraphAnalyzer;
    private Messages           messages;

    @Override
    public Integer call() throws Exception {
        messages           = Messages.of(lang);
        cpuAnalyzer        = new CpuAnalyzer(messages);
        gcAnalyzer         = new GcAnalyzer(messages);
        memoryAnalyzer     = new MemoryAnalyzer(messages);
        threadAnalyzer     = new ThreadAnalyzer(messages);
        flameGraphAnalyzer = new FlameGraphAnalyzer(messages);

        if (!inputFile.toFile().exists()) {
            System.err.println(messages.format("cli.error.file_not_found", inputFile));
            return 1;
        }

        InputType resolved = inputType == InputType.AUTO ? detect(inputFile) : inputType;
        System.out.printf(messages.format("cli.info.analyzing", resolved, inputFile) + "%n");

        return switch (resolved) {
            case JFR        -> runJfr();
            case FLAMEGRAPH -> runFlameGraph();
            default -> {
                System.err.println(messages.get("cli.error.type_unknown"));
                yield 1;
            }
        };
    }

    // ── JFR 파이프라인 ────────────────────────────────────────────────────────

    private int runJfr() throws Exception {
        long startMs = System.currentTimeMillis();

        ReadResult raw = new JfrReader(inputFile).read();

        long durationMs  = Duration.between(raw.recordingStart(), raw.recordingEnd()).toMillis();
        long durationSec = Math.max(durationMs / 1000, 1);

        CpuSummary    cpu     = cpuAnalyzer.buildSummary(raw.cpu());
        GcSummary     gc      = gcAnalyzer.buildSummary(raw.gc());
        MemorySummary memory  = memoryAnalyzer.buildSummary(raw.memory(), durationSec);
        ThreadSummary threads = threadAnalyzer.buildSummary(raw.thread());

        List<Finding> findings = new ArrayList<>();
        findings.addAll(cpuAnalyzer.analyze(cpu));
        findings.addAll(gcAnalyzer.analyze(gc, durationMs));
        findings.addAll(memoryAnalyzer.analyze(memory));
        findings.addAll(threadAnalyzer.analyze(threads));

        List<TimeBucket> timeline = TimelineBuilder.build(raw);

        RecordingMeta meta = new RecordingMeta(
                raw.recordingStart(),
                raw.recordingEnd(),
                Duration.ofMillis(durationMs),
                raw.meta().getJvmVersion(),
                raw.meta().getJvmArgs(),
                raw.meta().getMainClass(),
                raw.meta().getPid()
        );

        AnalysisResult result = new AnalysisResult(meta, timeline, cpu, gc, memory, threads, findings);
        new HtmlRenderer(messages.langTag()).render(result, outputFile);

        printCompletion(System.currentTimeMillis() - startMs, findings);
        return 0;
    }

    // ── Flame Graph 파이프라인 ────────────────────────────────────────────────

    private int runFlameGraph() throws Exception {
        long startMs = System.currentTimeMillis();

        // 1. Parse
        List<StackEntry> entries = CollapsedStacksReader.read(inputFile);
        if (entries.isEmpty()) {
            System.err.println(messages.format("cli.error.no_stack_entries", inputFile));
            return 1;
        }

        // 2. Build tree
        FlameNode root = FlameGraphBuilder.build(entries);

        // 3. Analyze
        FlameGraphSummary summary  = flameGraphAnalyzer.buildSummary(root);
        List<Finding>     findings = flameGraphAnalyzer.analyze(summary);

        // 4. Meta
        Set<String> uniqueFrames = new HashSet<>();
        entries.forEach(e -> uniqueFrames.addAll(e.frames()));
        FlameGraphMeta meta = new FlameGraphMeta(
                inputFile.getFileName().toString(),
                summary.totalSamples(),
                entries.size(),
                uniqueFrames.size(),
                Instant.now()
        );

        FlameGraphResult result = new FlameGraphResult(meta, root, summary, findings);
        new FlameGraphHtmlRenderer(messages.langTag()).render(result, outputFile);

        printCompletion(System.currentTimeMillis() - startMs, findings);
        return 0;
    }

    // ── 파일 타입 감지 ────────────────────────────────────────────────────────

    /**
     * 파일 타입을 감지한다.
     * 1. JFR 매직 바이트 확인 (0xFD 0x46 0x4C 0x52)
     * 2. 확장자 확인 (.collapsed / .stacks)
     * 3. 첫 줄 구조 확인 (';' 구분자 + 숫자 카운트)
     */
    private InputType detect(Path path) {
        if (isJfrMagic(path)) return InputType.JFR;

        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".jfr")) return InputType.JFR;
        if (name.endsWith(".collapsed") || name.endsWith(".stacks")) return InputType.FLAMEGRAPH;

        if (looksLikeCollapsedStacks(path)) return InputType.FLAMEGRAPH;

        return InputType.AUTO; // 감지 실패
    }

    private boolean isJfrMagic(Path path) {
        try (InputStream is = java.nio.file.Files.newInputStream(path)) {
            byte[] magic = new byte[4];
            return is.read(magic) == 4
                    && (magic[0] & 0xFF) == 0xFD
                    && magic[1] == 0x46   // 'F'
                    && magic[2] == 0x4C   // 'L'
                    && magic[3] == 0x52;  // 'R'
        } catch (IOException e) {
            return false;
        }
    }

    private boolean looksLikeCollapsedStacks(Path path) {
        try (var reader = java.nio.file.Files.newBufferedReader(path)) {
            String line;
            int checked = 0;
            while ((line = reader.readLine()) != null && checked < 5) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                // "frame;frame count" 구조인지 확인
                int lastSpace = line.lastIndexOf(' ');
                if (lastSpace > 0) {
                    String count = line.substring(lastSpace + 1).strip();
                    String stack = line.substring(0, lastSpace);
                    try {
                        Long.parseLong(count);
                        if (stack.contains(";")) return true;
                    } catch (NumberFormatException ignored) {}
                }
                checked++;
            }
        } catch (IOException ignored) {}
        return false;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void printCompletion(long elapsed, List<Finding> findings) {
        System.out.printf(messages.format("cli.info.done", elapsed, outputFile.toAbsolutePath()) + "%n");
        System.out.printf("  Findings: CRITICAL=%d  WARNING=%d  INFO=%d%n",
                count(findings, Finding.Severity.CRITICAL),
                count(findings, Finding.Severity.WARNING),
                count(findings, Finding.Severity.INFO));
    }

    private long count(List<Finding> findings, Finding.Severity sev) {
        return findings.stream().filter(f -> f.severity() == sev).count();
    }
}
