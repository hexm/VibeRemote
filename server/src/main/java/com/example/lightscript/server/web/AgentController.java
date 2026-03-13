package com.example.lightscript.server.web;

import com.example.lightscript.server.exception.BusinessException;
import com.example.lightscript.server.exception.ErrorCode;
import com.example.lightscript.server.model.AgentModels.*;
import com.example.lightscript.server.model.FileModels;
import com.example.lightscript.server.service.AgentService;
import com.example.lightscript.server.service.TaskService;
import com.example.lightscript.server.service.FileService;
import com.example.lightscript.server.service.AgentVersionService;
import com.example.lightscript.server.service.UpgradeStatusService;
import com.example.lightscript.server.entity.TaskLog;
import com.example.lightscript.server.entity.AgentVersion;
import com.example.lightscript.server.entity.AgentUpgradeLog;
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
public class AgentController {
	private final AgentService agentService;
	private final TaskService taskService;
	private final FileService fileService;
	private final AgentVersionService agentVersionService;
	private final UpgradeStatusService upgradeStatusService;

	public AgentController(AgentService agentService, TaskService taskService, FileService fileService, 
						  AgentVersionService agentVersionService, UpgradeStatusService upgradeStatusService) {
		this.agentService = agentService;
		this.taskService = taskService;
		this.fileService = fileService;
		this.agentVersionService = agentVersionService;
		this.upgradeStatusService = upgradeStatusService;
	}

	@PostMapping("/register")
	public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest req) {
		RegisterResponse response = agentService.register(req);
		return ResponseEntity.ok(response);
	}

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
			AgentVersionService.VersionCheckResult versionCheck = agentVersionService.checkForUpdate(
				req.getAgentVersion(), 
				"ALL" // 暂时使用ALL平台，后续可以根据osType判断
			);
			
			if (versionCheck.isUpdateAvailable()) {
				AgentVersion latestVersion = versionCheck.getLatestVersion();
				
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
			}
		}
		
		return ResponseEntity.ok(response);
	}

	@PostMapping("/offline")
	public ResponseEntity<Void> offline(@RequestParam String agentId, @RequestParam String agentToken) {
		boolean success = agentService.markOffline(agentId, agentToken);
		if (!success) {
			throw new BusinessException(ErrorCode.AGENT_TOKEN_INVALID);
		}
		return ResponseEntity.ok().build();
	}

	@GetMapping("/tasks/pull")
	public ResponseEntity<PullTasksResponse> pull(@RequestParam String agentId, @RequestParam String agentToken, @RequestParam(defaultValue = "10") int max) {
		if (!agentService.validateAgent(agentId, agentToken)) {
			throw new BusinessException(ErrorCode.AGENT_TOKEN_INVALID);
		}
		PullTasksResponse rsp = new PullTasksResponse();
		rsp.setTasks(taskService.pullTasks(agentId, Math.min(Math.max(max, 1), 50)));
		return ResponseEntity.ok(rsp);
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
	 * 获取Agent升级历史
	 */
	@GetMapping("/{agentId}/upgrade-history")
	public ResponseEntity<List<AgentUpgradeLog>> getUpgradeHistory(@PathVariable String agentId) {
		List<AgentUpgradeLog> history = upgradeStatusService.getUpgradeHistory(agentId);
		return ResponseEntity.ok(history);
	}
}