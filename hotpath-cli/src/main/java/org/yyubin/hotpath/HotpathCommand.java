package org.yyubin.hotpath;

import org.yyubin.hotpath.analyzer.*;
import org.yyubin.hotpath.model.*;
import org.yyubin.hotpath.reader.JfrReader;
import org.yyubin.hotpath.reader.ReadResult;
import org.yyubin.hotpath.reader.TimelineBuilder;
import org.yyubin.hotpath.renderer.HtmlRenderer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name        = "hotpath",
        description = "JFR 파일을 분석하여 HTML 리포트를 생성합니다.",
        mixinStandardHelpOptions = true,
        version     = "0.1.0"
)
public class HotpathCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "recording.jfr", description = "분석할 JFR 파일 경로")
    private Path jfrFile;

    @Option(names = {"-o", "--output"}, description = "출력 HTML 파일 경로 (기본값: report.html)", defaultValue = "report.html")
    private Path outputFile;

    private final CpuAnalyzer    cpuAnalyzer    = new CpuAnalyzer();
    private final GcAnalyzer     gcAnalyzer     = new GcAnalyzer();
    private final MemoryAnalyzer memoryAnalyzer = new MemoryAnalyzer();
    private final ThreadAnalyzer threadAnalyzer = new ThreadAnalyzer();

    @Override
    public Integer call() throws Exception {
        if (!jfrFile.toFile().exists()) {
            System.err.println("오류: 파일을 찾을 수 없습니다 — " + jfrFile);
            return 1;
        }

        System.out.println("분석 중: " + jfrFile);
        long startMs = System.currentTimeMillis();

        // 1. Read
        ReadResult raw = new JfrReader(jfrFile).read();

        // 2. Analyze
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

        // 3. Build timeline
        List<TimeBucket> timeline = TimelineBuilder.build(raw);

        // 4. RecordingMeta
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

        // 5. Render
        new HtmlRenderer().render(result, outputFile);

        long elapsed = System.currentTimeMillis() - startMs;
        System.out.printf("완료 (%d ms) → %s%n", elapsed, outputFile.toAbsolutePath());
        System.out.printf("  Findings: CRITICAL=%d  WARNING=%d  INFO=%d%n",
                count(findings, Finding.Severity.CRITICAL),
                count(findings, Finding.Severity.WARNING),
                count(findings, Finding.Severity.INFO));

        return 0;
    }

    private long count(List<Finding> findings, Finding.Severity sev) {
        return findings.stream().filter(f -> f.severity() == sev).count();
    }
}
