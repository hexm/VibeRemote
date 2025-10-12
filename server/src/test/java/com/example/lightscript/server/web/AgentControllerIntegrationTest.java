package com.example.lightscript.server.web;

import com.example.lightscript.server.model.AgentModels.RegisterRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AgentController集成测试
 */
@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
class AgentControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testAgentRegisterSuccess() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        RegisterRequest request = new RegisterRequest();
        request.setRegisterToken("test-register-token");
        request.setHostname("test-host");
        request.setOsType("LINUX");
        request.setIp("192.168.1.100");

        mockMvc.perform(post("/api/agent/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentId").exists())
                .andExpect(jsonPath("$.agentToken").exists());
    }

    @Test
    void testAgentRegisterWithInvalidToken() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        RegisterRequest request = new RegisterRequest();
        request.setRegisterToken("invalid-token");
        request.setHostname("test-host");
        request.setOsType("LINUX");
        request.setIp("192.168.1.100");

        mockMvc.perform(post("/api/agent/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(3000));
    }
}
