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
        if (!registerToken.equals(request.getRegisterToken())) {
            throw new BusinessException(ErrorCode.INVALID_REGISTER_TOKEN);
        }
        
        Agent agent = new Agent();
        agent.setAgentId(UUID.randomUUID().toString());
        agent.setAgentToken(UUID.randomUUID().toString());
        agent.setHostname(request.getHostname());
        agent.setOsType(request.getOsType());
        agent.setIp(request.getIp());
        agent.setLabels(request.getLabels());
        agent.setLastHeartbeat(LocalDateTime.now());
        agent.setStatus("ONLINE");
        
        agent = agentRepository.save(agent);
        
        RegisterResponse response = new RegisterResponse();
        response.setAgentId(agent.getAgentId());
        response.setAgentToken(agent.getAgentToken());
        
        LogUtil.logAgent("REGISTER", agent.getAgentId(), agent.getHostname(), 
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
            if (request.getCpuLoad() != null) {
                agent.setCpuLoad(request.getCpuLoad());
            }
            if (request.getFreeMemMb() != null) {
                agent.setFreeMemMb(request.getFreeMemMb());
            }
            agentRepository.save(agent);
            return true;
        }
        return false;
    }
    
    public boolean validateAgent(String agentId, String agentToken) {
        return agentRepository.findByAgentIdAndAgentToken(agentId, agentToken).isPresent();
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
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(2); // 2分钟没有心跳认为离线
        List<Agent> offlineAgents = agentRepository.findOfflineAgents(threshold);
        
        for (Agent agent : offlineAgents) {
            if ("ONLINE".equals(agent.getStatus())) {
                agent.setStatus("OFFLINE");
                agentRepository.save(agent);
                LogUtil.logAgent("STATUS_CHANGE", agent.getAgentId(), agent.getHostname(), 
                        "ONLINE -> OFFLINE (heartbeat timeout)");
            }
        }
    }
}
