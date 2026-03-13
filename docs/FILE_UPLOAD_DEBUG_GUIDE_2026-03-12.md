# 文件上传错误调试指南

## 问题描述
用户上传文件时遇到错误：`{error: "INTERNAL_SERVER_ERROR", message: "服务器内部错误", status: 500}`

## 已实施的改进

### 1. 增强错误处理
- **后端**：添加了详细的日志记录和错误分类
- **前端**：显示具体的错误信息
- **分层处理**：区分业务异常和系统异常

### 2. 详细日志记录
```java
log.info("Starting file upload: name={}, originalName={}, size={}, category={}, uploadBy={}", ...);
log.debug("File validation passed, calculating checksum...");
log.debug("Checksum calculated: MD5={}, SHA256={}", ...);
log.debug("Saving file to storage...");
log.debug("File saved to: {}", filePath);
log.debug("Saving file record to database...");
```

### 3. 文件存储改进
```java
// 检查目录权限
if (!Files.isWritable(storageDir)) {
    throw new IOException("Storage directory is not writable: " + storageDir.toAbsolutePath());
}
```

## 调试步骤

### 1. 检查服务器日志
启动服务器后，查看控制台日志中的详细错误信息：
```bash
cd server
mvn spring-boot:run
```

### 2. 检查文件权限
```bash
# 检查files目录权限
ls -ld files/
# 应该显示类似：drwxr-xr-x@ 10 user staff 320 Mar 12 10:45 files

# 测试文件创建
touch files/test.txt && rm files/test.txt
```

### 3. 检查磁盘空间
```bash
df -h .
```

### 4. 检查数据库连接
确保MySQL数据库正常运行并且连接配置正确。

### 5. 测试文件上传API
```bash
# 创建测试文件
echo "test content" > test.txt

# 测试上传（需要认证）
curl -X POST \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -F "file=@test.txt" \
  -F "name=test.txt" \
  -F "category=test" \
  http://localhost:8080/api/web/files/upload
```

## 常见问题和解决方案

### 1. 权限问题
**症状**：`Storage directory is not writable`
**解决**：
```bash
chmod 755 files/
```

### 2. 磁盘空间不足
**症状**：`No space left on device`
**解决**：清理磁盘空间或更改存储目录

### 3. 文件大小超限
**症状**：`文件大小超过限制`
**解决**：检查Spring Boot配置
```yaml
spring:
  servlet:
    multipart:
      max-file-size: 100MB
      max-request-size: 100MB
```

### 4. 数据库连接问题
**症状**：数据库相关异常
**解决**：
- 检查MySQL服务是否运行
- 验证数据库连接配置
- 确认数据库表是否存在

### 5. 文件名重复
**症状**：`文件名已存在`
**解决**：使用不同的文件名或删除现有文件

## 前端调试

### 1. 浏览器开发者工具
- 打开Network标签
- 查看上传请求的详细信息
- 检查响应状态和错误信息

### 2. 控制台日志
前端已添加详细的调试日志：
```javascript
console.log('Uploading file:', uploadFile.name)
console.log('File uploaded, response:', fileResponse)
console.log('Creating version from file ID:', fileResponse.fileId)
```

## 测试建议

### 1. 使用小文件测试
先用小文件（几KB）测试基本功能

### 2. 逐步增加文件大小
确认大文件上传的稳定性

### 3. 测试不同文件类型
验证JAR文件和其他文件类型的上传

### 4. 并发上传测试
测试多个文件同时上传的情况

## 监控和维护

### 1. 定期检查日志
监控文件上传的成功率和错误模式

### 2. 磁盘空间监控
设置磁盘空间告警

### 3. 数据库性能监控
监控文件记录的插入性能

## 联系支持

如果问题仍然存在，请提供：
1. 完整的服务器日志
2. 浏览器开发者工具的Network信息
3. 上传文件的详细信息（大小、类型、名称）
4. 系统环境信息（操作系统、Java版本、数据库版本）