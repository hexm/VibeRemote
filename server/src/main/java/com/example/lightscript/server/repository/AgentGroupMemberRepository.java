package com.example.lightscript.server.repository;

import com.example.lightscript.server.entity.AgentGroupMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentGroupMemberRepository extends JpaRepository<AgentGroupMember, Long> {
    
    /**
     * 根据分组ID查找所有成员
     */
    List<AgentGroupMember> findByGroupId(Long groupId);
    
    /**
     * 根据Agent ID查找所有分组
     */
    List<AgentGroupMember> findByAgentId(String agentId);
    
    /**
     * 检查Agent是否在分组中
     */
    boolean existsByGroupIdAndAgentId(Long groupId, String agentId);
    
    /**
     * 删除分组中的Agent
     */
    void deleteByGroupIdAndAgentId(Long groupId, String agentId);
    
    /**
     * 删除分组的所有成员
     */
    void deleteByGroupId(Long groupId);
    
    /**
     * 统计分组的Agent数量
     */
    long countByGroupId(Long groupId);
    
    /**
     * 批量查询多个分组的Agent数量
     */
    @Query("SELECT m.groupId, COUNT(m) FROM AgentGroupMember m WHERE m.groupId IN :groupIds GROUP BY m.groupId")
    List<Object[]> countByGroupIds(@Param("groupIds") List<Long> groupIds);
}
