package com.example.lightscript.server.repository;

import com.example.lightscript.server.entity.AgentUpgradeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentUpgradeLogRepository extends JpaRepository<AgentUpgradeLog, Long> {
    
    /**
     * 根据Agent ID查询升级历史，按创建时间倒序
     */
    List<AgentUpgradeLog> findByAgentIdOrderByCreatedAtDesc(String agentId);
    
    /**
     * 检查Agent是否正在升级
     */
    boolean existsByAgentIdAndUpgradeStatusIn(String agentId, List<String> statuses);
    
    /**
     * 查询指定状态的升级记录
     */
    List<AgentUpgradeLog> findByUpgradeStatusOrderByCreatedAtDesc(String upgradeStatus);
    
    /**
     * 查询最近的升级记录
     */
    List<AgentUpgradeLog> findTop10ByOrderByCreatedAtDesc();
}