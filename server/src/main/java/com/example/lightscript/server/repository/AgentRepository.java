package com.example.lightscript.server.repository;

import com.example.lightscript.server.entity.Agent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AgentRepository extends JpaRepository<Agent, String> {
    
    Optional<Agent> findByAgentIdAndAgentToken(String agentId, String agentToken);
    
    Optional<Agent> findByAgentId(String agentId);
    
    Optional<Agent> findByHostnameAndOsType(String hostname, String osType);
    
    List<Agent> findByStatus(String status);
    
    @Query("SELECT a FROM Agent a WHERE a.lastHeartbeat < :threshold")
    List<Agent> findOfflineAgents(@Param("threshold") LocalDateTime threshold);
    
    @Query("SELECT COUNT(a) FROM Agent a WHERE a.status = 'ONLINE'")
    long countOnlineAgents();
    
    @Query("SELECT COUNT(a) FROM Agent a WHERE a.status = 'OFFLINE'")
    long countOfflineAgents();
}
