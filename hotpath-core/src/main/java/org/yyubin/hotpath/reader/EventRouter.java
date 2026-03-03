package org.yyubin.hotpath.reader;

import jdk.jfr.consumer.RecordedEvent;
import org.yyubin.hotpath.reader.handler.EventHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 이벤트 타입명을 기준으로 등록된 핸들러에 분기한다.
 * 하나의 이벤트 타입에 여러 핸들러를 등록할 수 있다.
 */
public class EventRouter {

    private final Map<String, List<EventHandler>> routes = new HashMap<>();

    public void register(String eventType, EventHandler handler) {
        routes.computeIfAbsent(eventType, k -> new ArrayList<>()).add(handler);
    }

    public void dispatch(RecordedEvent event) {
        List<EventHandler> handlers = routes.get(event.getEventType().getName());
        if (handlers != null) {
            for (EventHandler h : handlers) {
                h.handle(event);
            }
        }
    }
}
