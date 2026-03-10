package com.example.lightscript.server.controller;

import com.example.lightscript.server.entity.AgentGroup;
import com.example.lightscript.server.entity.AgentGroupMember;
import com.example.lightscript.server.model.AgentGroupModels.*;
import com.example.lightscript.server.repository.AgentGroupMemberRepository;
import com.example.lightscript.server.security.RequirePermission;
import com.example.lightscript.server.service.AgentGroupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/web/agent-groups")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AgentGroupController {
    
    private final AgentGroupService agentGroupService;
    private final AgentGroupMemberRepository agentGroupMemberRepository;
    
    /**
     * 获取分组列表
     */
    @GetMapping
    @RequirePermission("agent:group")
    public ResponseEntity<?> getGroups(@RequestParam(required = false) String type) {
        List<AgentGroup> groups;
        
        if (type != null && !type.trim().isEmpty()) {
            groups = agentGroupService.getGroupsByType(type);
        } else {
            groups = agentGroupService.getAllGroups();
        }
        
        List<AgentGroupDTO> dtoList = groups.stream()
            .map(this::toAgentGroupDTO)
            .collect(Collectors.toList());
        
        Map<String, Object> response = new HashMap<>();
        response.put("content", dtoList);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取分组详情
     */
    @GetMapping("/{groupId}")
    @RequirePermission("agent:group")
    public ResponseEntity<?> getGroupById(@PathVariable Long groupId) {
        AgentGroup group = agentGroupService.getGroupById(groupId)
            .orElseThrow(() -> new IllegalArgumentException("分组不存在"));
        
        // 获取分组成员
        List<AgentGroupMember> members = agentGroupMemberRepository.findByGroupId(groupId);
        List<AgentMemberDTO> agentDTOs = members.stream()
            .map(this::toAgentMemberDTO)
            .collect(Collectors.toList());
        
        AgentGroupDetailDTO dto = new AgentGroupDetailDTO();
        dto.setId(group.getId());
        dto.setName(group.getName());
        dto.setDescription(group.getDescription());
        dto.setType(group.getType());
        dto.setAgents(agentDTOs);
        dto.setCreatedBy(group.getCreatedBy());
        dto.setCreatedAt(group.getCreatedAt());
        dto.setUpdatedAt(group.getUpdatedAt());
        
        return ResponseEntity.ok(dto);
    }
    
    /**
     * 创建分组
     */
    @PostMapping
    @RequirePermission("agent:group")
    public ResponseEntity<?> createGroup(@RequestBody CreateGroupRequest request) {
        try {
            // TODO: 从认证上下文获取当前用户
            String currentUser = "admin";
            
            AgentGroup group = agentGroupService.createGroup(
                request.getName(),
                request.getDescription(),
                request.getType(),
                currentUser
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("id", group.getId());
            response.put("message", "分组创建成功");
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }
    
    /**
     * 更新分组
     */
    @PutMapping("/{groupId}")
    @RequirePermission("agent:group")
    public ResponseEntity<?> updateGroup(
            @PathVariable Long groupId,
            @RequestBody UpdateGroupRequest request) {
        try {
            AgentGroup group = agentGroupService.updateGroup(
                groupId,
                request.getName(),
                request.getDescription()
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "分组更新成功");
            response.put("group", toAgentGroupDTO(group));
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }
    
    /**
     * 删除分组
     */
    @DeleteMapping("/{groupId}")
    @RequirePermission("agent:group")
    public ResponseEntity<?> deleteGroup(@PathVariable Long groupId) {
        try {
            agentGroupService.deleteGroup(groupId);
            return ResponseEntity.ok(createSuccessResponse("分组删除成功"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }
    
    /**
     * 添加Agent到分组
     */
    @PostMapping("/{groupId}/agents")
    @RequirePermission("agent:group")
    public ResponseEntity<?> addAgentsToGroup(
            @PathVariable Long groupId,
            @RequestBody AgentIdsRequest request) {
        try {
            int addedCount = agentGroupService.addAgentsToGroup(groupId, request.getAgentIds());
            
            Map<String, Object> response = new HashMap<>();
            response.put("addedCount", addedCount);
            response.put("message", String.format("成功添加%d个Agent到分组", addedCount));
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }
    
    /**
     * 从分组移除Agent
     */
    @DeleteMapping("/{groupId}/agents")
    @RequirePermission("agent:group")
    public ResponseEntity<?> removeAgentsFromGroup(
            @PathVariable Long groupId,
            @RequestBody AgentIdsRequest request) {
        try {
            int removedCount = agentGroupService.removeAgentsFromGroup(groupId, request.getAgentIds());
            
            Map<String, Object> response = new HashMap<>();
            response.put("removedCount", removedCount);
            response.put("message", String.format("成功从分组移除%d个Agent", removedCount));
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }
    
    /**
     * 转换为AgentGroupDTO
     */
    private AgentGroupDTO toAgentGroupDTO(AgentGroup group) {
        AgentGroupDTO dto = new AgentGroupDTO();
        dto.setId(group.getId());
        dto.setName(group.getName());
        dto.setDescription(group.getDescription());
        dto.setType(group.getType());
        dto.setAgentCount(group.getAgentCount());
        dto.setCreatedBy(group.getCreatedBy());
        dto.setCreatedAt(group.getCreatedAt());
        dto.setUpdatedAt(group.getUpdatedAt());
        return dto;
    }
    
    /**
     * 转换为AgentMemberDTO
     */
    private AgentMemberDTO toAgentMemberDTO(AgentGroupMember member) {
        AgentMemberDTO dto = new AgentMemberDTO();
        dto.setAgentId(member.getAgentId());
        // TODO: 从Agent服务获取hostname和status
        dto.setHostname(member.getAgentId());
        dto.setStatus("UNKNOWN");
        dto.setAddedAt(member.getAddedAt());
        return dto;
    }
    
    /**
     * 创建错误响应
     */
    private Map<String, String> createErrorResponse(String message) {
        Map<String, String> response = new HashMap<>();
        response.put("message", message);
        return response;
    }
    
    /**
     * 创建成功响应
     */
    private Map<String, String> createSuccessResponse(String message) {
        Map<String, String> response = new HashMap<>();
        response.put("message", message);
        return response;
    }
}
