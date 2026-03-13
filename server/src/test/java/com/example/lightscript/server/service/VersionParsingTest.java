package com.example.lightscript.server.service;

import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

public class VersionParsingTest {

    private String parseVersionFromFilename(String filename) {
        if (filename == null) return null;
        
        // 移除文件扩展名
        String nameWithoutExt = filename.replaceAll("\\.[^.]+$", "");
        
        // 使用简单的正则表达式，只匹配版本号部分
        Pattern pattern = Pattern.compile("(\\d+\\.\\d+\\.\\d+(?:\\.\\d+)?)");
        Matcher matcher = pattern.matcher(nameWithoutExt);
        
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return null;
    }

    @Test
    public void testVersionParsing() {
        // 测试标准格式
        assertEquals("1.2.3", parseVersionFromFilename("agent-1.2.3.jar"));
        assertEquals("2.0.0", parseVersionFromFilename("lightscript-agent-2.0.0.jar"));
        
        // 测试SNAPSHOT版本
        assertEquals("0.1.0", parseVersionFromFilename("agent-0.1.0-SNAPSHOT.jar"));
        assertEquals("0.1.0", parseVersionFromFilename("agent-0.1.0-SNAPSHOT-jar-with-dependencies.jar"));
        
        // 测试四位版本号
        assertEquals("1.2.3.4", parseVersionFromFilename("agent-1.2.3.4.jar"));
        
        // 测试无效格式
        assertNull(parseVersionFromFilename("invalid-filename.jar"));
        assertNull(parseVersionFromFilename("agent-abc.jar"));
        assertNull(parseVersionFromFilename(null));
        
        // 测试复杂的SNAPSHOT格式
        assertEquals("0.1.0", parseVersionFromFilename("agent-0.1.0-SNAPSHOT-jar-with-dependencies.jar"));
        assertEquals("1.0.0", parseVersionFromFilename("my-agent-1.0.0-RC1.jar"));
        assertEquals("2.1.5", parseVersionFromFilename("lightscript-2.1.5-BETA.jar"));
    }
}