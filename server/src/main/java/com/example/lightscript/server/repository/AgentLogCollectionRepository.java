package com.example.lightscript.server.repository;

import com.example.lightscript.server.entity.AgentLogCollection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AgentLogCollectionRepository extends JpaRepository<AgentLogCollection, Long> {
    Optional<AgentLogCollection> findFirstByAgentIdOrderByCreatedAtDesc(String agentId);
}
