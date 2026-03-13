package com.example.lightscript.server.model;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;
public class AgentModels {

	@Data
	public static class RegisterRequest {
		@NotBlank
		private String registerToken;
		@NotBlank
		private String hostname;
		@NotBlank
		private String osType; // WINDOWS | LINUX
		private String ip;
		private Map<String, String> labels;
	}

	@Data
	public static class RegisterResponse {
		private String agentId;
		private String agentToken;
	}

	@Data
	public static class HeartbeatRequest {
		@NotBlank
		private String agentId;
		@NotBlank
		private String agentToken;
		@NotNull
		private Instant time;
		private Double cpuLoad;
		private Long freeMemMb;
		private Long totalMemMb;
		
		// 扩展字段 - 系统详细信息（可选，首次心跳或定期更新时发送）
		private String startUser; // 启动用户
		private String workingDir; // 工作目录
		private Long diskSpaceGb; // 磁盘总空间(GB)
		private Long freeSpaceGb; // 磁盘可用空间(GB)
		private String osVersion; // 操作系统版本
		private String javaVersion; // Java版本
		private String agentVersion; // Agent版本
	}

	@Data
	public static class TaskSpec {
		private String taskId;
		private Long executionId; // 执行实例ID（必需，用于多代理支持）
		private String taskName; // 任务名称
		private String taskType = "SCRIPT"; // SCRIPT | FILE_TRANSFER
		private String scriptLang; // bash | powershell | cmd
		private String scriptContent;
		private Integer timeoutSec;
		private Map<String, String> env;
		
		// 文件传输相关字段
		private String fileId; // 传输的文件ID
		private String targetPath; // 目标路径
		private Boolean overwriteExisting = false; // 是否覆盖已存在的文件
		private Boolean verifyChecksum = true; // 是否验证校验和
	}
	
	@Data
	public static class PullTasksResponse {
		private List<TaskSpec> tasks;
	}

	@Data
	public static class LogChunkRequest {
		@NotBlank
		private String agentId;
		@NotBlank
		private String agentToken;
		@NotNull
		private Long executionId; // 执行实例ID（必需）
		@NotNull
		private Integer seq;
		@NotBlank
		private String stream; // stdout|stderr
		@NotNull
		private String data;
	}

	@Data
	public static class FinishRequest {
		@NotBlank
		private String agentId;
		@NotBlank
		private String agentToken;
		@NotNull
		private Long executionId; // 执行实例ID（必需）
		private Integer exitCode;
		private String status; // SUCCESS|FAILED|TIMEOUT|CANCELLED
		private String summary;
	}

	@Data
	public static class AgentInfo {
		private String agentId;
		private String agentToken;
		private String hostname;
		private String osType;
		private String ip;
		private Map<String, String> labels;
		private Instant lastHeartbeat;
	}
	
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
	
	@Data
	public static class UpgradeStartRequest {
		@NotBlank
		private String fromVersion;
		@NotBlank
		private String toVersion;
		private boolean forceUpgrade;
	}
	
	@Data
	public static class UpgradeStatusRequest {
		@NotNull
		private Long upgradeLogId;
		@NotBlank
		private String status;
		private String errorMessage;
	}
} 