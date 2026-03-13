# Agent自动升级设计方案（最终版）

## 文档信息
- **创建时间**: 2026-03-11
- **版本**: 2.0
- **状态**: 设计阶段
- **作者**: 系统架构师

## 1. 需求概述

### 1.1 业务需求
- Agent能够自动检测服务端的新版本
- 支持自动下载和安装新版本
- 支持强制升级和可选升级两种模式
- **任务执行过程中禁止升级操作**
- 升级过程中保持服务连续性
- 升级失败时能够回滚到原版本
- 跨平台支持（Windows/Linux/macOS）

### 1.2 技术需求
- 心跳集成的版本检查机制
- 文件下载和校验
- 独立升级器程序
- 配置和数据迁移
- 日志记录和监控

## 2. 系统架构设计

### 2.1 整体架构
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   管理端Web     │    │   服务端API     │    │   Agent客户端   │
│                 │    │                 │    │                 │
│ - 版本管理界面  │    │ - 版本信息API   │    │ - 心跳版本检查  │
│ - 升级策略配置  │    │ - 文件下载API   │    │ - 文件下载      │
│ - 升级状态监控  │    │ - 心跳响应增强  │    │ - 升级器调用    │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 │
                    ┌─────────────────┐
                    │   文件存储      │
                    │                 │
                    │ - Agent安装包   │
                    │ - 版本元数据    │
                    │ - 校验文件      │
                    └─────────────────┘
```

### 2.2 Agent目录结构
```
/opt/lightscript/
├── agent.jar                 # 主程序
├── upgrader.jar              # 升级器程序（独立JAR，不升级）
├── config/                   # 配置目录
├── data/                     # 数据目录
├── logs/                     # 日志目录
└── backup/                   # 备份目录
    └── 20260311-140024/      # 按时间戳命名的备份
        ├── agent.jar
        ├── config/
        └── data/
```

### 2.3 核心组件

### 2.3.1 服务端组件
- **版本管理服务** (AgentVersionService): 已实现
- **心跳服务增强** (HeartbeatService): 需修改
- **文件下载服务** (FileDownloadService): 已有文件下载API
- **升级状态管理服务** (UpgradeStatusService): 需新增
- **升级日志服务** (UpgradeLogService): 需新增

#### 2.3.2 Agent端组件
- **心跳版本检查器** (HeartbeatVersionChecker): 需新增
- **任务状态监控器** (TaskStatusMonitor): 需新增
- **文件下载器** (FileDownloader): 需新增
- **升级执行器** (UpgradeExecutor): 需新增
- **升级状态报告器** (UpgradeStatusReporter): 需新增

#### 2.3.3 独立升级器
- **升级器程序** (upgrader.jar): 需新增，独立JAR程序

## 3. 详细设计

### 3.1 心跳集成版本检查

#### 3.1.1 服务端心跳API增强
```java
// 修改心跳响应，增加版本检查信息
@PostMapping("/heartbeat")
public ResponseEntity<HeartbeatResponse> heartbeat(@Valid @RequestBody HeartbeatRequest req) {
    boolean success = agentService.updateHeartbeat(req.getAgentId(), req.getAgentToken(), req);
    if (!success) {
        throw new BusinessException(ErrorCode.AGENT_TOKEN_INVALID);
    }
    
    // 构建响应，包含版本检查信息
    HeartbeatResponse response = new HeartbeatResponse();
    
    // 检查版本更新（如果Agent在心跳中报告了版本信息）
    if (req.getAgentVersion() != null) {
        VersionCheckResult versionCheck = agentVersionService.checkForUpdate(
            req.getAgentVersion(), 
            req.getPlatform() != null ? req.getPlatform() : "ALL"
        );
        response.setVersionCheck(versionCheck);
    }
    
    return ResponseEntity.ok(response);
}

// 响应模型
@Data
public static class HeartbeatResponse {
    private VersionCheckResult versionCheck;
}

@Data
public static class VersionCheckResult {
    private boolean updateAvailable;
    private String message;
    private VersionInfo latestVersion;
}

@Data
public static class VersionInfo {
    private String version;
    private String downloadUrl;
    private Long fileSize;
    private String fileHash;
    private boolean forceUpgrade;
    private String releaseNotes;
}
```

#### 3.1.2 Agent端心跳版本检查
```java
// Agent端修改心跳逻辑，处理版本检查响应
public class HeartbeatVersionChecker {
    private final TaskStatusMonitor taskStatusMonitor;
    private final UpgradeExecutor upgradeExecutor;
    private final AgentApi agentApi;
    
    private void handleVersionCheck(VersionCheckResult versionCheck) {
        if (!versionCheck.isUpdateAvailable()) {
            return; // 无更新
        }
        
        log.info("Version update available: {}", versionCheck.getMessage());
        
        // 根据强制升级标志决定升级策略
        if (versionCheck.getLatestVersion().isForceUpgrade()) {
            log.info("Force upgrade detected, setting status to UPGRADING...");
            // 1. 主动设置Agent状态为UPGRADING
            try {
                agentApi.setUpgrading(agentId, agentToken);
                log.info("Status set to UPGRADING, stopping all tasks and starting upgrade immediately...");
            } catch (Exception e) {
                log.error("Failed to set upgrading status: " + e.getMessage());
                return;
            }
            
            // 2. 强制升级：立即停止所有任务并开始升级
            taskStatusMonitor.stopAllTasks();
            upgradeExecutor.executeUpgrade(versionCheck.getLatestVersion());
        } else {
            log.info("Normal upgrade detected, setting status to UPGRADING...");
            // 1. 主动设置Agent状态为UPGRADING
            try {
                agentApi.setUpgrading(agentId, agentToken);
                log.info("Status set to UPGRADING, entering upgrade waiting state...");
            } catch (Exception e) {
                log.error("Failed to set upgrading status: " + e.getMessage());
                return;
            }
            
            // 2. 普通升级：进入升级状态，等待任务完成后升级
            if (!taskStatusMonitor.hasRunningTasks()) {
                log.info("No running tasks, starting upgrade immediately...");
                upgradeExecutor.executeUpgrade(versionCheck.getLatestVersion());
            } else {
                log.info("Tasks are running, waiting for completion before upgrade...");
                upgradeExecutor.scheduleUpgradeRetry(versionCheck.getLatestVersion());
            }
        }
    }
}
```

### 3.2 任务状态监控器

```java
public class TaskStatusMonitor {
    private final AtomicInteger runningTaskCount = new AtomicInteger(0);
    private final Set<String> runningTaskIds = ConcurrentHashMap.newKeySet();
    
    /**
     * 检查是否有正在运行的任务
     */
    public boolean hasRunningTasks() {
        return runningTaskCount.get() > 0;
    }
    
    /**
     * 任务开始时调用
     */
    public void onTaskStart(String taskId) {
        runningTaskIds.add(taskId);
        runningTaskCount.incrementAndGet();
        log.info("Task started: {}, running tasks: {}", taskId, runningTaskCount.get());
    }
    
    /**
     * 任务结束时调用
     */
    public void onTaskComplete(String taskId) {
        if (runningTaskIds.remove(taskId)) {
            runningTaskCount.decrementAndGet();
            log.info("Task completed: {}, running tasks: {}", taskId, runningTaskCount.get());
        }
    }
    
    /**
     * 获取正在运行的任务列表
     */
    public Set<String> getRunningTaskIds() {
        return new HashSet<>(runningTaskIds);
    }
}

// 在SimpleTaskRunner中集成任务状态监控
public class SimpleTaskRunner {
    private final TaskStatusMonitor taskStatusMonitor;
    
    public void executeTask(TaskSpec task) {
        String taskId = task.getId();
        
        try {
            // 任务开始时注册
            taskStatusMonitor.onTaskStart(taskId);
            
            // 执行任务逻辑
            performTask(task);
            
        } catch (Exception e) {
            log.error("Task execution failed: " + taskId, e);
            throw e;
        } finally {
            // 任务结束时注销（无论成功还是失败）
            taskStatusMonitor.onTaskComplete(taskId);
        }
    }
}
```

### 3.4 升级状态管理和日志记录

#### 3.4.1 服务端升级状态管理
```java
// 升级状态实体
@Entity
@Table(name = "agent_upgrade_logs")
@Data
public class AgentUpgradeLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "agent_id", nullable = false)
    private String agentId;
    
    @Column(name = "from_version", length = 50)
    private String fromVersion;
    
    @Column(name = "to_version", length = 50)
    private String toVersion;
    
    @Column(name = "upgrade_status", length = 20)
    private String upgradeStatus; // STARTED, DOWNLOADING, INSTALLING, SUCCESS, FAILED, ROLLBACK
    
    @Column(name = "start_time")
    private LocalDateTime startTime;
    
    @Column(name = "end_time")
    private LocalDateTime endTime;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "force_upgrade")
    private Boolean forceUpgrade;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}

// 升级状态服务
@Service
@RequiredArgsConstructor
public class UpgradeStatusService {
    private final AgentUpgradeLogRepository upgradeLogRepository;
    private final AgentRepository agentRepository;
    
    /**
     * 开始升级
     */
    public Long startUpgrade(String agentId, String fromVersion, String toVersion, boolean forceUpgrade) {
        AgentUpgradeLog log = new AgentUpgradeLog();
        log.setAgentId(agentId);
        log.setFromVersion(fromVersion);
        log.setToVersion(toVersion);
        log.setUpgradeStatus("STARTED");
        log.setForceUpgrade(forceUpgrade);
        log.setStartTime(LocalDateTime.now());
        log.setCreatedAt(LocalDateTime.now());
        
        AgentUpgradeLog saved = upgradeLogRepository.save(log);
        
        // 更新Agent状态为升级中
        updateAgentStatus(agentId, "UPGRADING");
        
        log.info("Upgrade started for agent {}: {} -> {}", agentId, fromVersion, toVersion);
        return saved.getId();
    }
    
    /**
     * 更新升级状态
     */
    public void updateUpgradeStatus(Long upgradeLogId, String status, String errorMessage) {
        Optional<AgentUpgradeLog> logOpt = upgradeLogRepository.findById(upgradeLogId);
        if (logOpt.isPresent()) {
            AgentUpgradeLog log = logOpt.get();
            log.setUpgradeStatus(status);
            if (errorMessage != null) {
                log.setErrorMessage(errorMessage);
            }
            if ("SUCCESS".equals(status) || "FAILED".equals(status) || "ROLLBACK".equals(status)) {
                log.setEndTime(LocalDateTime.now());
                // 升级完成，恢复Agent正常状态
                updateAgentStatus(log.getAgentId(), "ONLINE");
            }
            upgradeLogRepository.save(log);
            
            log.info("Upgrade status updated for agent {}: {}", log.getAgentId(), status);
        }
    }
    
    /**
     * 更新Agent的状态
     */
    private void updateAgentStatus(String agentId, String status) {
        Optional<Agent> agentOpt = agentRepository.findByAgentId(agentId);
        if (agentOpt.isPresent()) {
            Agent agent = agentOpt.get();
            agent.setStatus(status); // 直接使用status字段
            agentRepository.save(agent);
        }
    }
    
    /**
     * 获取Agent的升级历史
     */
    public List<AgentUpgradeLog> getUpgradeHistory(String agentId) {
        return upgradeLogRepository.findByAgentIdOrderByCreatedAtDesc(agentId);
    }
    
    /**
     * 检查Agent是否正在升级
     */
    public boolean isAgentUpgrading(String agentId) {
        return upgradeLogRepository.existsByAgentIdAndUpgradeStatusIn(
            agentId, Arrays.asList("STARTED", "DOWNLOADING", "INSTALLING")
        );
    }
}

// 升级状态API
@RestController
@RequestMapping("/api/agent")
public class UpgradeStatusController {
    private final UpgradeStatusService upgradeStatusService;
    private final AgentService agentService;
    
    /**
     * Agent主动设置自己的状态为升级中
     */
    @PostMapping("/status/upgrading")
    public ResponseEntity<Void> setUpgrading(
            @RequestParam String agentId,
            @RequestParam String agentToken) {
        
        if (!agentService.validateAgent(agentId, agentToken)) {
            throw new BusinessException(ErrorCode.AGENT_TOKEN_INVALID);
        }
        
        agentService.setAgentUpgrading(agentId);
        return ResponseEntity.ok().build();
    }
    
    /**
     * Agent主动设置自己的状态为在线
     */
    @PostMapping("/status/online")
    public ResponseEntity<Void> setOnline(
            @RequestParam String agentId,
            @RequestParam String agentToken) {
        
        if (!agentService.validateAgent(agentId, agentToken)) {
            throw new BusinessException(ErrorCode.AGENT_TOKEN_INVALID);
        }
        
        agentService.setAgentOnline(agentId);
        return ResponseEntity.ok().build();
    }
    
    /**
     * Agent报告升级开始
     */
    @PostMapping("/upgrade/start")
    public ResponseEntity<Map<String, Object>> reportUpgradeStart(
            @RequestParam String agentId,
            @RequestParam String agentToken,
            @RequestBody UpgradeStartRequest request) {
        
        if (!agentService.validateAgent(agentId, agentToken)) {
            throw new BusinessException(ErrorCode.AGENT_TOKEN_INVALID);
        }
        
        Long upgradeLogId = upgradeStatusService.startUpgrade(
            agentId, 
            request.getFromVersion(), 
            request.getToVersion(), 
            request.isForceUpgrade()
        );
        
        Map<String, Object> response = new HashMap<>();
        response.put("upgradeLogId", upgradeLogId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Agent报告升级状态更新
     */
    @PostMapping("/upgrade/status")
    public ResponseEntity<Void> reportUpgradeStatus(
            @RequestParam String agentId,
            @RequestParam String agentToken,
            @RequestBody UpgradeStatusRequest request) {
        
        if (!agentService.validateAgent(agentId, agentToken)) {
            throw new BusinessException(ErrorCode.AGENT_TOKEN_INVALID);
        }
        
        upgradeStatusService.updateUpgradeStatus(
            request.getUpgradeLogId(),
            request.getStatus(),
            request.getErrorMessage()
        );
        
        return ResponseEntity.ok().build();
    }
}
```

#### 3.4.2 Agent端升级状态报告
```java
public class UpgradeStatusReporter {
    private final AgentApi agentApi;
    private Long currentUpgradeLogId;
    
    /**
     * 报告升级开始
     */
    public void reportUpgradeStart(String fromVersion, String toVersion, boolean forceUpgrade) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("fromVersion", fromVersion);
            request.put("toVersion", toVersion);
            request.put("forceUpgrade", forceUpgrade);
            
            Map<String, Object> response = agentApi.post("/api/agent/upgrade/start", request);
            this.currentUpgradeLogId = ((Number) response.get("upgradeLogId")).longValue();
            
            log.info("Reported upgrade start: {} -> {}, logId: {}", 
                    fromVersion, toVersion, currentUpgradeLogId);
                    
        } catch (Exception e) {
            log.error("Failed to report upgrade start", e);
        }
    }
    
    /**
     * 报告升级状态
     */
    public void reportUpgradeStatus(String status, String errorMessage) {
        if (currentUpgradeLogId == null) {
            log.warn("No current upgrade log ID, skipping status report");
            return;
        }
        
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("upgradeLogId", currentUpgradeLogId);
            request.put("status", status);
            if (errorMessage != null) {
                request.put("errorMessage", errorMessage);
            }
            
            agentApi.post("/api/agent/upgrade/status", request);
            
            log.info("Reported upgrade status: {} (logId: {})", status, currentUpgradeLogId);
            
            // 如果升级完成，清除当前升级ID
            if ("SUCCESS".equals(status) || "FAILED".equals(status) || "ROLLBACK".equals(status)) {
                currentUpgradeLogId = null;
            }
            
        } catch (Exception e) {
            log.error("Failed to report upgrade status: {}", status, e);
        }
    }
}
```

### 3.5 独立升级器设计

#### 3.5.1 升级器程序 (upgrader.jar)
```java
public class AgentUpgrader {
    private UpgradeStatusReporter statusReporter;
    
    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Usage: java -jar upgrader.jar <new-version-path> <agent-home> <upgrade-log-id> <server-url>");
            System.exit(1);
        }
        
        String newVersionPath = args[0];
        String agentHome = args[1];
        Long upgradeLogId = Long.parseLong(args[2]);
        String serverUrl = args[3];
        
        try {
            AgentUpgrader upgrader = new AgentUpgrader();
            upgrader.statusReporter = new UpgradeStatusReporter(serverUrl, upgradeLogId);
            upgrader.performUpgrade(newVersionPath, agentHome);
        } catch (Exception e) {
            System.err.println("Upgrade failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private void performUpgrade(String newVersionPath, String agentHome) throws Exception {
        System.out.println("Starting Agent upgrade process...");
        
        try {
            // 1. 报告开始安装
            statusReporter.reportStatus("INSTALLING", null);
            
            // 2. 等待主进程完全退出
            waitForProcessExit();
            
            // 3. 备份当前版本
            String backupDir = createBackup(agentHome);
            
            // 4. 替换主程序
            replaceMainJar(newVersionPath, agentHome);
            
            // 5. 启动新版本
            startNewVersion(agentHome);
            
            // 6. 验证启动成功
            if (verifyStartup(agentHome)) {
                System.out.println("Upgrade successful");
                statusReporter.reportStatus("SUCCESS", null);
                cleanupTempFiles(newVersionPath);
            } else {
                throw new Exception("New version failed to start");
            }
            
        } catch (Exception e) {
            System.err.println("Upgrade failed, rolling back...");
            statusReporter.reportStatus("ROLLBACK", e.getMessage());
            rollback(backupDir, agentHome);
            throw e;
        }
    }
    
    // ... 其他方法保持不变
}
```

#### 3.5.2 主程序中的升级执行器
```java
public class UpgradeExecutor {
    private static final String UPGRADER_JAR = "upgrader.jar";
    private final UpgradeStatusReporter statusReporter;
    
    public void executeUpgrade(VersionInfo versionInfo) {
        String fromVersion = getCurrentVersion();
        String toVersion = versionInfo.getVersion();
        boolean forceUpgrade = versionInfo.isForceUpgrade();
        
        try {
            // 1. 报告升级开始
            statusReporter.reportUpgradeStart(fromVersion, toVersion, forceUpgrade);
            
            // 2. 检查升级器是否存在
            if (!Files.exists(Paths.get(UPGRADER_JAR))) {
                statusReporter.reportUpgradeStatus("FAILED", "Upgrader not found: " + UPGRADER_JAR);
                return;
            }
            
            // 3. 报告开始下载
            statusReporter.reportUpgradeStatus("DOWNLOADING", null);
            
            // 4. 下载新版本
            String newVersionPath = downloadNewVersion(versionInfo);
            
            // 5. 启动升级器
            startUpgrader(newVersionPath);
            
            // 6. 主程序退出
            log.info("Upgrade initiated, main process exiting...");
            System.exit(0);
            
        } catch (Exception e) {
            log.error("Upgrade failed", e);
            statusReporter.reportUpgradeStatus("FAILED", e.getMessage());
        }
    }
    
    private void startUpgrader(String newVersionPath) throws IOException {
        String agentHome = System.getProperty("user.dir");
        String serverUrl = getServerUrl();
        
        ProcessBuilder pb = new ProcessBuilder();
        pb.command("java", "-jar", UPGRADER_JAR, 
                  newVersionPath, agentHome, 
                  statusReporter.getCurrentUpgradeLogId().toString(),
                  serverUrl);
        pb.directory(new File(agentHome));
        
        // 重定向输出到日志文件
        pb.redirectOutput(new File("upgrade.log"));
        pb.redirectError(new File("upgrade-error.log"));
        
        Process process = pb.start();
        log.info("Upgrader started with PID: {}", process.pid());
    }
}
```

### 3.6 Agent版本显示和任务分发控制

#### 3.6.1 Agent实体增强
```java
// 在Agent实体中增加版本字段
@Entity
@Table(name = "agents")
@Data
public class Agent {
    // ... 现有字段
    
    @Column(name = "agent_version", length = 50)
    private String agentVersion; // Agent版本号
    
    // 复用现有的status字段：ONLINE, OFFLINE, UPGRADING
    // @Column(name = "status", length = 20) // 已存在
    
    // ... 其他字段
}
```

#### 3.6.2 心跳服务增强
```java
// 修改心跳服务，保存Agent版本信息
@Service
public class AgentService {
    
    public boolean updateHeartbeat(String agentId, String agentToken, HeartbeatRequest request) {
        Optional<Agent> agentOpt = agentRepository.findByAgentIdAndAgentToken(agentId, agentToken);
        if (agentOpt.isPresent()) {
            Agent agent = agentOpt.get();
            agent.setLastHeartbeat(LocalDateTime.now());
            agent.setStatus("ONLINE");
            
            // 更新Agent版本信息
            if (request.getAgentVersion() != null) {
                agent.setAgentVersion(request.getAgentVersion());
            }
            
            // 更新其他信息...
            agentRepository.save(agent);
            return true;
        }
        return false;
    }
}
```

#### 3.6.3 任务分发控制
```java
// 修改任务拉取逻辑，升级中的Agent不接收新任务
@Service
public class TaskService {
    
    public List<TaskSpec> pullTasks(String agentId, int maxTasks) {
        // 检查Agent是否正在升级
        Optional<Agent> agentOpt = agentRepository.findByAgentId(agentId);
        if (agentOpt.isPresent()) {
            Agent agent = agentOpt.get();
            if ("UPGRADING".equals(agent.getStatus())) {
                log.info("Agent {} is upgrading, no tasks will be assigned", agentId);
                return Collections.emptyList(); // 升级中不分发任务
            }
        }
        
        // 正常的任务拉取逻辑
        return taskRepository.findPendingTasksForAgent(agentId, maxTasks);
    }
}
```

#### 3.6.4 前端Agent详情页面增强
```javascript
// 在Agent详情页面显示版本信息
const AgentDetail = ({ agent }) => {
  return (
    <Card title="Agent详情">
      <Descriptions column={2}>
        <Descriptions.Item label="主机名">{agent.hostname}</Descriptions.Item>
        <Descriptions.Item label="Agent版本">
          <Space>
            <Tag color="blue">{agent.agentVersion || 'Unknown'}</Tag>
            {agent.status === 'UPGRADING' && (
              <Tag color="orange" icon={<SyncOutlined spin />}>
                升级中
              </Tag>
            )}
          </Space>
        </Descriptions.Item>
        <Descriptions.Item label="状态">
          <Badge 
            status={agent.status === 'ONLINE' ? 'success' : 'error'} 
            text={agent.status} 
          />
        </Descriptions.Item>
        <Descriptions.Item label="最后心跳">
          {formatDateTime(agent.lastHeartbeat)}
        </Descriptions.Item>
        {/* 其他字段... */}
      </Descriptions>
      
      {/* 升级历史 */}
      <Divider>升级历史</Divider>
      <UpgradeHistoryTable agentId={agent.agentId} />
    </Card>
  );
};

// 升级历史组件
const UpgradeHistoryTable = ({ agentId }) => {
  const [upgradeHistory, setUpgradeHistory] = useState([]);
  const [loading, setLoading] = useState(false);
  
  useEffect(() => {
    loadUpgradeHistory();
  }, [agentId]);
  
  const loadUpgradeHistory = async () => {
    setLoading(true);
    try {
      const response = await api.get(`/web/agents/${agentId}/upgrade-history`);
      setUpgradeHistory(response);
    } catch (error) {
      message.error('加载升级历史失败');
    } finally {
      setLoading(false);
    }
  };
  
  const columns = [
    {
      title: '升级时间',
      dataIndex: 'startTime',
      render: (text) => formatDateTime(text),
    },
    {
      title: '版本变更',
      render: (_, record) => (
        <span>{record.fromVersion} → {record.toVersion}</span>
      ),
    },
    {
      title: '升级状态',
      dataIndex: 'upgradeStatus',
      render: (status) => {
        const statusConfig = {
          SUCCESS: { color: 'success', text: '成功' },
          FAILED: { color: 'error', text: '失败' },
          ROLLBACK: { color: 'warning', text: '回滚' },
          UPGRADING: { color: 'processing', text: '升级中' },
        };
        const config = statusConfig[status] || { color: 'default', text: status };
        return <Badge status={config.color} text={config.text} />;
      },
    },
    {
      title: '耗时',
      render: (_, record) => {
        if (record.endTime && record.startTime) {
          const duration = moment(record.endTime).diff(moment(record.startTime), 'seconds');
          return `${duration}秒`;
        }
        return '-';
      },
    },
    {
      title: '强制升级',
      dataIndex: 'forceUpgrade',
      render: (force) => force ? <Tag color="red">是</Tag> : <Tag>否</Tag>,
    },
  ];
  
  return (
    <Table
      columns={columns}
      dataSource={upgradeHistory}
      rowKey="id"
      loading={loading}
      size="small"
      pagination={{ pageSize: 5 }}
    />
  );
};
```

## 4. 升级场景和流程

### 4.1 正常升级场景（无任务执行）

**时间线**：
```
14:00:00 - Agent心跳时检测到版本更新 (1.0.0 -> 1.1.0)
14:00:01 - 检查任务状态：TaskStatusMonitor.hasRunningTasks() = false
14:00:02 - Agent主动请求：POST /api/agent/status/upgrading
14:00:03 - 服务端设置Agent状态为UPGRADING，停止分发新任务
14:00:04 - 报告升级开始：POST /api/agent/upgrade/start
14:00:05 - 服务端创建升级日志，返回upgradeLogId
14:00:06 - 开始下载新版本，报告状态：DOWNLOADING
14:00:17 - 下载完成，SHA256校验通过
14:00:18 - 启动升级器，主程序退出，心跳停止
14:00:19 - 升级器报告状态：INSTALLING

--- 升级器执行（心跳已停止）---
14:00:24 - 等待主进程退出（5秒）
14:00:25 - 创建备份：/opt/lightscript/backup/20260311-140023/
14:00:26 - 备份当前版本和配置
14:00:27 - 替换主程序：agent-1.1.0.jar -> agent.jar
14:00:28 - 启动新版本：nohup java -jar agent.jar
14:00:38 - 验证启动成功（等待10秒）
14:00:39 - 报告升级成功：SUCCESS

--- 新版本Agent启动（心跳恢复）---
14:00:40 - 新版本Agent发送心跳
14:00:41 - 服务端检测到心跳恢复，自动设置Agent状态为ONLINE
14:00:42 - 升级完成，清理临时文件，升级器退出
```

### 4.2 有任务执行时的升级

#### 4.2.1 普通升级场景
**时间线**：
```
14:00:00 - 心跳检测到普通升级，有任务正在执行
14:00:01 - Agent主动请求：POST /api/agent/status/upgrading
14:00:02 - 服务端设置Agent状态为UPGRADING，停止分发新任务
14:00:03 - 进入升级等待状态，每10秒检查任务完成情况
14:00:13 - 检查任务状态：仍有2个任务在执行
14:00:23 - 检查任务状态：仍有1个任务在执行
14:05:33 - 检查任务状态：所有任务已完成
14:05:34 - 开始升级流程（同正常升级场景）
```

#### 4.2.2 强制升级场景
**时间线**：
```
14:00:00 - 心跳检测到强制升级，有任务正在执行
14:00:01 - Agent主动请求：POST /api/agent/status/upgrading
14:00:02 - 服务端设置Agent状态为UPGRADING，停止分发新任务
14:00:03 - 立即停止所有正在执行的任务（3个任务被中断）
14:00:04 - 开始升级流程（同正常升级场景）
```

### 4.3 升级失败回滚场景

**时间线**：
```
14:00:27 - 启动新版本：nohup java -jar agent.jar
14:00:37 - 验证启动：进程检查失败
14:00:38 - 报告回滚状态：ROLLBACK
14:00:39 - 开始回滚：从备份恢复原版本
14:00:40 - 启动原版本：nohup java -jar agent.jar
14:00:50 - 回滚完成，服务端Agent状态恢复为ONLINE
```

### 4.4 升级中的任务分发控制

**场景**：Agent正在升级时，服务端不会向其分发新任务
```
14:00:03 - Agent状态设为UPGRADING
14:00:05 - 其他Agent请求任务：正常分发
14:00:06 - 升级中Agent请求任务：返回空列表（status=UPGRADING）
14:00:39 - 升级完成，Agent状态恢复为ONLINE
14:00:40 - Agent再次请求任务：正常分发
```

## 5. 安全考虑

### 4.1 下载安全
- **HTTPS传输**: 所有文件下载使用HTTPS
- **数字签名**: 验证文件的数字签名
- **哈希校验**: 使用SHA256校验文件完整性

### 4.2 执行安全
- **权限检查**: 验证升级权限
- **路径验证**: 防止路径遍历攻击
- **进程隔离**: 升级过程中的进程隔离

### 4.3 回滚安全
- **完整备份**: 确保能够完全回滚
- **原子操作**: 升级操作的原子性
- **状态检查**: 升级后的状态验证

## 5. 风险评估

### 5.1 下载安全
- **HTTPS传输**: 所有文件下载使用HTTPS
- **哈希校验**: 使用SHA256校验文件完整性
- **文件大小验证**: 验证下载文件大小

### 5.2 执行安全
- **权限检查**: 验证升级权限
- **路径验证**: 防止路径遍历攻击
- **进程隔离**: 升级过程中的进程隔离

### 5.3 回滚安全
- **完整备份**: 确保能够完全回滚
- **原子操作**: 升级操作的原子性
- **状态检查**: 升级后的状态验证

## 6. 风险评估

### 6.1 高风险项
| 风险项 | 风险等级 | 影响 | 缓解措施 |
|--------|----------|------|----------|
| 升级过程中断 | 高 | 服务不可用 | 完整备份+自动回滚 |
| 新版本有缺陷 | 高 | 功能异常 | 启动验证+快速回滚 |
| 任务执行中强制升级 | 高 | 任务中断/数据丢失 | 任务状态检查+等待机制 |
| 升级器程序损坏 | 中 | 无法升级 | 升级器完整性检查 |

### 6.2 中风险项
| 风险项 | 风险等级 | 影响 | 缓解措施 |
|--------|----------|------|----------|
| 网络中断 | 中 | 下载失败 | 重试机制+错误处理 |
| 磁盘空间不足 | 中 | 升级失败 | 空间检查+清理 |
| 权限不足 | 中 | 无法升级 | 权限检查+提示 |
| 长时间任务阻塞升级 | 中 | 升级延迟 | 超时机制+强制升级选项 |

### 6.3 风险缓解策略
1. **完整备份机制**: 升级前完整备份当前版本
2. **独立升级器**: 使用独立程序执行升级，避免进程依赖
3. **自动回滚功能**: 升级失败时自动回滚
4. **任务状态监控**: 实时监控任务执行状态，禁止任务执行期间升级
5. **心跳集成检查**: 利用心跳机制实时检查版本更新
6. **跨平台兼容**: Java程序天然跨平台支持

## 7. 实现计划

### 7.1 第一阶段：基础框架 (1周)
- [ ] 修改心跳API，增加版本检查响应
- [ ] Agent端心跳版本检查器实现
- [ ] 任务状态监控器实现
- [ ] 升级状态管理服务和API
- [ ] Agent实体增加版本字段
- [ ] 单元测试

### 7.2 第二阶段：升级器开发 (2周)
- [ ] 独立升级器程序开发 (upgrader.jar)
- [ ] 主程序升级执行器实现
- [ ] 升级状态报告器实现
- [ ] 文件下载和校验功能
- [ ] 跨平台兼容性测试

### 7.3 第三阶段：任务分发控制 (1周)
- [ ] 修改任务拉取逻辑，升级中Agent不接收任务
- [ ] 升级状态在前端显示
- [ ] 升级历史记录功能
- [ ] 集成测试

### 7.4 第四阶段：用户界面和监控 (1周)
- [ ] 管理端升级监控界面
- [ ] Agent详情页面版本显示
- [ ] 升级历史查询API
- [ ] 监控告警机制
- [ ] 用户验收测试

## 8. 技术实现要点

### 8.1 关键技术选型
- **HTTP客户端**: 使用现有的HttpClient
- **文件操作**: Java NIO (高性能文件操作)
- **进程管理**: ProcessBuilder (跨平台进程管理)
- **任务调度**: ScheduledExecutorService (定时任务)

### 8.2 跨平台兼容性
- **Windows**: 使用cmd命令启动进程
- **Linux/macOS**: 使用nohup命令启动进程
- **进程检查**: 使用tasklist(Windows)和pgrep(Linux/macOS)

### 8.3 性能优化
- **心跳集成**: 复用心跳请求，减少网络开销
- **增量检查**: 只在版本变化时触发升级
- **异步处理**: 升级过程异步执行，不阻塞主流程

## 9. 监控和运维

### 9.1 监控指标
- 升级成功率
- 升级耗时统计
- 回滚次数统计
- 错误类型分析
- 升级中Agent数量
- 版本分布统计

### 9.2 日志记录
- 升级过程详细日志
- 错误信息和堆栈
- 性能指标记录
- 升级状态变更日志

### 9.3 数据库表设计

#### 9.3.1 Agent升级日志表
```sql
CREATE TABLE agent_upgrade_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    agent_id VARCHAR(100) NOT NULL,
    from_version VARCHAR(50),
    to_version VARCHAR(50),
    upgrade_status VARCHAR(20) NOT NULL, -- STARTED, DOWNLOADING, INSTALLING, SUCCESS, FAILED, ROLLBACK
    start_time DATETIME,
    end_time DATETIME,
    error_message TEXT,
    force_upgrade BOOLEAN DEFAULT FALSE,
    created_at DATETIME NOT NULL,
    INDEX idx_agent_id (agent_id),
    INDEX idx_status (upgrade_status),
    INDEX idx_created_at (created_at)
);
```

#### 9.3.2 Agent表增强
```sql
-- 只需要增加版本字段，复用现有的status字段
ALTER TABLE agents 
ADD COLUMN agent_version VARCHAR(50);

-- status字段的可能值：ONLINE, OFFLINE, UPGRADING
-- 不需要额外的upgrade_status字段
```

### 9.3 升级策略说明

#### 9.3.1 普通升级策略
- **触发条件**: 检测到新版本且非强制升级
- **执行时机**: 立即进入升级状态，等待所有任务完成后升级
- **状态变化**: 立即设为UPGRADING，停止接收新任务
- **等待策略**: 每10秒检查任务状态，等待任务自然完成
- **失败处理**: 持续等待直到所有任务完成

#### 9.3.2 强制升级策略  
- **触发条件**: 检测到强制升级版本
- **执行时机**: 立即停止所有正在执行的任务并开始升级
- **状态变化**: 立即设为UPGRADING，停止接收新任务
- **等待策略**: 无等待，立即中断所有任务
- **失败处理**: 优先保证安全更新的及时性，可能导致任务中断

## 10. 结论

### 10.1 可行性评估
- **技术可行性**: ✅ 高 - 基于Java技术栈，跨平台兼容
- **业务可行性**: ✅ 高 - 满足自动化运维需求
- **资源可行性**: ✅ 中 - 需要约5周开发时间

### 10.2 核心优势
1. **心跳集成**: 减少网络请求，提高实时性
2. **独立升级器**: 解决进程依赖问题，支持跨平台
3. **任务保护**: 严格保护正在执行的任务
4. **自动回滚**: 升级失败时自动恢复
5. **简化设计**: 升级器不做版本管理，降低复杂度
6. **状态追踪**: 完整的升级状态管理和日志记录
7. **任务分发控制**: 升级中的Agent不接收新任务
8. **版本可视化**: 前端显示Agent版本和升级历史

### 10.3 新增功能总结

#### 10.3.1 升级状态管理
- ✅ 升级前后向服务端报送状态
- ✅ 服务端保存升级日志和状态
- ✅ 升级过程的完整追踪

#### 10.3.2 版本显示
- ✅ Agent实体增加版本字段
- ✅ 心跳时更新版本信息
- ✅ 前端Agent详情页显示版本号

#### 10.3.3 任务分发控制
- ✅ 升级中的Agent状态设为UPGRADING（复用现有status字段）
- ✅ 任务拉取时检查Agent状态
- ✅ 升级中不分发新任务

### 10.3 推荐实施
建议采用**分阶段实施**的策略，先实现基础功能，再逐步完善安全和监控机制。

---

**注意**: 本设计方案已简化升级器版本管理，专注于核心升级功能的稳定性和可靠性。