# Agent升级流程终极简化方案

## 文档信息
- **创建时间**: 2026-03-13
- **版本**: 3.0 (终极版)
- **状态**: 已实现
- **作者**: 系统架构师

## 1. 终极简化结果

### 1.1 升级器参数
**最终参数**: 只需要2个
```bash
java -jar upgrader.jar <new-version-filename> <main-jar-name>
```

**参数说明**:
- `new-version-filename`: 新版本JAR文件名（如 `agent-2.1.0.jar`）
- `main-jar-name`: 当前主程序JAR文件名（如 `agent.jar`）

### 1.2 关键洞察
> **Agent目录就是工作目录**
> 
> 升级器运行在Agent目录下，所以不需要传递目录路径，只需要文件名即可。

## 2. 设计原理

### 2.1 目录关系澄清
```
/opt/lightscript/agent/          # Agent安装目录
├── agent.jar                    # 主程序 (main-jar-name)
├── agent-2.1.0.jar            # 新版本 (new-version-filename)
├── upgrader.jar                # 升级器
├── start-agent.sh              # 启动脚本
└── backup/                     # 备份目录
    └── current/
        └── agent.jar           # 备份的主程序
```

### 2.2 工作原理
1. **Agent下载新版本** → 保存到当前目录 (`agent-2.1.0.jar`)
2. **Agent启动升级器** → 传递文件名 (`agent-2.1.0.jar`, `agent.jar`)
3. **升级器获取工作目录** → `System.getProperty("user.dir")`
4. **升级器执行操作** → 在当前目录下操作文件

## 3. 实现细节

### 3.1 Agent端实现
```java
private void startUpgrader(String newVersionPath) throws IOException {
    // 从完整路径中提取文件名
    String newVersionFilename = Paths.get(newVersionPath).getFileName().toString();
    String mainJarName = getMainJarName();
    
    ProcessBuilder pb = new ProcessBuilder();
    // 只传递2个参数：新版本文件名、主程序文件名
    pb.command("java", "-jar", UPGRADER_JAR, newVersionFilename, mainJarName);
    pb.directory(new File(System.getProperty("user.dir")));
    
    // ...
}
```

### 3.2 升级器端实现
```java
public static void main(String[] args) {
    String newVersionFilename = args[0];  // agent-2.1.0.jar
    String mainJarName = args[1];         // agent.jar
    String agentHome = System.getProperty("user.dir"); // /opt/lightscript/agent
    
    // 所有文件操作都在agentHome目录下进行
    Path newJar = Paths.get(agentHome, newVersionFilename);
    Path currentJar = Paths.get(agentHome, mainJarName);
    
    // ...
}
```

## 4. 升级流程

### 4.1 完整流程
```
1. Agent检测到新版本
2. Agent下载: /opt/agent/agent-2.1.0.jar
3. Agent启动升级器: java -jar upgrader.jar agent-2.1.0.jar agent.jar
4. 升级器工作目录: /opt/agent/ (自动获取)
5. 升级器备份: agent.jar → backup/current/agent.jar
6. 升级器替换: agent-2.1.0.jar → agent.jar
7. 升级器启动: ./start-agent.sh
8. 升级器验证: 检查启动日志
9. 升级器清理: 删除 agent-2.1.0.jar
```

### 4.2 文件操作示例
```bash
# 升级前
/opt/agent/
├── agent.jar           # 当前版本 2.0.0
├── agent-2.1.0.jar    # 新下载的版本
└── upgrader.jar

# 备份阶段
/opt/agent/backup/current/
└── agent.jar           # 备份的 2.0.0 版本

# 替换阶段
cp agent-2.1.0.jar agent.jar  # 替换主程序

# 清理阶段
rm agent-2.1.0.jar            # 删除临时文件
```

## 5. 优势分析

### 5.1 参数简化对比
| 版本 | 参数个数 | 参数内容 | 复杂度 |
|------|----------|----------|--------|
| 原始版本 | 4个 | 路径+目录+ID+URL | 很高 |
| 优化版本 | 3个 | 路径+目录+文件名 | 高 |
| **终极版本** | **2个** | **文件名+文件名** | **很低** |

### 5.2 核心优势
1. **极简参数**: 只需要2个文件名
2. **自动定位**: 升级器自动获取工作目录
3. **逻辑清晰**: 所有操作都在同一目录下
4. **易于理解**: 参数含义直观明确
5. **减少错误**: 参数越少，出错概率越低

## 6. 使用场景

### 6.1 标准场景
```bash
# Agent下载新版本到当前目录
wget https://server.com/agent-2.1.0.jar

# Agent启动升级器
java -jar upgrader.jar agent-2.1.0.jar agent.jar
```

### 6.2 自定义文件名场景
```bash
# 自定义主程序文件名
java -jar upgrader.jar lightscript-agent-2.1.0.jar lightscript-agent.jar
```

### 6.3 版本化部署场景
```bash
# 带版本号的文件名
java -jar upgrader.jar myapp-v2.1.0.jar myapp-v2.0.0.jar
```

## 7. 错误处理

### 7.1 文件不存在
```java
if (!Files.exists(newJar)) {
    throw new IOException("New version file not found: " + newJar);
}
```

### 7.2 备份失败
```java
if (!Files.exists(currentJar)) {
    throw new IOException("Current main JAR not found for backup: " + currentJar);
}
```

### 7.3 启动失败
```java
if (!verifyStartup(agentHome)) {
    // 自动回滚到备份版本
    rollback(backupDir, agentHome, mainJarName);
}
```

## 8. 测试验证

### 8.1 测试命令
```bash
# 创建测试环境
cd /tmp/test-agent
cp /opt/agent/agent.jar .
cp /opt/agent/upgrader.jar .

# 创建新版本文件
cp agent.jar agent-2.1.0.jar

# 测试升级器
java -jar upgrader.jar agent-2.1.0.jar agent.jar
```

### 8.2 验证要点
- ✅ 参数传递正确
- ✅ 工作目录自动获取
- ✅ 文件操作成功
- ✅ 备份和回滚正常
- ✅ 启动脚本生成正确

## 9. 最佳实践

### 9.1 文件命名建议
- **主程序**: 使用简单名称如 `agent.jar`
- **新版本**: 包含版本号如 `agent-2.1.0.jar`
- **保持一致**: 同一部署环境使用统一命名规范

### 9.2 部署建议
- **目录结构**: 保持Agent目录结构简洁
- **权限设置**: 确保升级器有文件操作权限
- **日志监控**: 监控升级日志确保成功

### 9.3 故障排除
- **检查文件**: 确认新版本文件已下载
- **检查权限**: 确认有文件替换权限
- **检查日志**: 查看升级器输出日志

## 10. 总结

### 10.1 终极简化成果
通过深入理解升级场景，我们实现了真正的极简设计：

- **参数数量**: 从4个减少到2个 (50%减少)
- **参数复杂度**: 从路径+ID+URL简化为文件名+文件名
- **理解难度**: 从需要理解多个概念到只需要知道文件名
- **出错概率**: 大幅降低参数传递错误的可能性

### 10.2 设计哲学
> **"升级器应该像重命名文件一样简单：告诉它旧文件名和新文件名就够了。"**

### 10.3 核心价值
1. **简单**: 参数含义直观，易于理解
2. **可靠**: 减少参数传递错误
3. **灵活**: 支持任意文件名
4. **高效**: 减少开发和维护成本

这个终极简化方案成功地将复杂的升级系统变成了一个真正简单、直观、可靠的解决方案。升级器现在只需要知道"把哪个文件替换成哪个文件"，这是最自然、最容易理解的设计。