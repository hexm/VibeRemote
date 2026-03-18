# 需求文档：Agent-服务器通信优化与加密

## 简介

本文档定义 LightScript Agent 与服务器之间通信优化与加密功能的需求。该功能分两个阶段：

- **第一阶段（已完成）**：批量日志传输、GZIP 压缩、异步处理、重试机制，将 100MB+ 日志传输时间从数小时缩短至分钟级别。
- **第二阶段（进行中）**：AES-256-GCM + RSA-2048 端到端加密，防止安全监控软件将命令/脚本传输误报为注入攻击。

## 术语表

- **Agent**：运行在客户端机器上的 LightScript 代理程序
- **Server**：LightScript 服务器，接收并处理 Agent 上报的数据
- **LogBuffer**：Agent 端日志缓冲区，积累日志条目直到触发批量发送
- **BatchLogRequest**：包含批量日志条目的 HTTP 请求体
- **EncryptedBatchLogRequest**：包含加密批量日志的 HTTP 请求体，继承自 BatchLogRequest
- **EncryptionService**：提供 AES-256-GCM 加密和 RSA 密钥操作的服务
- **AgentEncryptionContext**：Agent 端密钥管理上下文，存储密钥对和服务器公钥
- **ServerEncryptionContext**：服务器端密钥管理上下文，存储私钥和已注册 Agent 的公钥
- **SessionKey**：每批次随机生成的 AES-256 对称密钥
- **EncryptedPayload**：加密载荷，包含加密数据、加密会话密钥、IV、认证标签和签名
- **RetryQueue**：存储发送失败批次的队列，用于指数退避重试
- **ExecutionId**：任务执行的唯一标识符

---

## 需求

### 需求 1：批量日志传输

**用户故事：** 作为系统运维人员，我希望 Agent 以批量方式上传日志，以便大幅减少 HTTP 请求数量并提升传输效率。

#### 验收标准

1. THE LogBuffer SHALL 积累日志条目，直到批次大小达到 1000 条或等待时间超过 5000 毫秒。
2. WHEN LogBuffer 触发刷新时，THE Agent SHALL 将缓冲区中的所有日志条目作为一个批次发送到服务器。
3. WHEN 任务执行结束时，THE Agent SHALL 将缓冲区中剩余的日志条目作为最终批次发送，并等待发送完成。
4. WHEN Server 收到批量日志请求时，THE Server SHALL 将批次中的所有日志条目写入对应任务的日志文件。
5. THE Server SHALL 在单次批量请求中支持最多 1000 条日志条目。

### 需求 2：GZIP 压缩传输

**用户故事：** 作为系统运维人员，我希望日志数据在传输前被压缩，以便减少网络带宽消耗。

#### 验收标准

1. WHEN Agent 发送批量日志时，THE Agent SHALL 使用 GZIP 算法压缩序列化后的日志数据。
2. THE Agent SHALL 在 HTTP 请求头中设置 `Content-Encoding: gzip` 以标识压缩格式。
3. WHEN Server 收到带有 `Content-Encoding: gzip` 的请求时，THE Server SHALL 对请求体进行 GZIP 解压缩后再处理。
4. FOR ALL 有效的日志批次数据，压缩后再解压缩 SHALL 产生与原始数据等价的内容（往返属性）。
5. IF GZIP 压缩失败，THEN THE Agent SHALL 降级为不压缩方式发送批量日志，并记录警告日志。

### 需求 3：异步日志发送

**用户故事：** 作为开发者，我希望日志上传在后台异步执行，以便任务执行不被日志传输阻塞。

#### 验收标准

1. THE Agent SHALL 使用独立的异步线程池执行批量日志发送操作。
2. WHEN 批量日志发送失败时，THE Agent SHALL 将失败批次加入 RetryQueue，而不影响任务执行的继续进行。
3. THE Agent SHALL 在任务执行完成后，等待所有待发送批次完成传输再退出。

### 需求 4：失败重试机制

**用户故事：** 作为系统运维人员，我希望日志发送失败时自动重试，以便在网络不稳定时保证日志不丢失。

#### 验收标准

1. WHEN 批量日志发送失败时，THE Agent SHALL 将失败批次加入 RetryQueue 并按指数退避策略重试。
2. THE Agent SHALL 最多重试 3 次，重试间隔依次为 1 秒、2 秒、4 秒。
3. IF 批次在达到最大重试次数后仍发送失败，THEN THE Agent SHALL 将该批次记录到本地文件并停止重试。
4. THE RetryQueue SHALL 按批次失败时间排序，保证先失败的批次先重试。

### 需求 5：AES-256-GCM 数据加密

**用户故事：** 作为安全管理员，我希望传输的日志数据使用强加密算法保护，以便防止安全监控软件误报注入攻击。

#### 验收标准

1. WHEN Agent 发送加密批量日志时，THE EncryptionService SHALL 为每个批次生成一个随机的 256 位 AES SessionKey。
2. THE EncryptionService SHALL 使用 AES-256-GCM 算法和随机生成的 12 字节 IV 加密压缩后的日志数据。
3. THE EncryptedPayload SHALL 包含加密数据、加密会话密钥、IV、GCM 认证标签和数字签名。
4. FOR ALL 有效的压缩日志数据和匹配的密钥对，加密后再解密 SHALL 产生与原始压缩数据完全相同的内容（往返属性）。
5. WHEN Server 收到加密批量日志请求时，THE Server SHALL 使用 AES-256-GCM 解密数据，并在认证标签验证失败时拒绝请求。

### 需求 6：RSA-2048 密钥交换

**用户故事：** 作为安全管理员，我希望使用非对称加密保护会话密钥，以便确保只有目标服务器能解密日志数据。

#### 验收标准

1. THE EncryptionService SHALL 使用 RSA-2048 算法加密每批次的 AES SessionKey。
2. THE Agent SHALL 使用服务器公钥加密 SessionKey，确保只有持有对应私钥的服务器能解密。
3. FOR ALL 有效的 RSA-2048 密钥对，使用公钥加密后再用私钥解密 SHALL 还原原始 SessionKey（往返属性）。
4. THE Server SHALL 使用自身私钥解密 EncryptedPayload 中的加密 SessionKey，再用 SessionKey 解密日志数据。
5. THE EncryptionService SHALL 生成符合 RSA-2048 标准的密钥对，并以 PEM 格式存储。

### 需求 7：数字签名与完整性验证

**用户故事：** 作为安全管理员，我希望每个加密批次都附带数字签名，以便服务器验证数据来源和完整性。

#### 验收标准

1. WHEN Agent 发送加密批量日志时，THE Agent SHALL 使用自身私钥对加密数据、加密密钥、IV 和时间戳的组合进行 RSA 签名。
2. WHEN Server 收到加密批量日志时，THE Server SHALL 使用已注册的 Agent 公钥验证数字签名。
3. IF 签名验证失败，THEN THE Server SHALL 返回 HTTP 403 状态码并记录安全事件日志。
4. FOR ALL 有效的 RSA 密钥对，对任意数据签名后验证 SHALL 返回 true；对修改后的数据验证 SHALL 返回 false。

### 需求 8：重放攻击防护

**用户故事：** 作为安全管理员，我希望系统能检测并拒绝重放的加密请求，以便防止攻击者重复提交已捕获的合法请求。

#### 验收标准

1. THE Agent SHALL 在每个 EncryptedPayload 中包含当前系统时间戳（毫秒级 Unix 时间戳）。
2. WHEN Server 收到加密批量日志时，THE Server SHALL 验证 payload 中的时间戳与服务器当前时间的差值不超过 300000 毫秒（5 分钟）。
3. IF payload 时间戳超出 5 分钟有效窗口，THEN THE Server SHALL 拒绝请求并抛出 SecurityException。
4. IF payload 时间戳超出有效窗口，THEN THE Server SHALL 返回 HTTP 403 状态码。

### 需求 9：Agent 注册与公钥交换

**用户故事：** 作为系统管理员，我希望 Agent 在注册时与服务器交换公钥，以便建立安全的加密通信信道。

#### 验收标准

1. WHEN Agent 首次注册或密钥轮换时，THE Agent SHALL 将自身公钥发送给服务器进行注册。
2. WHEN Server 收到 Agent 公钥注册请求时，THE Server SHALL 将 Agent 公钥与对应的 AgentId 关联存储在 ServerEncryptionContext 中。
3. WHEN Agent 完成注册时，THE Server SHALL 将服务器公钥返回给 Agent。
4. THE AgentEncryptionContext SHALL 持久化存储服务器公钥和 Agent 自身密钥对，以便 Agent 重启后无需重新注册。

### 需求 10：密钥轮换

**用户故事：** 作为安全管理员，我希望密钥定期自动轮换，以便降低长期密钥泄露的安全风险。

#### 验收标准

1. THE AgentEncryptionContext SHALL 记录密钥生成时间，并在密钥使用超过 30 天时触发轮换流程。
2. WHEN 密钥轮换触发时，THE Agent SHALL 生成新的 RSA-2048 密钥对并重新向服务器注册公钥。
3. WHEN 密钥轮换完成时，THE Agent SHALL 使用新密钥对后续的加密批量日志进行签名和加密。

### 需求 11：加密功能开关与向后兼容

**用户故事：** 作为系统管理员，我希望加密功能可以通过配置开关控制，以便在不同环境中灵活启用或禁用加密。

#### 验收标准

1. THE Agent SHALL 读取 `encryption.enabled` 配置项，当值为 `false` 时使用非加密的批量日志传输路径。
2. WHEN `encryption.enabled` 为 `true` 时，THE Agent SHALL 使用加密批量日志传输路径发送所有日志数据。
3. THE Server SHALL 同时支持加密端点（`/encrypted-batch-log`）和非加密端点（`/batch-log`），以保证向后兼容性。
4. WHEN Agent 使用非加密模式时，THE Server SHALL 通过非加密端点正常接收和处理批量日志。

### 需求 12：加密错误处理与降级

**用户故事：** 作为系统运维人员，我希望加密过程中的错误能被妥善处理，以便系统在加密失败时仍能保持可用性。

#### 验收标准

1. IF Agent 使用的服务器公钥失效或损坏，THEN THE Agent SHALL 触发密钥更新流程，重新获取服务器公钥后重试发送。
2. IF Server 无法解密收到的批量日志数据，THEN THE Server SHALL 返回 HTTP 400 状态码并保留原始加密数据用于调试。
3. IF Agent 收到 HTTP 403 响应，THEN THE Agent SHALL 重新生成密钥对并重新注册后重试发送。
4. WHEN 加密批量日志发送失败时，THE Agent SHALL 将失败批次加入 RetryQueue，遵循与非加密模式相同的重试策略。
