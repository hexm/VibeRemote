package com.example.lightscript.server.screen;

import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

/**
 * 屏幕监控会话（纯内存，不持久化）
 */
public class ScreenSession {
    private final WebSocketSession wsSession;
    private final Instant startTime;
    private volatile ScheduledFuture<?> timeoutFuture;

    public ScreenSession(WebSocketSession wsSession, Instant startTime) {
        this.wsSession = wsSession;
        this.startTime = startTime;
    }

    public WebSocketSession getWsSession() { return wsSession; }
    public Instant getStartTime() { return startTime; }
    public ScheduledFuture<?> getTimeoutFuture() { return timeoutFuture; }
    public void setTimeoutFuture(ScheduledFuture<?> timeoutFuture) { this.timeoutFuture = timeoutFuture; }
}
