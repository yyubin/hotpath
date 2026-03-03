package org.yyubin.hotpath.model;

import java.time.Instant;
import java.time.Duration;

public record RecordingMeta(
        Instant startTime,
        Instant endTime,
        Duration duration,
        String jvmVersion,
        String jvmArgs,
        String mainClass,
        long pid
) {}
