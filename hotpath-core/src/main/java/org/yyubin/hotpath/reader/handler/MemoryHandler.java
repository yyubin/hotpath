package org.yyubin.hotpath.reader.handler;

import jdk.jfr.consumer.RecordedEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MemoryHandler implements EventHandler {

    public record HeapSample(Instant time, long usedBytes, long committedBytes) {}
    public record AllocSample(String className, long bytes) {}

    private final List<HeapSample> heapSamples = new ArrayList<>();
    private final List<AllocSample> allocSamples = new ArrayList<>();

    @Override
    public void handle(RecordedEvent event) {
        switch (event.getEventType().getName()) {
            case "jdk.GCHeapSummary" -> {
                long used      = event.hasField("heapUsed")      ? event.getLong("heapUsed")      : 0;
                long committed = event.hasField("heapSpace")     ? 0                              : 0;
                heapSamples.add(new HeapSample(event.getStartTime(), used, committed));
            }
            case "jdk.ObjectAllocationInNewTLAB" -> recordAlloc(event, "tlabSize");
            case "jdk.ObjectAllocationOutsideTLAB" -> recordAlloc(event, "allocationSize");
        }
    }

    private void recordAlloc(RecordedEvent event, String sizeField) {
        String className = "Unknown";
        if (event.hasField("objectClass")) {
            try {
                jdk.jfr.consumer.RecordedClass cls = event.getClass("objectClass");
                if (cls != null) className = cls.getName();
            } catch (Exception e) {
                className = "Unknown";
            }
        }
        long bytes = event.hasField(sizeField) ? event.getLong(sizeField) : 0;
        allocSamples.add(new AllocSample(className, bytes));
    }

    public List<HeapSample> getHeapSamples()   { return Collections.unmodifiableList(heapSamples); }
    public List<AllocSample> getAllocSamples()  { return Collections.unmodifiableList(allocSamples); }
}
