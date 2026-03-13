# Agent主程序文件名沟通机制

## 文档信息
- **创建时间**: 2026-03-13
- **版本**: 1.0
- **状态**: 已实现
- **作者**: 系统架构师

## 1. 问题背景

在升级流程中，升级器需要知道当前主程序的文件名才能正确执行备份、替换和回滚操作。之前的实现硬编码了 `agent.jar` 作为主程序文件名，但这在实际部署中可能不准确。

## 2. 解决方案

### 2.1 参数传递方式
Agent在启动升级器时，将主程序文件名作为第3个参数传递：

```bash
java -jar upgrader.jar <new-version-path> <agent-home> <main-jar-name>
```

### 2.2 参数说明
- **new-version-path**: 新版本JAR文件的完整路径
- **agent-home**: Agent安装目录路径  
- **main-jar-name**: 当前主程序JAR文件名（如 `agent.jar`）

## 3. Agent端实现

### 3.1 主程序文件名检测逻辑

Agent使用多种方法来确定当前主程序文件名：

```java
private String getMainJarName() {
    // 方法1: 从系统属性获取（优先级最高）
    String jarName = System.getProperty("agent.jar.name");
    if (jarName != null && !jarName.isEmpty()) {
        return jarName;
    }
    
    // 方法2: 从当前运行的JAR路径推断
    try {
        String jarPath = AgentMain.class.getProtectionDomain()
            .getCodeSource().getLocation().getPath();
        if (jarPath.endsWith(".jar")) {
            return Paths.get(jarPath).getFileName().toString();
        }
    } catch (Exception e) {
        // 处理异常
    }
    
    // 方法3: 检查常见的JAR文件名
    String[] commonNames = {"agent.jar", "lightscript-agent.jar", "app.jar"};
    String currentDir = System.getProperty("user.dir");
    for (String name : commonNames) {
        if (Files.exists(Paths.get(currentDir, name))) {
            return name;
        }
    }
    
    // 默认值
    return "agent.jar";
}
```

### 3.2 检测方法优先级

1. **系统属性** (最高优先级)
   - 通过 `-Dagent.jar.name=myagent.jar` 启动参数设置
   - 适用于自定义部署场景

2. **代码源路径推断** (中等优先级)
   - 从当前运行的JAR文件路径获取文件名
   - 适用于标准JAR部署

3. **常见文件名检查** (较低优先级)
   - 检查工作目录中是否存在常见的JAR文件名
   - 提供兼容性支持

4. **默认值** (最低优先级)
   - 使用 `agent.jar` 作为默认值
   - 确保向后兼容

## 4. 升级器端实现

### 4.1 参数接收
```java
public static void main(String[] args) {
    if (args.length < 3) {
        System.err.println("Usage: java -jar upgrader.jar <new-version-path> <agent-home> <main-jar-name>");
        System.exit(1);
    }
    
    String newVersionPath = args[0];
    String agentHome = args[1];
    String mainJarName = args[2];  // 接收主程序文件名
    
    // ...
}
```

### 4.2 文件操作使用
升级器在所有文件操作中使用传递的主程序文件名：

```java
// 备份操作
Path currentJar = Paths.get(agentHome, mainJarName);
Files.copy(currentJar, backupPath.resolve(mainJarName), StandardCopyOption.REPLACE_EXISTING);

// 替换操作  
Files.copy(newJar, currentJar, StandardCopyOption.REPLACE_EXISTING);

// 回滚操作
Path backupJar = backupPath.resolve(mainJarName);
Files.copy(backupJar, currentJar, StandardCopyOption.REPLACE_EXISTING);
```

### 4.3 启动脚本生成
升级器生成的启动脚本也使用正确的JAR文件名：

```bash
# Unix/Linux 脚本
AGENT_JAR="$mainJarName"

# Windows 脚本  
set "AGENT_JAR=$mainJarName"
```

## 5. 使用场景

### 5.1 标准部署
```bash
# 标准部署，主程序名为 agent.jar
java -jar agent.jar
# 升级器调用: java -jar upgrader.jar /tmp/agent-2.1.0.jar /opt/agent agent.jar
```

### 5.2 自定义部署
```bash
# 自定义部署，主程序名为 lightscript-agent-v2.jar
java -Dagent.jar.name=lightscript-agent-v2.jar -jar lightscript-agent-v2.jar
# 升级器调用: java -jar upgrader.jar /tmp/new-version.jar /opt/agent lightscript-agent-v2.jar
```

### 5.3 多实例部署
```bash
# 实例1
java -Dagent.jar.name=agent-instance1.jar -jar agent-instance1.jar

# 实例2  
java -Dagent.jar.name=agent-instance2.jar -jar agent-instance2.jar
```

## 6. 优势

### 6.1 灵活性
- 支持任意主程序文件名
- 适应不同的部署场景
- 支持多实例部署

### 6.2 可靠性
- 多种检测方法确保准确性
- 有默认值保证向后兼容
- 详细的日志记录便于调试

### 6.3 简洁性
- 只增加1个参数
- 不需要配置文件
- 不依赖外部环境

## 7. 测试验证

### 7.1 测试场景
```bash
# 测试1: 标准文件名
cp agent.jar test-agent.jar
java -jar upgrader.jar /tmp/new-version.jar /opt/agent agent.jar

# 测试2: 自定义文件名
cp agent.jar my-custom-agent.jar  
java -jar upgrader.jar /tmp/new-version.jar /opt/agent my-custom-agent.jar

# 测试3: 带版本号的文件名
cp agent.jar lightscript-agent-2.0.0.jar
java -jar upgrader.jar /tmp/new-version.jar /opt/agent lightscript-agent-2.0.0.jar
```

### 7.2 验证要点
- ✅ 备份文件名正确
- ✅ 替换操作成功
- ✅ 启动脚本文件名正确
- ✅ 回滚操作正确

## 8. 最佳实践

### 8.1 部署建议
1. **标准部署**: 使用 `agent.jar` 作为文件名
2. **自定义部署**: 通过系统属性明确指定文件名
3. **多实例部署**: 为每个实例使用不同的文件名

### 8.2 启动脚本建议
```bash
#!/bin/bash
# 设置主程序文件名
AGENT_JAR_NAME="agent.jar"

# 启动Agent并传递文件名信息
java -Dagent.jar.name="$AGENT_JAR_NAME" -jar "$AGENT_JAR_NAME"
```

### 8.3 监控建议
- 在日志中记录检测到的主程序文件名
- 监控升级器的参数传递是否正确
- 验证备份和恢复操作的文件名一致性

## 9. 总结

通过在升级器参数中传递主程序文件名，我们解决了硬编码文件名的问题，使升级系统能够适应各种部署场景。这个方案：

- **简单**: 只增加1个参数
- **可靠**: 多种检测方法保证准确性  
- **灵活**: 支持任意文件名和部署方式
- **兼容**: 保持向后兼容性

这样，Agent和升级器之间就有了清晰的沟通机制，确保升级过程中文件操作的准确性。