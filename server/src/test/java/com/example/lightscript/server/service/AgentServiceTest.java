package com.example.lightscript.server.service;

import com.example.lightscript.server.entity.Agent;
import com.example.lightscript.server.exception.BusinessException;
import com.example.lightscript.server.exception.ErrorCode;
import com.example.lightscript.server.model.AgentModels.RegisterRequest;
import com.example.lightscript.server.model.AgentModels.RegisterResponse;
import com.example.lightscript.server.repository.AgentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AgentService单元测试
 */
@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    @Mock
    private AgentRepository agentRepository;

    @InjectMocks
    private AgentService agentService;

    private RegisterRequest validRegisterRequest;
    private Agent mockAgent;

    @BeforeEach
    void setUp() {
        // 设置注册令牌
        ReflectionTestUtils.setField(agentService, "registerToken", "test-token");

        // 创建有效的注册请求
        validRegisterRequest = new RegisterRequest();
        validRegisterRequest.setRegisterToken("test-token");
        validRegisterRequest.setHostname("test-host");
        validRegisterRequest.setOsType("LINUX");
        validRegisterRequest.setIp("192.168.1.100");

        // 创建模拟Agent
        mockAgent = new Agent();
        mockAgent.setAgentId("test-agent-id");
        mockAgent.setAgentToken("test-agent-token");
        mockAgent.setHostname("test-host");
        mockAgent.setOsType("LINUX");
        mockAgent.setIp("192.168.1.100");
        mockAgent.setStatus("ONLINE");
    }

    @Test
    void testRegisterSuccess() {
        // Given
        when(agentRepository.save(any(Agent.class))).thenReturn(mockAgent);

        // When
        RegisterResponse response = agentService.register(validRegisterRequest);

        // Then
        assertNotNull(response);
        assertEquals("test-agent-id", response.getAgentId());
        assertEquals("test-agent-token", response.getAgentToken());
        verify(agentRepository, times(1)).save(any(Agent.class));
    }

    @Test
    void testRegisterWithInvalidToken() {
        // Given
        RegisterRequest invalidRequest = new RegisterRequest();
        invalidRequest.setRegisterToken("invalid-token");
        invalidRequest.setHostname("test-host");
        invalidRequest.setOsType("LINUX");

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, 
                () -> agentService.register(invalidRequest));
        assertEquals(ErrorCode.INVALID_REGISTER_TOKEN, exception.getErrorCode());
        verify(agentRepository, never()).save(any(Agent.class));
    }

    @Test
    void testValidateAgentSuccess() {
        // Given
        when(agentRepository.findByAgentIdAndAgentToken("test-agent-id", "test-agent-token"))
                .thenReturn(Optional.of(mockAgent));

        // When
        boolean result = agentService.validateAgent("test-agent-id", "test-agent-token");

        // Then
        assertTrue(result);
        verify(agentRepository, times(1))
                .findByAgentIdAndAgentToken("test-agent-id", "test-agent-token");
    }

    @Test
    void testValidateAgentFailure() {
        // Given
        when(agentRepository.findByAgentIdAndAgentToken("invalid-id", "invalid-token"))
                .thenReturn(Optional.empty());

        // When
        boolean result = agentService.validateAgent("invalid-id", "invalid-token");

        // Then
        assertFalse(result);
        verify(agentRepository, times(1))
                .findByAgentIdAndAgentToken("invalid-id", "invalid-token");
    }

    @Test
    void testGetAgentSuccess() {
        // Given
        when(agentRepository.findById("test-agent-id")).thenReturn(Optional.of(mockAgent));

        // When
        Optional<Agent> result = agentService.getAgent("test-agent-id");

        // Then
        assertTrue(result.isPresent());
        assertEquals(mockAgent, result.get());
        verify(agentRepository, times(1)).findById("test-agent-id");
    }

    @Test
    void testGetAgentNotFound() {
        // Given
        when(agentRepository.findById("non-existent-id")).thenReturn(Optional.empty());

        // When
        Optional<Agent> result = agentService.getAgent("non-existent-id");

        // Then
        assertFalse(result.isPresent());
        verify(agentRepository, times(1)).findById("non-existent-id");
    }
}
