# Agent日志重复问题修复报告

**日期**: 2026-03-15  
**版本**: v0.4.0  
**状态**: ✅ 已完成

## 问题描述

用户反馈Agent运行时日志重复打印，每条日志都出现两次：

```log
2026-03-15 22:29:42.988 [main] INFO  [AGENT] - Agent ID: d7c8a708-a5e5-4a42-b258-2050abe5bb4d
2026-03-15 22:29:42.988 [main] INFO  [AGENT] - Agent ID: d7c8a708-a5e5-4a42-b258-2050abe5bb4d
2026-03-15 22:29:42.988 [main] INFO  [AGENT] - Agent Token: 26cd6d0d-a491-47e4-8a86-c2a6db3c1997
2026-03-15 22:29:42.988 [main] INFO  [AGENT] - Agent Token: 26cd6d0d-a491-47e4-8a86-c2a6db3c1997
```

## 根本原因分析

日志重复的原因是**双重日志捕获**：

1. **Logback配置**：只输出到文件 (`UNIFIED_FILE` appender)
2. **启动脚本重定向**：使用 `>> "$LOG_FILE" 2>&1` 将所有输出重定向到同一个日志文件
3. **结果**：每条日志被写入两次 - 一次来自logback，一次来自shell重定向

## 修复方案

### 1. 修改Logback配置
**文件**: `agent/src/main/resources/logback.xml`

```xml
<!-- 修复前：只输出到文件 -->
<root level="INFO">
    <appender-ref ref="UNIFIED_FILE"/>
</root>

<!-- 修复后：同时输出到控制台和文件 -->
<root level="INFO">
    <appender-ref ref="CONSOLE"/>
    <appender-ref ref="UNIFIED_FILE"/>
</root>
```

### 2. 修改启动脚本
**文件**: `agent/build-release.sh`

```bash
# 修复前：重定向到日志文件（导致重复）
nohup "$JAVA_CMD" \
    -jar "$SCRIPT_DIR/agent.jar" \
    >> "$LOG_FILE" 2>&1 &

# 修复后：不重定向，让logback处理日志
nohup "$JAVA_CMD" \
    -jar "$SCRIPT_DIR/agent.jar" \
    > /dev/null 2>&1 &
```

## 修复效果验证

### 修复前（重复日志）
```log
2026-03-15 22:29:42.988 [main] INFO  [AGENT] - Starting LightScript Agent...
2026-03-15 22:29:42.988 [main] INFO  [AGENT] - Starting LightScript Agent...
2026-03-15 22:29:42.988 [main] INFO  [AGENT] - Agent ID: d7c8a708-a5e5-4a42-b258-2050abe5bb4d
2026-03-15 22:29:42.988 [main] INFO  [AGENT] - Agent ID: d7c8a708-a5e5-4a42-b258-2050abe5bb4d
```

### 修复后（正常日志）
```log
2026-03-15 22:37:10.554 [main] INFO  [AGENT] - Starting LightScript Agent...
2026-03-15 22:37:10.555 [main] INFO  [AGENT] - Server: http://8.138.114.34:8080
2026-03-15 22:37:10.555 [main] INFO  [AGENT] - Register Token: dev-regist...
2026-03-15 22:37:10.953 [main] INFO  [AGENT] - Registering agent...
```

## 附加优化

### 删除通用安装脚本占位符
根据用户反馈，删除了客户端安装页面中的通用安装包占位符功能，简化用户体验。

**修改文件**: `portal/client-install.html`
- 删除了 `universal` 安装包的特殊处理逻辑
- 简化了 `downloadAgent()` 函数

## 部署状态

✅ **代码修复**: 已完成  
✅ **重新构建**: Agent v0.4.0  
✅ **测试验证**: macOS ARM64平台测试通过  
✅ **服务器部署**: 已部署到生产环境  
✅ **门户更新**: 已同步更新

## 测试结果

- ✅ 日志不再重复
- ✅ Agent正常启动和注册
- ✅ 心跳和任务执行正常
- ✅ 日志文件格式正确
- ✅ 控制台输出清晰

## 影响范围

- **用户体验**: 大幅改善，日志清晰易读
- **调试效率**: 提高，不再被重复日志干扰
- **存储空间**: 减少50%的日志存储占用
- **兼容性**: 无影响，向后兼容

---

**修复完成**: 2026-03-15 22:37  
**验证通过**: macOS ARM64, 预期其他平台同样修复