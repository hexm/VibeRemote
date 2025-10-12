package com.example.lightscript.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 应用启动测试
 */
@SpringBootTest
@ActiveProfiles("test")
class ServerApplicationTest {

    @Test
    void contextLoads() {
        // 测试Spring上下文是否能正常加载
    }
}
