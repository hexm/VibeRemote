package com.example.lightscript.server.web;

import com.example.lightscript.server.entity.AgentUpgradeLog;
import com.example.lightscript.server.service.UpgradeStatusService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/web/upgrade")
public class UpgradeController {
    
    private final UpgradeStatusService upgradeStatusService;
    
    public UpgradeController(UpgradeStatusService upgradeStatusService) {
        this.upgradeStatusService = upgradeStatusService;
    }
    
    /**
     * 获取Agent升级历史
     */
    @GetMapping("/agents/{agentId}/history")
    public ResponseEntity<List<AgentUpgradeLog>> getAgentUpgradeHistory(@PathVariable String agentId) {
        List<AgentUpgradeLog> history = upgradeStatusService.getUpgradeHistory(agentId);
        return ResponseEntity.ok(history);
    }
    
    /**
     * 获取最近的升级记录
     */
    @GetMapping("/recent")
    public ResponseEntity<List<AgentUpgradeLog>> getRecentUpgrades() {
        List<AgentUpgradeLog> recent = upgradeStatusService.getRecentUpgrades();
        return ResponseEntity.ok(recent);
    }
    
    /**
     * 按状态获取升级记录
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<AgentUpgradeLog>> getUpgradesByStatus(@PathVariable String status) {
        List<AgentUpgradeLog> upgrades = upgradeStatusService.getUpgradesByStatus(status);
        return ResponseEntity.ok(upgrades);
    }
}