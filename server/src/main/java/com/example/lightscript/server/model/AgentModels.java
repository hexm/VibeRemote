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
	}

	@Data
	public static class TaskSpec {
		private String taskId;
		private String scriptLang; // bash | powershell | cmd
		private String scriptContent;
		private Integer timeoutSec;
		private Map<String, String> env;
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
		@NotBlank
		private String taskId;
		@NotNull
		private Integer seq;
		@NotBlank
		private String stream; // stdout|stderr
		@NotBlank
		private String data;
	}

	@Data
	public static class FinishRequest {
		@NotBlank
		private String agentId;
		@NotBlank
		private String agentToken;
		@NotBlank
		private String taskId;
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
} 