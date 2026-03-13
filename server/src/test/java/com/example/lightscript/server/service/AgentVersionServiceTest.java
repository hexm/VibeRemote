package com.example.lightscript.server.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class AgentVersionServiceTest {

    @Test
    public void testVersionComparison() throws Exception {
        AgentVersionService service = new AgentVersionService(null, null);
        
        // 使用反射访问私有方法进行测试
        Method compareVersions = AgentVersionService.class.getDeclaredMethod("compareVersions", String.class, String.class);
        compareVersions.setAccessible(true);
        
        // 测试基本版本比较
        assertTrue((Integer) compareVersions.invoke(service, "2.0.0", "1.9.9") > 0);
        assertTrue((Integer) compareVersions.invoke(service, "1.10.0", "1.9.0") > 0);
        assertTrue((Integer) compareVersions.invoke(service, "1.0.1", "1.0.0") > 0);
        assertEquals(0, (Integer) compareVersions.invoke(service, "1.0.0", "1.0.0"));
        
        // 测试不同长度的版本号
        assertTrue((Integer) compareVersions.invoke(service, "1.1", "1.0.9") > 0);
        assertEquals(0, (Integer) compareVersions.invoke(service, "1.0", "1.0.0"));
        
        // 测试边界情况
        assertTrue((Integer) compareVersions.invoke(service, "1.0.0", null) > 0);
        assertTrue((Integer) compareVersions.invoke(service, null, "1.0.0") < 0);
        assertEquals(0, (Integer) compareVersions.invoke(service, null, null));
        
        // 测试无效版本号（回退到字符串比较）
        assertNotNull(compareVersions.invoke(service, "invalid", "1.0.0"));
    }
    
    @Test
    public void testVersionParsing() throws Exception {
        AgentVersionService service = new AgentVersionService(null, null);
        
        // 使用反射访问私有方法进行测试
        Method parseVersionFromFilename = AgentVersionService.class.getDeclaredMethod("parseVersionFromFilename", String.class);
        parseVersionFromFilename.setAccessible(true);
        
        // 测试标准格式
        assertEquals("1.2.3", parseVersionFromFilename.invoke(service, "agent-1.2.3.jar"));
        assertEquals("2.0.0", parseVersionFromFilename.invoke(service, "lightscript-agent-2.0.0.jar"));
        
        // 测试SNAPSHOT版本
        assertEquals("0.1.0", parseVersionFromFilename.invoke(service, "agent-0.1.0-SNAPSHOT.jar"));
        assertEquals("0.1.0", parseVersionFromFilename.invoke(service, "agent-0.1.0-SNAPSHOT-jar-with-dependencies.jar"));
        
        // 测试四位版本号
        assertEquals("1.2.3.4", parseVersionFromFilename.invoke(service, "agent-1.2.3.4.jar"));
        
        // 测试无效格式
        assertNull(parseVersionFromFilename.invoke(service, "invalid-filename.jar"));
        assertNull(parseVersionFromFilename.invoke(service, "agent-abc.jar"));
        assertNull(parseVersionFromFilename.invoke(service, null));
    }
    
    @Test
    public void testVersionSequence() throws Exception {
        AgentVersionService service = new AgentVersionService(null, null);
        Method compareVersions = AgentVersionService.class.getDeclaredMethod("compareVersions", String.class, String.class);
        compareVersions.setAccessible(true);
        
        String[] versions = {"1.0.0", "1.0.1", "1.1.0", "1.10.0", "2.0.0"};
        
        // 验证版本序列是递增的
        for (int i = 0; i < versions.length - 1; i++) {
            assertTrue((Integer) compareVersions.invoke(service, versions[i + 1], versions[i]) > 0,
                String.format("%s should be greater than %s", versions[i + 1], versions[i]));
        }
    }
}