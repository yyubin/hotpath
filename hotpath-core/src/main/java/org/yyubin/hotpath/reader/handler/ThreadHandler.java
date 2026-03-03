package org.yyubin.hotpath.reader.handler;

import jdk.jfr.consumer.RecordedEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ThreadHandler implements EventHandler {

    public record ContentionEvent(
            Instant time,
            long waitMs,
            String monitorClass,
            String blockedThread
    ) {}

    public record ThreadCountSample(Instant time, long activeCount) {}

    private final List<ContentionEvent> contentions = new ArrayList<>();
    private final List<ThreadCountSample> threadCounts = new ArrayList<>();

    @Override
    public void handle(RecordedEvent event) {
        switch (event.getEventType().getName()) {
            case "jdk.JavaMonitorEnter" -> {
                long waitMs = event.getDuration().toMillis();
                if (waitMs <= 0) break;
                String monitorClass = "Unknown";
                if (event.hasField("monitorClass")) {
                    try {
                        jdk.jfr.consumer.RecordedClass cls = event.getClass("monitorClass");
                        if (cls != null) monitorClass = cls.getName();
                    } catch (Exception ignored) {}
                }
                String thread = "Unknown";
                if (event.hasField("eventThread")) {
                    try {
                        jdk.jfr.consumer.RecordedThread t = event.getThread("eventThread");
                        if (t != null) thread = t.getJavaName() != null ? t.getJavaName() : t.getOSName();
                    } catch (Exception ignored) {}
                }
                contentions.add(new ContentionEvent(event.getStartTime(), waitMs, monitorClass, thread));
            }
            case "jdk.JavaThreadStatistics" -> {
                long active = event.hasField("activeCount") ? event.getLong("activeCount") : 0;
                threadCounts.add(new ThreadCountSample(event.getStartTime(), active));
            }
        }
    }

    public List<ContentionEvent> getContentions()      { return Collections.unmodifiableList(contentions); }
    public List<ThreadCountSample> getThreadCounts()   { return Collections.unmodifiableList(threadCounts); }
}
