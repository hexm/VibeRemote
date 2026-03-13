# Agent日志统一化设计

## 文档信息
- **创建时间**: 2026-03-13
- **版本**: 1.0
- **状态**: 设计方案
- **作者**: 系统架构师

## 1. 当前日志分散问题

### 1.1 现状分析

#### Agent当前日志文件
- `logs/agent.log` - Agent主程序日志
- `logs/tasks.log` - 任务执行日志
- `logs/agent.yyyy-MM-dd.*.log` - 按日期和大小滚动的历史日志

#### 升级器当前日志文件
- `logs/upgrade-yyyyMMdd-HHmmss.log` - 每次升级创建独立日志文件

### 1.2 问题识别
- **日志文件过多**: Agent和升级器分别创建日志文件
- **查找困难**: 升级相关问题需要在多个文件中查找
- **时间关联性差**: 难以追踪Agent和升级器的时间序列关系
- **运维复杂**: 需要监控多个日志文件

## 2. 统一日志设计方案

### 2.1 设计目标
- **单一日志文件**: 所有Agent相关日志写入同一个文件
- **清晰标识**: 通过日志前缀区分不同组件
- **时间连续性**: 保持时间顺序，便于问题追踪
- **滚动策略**: 统一的日志滚动和清理策略

### 2.2 统一日志文件结构

#### 主日志文件
```
logs/agent.log - 统一的Agent日志文件
```

#### 日志格式标准
```
yyyy-MM-dd HH:mm:ss.SSS [THREAD] LEVEL [COMPONENT] - MESSAGE
```

#### 组件标识
- `[AGENT]` - Agent主程序日志
- `[TASK]` - 任务执行日志
- `[UPGRADE]` - 升级相关日志
- `[UPGRADER]` - 升级器程序日志

### 2.3 日志示例
```
2026-03-13 10:30:15.123 [main] INFO [AGENT] - Agent started successfully
2026-03-13 10:30:16.456 [scheduler] INFO [TASK] - Task T001 started
2026-03-13 10:35:20.789 [upgrade] INFO [UPGRADE] - Starting upgrade: 2.0.0 -> 2.1.0
2026-03-13 10:35:21.012 [upgrade] INFO [UPGRADE] - Downloading new version...
2026-03-13 10:35:25.345 [upgrade] INFO [UPGRADE] - Starting upgrader process
2026-03-13 10:35:26.678 [main] INFO [UPGRADER] - Upgrader started, backing up current version
2026-03-13 10:35:30.901 [main] INFO [UPGRADER] - Backup completed: backup/agent-20260313-103530
2026-03-13 10:35:31.234 [main] INFO [UPGRADER] - Replacing main JAR file
2026-03-13 10:35:35.567 [main] INFO [UPGRADER] - Starting new version
2026-03-13 10:35:40.890 [main] INFO [UPGRADER] - Upgrade completed successfully
2026-03-13 10:35:41.123 [main] INFO [AGENT] - Agent restarted after upgrade
```

## 3. 实现方案

### 3.1 Agent端日志配置更新

#### 更新logback.xml
```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- 定义日志文件路径 -->
    <property name="LOG_HOME" value="./logs" />
    
    <!-- 控制台输出 -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [AGENT] - %msg%n</pattern>
        </encoder>
    </appender>
    
    <!-- 统一文件输出 -->
    <appender name="UNIFIED_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_HOME}/agent.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <fileNamePattern>${LOG_HOME}/agent.%d{yyyy-MM-dd}.%i.log</fileNamePattern>
            <maxFileSize>50MB</maxFileSize>
            <maxHistory>30</maxHistory>
            <totalSizeCap>2GB</totalSizeCap>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [AGENT] - %msg%n</pattern>
        </encoder>
    </appender>
    
    <!-- 任务执行日志 - 使用统一文件 -->
    <appender name="TASK_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_HOME}/agent.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <fileNamePattern>${LOG_HOME}/agent.%d{yyyy-MM-dd}.%i.log</fileNamePattern>
            <maxFileSize>50MB</maxFileSize>
            <maxHistory>30</maxHistory>
            <totalSizeCap>2GB</totalSizeCap>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [TASK] - %msg%n</pattern>
        </encoder>
    </appender>
    
    <!-- 升级日志 - 使用统一文件 -->
    <appender name="UPGRADE_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_HOME}/agent.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <fileNamePattern>${LOG_HOME}/agent.%d{yyyy-MM-dd}.%i.log</fileNamePattern>
            <maxFileSize>50MB</maxFileSize>
            <maxHistory>30</maxHistory>
            <totalSizeCap>2GB</totalSizeCap>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [UPGRADE] - %msg%n</pattern>
        </encoder>
    </appender>
    
    <!-- 任务执行器的日志 -->
    <logger name="com.example.lightscript.agent.SimpleTaskRunner" level="DEBUG" additivity="false">
        <appender-ref ref="TASK_FILE"/>
        <appender-ref ref="CONSOLE"/>
    </logger>
    
    <!-- 升级执行器的日志 -->
    <logger name="com.example.lightscript.agent.UpgradeExecutor" level="DEBUG" additivity="false">
        <appender-ref ref="UPGRADE_FILE"/>
        <appender-ref ref="CONSOLE"/>
    </logger>
    
    <!-- 根日志级别 -->
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="UNIFIED_FILE"/>
    </root>
</configuration>
```

### 3.2 升级器日志配置更新

#### 修改AgentUpgrader.java的日志初始化
```java
private void initializeLogging() throws IOException {
    File logsDir = new File(agentHome, "logs");
    logsDir.mkdirs();
    
    // 使用统一的agent.log文件，而不是创建新的升级日志文件
    File logFile = new File(logsDir, "agent.log");
    
    // 追加模式写入统一日志文件
    logWriter = new PrintWriter(new FileWriter(logFile, true));
    log("=== Agent Upgrade Started ===");
    log("Agent home: " + agentHome);
    log("Main JAR: " + MAIN_JAR_NAME);
}

private void log(String message) {
    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
    String logEntry = String.format("%s [main] INFO [UPGRADER] - %s", timestamp, message);
    
    // 写入文件
    if (logWriter != null) {
        logWriter.println(logEntry);
        logWriter.flush();
    }
    
    // 同时输出到控制台
    System.out.println(logEntry);
}

private void logError(String message, Exception e) {
    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
    String logEntry = String.format("%s [main] ERROR [UPGRADER] - %s", timestamp, message);
    
    if (logWriter != null) {
        logWriter.println(logEntry);
        if (e != null) {
            logWriter.println(String.format("%s [main] ERROR [UPGRADER] - Exception: %s", timestamp, e.getMessage()));
            e.printStackTrace(logWriter);
        }
        logWriter.flush();
    }
    
    System.err.println(logEntry);
    if (e != null) {
        e.printStackTrace();
    }
}
```

### 3.3 Agent升级执行器日志优化

#### 更新UpgradeExecutor.java
```java
// 在类开头添加专用的升级日志记录器
private static final Logger upgradeLogger = LoggerFactory.getLogger("com.example.lightscript.agent.UpgradeExecutor");

public void executeUpgrade(VersionInfo versionInfo) {
    String fromVersion = getCurrentVersion();
    String toVersion = versionInfo.getVersion();
    boolean forceUpgrade = versionInfo.isForceUpgrade();
    
    upgradeLogger.info("Starting upgrade: {} -> {} (force: {})", fromVersion, toVersion, forceUpgrade);
    
    try {
        // 1. 报告升级开始
        statusReporter.reportUpgradeStart(fromVersion, toVersion, forceUpgrade);
        upgradeLogger.info("Upgrade status reported to server");
        
        // 2. 检查升级器是否存在
        if (!Files.exists(Paths.get(UPGRADER_JAR))) {
            statusReporter.reportUpgradeStatus("FAILED", "Upgrader not found: " + UPGRADER_JAR);
            upgradeLogger.error("Upgrader not found: {}", UPGRADER_JAR);
            return;
        }
        upgradeLogger.info("Upgrader found: {}", UPGRADER_JAR);
        
        // 3. 报告开始下载
        statusReporter.reportUpgradeStatus("DOWNLOADING", null);
        upgradeLogger.info("Starting new version download");
        
        // 4. 下载新版本
        String newVersionPath = downloadNewVersion(versionInfo);
        if (newVersionPath == null) {
            statusReporter.reportUpgradeStatus("FAILED", "Failed to download new version");
            upgradeLogger.error("Failed to download new version");
            return;
        }
        upgradeLogger.info("New version downloaded: {}", newVersionPath);
        
        // 5. 启动升级器
        upgradeLogger.info("Starting upgrader process with new version: {}", newVersionPath);
        startUpgrader(newVersionPath);
        
        // 6. 主程序退出
        upgradeLogger.info("Upgrade initiated, main process exiting...");
        System.exit(0);
        
    } catch (Exception e) {
        upgradeLogger.error("Upgrade failed: {}", e.getMessage(), e);
        statusReporter.reportUpgradeStatus("FAILED", e.getMessage());
    }
}
```

## 4. 实施步骤

### 4.1 第一阶段：更新日志配置

#### 1. 更新Agent的logback.xml
- 统一所有日志到agent.log
- 添加组件标识前缀
- 调整滚动策略

#### 2. 更新升级器日志方法
- 修改initializeLogging()方法
- 更新log()和logError()方法
- 使用统一的日志格式

#### 3. 更新Agent升级执行器
- 添加详细的升级日志记录
- 使用专用的升级日志记录器

### 4.2 第二阶段：测试验证

#### 1. 功能测试
- 验证Agent启动日志正常
- 验证任务执行日志正常
- 验证升级流程日志完整

#### 2. 日志格式验证
- 检查日志格式统一性
- 验证组件标识正确
- 确认时间顺序正确

### 4.3 第三阶段：清理优化

#### 1. 清理旧日志文件
- 删除历史的upgrade-*.log文件
- 清理分散的tasks.log文件

#### 2. 文档更新
- 更新部署文档
- 更新运维手册

## 5. 预期效果

### 5.1 运维改善
- **单一日志文件**: 只需监控logs/agent.log
- **问题追踪**: 升级问题可在一个文件中完整追踪
- **时间连续**: 所有事件按时间顺序记录

### 5.2 日志示例（统一后）
```
2026-03-13 10:30:15.123 [main] INFO [AGENT] - Agent started successfully, version: 2.0.0
2026-03-13 10:30:16.456 [scheduler] INFO [TASK] - Task scheduler started
2026-03-13 10:35:20.789 [upgrade] INFO [UPGRADE] - Starting upgrade: 2.0.0 -> 2.1.0 (force: false)
2026-03-13 10:35:21.012 [upgrade] INFO [UPGRADE] - Upgrade status reported to server
2026-03-13 10:35:21.345 [upgrade] INFO [UPGRADE] - Upgrader found: ./upgrader.jar
2026-03-13 10:35:21.678 [upgrade] INFO [UPGRADE] - Starting new version download
2026-03-13 10:35:25.901 [upgrade] INFO [UPGRADE] - New version downloaded: ./agent-2.1.0.jar
2026-03-13 10:35:26.234 [upgrade] INFO [UPGRADE] - Starting upgrader process with new version: ./agent-2.1.0.jar
2026-03-13 10:35:26.567 [upgrade] INFO [UPGRADE] - Upgrade initiated, main process exiting...
2026-03-13 10:35:27.890 [main] INFO [UPGRADER] - === Agent Upgrade Started ===
2026-03-13 10:35:28.123 [main] INFO [UPGRADER] - Agent home: /opt/lightscript/agent
2026-03-13 10:35:28.456 [main] INFO [UPGRADER] - Main JAR: agent.jar
2026-03-13 10:35:29.789 [main] INFO [UPGRADER] - Validating new version file: ./agent-2.1.0.jar
2026-03-13 10:35:30.012 [main] INFO [UPGRADER] - Creating backup of current version
2026-03-13 10:35:32.345 [main] INFO [UPGRADER] - Backup completed: backup/agent-20260313-103532
2026-03-13 10:35:32.678 [main] INFO [UPGRADER] - Replacing main JAR file
2026-03-13 10:35:33.901 [main] INFO [UPGRADER] - Starting new version
2026-03-13 10:35:38.234 [main] INFO [UPGRADER] - Verifying startup...
2026-03-13 10:35:42.567 [main] INFO [UPGRADER] - Startup verification successful
2026-03-13 10:35:42.890 [main] INFO [UPGRADER] - Cleaning up temporary files
2026-03-13 10:35:43.123 [main] INFO [UPGRADER] - === Agent Upgrade Completed Successfully ===
2026-03-13 10:35:45.456 [main] INFO [AGENT] - Agent started successfully, version: 2.1.0
2026-03-13 10:35:46.789 [scheduler] INFO [TASK] - Task scheduler started
```

### 5.3 运维优势
- **问题定位快**: 一个文件包含完整的升级流程
- **监控简单**: 只需要监控一个日志文件
- **分析方便**: 可以清楚看到Agent和升级器的交互过程
- **存储优化**: 统一的滚动策略，避免日志文件碎片化

这个设计将大大简化Agent的日志管理，提高问题排查效率。