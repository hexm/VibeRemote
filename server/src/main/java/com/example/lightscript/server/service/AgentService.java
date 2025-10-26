package com.example.lightscript.server.service;

import com.example.lightscript.server.entity.Agent;
import com.example.lightscript.server.exception.BusinessException;
import com.example.lightscript.server.exception.ErrorCode;
import com.example.lightscript.server.model.AgentModels.*;
import com.example.lightscript.server.repository.AgentRepository;
import com.example.lightscript.server.util.LogUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentService {
    
    private final AgentRepository agentRepository;
    
    @Value("${lightscript.register.token}")
    private String registerToken;
    
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        log.info("[Agent] Registration attempt from {} ({})", 
                request.getHostname(), request.getOsType());
        
        if (!registerToken.equals(request.getRegisterToken())) {
            log.warn("[Agent] Registration failed - Invalid token from {}", request.getHostname());
            throw new BusinessException(ErrorCode.INVALID_REGISTER_TOKEN);
        }
        
        Agent agent;
        boolean isNewRegistration = false;
        boolean wasOffline = false;
        
        // 先查询是否已存在
        Optional<Agent> existingAgent = agentRepository.findByHostnameAndOsType(
                request.getHostname(), request.getOsType());
        
        if (existingAgent.isPresent()) {
            // 已存在，复用并更新
            agent = existingAgent.get();
            wasOffline = "OFFLINE".equals(agent.getStatus());
            
            agent.setIp(request.getIp());
            agent.setLabels(request.getLabels());
            agent.setLastHeartbeat(LocalDateTime.now());
            agent.setStatus("ONLINE");
            // 生成新的token，确保服务器重启后agent可以重新建立连接
            agent.setAgentToken(UUID.randomUUID().toString());
            agent = agentRepository.save(agent);
            
            log.info("[Agent] Re-registered: {} ({}) - AgentId: {} (new token generated, was offline: {})", 
                    agent.getHostname(), agent.getOsType(), agent.getAgentId(), wasOffline);
            
            // 注意：不在这里立即重置任务！
            // 原因：agent离线不代表进程崩溃，可能只是网络中断，任务还在执行
            // 如果立即重置任务，会导致任务重复执行
            // 由定时任务checkOfflineAgentTasks()在agent真正离线一段时间后才重置任务
        } else {
            // 不存在，尝试创建新Agent
            try {
                agent = new Agent();
                agent.setAgentId(UUID.randomUUID().toString());
                agent.setAgentToken(UUID.randomUUID().toString());
                agent.setHostname(request.getHostname());
                agent.setOsType(request.getOsType());
                agent.setIp(request.getIp());
                agent.setLabels(request.getLabels());
                agent.setLastHeartbeat(LocalDateTime.now());
                agent.setStatus("ONLINE");
                
                agent = agentRepository.save(agent);
                isNewRegistration = true;
                
                log.info("[Agent] NEW registration: {} ({}) - AgentId: {}", 
                        agent.getHostname(), agent.getOsType(), agent.getAgentId());
                        
            } catch (Exception e) {
                // 并发情况下可能违反唯一约束，重新查询已存在的Agent
                log.warn("Concurrent registration detected for {} ({}), retrying query", 
                        request.getHostname(), request.getOsType());
                        
                existingAgent = agentRepository.findByHostnameAndOsType(
                        request.getHostname(), request.getOsType());
                        
                if (existingAgent.isPresent()) {
                    agent = existingAgent.get();
                    wasOffline = "OFFLINE".equals(agent.getStatus());
                    
                    agent.setIp(request.getIp());
                    agent.setLabels(request.getLabels());
                    agent.setLastHeartbeat(LocalDateTime.now());
                    agent.setStatus("ONLINE");
                    // 生成新的token
                    agent.setAgentToken(UUID.randomUUID().toString());
                    agent = agentRepository.save(agent);
                    
                    log.info("Agent re-registered after concurrent conflict: {} ({}), ID: {} (new token generated)", 
                            agent.getHostname(), agent.getOsType(), agent.getAgentId());
                    
                    // 不立即重置任务，避免重复执行
                } else {
                    // 极端情况：仍然查不到，抛出原始异常
                    throw e;
                }
            }
        }
        
        RegisterResponse response = new RegisterResponse();
        response.setAgentId(agent.getAgentId());
        response.setAgentToken(agent.getAgentToken());
        
        LogUtil.logAgent(isNewRegistration ? "REGISTER" : "RE-REGISTER", 
                agent.getAgentId(), agent.getHostname(), 
                String.format("OS: %s, IP: %s", agent.getOsType(), agent.getIp()));
        
        return response;
    }
    
    @Transactional
    public boolean updateHeartbeat(String agentId, String agentToken, HeartbeatRequest request) {
        Optional<Agent> agentOpt = agentRepository.findByAgentIdAndAgentToken(agentId, agentToken);
        if (agentOpt.isPresent()) {
            Agent agent = agentOpt.get();
            agent.setLastHeartbeat(LocalDateTime.now());
            agent.setStatus("ONLINE");
            log.debug("[Agent] Heartbeat received from {} ({})", agent.getHostname(), agentId);
            if (request.getCpuLoad() != null) {
                agent.setCpuLoad(request.getCpuLoad());
            }
            if (request.getFreeMemMb() != null) {
                agent.setFreeMemMb(request.getFreeMemMb());
            }
            agentRepository.save(agent);
            return true;
        }
        
        // Token验证失败，但是agent可能还存在（服务器重启导致token失效）
        // 记录警告日志，让agent知道需要重新注册
        Optional<Agent> agentById = agentRepository.findById(agentId);
        if (agentById.isPresent()) {
            log.warn("[Agent] Heartbeat failed - Token mismatch for agent {} ({}). Agent needs to re-register.", 
                    agentById.get().getHostname(), agentId);
        } else {
            log.warn("[Agent] Heartbeat failed - Unknown agent ID: {}. Agent needs to register.", agentId);
        }
        
        return false;
    }
    
    public boolean validateAgent(String agentId, String agentToken) {
        return agentRepository.findByAgentIdAndAgentToken(agentId, agentToken).isPresent();
    }
    
    @Transactional
    public boolean markOffline(String agentId, String agentToken) {
        Optional<Agent> agentOpt = agentRepository.findByAgentIdAndAgentToken(agentId, agentToken);
        if (agentOpt.isPresent()) {
            Agent agent = agentOpt.get();
            agent.setStatus("OFFLINE");
            agent.setLastHeartbeat(LocalDateTime.now());
            agentRepository.save(agent);
            log.info("[Agent] {} ({}) marked as OFFLINE - Agent shutdown notification received", 
                    agent.getHostname(), agentId);
            LogUtil.logAgent("STATUS_CHANGE", agent.getAgentId(), agent.getHostname(), 
                    "ONLINE -> OFFLINE (agent shutdown)");
            return true;
        }
        return false;
    }
    
    public Optional<Agent> getAgent(String agentId) {
        return agentRepository.findById(agentId);
    }
    
    public Page<Agent> getAllAgents(Pageable pageable) {
        return agentRepository.findAll(pageable);
    }
    
    public List<Agent> getOnlineAgents() {
        return agentRepository.findByStatus("ONLINE");
    }
    
    public List<Agent> getOfflineAgents() {
        return agentRepository.findByStatus("OFFLINE");
    }
    
    public long getOnlineAgentCount() {
        return agentRepository.countOnlineAgents();
    }
    
    public long getOfflineAgentCount() {
        return agentRepository.countOfflineAgents();
    }
    
    @Scheduled(fixedRate = 60000) // 每分钟检查一次
    @Transactional
    public void checkOfflineAgents() {
        log.debug("[Scheduled] Checking offline agents...");
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(2); // 2分钟没有心跳认为离线
        List<Agent> offlineAgents = agentRepository.findOfflineAgents(threshold);
        
        if (!offlineAgents.isEmpty()) {
            log.info("[Scheduled] Found {} agents with no heartbeat since {}", 
                    offlineAgents.size(), threshold);
        }
        
        int offlineCount = 0;
        for (Agent agent : offlineAgents) {
            if ("ONLINE".equals(agent.getStatus())) {
                agent.setStatus("OFFLINE");
                agentRepository.save(agent);
                log.warn("[Agent] {} ({}) marked as OFFLINE - Last heartbeat: {}", 
                        agent.getHostname(), agent.getAgentId(), agent.getLastHeartbeat());
                LogUtil.logAgent("STATUS_CHANGE", agent.getAgentId(), agent.getHostname(), 
                        "ONLINE -> OFFLINE (heartbeat timeout)");
                offlineCount++;
            }
        }
        
        if (offlineCount > 0) {
            log.info("[Scheduled] Marked {} agents as OFFLINE", offlineCount);
        }
    }
    
    // 已删除handleAgentRecovery()方法
    // 原因：agent重连时不应立即重置任务，应由定时任务延迟处理
    // 这样可以避免短暂网络中断导致任务重复执行
}
