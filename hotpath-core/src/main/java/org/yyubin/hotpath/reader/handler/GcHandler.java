package org.yyubin.hotpath.reader.handler;

import jdk.jfr.consumer.RecordedEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GcHandler implements EventHandler {

    public record RawGcEvent(
            long startEpochMs,
            long pauseMs,
            String cause,
            String name,
            long heapBeforeBytes,
            long heapAfterBytes
    ) {}

    private final List<RawGcEvent> events = new ArrayList<>();
    // gcId → heapBefore (GCHeapSummary before GC)
    private final java.util.Map<Integer, Long> heapBefore = new java.util.HashMap<>();

    @Override
    public void handle(RecordedEvent event) {
        switch (event.getEventType().getName()) {
            case "jdk.GCHeapSummary" -> {
                int gcId = event.hasField("gcId") ? event.getInt("gcId") : -1;
                long used = event.hasField("heapUsed") ? event.getLong("heapUsed") : 0;
                String when = event.hasField("when") ? safeString(event, "when") : "";
                if ("Before GC".equals(when)) {
                    heapBefore.put(gcId, used);
                } else if ("After GC".equals(when)) {
                    long before = heapBefore.getOrDefault(gcId, 0L);
                    // pauseMs는 GarbageCollection 이벤트에서 채워짐 — 여기선 heap만 기록
                    heapBefore.remove(gcId);
                    // heap after는 RawGcEvent에 나중에 merge
                    heapAfterMap.put(gcId, new long[]{before, used});
                }
            }
            case "jdk.GarbageCollection" -> {
                long pauseMs = event.getDuration().toMillis();
                String cause = event.hasField("cause") ? safeString(event, "cause") : "Unknown";
                String name  = event.hasField("name")  ? safeString(event, "name")  : "Unknown";
                int gcId     = event.hasField("gcId")  ? event.getInt("gcId") : -1;
                long[] heap  = heapAfterMap.getOrDefault(gcId, new long[]{0, 0});
                events.add(new RawGcEvent(
                        event.getStartTime().toEpochMilli(),
                        pauseMs,
                        cause,
                        name,
                        heap[0],
                        heap[1]
                ));
            }
        }
    }

    // gcId → [heapBefore, heapAfter]
    private final java.util.Map<Integer, long[]> heapAfterMap = new java.util.HashMap<>();

    public List<RawGcEvent> getEvents() { return Collections.unmodifiableList(events); }

    private String safeString(RecordedEvent event, String field) {
        try {
            String v = event.getString(field);
            return v != null ? v : "";
        } catch (Exception e) {
            Object v = event.getValue(field);
            return v != null ? v.toString() : "";
        }
    }
}
