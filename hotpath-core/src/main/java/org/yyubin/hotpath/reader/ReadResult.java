package org.yyubin.hotpath.reader;

import org.yyubin.hotpath.reader.handler.*;

import java.time.Instant;

/**
 * JfrReader가 단일 패스를 마친 후 반환하는 핸들러 묶음.
 * 각 Analyzer는 필요한 핸들러만 꺼내 사용한다.
 */
public record ReadResult(
        Instant recordingStart,
        Instant recordingEnd,
        MetaHandler   meta,
        CpuHandler    cpu,
        GcHandler     gc,
        MemoryHandler memory,
        ThreadHandler thread
) {}
