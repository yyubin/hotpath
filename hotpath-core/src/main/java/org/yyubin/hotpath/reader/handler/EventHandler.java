package org.yyubin.hotpath.reader.handler;

import jdk.jfr.consumer.RecordedEvent;

public interface EventHandler {
    void handle(RecordedEvent event);
}
