# Agent版本上传错误修复

## 问题描述

用户上传 `agent-0.1.0-SNAPSHOT-jar-with-dependencies.jar` 文件时报错："服务器内部错误"

## 问题分析

### 1. 版本号解析问题
原始的正则表达式过于复杂，无法正确解析包含多个连字符的文件名：
```java
// 原始正则表达式（有问题）
Pattern.compile("(\\d+\\.\\d+\\.\\d+(?:\\.\\d+)?)(?:-[A-Za-z0-9]+)*")
```

对于文件名 `agent-0.1.0-SNAPSHOT-jar-with-dependencies.jar`：
- 移除扩展名后：`agent-0.1.0-SNAPSHOT-jar-with-dependencies`
- 复杂的正则表达式可能无法正确匹配版本号部分

### 2. 错误处理不完善
- 后端没有提供详细的错误信息
- 前端没有显示具体的错误原因

## 解决方案

### 1. 简化版本号解析
```java
// 修复后的正则表达式（简化）
Pattern.compile("(\\d+\\.\\d+\\.\\d+(?:\\.\\d+)?)")
```

**优势**：
- 只匹配版本号部分，不关心前缀和后缀
- 对于 `agent-0.1.0-SNAPSHOT-jar-with-dependencies` 能正确提取 `0.1.0`
- 更加健壮和可靠

### 2. 增强错误处理

**后端改进**：
```java
@PostMapping("/web/agent-versions/from-file")
public ResponseEntity<?> createVersionFromFile(...) {
    try {
        // 创建版本逻辑
        return ResponseEntity.ok(created);
    } catch (IllegalArgumentException e) {
        // 返回详细的验证错误
        Map<String, String> error = new HashMap<>();
        error.put("error", "VALIDATION_ERROR");
        error.put("message", e.getMessage());
        return ResponseEntity.badRequest().body(error);
    } catch (Exception e) {
        // 返回通用错误
        return ResponseEntity.status(500).body(error);
    }
}
```

**前端改进**：
```javascript
// 显示详细错误信息
let errorMessage = '创建版本失败'
if (error.response?.data?.message) {
    errorMessage += ': ' + error.response.data.message
}
message.error(errorMessage)
```

### 3. 增加调试日志
```java
log.debug("Parsing version from filename: {}", filename);
log.debug("Filename without extension: {}", nameWithoutExt);
log.debug("Found version: {}", version);
```

## 测试验证

### 支持的文件名格式
- ✅ `agent-1.2.3.jar` → `1.2.3`
- ✅ `lightscript-agent-2.0.0.jar` → `2.0.0`
- ✅ `agent-0.1.0-SNAPSHOT.jar` → `0.1.0`
- ✅ `agent-0.1.0-SNAPSHOT-jar-with-dependencies.jar` → `0.1.0`
- ✅ `my-agent-1.2.3.4.jar` → `1.2.3.4`

### 测试结果
```bash
mvn test -Dtest=VersionParsingTest
# [INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
```

## 用户指导

### 推荐的文件命名格式
1. **标准格式**：`agent-{version}.jar`
   - 例如：`agent-1.2.3.jar`

2. **带前缀格式**：`{prefix}-agent-{version}.jar`
   - 例如：`lightscript-agent-2.0.0.jar`

3. **SNAPSHOT版本**：`agent-{version}-SNAPSHOT.jar`
   - 例如：`agent-1.0.0-SNAPSHOT.jar`

4. **复杂构建格式**：`agent-{version}-{suffix}.jar`
   - 例如：`agent-0.1.0-SNAPSHOT-jar-with-dependencies.jar`

### 版本号要求
- 必须包含至少三位版本号：`x.y.z`
- 支持四位版本号：`x.y.z.w`
- 版本号必须是数字，用点分隔

## 总结

通过简化版本号解析逻辑和增强错误处理，现在系统可以：
1. 正确解析各种复杂的文件名格式
2. 提供清晰的错误信息帮助用户排查问题
3. 支持更多的文件命名约定

这个修复确保了版本管理系统的健壮性和用户友好性。