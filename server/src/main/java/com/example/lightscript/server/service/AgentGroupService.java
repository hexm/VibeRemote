package com.example.lightscript.server.service;

import com.example.lightscript.server.entity.AgentGroup;
import com.example.lightscript.server.entity.AgentGroupMember;
import com.example.lightscript.server.repository.AgentGroupMemberRepository;
import com.example.lightscript.server.repository.AgentGroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentGroupService {
    
    private final AgentGroupRepository agentGroupRepository;
    private final AgentGroupMemberRepository agentGroupMemberRepository;
    
    /**
     * 获取所有分组
     */
    public List<AgentGroup> getAllGroups() {
        List<AgentGroup> groups = agentGroupRepository.findAll();
        loadAgentCounts(groups);
        return groups;
    }
    
    /**
     * 根据类型获取分组
     */
    public List<AgentGroup> getGroupsByType(String type) {
        List<AgentGroup> groups = agentGroupRepository.findByType(type);
        loadAgentCounts(groups);
        return groups;
    }
    
    /**
     * 根据ID获取分组
     */
    public Optional<AgentGroup> getGroupById(Long groupId) {
        Optional<AgentGroup> groupOpt = agentGroupRepository.findById(groupId);
        groupOpt.ifPresent(group -> {
            long count = agentGroupMemberRepository.countByGroupId(groupId);
            group.setAgentCount((int) count);
        });
        return groupOpt;
    }
    
    /**
     * 创建分组
     */
    @Transactional
    public AgentGroup createGroup(String name, String description, String type, String createdBy) {
        // 验证名称唯一性
        if (agentGroupRepository.existsByName(name)) {
            throw new IllegalArgumentException("分组名称已存在: " + name);
        }
        
        AgentGroup group = new AgentGroup();
        group.setName(name);
        group.setDescription(description);
        group.setType(type);
        group.setCreatedBy(createdBy);
        
        group = agentGroupRepository.save(group);
        group.setAgentCount(0);
        
        log.info("Agent group created: {}", name);
        return group;
    }
    
    /**
     * 更新分组
     */
    @Transactional
    public AgentGroup updateGroup(Long groupId, String name, String description) {
        AgentGroup group = agentGroupRepository.findById(groupId)
            .orElseThrow(() -> new IllegalArgumentException("分组不存在: " + groupId));
        
        // 如果名称改变，检查唯一性
        if (name != null && !name.equals(group.getName())) {
            if (agentGroupRepository.existsByName(name)) {
                throw new IllegalArgumentException("分组名称已存在: " + name);
            }
            group.setName(name);
        }
        
        if (description != null) {
            group.setDescription(description);
        }
        
        group = agentGroupRepository.save(group);
        
        // 加载Agent数量
        long count = agentGroupMemberRepository.countByGroupId(groupId);
        group.setAgentCount((int) count);
        
        log.info("Agent group updated: {}", group.getName());
        return group;
    }
    
    /**
     * 删除分组
     */
    @Transactional
    public void deleteGroup(Long groupId) {
        AgentGroup group = agentGroupRepository.findById(groupId)
            .orElseThrow(() -> new IllegalArgumentException("分组不存在: " + groupId));
        
        // 删除分组（级联删除成员）
        agentGroupRepository.delete(group);
        log.info("Agent group deleted: {}", group.getName());
    }
    
    /**
     * 添加Agent到分组
     */
    @Transactional
    public int addAgentsToGroup(Long groupId, List<String> agentIds) {
        // 验证分组存在
        if (!agentGroupRepository.existsById(groupId)) {
            throw new IllegalArgumentException("分组不存在: " + groupId);
        }
        
        int addedCount = 0;
        for (String agentId : agentIds) {
            // 检查是否已存在
            if (!agentGroupMemberRepository.existsByGroupIdAndAgentId(groupId, agentId)) {
                AgentGroupMember member = new AgentGroupMember();
                member.setGroupId(groupId);
                member.setAgentId(agentId);
                agentGroupMemberRepository.save(member);
                addedCount++;
            }
        }
        
        log.info("Added {} agents to group {}", addedCount, groupId);
        return addedCount;
    }
    
    /**
     * 从分组移除Agent
     */
    @Transactional
    public int removeAgentsFromGroup(Long groupId, List<String> agentIds) {
        // 验证分组存在
        if (!agentGroupRepository.existsById(groupId)) {
            throw new IllegalArgumentException("分组不存在: " + groupId);
        }
        
        int removedCount = 0;
        for (String agentId : agentIds) {
            if (agentGroupMemberRepository.existsByGroupIdAndAgentId(groupId, agentId)) {
                agentGroupMemberRepository.deleteByGroupIdAndAgentId(groupId, agentId);
                removedCount++;
            }
        }
        
        log.info("Removed {} agents from group {}", removedCount, groupId);
        return removedCount;
    }
    
    /**
     * 获取分组的所有Agent ID
     */
    public List<String> getGroupAgentIds(Long groupId) {
        List<AgentGroupMember> members = agentGroupMemberRepository.findByGroupId(groupId);
        return members.stream()
            .map(AgentGroupMember::getAgentId)
            .collect(Collectors.toList());
    }
    
    /**
     * 获取Agent所属的所有分组
     */
    public List<AgentGroup> getAgentGroups(String agentId) {
        List<AgentGroupMember> members = agentGroupMemberRepository.findByAgentId(agentId);
        List<Long> groupIds = members.stream()
            .map(AgentGroupMember::getGroupId)
            .collect(Collectors.toList());
        
        if (groupIds.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        
        return agentGroupRepository.findAllById(groupIds);
    }
    
    /**
     * 加载分组的Agent数量
     */
    private void loadAgentCounts(List<AgentGroup> groups) {
        if (groups.isEmpty()) {
            return;
        }
        
        List<Long> groupIds = groups.stream()
            .map(AgentGroup::getId)
            .collect(Collectors.toList());
        
        List<Object[]> counts = agentGroupMemberRepository.countByGroupIds(groupIds);
        Map<Long, Long> countMap = counts.stream()
            .collect(Collectors.toMap(
                arr -> (Long) arr[0],
                arr -> (Long) arr[1]
            ));
        
        groups.forEach(group -> {
            Long count = countMap.getOrDefault(group.getId(), 0L);
            group.setAgentCount(count.intValue());
        });
    }
}
