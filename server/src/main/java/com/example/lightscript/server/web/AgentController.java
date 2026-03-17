package com.example.lightscript.server.web;

import com.example.lightscript.server.exception.BusinessException;
import com.example.lightscript.server.exception.ErrorCode;
import com.example.lightscript.server.model.AgentModels.*;
import com.example.lightscript.server.model.EncryptedBatchLogRequest;
import com.example.lightscript.server.model.FileModels;
import com.example.lightscript.server.service.AgentService;
import com.example.lightscript.server.service.TaskService;
import com.example.lightscript.server.service.FileService;
import com.example.lightscript.server.service.AgentVersionService;
import com.example.lightscript.server.service.UpgradeStatusService;
import com.example.lightscript.server.service.EncryptionService;
import com.example.lightscript.server.service.ServerEncryptionContext;
import com.example.lightscript.server.entity.TaskLog;
import com.example.lightscript.server.entity.AgentVersion;
import com.example.lightscript.server.entity.AgentUpgradeLog;
import lombok.extern.slf4j.Slf4j;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import javax.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/agent")
@Slf4j
public class AgentController {
	private final AgentService agentService;
	private final TaskService taskService;
	private final FileService fileService;
	private final AgentVersionService agentVersionService;
	private final UpgradeStatusService upgradeStatusService;
	private final EncryptionService encryptionService;
	private final ServerEncryptionContext serverEncryptionContext;

	public AgentController(AgentService agentService, TaskService taskService, FileService fileService, 
						  AgentVersionService agentVersionService, UpgradeStatusService upgradeStatusService,
						  EncryptionService encryptionService, ServerEncryptionContext serverEncryptionContext) {
		this.agentService = agentService;
		this.taskService = taskService;
		this.fileService = fileService;
		this.agentVersionService = agentVersionService;
		this.upgradeStatusService = upgradeStatusService;
		this.encryptionService = encryptionService;
		this.serverEncryptionContext = serverEncryptionContext;
	}

	/**
	 * 获取客户端IP地址
	 */
	private String getClientIpAddress() {
		try {
			ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
			if (attributes != null) {
				HttpServletRequest request = attributes.getRequest();
				String xForwardedFor = request.getHeader("X-Forwarded-For");
				if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
					return xForwardedFor.split(",")[0].trim();
				}
				String xRealIp = request.getHeader("X-Real-IP");
				if (xRealIp != null && !xRealIp.isEmpty()) {
					return xRealIp;
				}
				return request.getRemoteAddr();
			}
		} catch (Exception e) {
			log.debug("Failed to get client IP: {}", e.getMessage());
		}
		return "unknown";
	}
	public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest req) {
		log.info("========================================");
		log.info("AGENT REGISTRATION REQUEST");
		log.info("========================================");
		log.info("Registration token: {}", req.getRegisterToken().substring(0, Math.min(10, req.getRegisterToken().length())) + "...");
		log.info("Hostname: {}", req.getHostname());
		log.info("OS Type: {}", req.getOsType());
		log.info("Client IP: {}", getClientIpAddress());
		
		try {
			RegisterResponse response = agentService.register(req);
			log.info("✓ Agent registration successful");
			log.info("Agent ID: {}", response.getAgentId());
			log.info("Agent Token: {}", response.getAgentToken().substring(0, Math.min(10, response.getAgentToken().length())) + "...");
			log.info("========================================");
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			log.error("✗ Agent registration failed: {}", e.getMessage());
			log.error("========================================");
			throw e;
		}
	}

	@PostMapping("/heartbeat")
	public ResponseEntity<HeartbeatResponse> heartbeat(@Valid @RequestBody HeartbeatRequest req) {
		log.debug("Heartbeat received from Agent: {}", req.getAgentId());
		log.debug("System info included: {}", req.getCpuLoad() != null || req.getFreeMemMb() != null);
		
		try {
			boolean success = agentService.updateHeartbeat(req.getAgentId(), req.getAgentToken(), req);
			if (!success) {
				log.warn("✗ Heartbeat failed - invalid agent token: {}", req.getAgentId());
				throw new BusinessException(ErrorCode.AGENT_TOKEN_INVALID);
			}
			
			// 构建响应，包含版本检查信息
			HeartbeatResponse response = new HeartbeatResponse();
			
			// 检查版本更新（如果Agent在心跳中报告了版本信息）
			if (req.getAgentVersion() != null) {
				log.debug("Checking version update for Agent: {} (current: {})", req.getAgentId(), req.getAgentVersion());
				AgentVersionService.VersionCheckResult versionCheck = agentVersionService.checkForUpdate(
					req.getAgentVersion(), 
					"ALL" // 暂时使用ALL平台，后续可以根据osType判断
				);
				
				if (versionCheck.isUpdateAvailable()) {
					AgentVersion latestVersion = versionCheck.getLatestVersion();
					log.info("Version update available for Agent: {} (current: {} -> latest: {})", 
						req.getAgentId(), req.getAgentVersion(), latestVersion.getVersion());
					
					VersionCheckResult result = new VersionCheckResult();
					result.setUpdateAvailable(true);
					result.setMessage(versionCheck.getMessage());
					
					VersionInfo versionInfo = new VersionInfo();
					versionInfo.setVersion(latestVersion.getVersion());
					versionInfo.setDownloadUrl(latestVersion.getDownloadUrl());
					versionInfo.setFileSize(latestVersion.getFileSize());
					versionInfo.setFileHash(latestVersion.getFileHash());
					versionInfo.setForceUpgrade(Boolean.TRUE.equals(latestVersion.getForceUpgrade()));
					versionInfo.setReleaseNotes(latestVersion.getReleaseNotes());
					
					result.setLatestVersion(versionInfo);
					response.setVersionCheck(result);
					
					if (versionInfo.isForceUpgrade()) {
						log.warn("Force upgrade required for Agent: {} (current: {} -> required: {})", 
							req.getAgentId(), req.getAgentVersion(), latestVersion.getVersion());
					}
				} else {
					log.debug("Agent version is up to date: {} ({})", req.getAgentId(), req.getAgentVersion());
				}
			}
			
			log.debug("✓ Heartbeat processed successfully for Agent: {}", req.getAgentId());
			return ResponseEntity.ok(response);
			
		} catch (BusinessException e) {
			throw e;
		} catch (Exception e) {
			log.error("✗ Heartbeat processing failed for Agent: {}: {}", req.getAgentId(), e.getMessage(), e);
			throw new BusinessException(ErrorCode.SYSTEM_ERROR);
		}
	}

	@PostMapping("/offline")
	public ResponseEntity<Void> offline(@RequestParam String agentId, @RequestParam String agentToken) {
		log.info("Agent offline request: {}", agentId);
		try {
			boolean success = agentService.markOffline(agentId, agentToken);
			if (!success) {
				log.warn("✗ Agent offline failed - invalid token: {}", agentId);
				throw new BusinessException(ErrorCode.AGENT_TOKEN_INVALID);
			}
			log.info("✓ Agent marked offline successfully: {}", agentId);
			return ResponseEntity.ok().build();
		} catch (BusinessException e) {
			throw e;
		} catch (Exception e) {
			log.error("✗ Agent offline processing failed: {}: {}", agentId, e.getMessage(), e);
			throw new BusinessException(ErrorCode.SYSTEM_ERROR);
		}
	}

	@GetMapping("/tasks/pull")
	public ResponseEntity<PullTasksResponse> pull(@RequestParam String agentId, @RequestParam String agentToken, @RequestParam(defaultValue = "10") int max) {
		log.debug("Task pull request from Agent: {} (max: {})", agentId, max);
		
		try {
			if (!agentService.validateAgent(agentId, agentToken)) {
				log.warn("✗ Task pull failed - invalid agent token: {}", agentId);
				throw new BusinessException(ErrorCode.AGENT_TOKEN_INVALID);
			}
			
			int normalizedMax = Math.min(Math.max(max, 1), 50);
			PullTasksResponse rsp = new PullTasksResponse();
			rsp.setTasks(taskService.pullTasks(agentId, normalizedMax));
			
			if (rsp.getTasks().isEmpty()) {
				log.debug("No tasks available for Agent: {}", agentId);
			} else {
				log.info("✓ Pulled {} tasks for Agent: {}", rsp.getTasks().size(), agentId);
				// 记录任务ID列表用于调试
				if (log.isDebugEnabled()) {
					String taskIds = rsp.getTasks().stream()
						.map(task -> task.getTaskId())
						.collect(java.util.stream.Collectors.joining(", "));
					log.debug("Task IDs: [{}]", taskIds);
				}
			}
			
			return ResponseEntity.ok(rsp);
			
		} catch (BusinessException e) {
			throw e;
		} catch (Exception e) {
			log.error("✗ Task pull processing failed for Agent: {}: {}", agentId, e.getMessage(), e);
			throw new BusinessException(ErrorCode.SYSTEM_ERROR);
		}
	}

	@GetMapping("/tasks/encrypted-pull")
	public ResponseEntity<String> encryptedPull(@RequestParam String agentId, 
											   @RequestParam String agentToken, 
											   @RequestParam(defaultValue = "10") int max) {
		
		// 验证加密是否启用
		if (!serverEncryptionContext.isEncryptionEnabled()) {
			log.warn("收到加密任务拉取请求但服务器加密未启用: agentId={}", agentId);
			return ResponseEntity.status(400).build();
		}
		
		// 验证Agent凭证
		if (!agentService.validateAgent(agentId, agentToken)) {
			throw new BusinessException(ErrorCode.AGENT_TOKEN_INVALID);
		}
		
		// 获取Agent公钥
		String agentPublicKey = serverEncryptionContext.getAgentPublicKey(agentId);
		if (agentPublicKey == null) {
			log.warn("Agent公钥未注册，无法加密任务: agentId={}", agentId);
			return ResponseEntity.status(403).build();
		}
		
		try {
			// 获取任务列表
			PullTasksResponse response = new PullTasksResponse();
			response.setTasks(taskService.pullTasks(agentId, Math.min(Math.max(max, 1), 50)));
			
			// 序列化任务数据
			com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
			String jsonData = objectMapper.writeValueAsString(response);
			
			// GZIP压缩
			byte[] compressedData = gzipCompress(jsonData.getBytes("UTF-8"));
			
			// 加密数据 - 增加Agent级别的错误处理
			EncryptionService.EncryptedPayload payload;
			try {
				payload = encryptionService.encrypt(
					compressedData,
					agentPublicKey,
					serverEncryptionContext.getServerPrivateKey()
				);
			} catch (Exception encryptionError) {
				// 记录详细的加密错误信息
				log.error("Agent公钥加密失败，可能公钥格式损坏: agentId={}, error={}", 
					agentId, encryptionError.getMessage());
				
				// 标记该Agent的公钥为有问题
				serverEncryptionContext.markAgentPublicKeyAsCorrupted(agentId, encryptionError.getMessage());
				
				// 返回特定错误码，提示Agent重新注册公钥
				return ResponseEntity.status(422) // Unprocessable Entity
					.header("X-Error-Code", "CORRUPTED_PUBLIC_KEY")
					.header("X-Error-Message", "Agent public key is corrupted, please re-register")
					.build();
			}
			
			// 返回加密载荷的JSON
			String encryptedResponse = objectMapper.writeValueAsString(payload);
			
			log.info("成功加密任务数据: agentId={}, taskCount={}, originalSize={}KB, encryptedSize={}KB", 
				agentId, response.getTasks().size(), jsonData.length() / 1024, encryptedResponse.length() / 1024);
			
			return ResponseEntity.ok()
				.header("Content-Type", "application/json")
				.header("X-Encryption-Version", "1.0")
				.body(encryptedResponse);
			
		} catch (Exception e) {
			log.error("加密任务拉取失败: agentId={}", agentId, e);
			return ResponseEntity.status(500).build();
		}
	}
	/**
	 * Agent获取服务器公钥 - 用于自动密钥分发
	 */
	@GetMapping("/encryption/server-public-key")
	public ResponseEntity<Map<String, Object>> getServerPublicKey(
			@RequestParam String agentId,
			@RequestParam String agentToken) {

		if (!agentService.validateAgent(agentId, agentToken)) {
			throw new BusinessException(ErrorCode.AGENT_TOKEN_INVALID);
		}

		if (!serverEncryptionContext.isEncryptionEnabled()) {
			Map<String, Object> errorResponse = new HashMap<>();
			errorResponse.put("error", "服务器端加密未启用");
			return ResponseEntity.status(400).body(errorResponse);
		}

		try {
			Map<String, Object> response = new HashMap<>();
			response.put("serverPublicKey", serverEncryptionContext.getServerPublicKey());
			response.put("keyGenerationTime", serverEncryptionContext.getKeyGenerationTime());
			response.put("keyAgeDays", serverEncryptionContext.getKeyAgeDays());
			response.put("encryptionVersion", "1.0");

			log.info("Agent获取服务器公钥: agentId={}, keyAge={}天",
				agentId, serverEncryptionContext.getKeyAgeDays());

			return ResponseEntity.ok(response);

		} catch (Exception e) {
			log.error("获取服务器公钥失败: agentId={}", agentId, e);
			Map<String, Object> errorResponse = new HashMap<>();
			errorResponse.put("error", "获取服务器公钥失败: " + e.getMessage());
			return ResponseEntity.status(500).body(errorResponse);
		}
	}
	/**
	 * Agent公钥注册API
	 */
	@PostMapping("/encryption/register-public-key")
	public ResponseEntity<Map<String, Object>> registerAgentPublicKey(
			@RequestParam String agentId,
			@RequestParam String agentToken,
			@RequestBody Map<String, String> request) {

		// 验证Agent身份
		if (!agentService.validateAgent(agentId, agentToken)) {
			return ResponseEntity.status(401).build();
		}

		// 检查加密是否启用
		if (!serverEncryptionContext.isEncryptionEnabled()) {
			Map<String, Object> response = new HashMap<>();
			response.put("success", false);
			response.put("message", "服务器端加密未启用");
			return ResponseEntity.badRequest().body(response);
		}

		try {
			String agentPublicKey = request.get("publicKey");
			if (agentPublicKey == null || agentPublicKey.trim().isEmpty()) {
				Map<String, Object> response = new HashMap<>();
				response.put("success", false);
				response.put("message", "公钥不能为空");
				return ResponseEntity.badRequest().body(response);
			}

			// 注册Agent公钥
			serverEncryptionContext.registerAgentPublicKey(agentId, agentPublicKey);

			Map<String, Object> response = new HashMap<>();
			response.put("success", true);
			response.put("message", "Agent公钥注册成功");
			response.put("agentId", agentId);

			log.info("[Agent] 公钥注册成功: agentId={}", agentId);
			return ResponseEntity.ok(response);

		} catch (Exception e) {
			log.error("[Agent] 公钥注册失败: agentId={}, error={}", agentId, e.getMessage());
			Map<String, Object> response = new HashMap<>();
			response.put("success", false);
			response.put("message", "公钥注册失败: " + e.getMessage());
			return ResponseEntity.status(500).body(response);
		}
	}

	/**
	 * Agent获取服务器公钥 - 优雅版本，支持新旧密钥
	 */
	@GetMapping("/encryption/server-public-key-graceful")
	public ResponseEntity<Map<String, Object>> getServerPublicKeyGraceful(
			@RequestParam String agentId,
			@RequestParam String agentToken) {

		if (!agentService.validateAgent(agentId, agentToken)) {
			throw new BusinessException(ErrorCode.AGENT_TOKEN_INVALID);
		}

		// 检查是否有优雅加密上下文
		try {
			// 这里需要注入GracefulServerEncryptionContext
			// 暂时使用原有的serverEncryptionContext
			if (!serverEncryptionContext.isEncryptionEnabled()) {
				Map<String, Object> errorResponse = new HashMap<>();
				errorResponse.put("error", "服务器端加密未启用");
				return ResponseEntity.status(400).body(errorResponse);
			}

			Map<String, Object> response = new HashMap<>();
			response.put("serverPublicKey", serverEncryptionContext.getServerPublicKey());
			response.put("keyGenerationTime", serverEncryptionContext.getKeyGenerationTime());
			response.put("keyAgeDays", serverEncryptionContext.getKeyAgeDays());
			response.put("encryptionVersion", "1.1-graceful");
			response.put("gracefulRotationSupported", true);

			log.info("Agent获取服务器公钥(优雅版本): agentId={}, keyAge={}天",
				agentId, serverEncryptionContext.getKeyAgeDays());

			return ResponseEntity.ok(response);

		} catch (Exception e) {
			log.error("获取服务器公钥失败: agentId={}", agentId, e);
			Map<String, Object> errorResponse = new HashMap<>();
			errorResponse.put("error", "获取服务器公钥失败: " + e.getMessage());
			return ResponseEntity.status(500).body(errorResponse);
		}
	}
	
	/**
	 * GZIP压缩数据
	 */
	private byte[] gzipCompress(byte[] data) throws java.io.IOException {
		try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
			 java.util.zip.GZIPOutputStream gzipOut = new java.util.zip.GZIPOutputStream(baos)) {
			gzipOut.write(data);
			gzipOut.finish();
			return baos.toByteArray();
		}
	}

	/**
	 * 确认任务开始执行（使用executionId）
	 */
	@PostMapping("/tasks/executions/{executionId}/ack")
	public ResponseEntity<Void> ackExecution(@PathVariable Long executionId, @RequestParam String agentId, @RequestParam String agentToken) {
		if (!agentService.validateAgent(agentId, agentToken)) {
			throw new BusinessException(ErrorCode.AGENT_TOKEN_INVALID);
		}
		taskService.ackTaskExecution(executionId);
		return ResponseEntity.ok().build();
	}

	@PostMapping("/tasks/executions/{executionId}/log")
	public ResponseEntity<Void> log(@PathVariable Long executionId, @Valid @RequestBody LogChunkRequest req) {
		if (!agentService.validateAgent(req.getAgentId(), req.getAgentToken())) {
			throw new BusinessException(ErrorCode.AGENT_TOKEN_INVALID);
		}
		taskService.appendLog(req);
		return ResponseEntity.ok().build();
	}

	@PostMapping("/tasks/executions/{executionId}/batch-log")
	public ResponseEntity<Void> batchLog(@PathVariable Long executionId, @Valid @RequestBody BatchLogRequest req) {
		if (!agentService.validateAgent(req.getAgentId(), req.getAgentToken())) {
			throw new BusinessException(ErrorCode.AGENT_TOKEN_INVALID);
		}
		
		try {
			taskService.appendBatchLogs(req);
			return ResponseEntity.ok().build();
		} catch (Exception e) {
			log.error("批量日志处理失败", e);
			return ResponseEntity.status(500).build();
		}
	}

	@PostMapping("/tasks/executions/{executionId}/encrypted-batch-log")
	public ResponseEntity<Void> encryptedBatchLog(@PathVariable Long executionId, 
												 @Valid @RequestBody EncryptedBatchLogRequest req,
												 @RequestHeader(value = "X-Agent-Public-Key", required = false) String agentPublicKeyHeader) {
		
		// 验证加密是否启用
		if (!serverEncryptionContext.isEncryptionEnabled()) {
			log.warn("收到加密日志请求但服务器加密未启用: agentId={}", req.getAgentId());
			return ResponseEntity.status(400).build();
		}
		
		// 验证请求完整性
		if (!req.isValid()) {
			log.warn("加密批量日志请求无效: {}", req);
			return ResponseEntity.status(400).build();
		}
		
		// 验证时间戳
		if (!req.isTimestampValid()) {
			log.warn("加密批量日志时间戳无效，可能是重放攻击: agentId={}, timestamp={}", 
				req.getAgentId(), req.getTimestamp());
			return ResponseEntity.status(403).build();
		}
		
		try {
			// 注册Agent公钥（如果提供）
			if (agentPublicKeyHeader != null && !agentPublicKeyHeader.trim().isEmpty()) {
				String cleanPublicKey = "-----BEGIN PUBLIC KEY-----\n" + 
					agentPublicKeyHeader.replaceAll("(.{64})", "$1\n") + 
					"\n-----END PUBLIC KEY-----";
				serverEncryptionContext.registerAgentPublicKey(req.getAgentId(), cleanPublicKey);
			}
			
			// 获取Agent公钥
			String agentPublicKey = serverEncryptionContext.getAgentPublicKey(req.getAgentId());
			if (agentPublicKey == null) {
				log.warn("Agent公钥未注册: agentId={}", req.getAgentId());
				return ResponseEntity.status(403).build();
			}
			
			// 解密批量日志
			EncryptionService.EncryptedPayload payload = req.toPayload();
			byte[] decryptedData = encryptionService.decrypt(
				payload, 
				serverEncryptionContext.getServerPrivateKey(),
				agentPublicKey
			);
			
			// 解压缩数据
			byte[] decompressedData = gzipDecompress(decryptedData);
			String jsonData = new String(decompressedData, "UTF-8");
			
			// 反序列化日志批次
			com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
			com.fasterxml.jackson.core.type.TypeReference<java.util.List<com.example.lightscript.server.model.AgentModels.LogEntry>> typeRef = 
				new com.fasterxml.jackson.core.type.TypeReference<java.util.List<com.example.lightscript.server.model.AgentModels.LogEntry>>() {};
			List<com.example.lightscript.server.model.AgentModels.LogEntry> logs = objectMapper.readValue(jsonData, typeRef);
			
			// 创建BatchLogRequest并处理
			BatchLogRequest batchRequest = new BatchLogRequest();
			batchRequest.setAgentId(req.getAgentId());
			batchRequest.setExecutionId(req.getExecutionId());
			batchRequest.setLogs(logs);
			
			taskService.appendBatchLogs(batchRequest);
			
			log.info("成功处理加密批量日志: agentId={}, executionId={}, batchSize={}, encryptedSize={}KB", 
				req.getAgentId(), req.getExecutionId(), req.getBatchSize(), req.getEstimatedSize() / 1024);
			
			return ResponseEntity.ok().build();
			
		} catch (SecurityException e) {
			log.warn("加密批量日志安全验证失败: agentId={}, error={}", req.getAgentId(), e.getMessage());
			return ResponseEntity.status(403).build();
		} catch (Exception e) {
			log.error("加密批量日志处理失败: agentId={}, executionId={}", req.getAgentId(), req.getExecutionId(), e);
			return ResponseEntity.status(500).build();
		}
	}
	
	/**
	 * GZIP解压缩数据
	 */
	private byte[] gzipDecompress(byte[] compressedData) throws java.io.IOException {
		try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(compressedData);
			 java.util.zip.GZIPInputStream gzipIn = new java.util.zip.GZIPInputStream(bais);
			 java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
			
			byte[] buffer = new byte[1024];
			int len;
			while ((len = gzipIn.read(buffer)) != -1) {
				baos.write(buffer, 0, len);
			}
			return baos.toByteArray();
		}
	}

	@PostMapping("/tasks/executions/{executionId}/finish")
	public ResponseEntity<Void> finish(@PathVariable Long executionId, @Valid @RequestBody FinishRequest req) {
		if (!agentService.validateAgent(req.getAgentId(), req.getAgentToken())) {
			throw new BusinessException(ErrorCode.AGENT_TOKEN_INVALID);
		}
		taskService.finishTask(req);
		return ResponseEntity.ok().build();
	}

	@PostMapping("/debug/enqueue")
	public ResponseEntity<Map<String, String>> debugEnqueue(@RequestParam String agentId, @RequestParam String agentToken, @RequestBody TaskSpec spec) {
		if (!agentService.validateAgent(agentId, agentToken)) {
			throw new BusinessException(ErrorCode.AGENT_TOKEN_INVALID);
		}
		String taskId = taskService.createTask(agentId, spec, "debug");
		Map<String, String> response = new HashMap<>();
		response.put("taskId", taskId);
		return ResponseEntity.ok(response);
	}
	/**
	 * Agent下载文件 - 支持流式传输，避免内存溢出
	 */
	@GetMapping("/files/{fileId}/download")
	public ResponseEntity<Resource> downloadFile(
			@PathVariable String fileId,
			@RequestParam String agentId,
			@RequestParam String agentToken) {

		if (!agentService.validateAgent(agentId, agentToken)) {
			throw new BusinessException(ErrorCode.AGENT_TOKEN_INVALID);
		}

		try {
			// 获取文件信息
			FileModels.FileDTO fileInfo = fileService.getFileById(fileId);
			
			// 获取文件路径
			String filePath = fileService.getFilePath(fileId);
			Path path = Paths.get(filePath);
			
			if (!Files.exists(path)) {
				throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "文件不存在: " + filePath);
			}

			// 创建文件资源，支持流式传输
			Resource resource = new FileSystemResource(path);

			return ResponseEntity.ok()
					.header("Content-Disposition", "attachment; filename=\"" + fileInfo.getOriginalName() + "\"")
					.header("Content-Type", fileInfo.getFileType() != null ? fileInfo.getFileType() : "application/octet-stream")
					.header("Content-Length", String.valueOf(fileInfo.getFileSize()))
					.header("X-File-MD5", fileInfo.getMd5())
					.header("X-File-SHA256", fileInfo.getSha256())
					.header("Accept-Ranges", "bytes") // 支持断点续传
					.body(resource);
		} catch (Exception e) {
			throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件下载失败: " + e.getMessage());
		}
	}

	@GetMapping("/tasks/{taskId}/logs")
	public ResponseEntity<List<String>> logs(@PathVariable String taskId) {
		List<TaskLog> taskLogs = taskService.getTaskLogs(taskId);
		List<String> logs = taskLogs.stream()
				.map(log -> String.format("[%d][%s] %s", log.getSeqNum(), log.getStream(), log.getContent()))
				.collect(Collectors.toList());
		return ResponseEntity.ok(logs);
	}
	
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
	
	/**
	 * 获取Agent升级历史（支持分页和限制）
	 */
	@GetMapping("/{agentId}/upgrade-history")
	public ResponseEntity<List<AgentUpgradeLog>> getUpgradeHistory(
			@PathVariable String agentId,
			@RequestParam(defaultValue = "50") int limit) {
		
		// 限制最大返回数量，防止数据过多
		int maxLimit = Math.min(Math.max(limit, 1), 100);
		List<AgentUpgradeLog> history = upgradeStatusService.getUpgradeHistory(agentId, maxLimit);
		return ResponseEntity.ok(history);
	}
}