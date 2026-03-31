package com.example.lightscript.server.screen;

import com.example.lightscript.server.security.RequirePermission;
import com.example.lightscript.server.service.AgentService;
import com.example.lightscript.server.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 屏幕监控 HTTP 接口
 * - Agent 推送截图帧（HTTP POST）
 * - 服务器收到后立即通过 WebSocket 转发给前端，不缓存
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ScreenController {

    private static final String SCREEN_MONITOR_ENABLED_KEY = "agent.screen_monitor.enabled";

    private final ScreenSessionHandler screenSessionHandler;
    private final AgentService agentService;
    private final SystemSettingService systemSettingService;

    /**
     * Agent 推送截图帧 → 立即转发给 WS 前端，不落盘不缓存
     */
    @PostMapping("/api/agent/screen/{agentId}")
    public ResponseEntity<Map<String, Object>> receiveFrame(
            @PathVariable String agentId,
            @RequestBody Map<String, Object> payload) {

        if (!isScreenMonitorEnabled()) {
            return ResponseEntity.status(403).body(Collections.singletonMap("error", "screen monitor disabled"));
        }

        // 验证 agentToken
        String agentToken = (String) payload.get("agentToken");
        if (!agentService.validateAgent(agentId, agentToken)) {
            return ResponseEntity.status(401).body(Collections.singletonMap("error", "invalid token"));
        }

        if (!screenSessionHandler.isMonitoring(agentId)) {
            // 没有前端在看，告知 Agent 停止（下次心跳会收到 interval=0）
            return ResponseEntity.ok(Collections.singletonMap("skip", true));
        }

        String imageData = (String) payload.get("imageData");
        String timestamp = (String) payload.get("timestamp");

        if (imageData == null || imageData.isEmpty()) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", "imageData is required"));
        }

        boolean pushed = screenSessionHandler.pushFrame(agentId, imageData, timestamp);
        // imageData 在此方法返回后即可被 GC，不持有引用
        return ResponseEntity.ok(Collections.singletonMap("pushed", pushed));
    }

    private boolean isScreenMonitorEnabled() {
        return systemSettingService.getBooleanValue(SCREEN_MONITOR_ENABLED_KEY, true);
    }
}
