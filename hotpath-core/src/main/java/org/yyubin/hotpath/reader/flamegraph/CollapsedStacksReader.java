package org.yyubin.hotpath.reader.flamegraph;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * async-profiler 등이 생성한 collapsed stacks 파일을 파싱한다.
 *
 * <p>각 줄의 형식:
 * <pre>
 *   frame0;frame1;...;frameN count
 * </pre>
 * 프레임은 루트(왼쪽)에서 리프(오른쪽) 순서로 ;로 구분되며,
 * 마지막 공백 뒤의 정수가 샘플 카운트다.
 */
public class CollapsedStacksReader {

    /** 단일 스택 경로와 샘플 카운트 쌍. */
    public record StackEntry(List<String> frames, long samples) {}

    /** 처리할 최대 줄 수. 초과 시 이후 줄은 무시한다. */
    private static final int MAX_LINES = 1_000_000;

    private CollapsedStacksReader() {}

    /**
     * collapsed stacks 파일을 읽어 StackEntry 목록을 반환한다.
     *
     * @param path 파일 경로
     * @return 파싱된 스택 항목 목록 (불변)
     * @throws IOException 파일 읽기 실패 시
     */
    public static List<StackEntry> read(Path path) throws IOException {
        List<StackEntry> entries = new ArrayList<>();
        int parsed = 0;

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (parsed >= MAX_LINES) break;

                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;

                StackEntry entry = parseLine(line);
                if (entry != null) {
                    entries.add(entry);
                    parsed++;
                }
            }
        }

        return Collections.unmodifiableList(entries);
    }

    /**
     * 한 줄을 파싱해 StackEntry로 변환한다.
     * 파싱할 수 없는 줄이면 null을 반환한다.
     */
    static StackEntry parseLine(String line) {
        int lastSpace = line.lastIndexOf(' ');
        if (lastSpace < 0) return null;

        String stackPart = line.substring(0, lastSpace).strip();
        String countPart = line.substring(lastSpace + 1).strip();

        if (stackPart.isEmpty()) return null;

        long count;
        try {
            count = Long.parseLong(countPart);
        } catch (NumberFormatException e) {
            return null;
        }

        if (count <= 0) return null;

        List<String> frames = splitFrames(stackPart);
        if (frames.isEmpty()) return null;

        return new StackEntry(frames, count);
    }

    /**
     * 스택 문자열을 ; 구분자로 분리해 빈 항목을 제거한 프레임 목록을 반환한다.
     */
    private static List<String> splitFrames(String stack) {
        String[] parts = stack.split(";");
        List<String> frames = new ArrayList<>(parts.length);
        for (String part : parts) {
            String trimmed = part.strip();
            if (!trimmed.isEmpty()) {
                frames.add(trimmed);
            }
        }
        return Collections.unmodifiableList(frames);
    }
}
