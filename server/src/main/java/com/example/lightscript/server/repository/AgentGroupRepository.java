package com.example.lightscript.server.repository;

import com.example.lightscript.server.entity.AgentGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgentGroupRepository extends JpaRepository<AgentGroup, Long> {
    
    /**
     * 根据名称查找分组
     */
    Optional<AgentGroup> findByName(String name);
    
    /**
     * 检查名称是否存在
     */
    boolean existsByName(String name);
    
    /**
     * 根据类型查找分组
     */
    List<AgentGroup> findByType(String type);
    
    /**
     * 根据创建者查找分组
     */
    List<AgentGroup> findByCreatedBy(String createdBy);
}
