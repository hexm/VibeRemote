package com.example.lightscript.server.service;

import com.example.lightscript.server.entity.Agent;
import com.example.lightscript.server.exception.BusinessException;
import com.example.lightscript.server.exception.ErrorCode;
import com.example.lightscript.server.model.AgentModels.*;
import com.example.lightscript.server.repository.AgentRepository;
import com.example.lightscript.server.util.LogUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentService {
    
    private final AgentRepository agentRepository;
    
    @Value("${lightscript.register.token}")
    private String registerToken;

    // agentId -> agentToken 缓存，TTL 60s 兜底，正常由 register() 主动失效
    private final Cache<String, String> tokenCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .build();
    
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        log.info("========================================");
        log.info("AGENT REGISTRATION PROCESSING");
        log.info("========================================");
        log.info("Hostname: {}", request.getHostname());
        log.info("OS Type: {}", request.getOsType());
        log.info("IP: {}", request.getIp());
        log.info("Labels: {}", request.getLabels());
        
        try {
            if (!registerToken.equals(request.getRegisterToken())) {
                log.error("✗ Registration failed - Invalid token from {}", request.getHostname());
                log.info("Expected token: {}", registerToken.substring(0, Math.min(10, registerToken.length())) + "...");
                log.info("Received token: {}", request.getRegisterToken().substring(0, Math.min(10, request.getRegisterToken().length())) + "...");
                log.info("========================================");
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
                
                log.info("Found existing agent: {}", agent.getAgentId());
                log.info("Previous status: {}", agent.getStatus());
                log.info("Was offline: {}", wasOffline);
                
                agent.setIp(request.getIp());
                agent.setLabels(request.getLabels());
                agent.setLastHeartbeat(LocalDateTime.now());
                agent.setStatus("ONLINE");
                // 直接使用 registerToken 作为 agentToken，保持不变
                agent.setAgentToken(request.getRegisterToken());
                agent = agentRepository.save(agent);
                
                // 注意：不在这里立即重置任务！
                // 原因：agent离线不代表进程崩溃，可能只是网络中断，任务还在执行
                // 如果立即重置任务，会导致任务重复执行
                // 由定时任务checkOfflineAgentTasks()在agent真正离线一段时间后才重置任务
            } else {
                // 不存在，尝试创建新Agent
                log.info("Creating new agent registration");
                try {
                    agent = new Agent();
                    agent.setAgentId(UUID.randomUUID().toString());
                    agent.setAgentToken(request.getRegisterToken());
                    agent.setHostname(request.getHostname());
                    agent.setOsType(request.getOsType());
                    agent.setIp(request.getIp());
                    agent.setLabels(request.getLabels());
                    agent.setLastHeartbeat(LocalDateTime.now());
                    agent.setStatus("ONLINE");
                    
                    agent = agentRepository.save(agent);
                    isNewRegistration = true;
                    
                    log.info("✓ NEW agent registered successfully");
                    log.info("Agent ID: {}", agent.getAgentId());
                    log.info("Agent Token: {}", agent.getAgentToken().substring(0, Math.min(10, agent.getAgentToken().length())) + "...");
                            
                } catch (Exception e) {
                    // 并发情况下可能违反唯一约束，重新查询已存在的Agent
                    log.warn("Concurrent registration detected for {} ({}), retrying query", 
                            request.getHostname(), request.getOsType());
                            
                    existingAgent = agentRepository.findByHostnameAndOsType(
                            request.getHostname(), request.getOsType());
                            
                    if (existingAgent.isPresent()) {
                        agent = existingAgent.get();
                        wasOffline = "OFFLINE".equals(agent.getStatus());
                        
                        log.info("Found agent after concurrent conflict: {}", agent.getAgentId());
                        
                        agent.setIp(request.getIp());
                        agent.setLabels(request.getLabels());
                        agent.setLastHeartbeat(LocalDateTime.now());
                        agent.setStatus("ONLINE");
                        // 直接使用 registerToken 作为 agentToken
                        agent.setAgentToken(request.getRegisterToken());
                        agent = agentRepository.save(agent);
                        
                        log.info("✓ Agent re-registered after concurrent conflict");
                        log.info("Agent ID: {}", agent.getAgentId());
                        
                        // 不立即重置任务，避免重复执行
                    } else {
                        // 极端情况：仍然查不到，抛出原始异常
                        log.error("✗ Failed to create or find agent after concurrent conflict");
                        throw e;
                    }
                }
            }
            
            RegisterResponse response = new RegisterResponse();
            response.setAgentId(agent.getAgentId());
            response.setAgentToken(agent.getAgentToken());
            
            // token 已变更，主动失效缓存，避免旧 token 被误判为合法
            tokenCache.invalidate(agent.getAgentId());
            
            LogUtil.logAgent(isNewRegistration ? "REGISTER" : "RE-REGISTER", 
                    agent.getAgentId(), agent.getHostname(), 
                    String.format("OS: %s, IP: %s", agent.getOsType(), agent.getIp()));
            
            log.info("Registration type: {}", isNewRegistration ? "NEW" : "RE-REGISTER");
            log.info("========================================");
            return response;
            
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("✗ Agent registration failed: {}", e.getMessage(), e);
            log.info("========================================");
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
    }
    
    @Transactional
    public boolean updateHeartbeat(String agentId, String agentToken, HeartbeatRequest request) {
        Optional<Agent> agentOpt = agentRepository.findByAgentIdAndAgentToken(agentId, agentToken);
        if (agentOpt.isPresent()) {
            Agent agent = agentOpt.get();
            agent.setLastHeartbeat(LocalDateTime.now());
            
            // 只有当Agent不在升级状态时，才设置为ONLINE
            // 升级过程中心跳停止，升级完成后心跳恢复时自动设为ONLINE
            if (!"UPGRADING".equals(agent.getStatus())) {
                agent.setStatus("ONLINE");
            }
            
            // 更新Agent版本信息
            if (request.getAgentVersion() != null) {
                agent.setAgentVersion(request.getAgentVersion());
            }
            
            log.debug("[Agent] Heartbeat received from {} ({})", agent.getHostname(), agentId);
            
            // 更新资源信息
            if (request.getCpuLoad() != null) {
                agent.setCpuLoad(request.getCpuLoad());
            }
            if (request.getFreeMemMb() != null) {
                agent.setFreeMemMb(request.getFreeMemMb());
            }
            if (request.getTotalMemMb() != null) {
                agent.setTotalMemMb(request.getTotalMemMb());
            }
            
            // 更新扩展系统信息（如果提供）
            if (request.getStartUser() != null) {
                agent.setStartUser(request.getStartUser());
            }
            if (request.getWorkingDir() != null) {
                agent.setWorkingDir(request.getWorkingDir());
            }
            if (request.getDiskSpaceGb() != null) {
                agent.setDiskSpaceGb(request.getDiskSpaceGb());
            }
            if (request.getFreeSpaceGb() != null) {
                agent.setFreeSpaceGb(request.getFreeSpaceGb());
            }
            if (request.getOsVersion() != null) {
                agent.setOsVersion(request.getOsVersion());
            }
            if (request.getJavaVersion() != null) {
                agent.setJavaVersion(request.getJavaVersion());
            }
            if (request.getAgentVersion() != null) {
                agent.setAgentVersion(request.getAgentVersion());
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
        // 先查缓存，命中则纯内存比较，不查 DB
        String cached = tokenCache.getIfPresent(agentId);
        if (cached != null) {
            return cached.equals(agentToken);
        }
        // 缓存未命中，查 DB 并回填缓存
        Optional<Agent> agent = agentRepository.findByAgentIdAndAgentToken(agentId, agentToken);
        if (agent.isPresent()) {
            tokenCache.put(agentId, agentToken);
            return true;
        }
        return false;
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
    
    /**
     * 设置Agent为升级状态
     */
    @Transactional
    public boolean setAgentUpgrading(String agentId) {
        Optional<Agent> agentOpt = agentRepository.findByAgentId(agentId);
        if (agentOpt.isPresent()) {
            Agent agent = agentOpt.get();
            String oldStatus = agent.getStatus();
            agent.setStatus("UPGRADING");
            agentRepository.save(agent);
            log.info("[Agent] {} ({}) status changed: {} -> UPGRADING - Upgrade detected", 
                    agent.getHostname(), agentId, oldStatus);
            LogUtil.logAgent("STATUS_CHANGE", agent.getAgentId(), agent.getHostname(), 
                    oldStatus + " -> UPGRADING (upgrade detected)");
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

    /**
     * 删除Agent
     * 注意：只能删除离线的Agent
     */
    @Transactional
    public void deleteAgent(String agentId) {
        Agent agent = agentRepository.findById(agentId).orElse(null);
        if (agent == null) {
            throw new IllegalArgumentException("客户端不存在");
        }

        if ("ONLINE".equals(agent.getStatus())) {
            throw new IllegalStateException("不能删除在线的客户端");
        }

        agentRepository.deleteById(agentId);
        tokenCache.invalidate(agentId);
        log.info("[Agent] Deleted agent: {} ({})", agent.getHostname(), agentId);
        LogUtil.logAgent("DELETE", agentId, agent.getHostname(), "Agent deleted");
    }

    /**
     * 根据ID获取Agent
     */
    public Agent getAgentById(String agentId) {
        return agentRepository.findById(agentId).orElse(null);
    }

    
    // 已删除handleAgentRecovery()方法
    // 原因：agent重连时不应立即重置任务，应由定时任务延迟处理
    // 这样可以避免短暂网络中断导致任务重复执行


    /**
     * 增加Agent的任务计数
     */
    @Transactional
    public void incrementTaskCount(String agentId) {
        Agent agent = agentRepository.findById(agentId).orElse(null);
        if (agent != null) {
            agent.setTaskCount(agent.getTaskCount() == null ? 1 : agent.getTaskCount() + 1);
            agentRepository.save(agent);
            log.info("Agent {} task count incremented to {}", agentId, agent.getTaskCount());
        }
    }

    /**
     * 批量增加多个Agent的任务计数
     */
    @Transactional
    public void incrementTaskCount(List<String> agentIds) {
        for (String agentId : agentIds) {
            incrementTaskCount(agentId);
        }
    }

    /**
     * 更新Agent的最后一次深度检查任务信息
     */
    @Transactional
    public void updateLastDiagnosticTask(String agentId, String taskId, String taskName) {
        try {
            Agent agent = agentRepository.findById(agentId).orElse(null);
            if (agent != null) {
                log.info("Updating deep check task info for agent {}: taskId={}, taskName={}", 
                        agentId, taskId, taskName);
                agent.setLastDiagnosticTaskId(taskId);
                agent.setLastDiagnosticTaskName(taskName);
                agent.setLastDiagnosticTime(LocalDateTime.now());
                agentRepository.save(agent);
                log.info("Successfully updated deep check task info for agent {}", agentId);
            } else {
                log.warn("Agent not found when updating deep check task info: {}", agentId);
            }
        } catch (Exception e) {
            log.error("Failed to update deep check task info for agent {}: {}", agentId, e.getMessage(), e);
            throw e;
        }
    }
}
