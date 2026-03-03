package org.yyubin.hotpath.reader.handler;

import jdk.jfr.consumer.RecordedEvent;

public class MetaHandler implements EventHandler {

    private String jvmVersion = "";
    private String jvmArgs = "";
    private String mainClass = "";
    private long pid = -1;

    @Override
    public void handle(RecordedEvent event) {
        switch (event.getEventType().getName()) {
            case "jdk.JVMInformation" -> {
                jvmVersion = stringOrEmpty(event, "jvmVersion");
                jvmArgs    = stringOrEmpty(event, "jvmArguments");
                mainClass  = stringOrEmpty(event, "javaArguments");
                pid        = event.hasField("pid") ? event.getLong("pid") : -1;
            }
        }
    }

    private String stringOrEmpty(RecordedEvent event, String field) {
        if (!event.hasField(field)) return "";
        try {
            String v = event.getString(field);
            return v != null ? v : "";
        } catch (Exception e) {
            return "";
        }
    }

    public String getJvmVersion() { return jvmVersion; }
    public String getJvmArgs()    { return jvmArgs; }
    public String getMainClass()  { return mainClass; }
    public long   getPid()        { return pid; }
}
