package com.example.lightscript.server.screen;

import com.example.lightscript.server.security.JwtUtil;
import com.example.lightscript.server.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.util.concurrent.*;

/**
 * 屏幕监控 WebSocket 会话管理器
 * - 每个 agentId 最多一个活跃会话
 * - 超时后服务器主动断开
 * - 截图帧收到即转发，不缓存
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScreenSessionHandler extends TextWebSocketHandler {

    private static final String SCREEN_MONITOR_ENABLED_KEY = "agent.screen_monitor.enabled";

    private final JwtUtil jwtUtil;
    private final SystemSettingService systemSettingService;

    @Value("${lightscript.screen.max-duration-minutes:30}")
    private int maxDurationMinutes;

    // agentId → 会话（纯内存）
    private final ConcurrentHashMap<String, ScreenSession> sessions = new ConcurrentHashMap<>();
    // WS 断开后需要通知 Agent 停止的 agentId 集合（下次 pull/心跳时下发 interval=0）
    private final java.util.Set<String> pendingStop = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1,
            r -> { Thread t = new Thread(r, "screen-timeout"); t.setDaemon(true); return t; });

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String agentId = extractAgentId(session);
        if (agentId == null) {
            closeQuietly(session, CloseStatus.BAD_DATA);
            return;
        }

        if (!isScreenMonitorEnabled()) {
            log.info("[Screen] WS rejected - screen monitor disabled for agent: {}", agentId);
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }

        // 验证 JWT token（query param: ?token=xxx）
        String token = extractToken(session);
        if (!isValidToken(token)) {
            log.warn("[Screen] WS rejected - invalid token for agent: {}", agentId);
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }

        // 踢掉同一 agentId 的旧连接
        ScreenSession old = sessions.remove(agentId);
        if (old != null) {
            cancelTimeout(old);
            closeQuietly(old.getWsSession(), CloseStatus.POLICY_VIOLATION);
        }

        ScreenSession screenSession = new ScreenSession(session, Instant.now());

        // 调度超时任务
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            log.info("[Screen] Session timeout ({}min) for agent: {}", maxDurationMinutes, agentId);
            sessions.remove(agentId, screenSession);
            cancelTimeout(screenSession);
            closeQuietly(session, CloseStatus.SESSION_NOT_RELIABLE);
        }, maxDurationMinutes, TimeUnit.MINUTES);

        screenSession.setTimeoutFuture(future);
        sessions.put(agentId, screenSession);

        // 推送 waiting 消息，告知前端预计等待时间（Agent 心跳默认 30 秒）
        try {
            session.sendMessage(new TextMessage(
                "{\"type\":\"waiting\",\"heartbeatInterval\":30,\"message\":\"等待 Agent 响应，最多 30 秒后开始回传画面\"}"
            ));
        } catch (Exception ignored) {}

        log.info("[Screen] WS connected for agent: {}, timeout={}min", agentId, maxDurationMinutes);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String agentId = extractAgentId(session);
        if (agentId == null) return;

        ScreenSession s = sessions.get(agentId);
        if (s != null && s.getWsSession().getId().equals(session.getId())) {
            sessions.remove(agentId);
            cancelTimeout(s);
            pendingStop.add(agentId); // 标记：下次 pull/心跳时通知 Agent 停止
        }
        log.info("[Screen] WS disconnected for agent: {}, status: {}", agentId, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("[Screen] WS transport error for session {}: {}", session.getId(),
                exception != null ? exception.getMessage() : "connection reset");
        // 直接清理会话，不调用 afterConnectionClosed 避免重复处理
        String agentId = extractAgentId(session);
        if (agentId == null) return;
        ScreenSession s = sessions.get(agentId);
        if (s != null && s.getWsSession().getId().equals(session.getId())) {
            sessions.remove(agentId);
            cancelTimeout(s);
            pendingStop.add(agentId);
        }
        closeQuietly(session, CloseStatus.SERVER_ERROR);
    }

    /**
     * 将截图帧推送给前端（由 ScreenController 调用）
     * 帧数据推送后立即丢弃，不缓存
     */
    public boolean pushFrame(String agentId, String imageData, String timestamp) {
        if (!isScreenMonitorEnabled()) {
            return false;
        }
        ScreenSession s = sessions.get(agentId);
        if (s == null || !s.getWsSession().isOpen()) return false;
        try {
            String msg = "{\"imageData\":\"data:image/jpeg;base64," + imageData
                    + "\",\"timestamp\":\"" + timestamp + "\"}";
            synchronized (s.getWsSession()) {
                s.getWsSession().sendMessage(new TextMessage(msg));
            }
            return true;
        } catch (Exception e) {
            log.warn("[Screen] Failed to push frame to agent {}: {}", agentId, e.getMessage());
            sessions.remove(agentId, s);
            cancelTimeout(s);
            return false;
        }
    }

    /** 是否有前端正在监控该 agentId */
    public boolean isMonitoring(String agentId) {
        if (!isScreenMonitorEnabled()) {
            return false;
        }
        ScreenSession s = sessions.get(agentId);
        return s != null && s.getWsSession().isOpen();
    }

    /**
     * 获取该 agentId 应下发的 screenCaptureInterval：
     * - 正在监控 → 3（截图间隔秒数）
     * - 刚断开待停止 → 0（通知 Agent 停止，消费后从 pendingStop 移除）
     * - 其他 → null（不下发，Agent 保持当前状态）
     */
    public Integer getScreenCaptureInterval(String agentId) {
        if (!isScreenMonitorEnabled()) {
            pendingStop.remove(agentId);
            return 0;
        }
        if (isMonitoring(agentId)) return 3;
        if (pendingStop.remove(agentId)) return 0;
        return null;
    }

    // ---- 私有工具方法 ----

    private String extractAgentId(WebSocketSession session) {
        try {
            String path = session.getUri().getPath(); // /ws/screen/{agentId}
            String id = path.substring(path.lastIndexOf('/') + 1);
            return id.isEmpty() ? null : id;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractToken(WebSocketSession session) {
        try {
            String query = session.getUri().getQuery(); // token=xxx
            if (query == null) return null;
            for (String param : query.split("&")) {
                if (param.startsWith("token=")) {
                    return param.substring(6);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private boolean isValidToken(String token) {
        if (token == null || token.isEmpty()) return false;
        try {
            String username = jwtUtil.extractUsername(token);
            if (username == null || username.isEmpty()) return false;
            return jwtUtil.validateToken(token, username);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isScreenMonitorEnabled() {
        return systemSettingService.getBooleanValue(SCREEN_MONITOR_ENABLED_KEY, true);
    }

    private void cancelTimeout(ScreenSession s) {
        if (s != null && s.getTimeoutFuture() != null) {
            s.getTimeoutFuture().cancel(false);
        }
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            if (session != null && session.isOpen()) {
                session.close(status);
            }
        } catch (Exception ignored) {}
    }
}
