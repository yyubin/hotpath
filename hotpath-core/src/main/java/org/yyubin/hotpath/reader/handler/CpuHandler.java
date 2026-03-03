package org.yyubin.hotpath.reader.handler;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;

import java.time.Instant;
import java.util.*;

public class CpuHandler implements EventHandler {

    public record CpuSample(Instant time, double user, double system) {}

    private final List<CpuSample> samples = new ArrayList<>();
    // methodFqn -> sample count
    private final Map<String, Integer> executionSamples = new HashMap<>();

    @Override
    public void handle(RecordedEvent event) {
        switch (event.getEventType().getName()) {
            case "jdk.CPULoad" -> {
                double user   = event.hasField("jvmUser")   ? event.getFloat("jvmUser")   : 0;
                double system = event.hasField("jvmSystem") ? event.getFloat("jvmSystem") : 0;
                samples.add(new CpuSample(event.getStartTime(), user, system));
            }
            case "jdk.ExecutionSample" -> {
                RecordedStackTrace stack = event.getStackTrace();
                if (stack != null && !stack.getFrames().isEmpty()) {
                    RecordedMethod method = stack.getFrames().getFirst().getMethod();
                    String key = method.getType().getName() + "#" + method.getName();
                    executionSamples.merge(key, 1, Integer::sum);
                }
            }
        }
    }

    public List<CpuSample> getSamples()               { return Collections.unmodifiableList(samples); }
    public Map<String, Integer> getExecutionSamples() { return Collections.unmodifiableMap(executionSamples); }
}
