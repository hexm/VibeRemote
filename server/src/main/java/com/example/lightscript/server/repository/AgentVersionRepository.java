package com.example.lightscript.server.repository;

import com.example.lightscript.server.entity.AgentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgentVersionRepository extends JpaRepository<AgentVersion, Long> {
    
    /**
     * 查找最新版本
     */
    Optional<AgentVersion> findByIsLatestTrueAndStatus(String status);
    
    /**
     * 查找当前版本
     */
    Optional<AgentVersion> findByIsCurrentTrueAndStatus(String status);
    
    /**
     * 根据版本号查找
     */
    Optional<AgentVersion> findByVersionAndStatus(String version, String status);
    
    /**
     * 查找指定平台的所有活跃版本
     */
    @Query("SELECT av FROM AgentVersion av WHERE av.status = 'ACTIVE' AND (av.platform = :platform OR av.platform = 'ALL') ORDER BY av.createdAt DESC")
    List<AgentVersion> findByPlatformAndStatus(@Param("platform") String platform);
    
    /**
     * 查找所有活跃版本，按创建时间倒序
     */
    List<AgentVersion> findByStatusOrderByCreatedAtDesc(String status);
    
    /**
     * 检查版本是否存在
     */
    boolean existsByVersionAndStatus(String version, String status);
    
    /**
     * 查找需要强制升级的版本
     */
    List<AgentVersion> findByForceUpgradeTrueAndStatus(String status);
}