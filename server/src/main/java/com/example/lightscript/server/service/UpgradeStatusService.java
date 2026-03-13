package com.example.lightscript.server.service;

import com.example.lightscript.server.entity.Agent;
import com.example.lightscript.server.entity.AgentUpgradeLog;
import com.example.lightscript.server.repository.AgentRepository;
import com.example.lightscript.server.repository.AgentUpgradeLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UpgradeStatusService {
    
    private final AgentUpgradeLogRepository upgradeLogRepository;
    private final AgentRepository agentRepository;
    
    /**
     * 开始升级
     */
    @Transactional
    public Long startUpgrade(String agentId, String fromVersion, String toVersion, boolean forceUpgrade) {
        AgentUpgradeLog upgradeLog = new AgentUpgradeLog();
        upgradeLog.setAgentId(agentId);
        upgradeLog.setFromVersion(fromVersion);
        upgradeLog.setToVersion(toVersion);
        upgradeLog.setUpgradeStatus("STARTED");
        upgradeLog.setForceUpgrade(forceUpgrade);
        upgradeLog.setStartTime(LocalDateTime.now());
        
        AgentUpgradeLog saved = upgradeLogRepository.save(upgradeLog);
        
        // 更新Agent状态为升级中
        updateAgentStatus(agentId, "UPGRADING");
        
        log.info("Upgrade started for agent {}: {} -> {}, logId: {}", 
                agentId, fromVersion, toVersion, saved.getId());
        return saved.getId();
    }
    
    /**
     * 更新升级状态
     */
    @Transactional
    public void updateUpgradeStatus(Long upgradeLogId, String status, String errorMessage) {
        Optional<AgentUpgradeLog> logOpt = upgradeLogRepository.findById(upgradeLogId);
        if (logOpt.isPresent()) {
            AgentUpgradeLog upgradeLog = logOpt.get();
            upgradeLog.setUpgradeStatus(status);
            if (errorMessage != null) {
                upgradeLog.setErrorMessage(errorMessage);
            }
            
            // 如果升级完成（成功、失败或回滚），设置结束时间
            // 注意：不再自动设置Agent状态，由Agent主动报送
            if ("SUCCESS".equals(status) || "FAILED".equals(status) || "ROLLBACK".equals(status)) {
                upgradeLog.setEndTime(LocalDateTime.now());
            }
            
            upgradeLogRepository.save(upgradeLog);
            
            log.info("Upgrade status updated for agent {}: {} (logId: {})", 
                    upgradeLog.getAgentId(), status, upgradeLogId);
        } else {
            log.warn("Upgrade log not found: {}", upgradeLogId);
        }
    }
    
    /**
     * 更新Agent状态
     */
    private void updateAgentStatus(String agentId, String status) {
        Optional<Agent> agentOpt = agentRepository.findByAgentId(agentId);
        if (agentOpt.isPresent()) {
            Agent agent = agentOpt.get();
            agent.setStatus(status);
            agentRepository.save(agent);
            log.info("Agent {} status updated to: {}", agentId, status);
        } else {
            log.warn("Agent not found: {}", agentId);
        }
    }
    
    /**
     * 获取Agent的升级历史
     */
    public List<AgentUpgradeLog> getUpgradeHistory(String agentId) {
        return upgradeLogRepository.findByAgentIdOrderByCreatedAtDesc(agentId);
    }
    
    /**
     * 检查Agent是否正在升级
     */
    public boolean isAgentUpgrading(String agentId) {
        return upgradeLogRepository.existsByAgentIdAndUpgradeStatusIn(
            agentId, Arrays.asList("STARTED", "DOWNLOADING", "INSTALLING")
        );
    }
    
    /**
     * 获取最近的升级记录
     */
    public List<AgentUpgradeLog> getRecentUpgrades() {
        return upgradeLogRepository.findTop10ByOrderByCreatedAtDesc();
    }
    
    /**
     * 获取指定状态的升级记录
     */
    public List<AgentUpgradeLog> getUpgradesByStatus(String status) {
        return upgradeLogRepository.findByUpgradeStatusOrderByCreatedAtDesc(status);
    }
}