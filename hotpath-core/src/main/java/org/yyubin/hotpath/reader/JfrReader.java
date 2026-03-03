package org.yyubin.hotpath.reader;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.yyubin.hotpath.reader.handler.*;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;

/**
 * .jfr 파일을 단일 패스로 스트리밍하며 각 핸들러에 이벤트를 전달한다.
 */
public class JfrReader {

    private final Path jfrPath;

    // 핸들러
    private final MetaHandler   metaHandler   = new MetaHandler();
    private final CpuHandler    cpuHandler    = new CpuHandler();
    private final GcHandler     gcHandler     = new GcHandler();
    private final MemoryHandler memoryHandler = new MemoryHandler();
    private final ThreadHandler threadHandler = new ThreadHandler();

    private Instant recordingStart;
    private Instant recordingEnd;

    public JfrReader(Path jfrPath) {
        this.jfrPath = jfrPath;
    }

    public ReadResult read() throws IOException {
        EventRouter router = buildRouter();

        try (RecordingFile rf = new RecordingFile(jfrPath)) {
            RecordedEvent first = null;
            RecordedEvent last  = null;

            while (rf.hasMoreEvents()) {
                RecordedEvent event = rf.readEvent();
                if (first == null) first = event;
                last = event;
                router.dispatch(event);
            }

            recordingStart = first != null ? first.getStartTime() : Instant.now();
            recordingEnd   = last  != null ? last.getStartTime()  : Instant.now();
        }

        return new ReadResult(
                recordingStart,
                recordingEnd,
                metaHandler,
                cpuHandler,
                gcHandler,
                memoryHandler,
                threadHandler
        );
    }

    private EventRouter buildRouter() {
        EventRouter router = new EventRouter();

        router.register("jdk.JVMInformation",                  metaHandler);

        router.register("jdk.CPULoad",                         cpuHandler);
        router.register("jdk.ExecutionSample",                 cpuHandler);

        router.register("jdk.GarbageCollection",               gcHandler);
        router.register("jdk.GCHeapSummary",                   gcHandler);
        router.register("jdk.GCHeapSummary",                   memoryHandler);

        router.register("jdk.ObjectAllocationInNewTLAB",       memoryHandler);
        router.register("jdk.ObjectAllocationOutsideTLAB",     memoryHandler);

        router.register("jdk.JavaMonitorEnter",                threadHandler);
        router.register("jdk.JavaThreadStatistics",            threadHandler);

        return router;
    }
}
