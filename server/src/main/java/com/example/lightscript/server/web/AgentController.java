package com.example.lightscript.server.web;

import com.example.lightscript.server.exception.BusinessException;
import com.example.lightscript.server.exception.ErrorCode;
import com.example.lightscript.server.model.AgentModels.*;
import com.example.lightscript.server.service.AgentService;
import com.example.lightscript.server.service.TaskService;
import com.example.lightscript.server.entity.TaskLog;
import javax.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/agent")
public class AgentController {
	private final AgentService agentService;
	private final TaskService taskService;

	public AgentController(AgentService agentService, TaskService taskService) {
		this.agentService = agentService;
		this.taskService = taskService;
	}

	@PostMapping("/register")
	public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest req) {
		RegisterResponse response = agentService.register(req);
		return ResponseEntity.ok(response);
	}

	@PostMapping("/heartbeat")
	public ResponseEntity<Void> heartbeat(@Valid @RequestBody HeartbeatRequest req) {
		boolean success = agentService.updateHeartbeat(req.getAgentId(), req.getAgentToken(), req);
		if (!success) {
			throw new BusinessException(ErrorCode.AGENT_TOKEN_INVALID);
		}
		return ResponseEntity.ok().build();
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

	@PostMapping("/tasks/{taskId}/ack")
	public ResponseEntity<Void> ack(@PathVariable String taskId, @RequestParam String agentId, @RequestParam String agentToken) {
		if (!agentService.validateAgent(agentId, agentToken)) {
			throw new BusinessException(ErrorCode.AGENT_TOKEN_INVALID);
		}
		taskService.ackTask(taskId);
		return ResponseEntity.ok().build();
	}

	@PostMapping("/tasks/{taskId}/log")
	public ResponseEntity<Void> log(@PathVariable String taskId, @Valid @RequestBody LogChunkRequest req) {
		if (!agentService.validateAgent(req.getAgentId(), req.getAgentToken())) {
			throw new BusinessException(ErrorCode.AGENT_TOKEN_INVALID);
		}
		taskService.appendLog(taskId, req);
		return ResponseEntity.ok().build();
	}

	@PostMapping("/tasks/{taskId}/finish")
	public ResponseEntity<Void> finish(@PathVariable String taskId, @Valid @RequestBody FinishRequest req) {
		if (!agentService.validateAgent(req.getAgentId(), req.getAgentToken())) {
			throw new BusinessException(ErrorCode.AGENT_TOKEN_INVALID);
		}
		taskService.finishTask(taskId, req);
		return ResponseEntity.ok().build();
	}

	@PostMapping("/debug/enqueue")
	public ResponseEntity<Map<String, String>> debugEnqueue(@RequestParam String agentId, @RequestParam String agentToken, @RequestBody TaskSpec spec) {
		if (!agentService.validateAgent(agentId, agentToken)) {
			throw new BusinessException(ErrorCode.AGENT_TOKEN_INVALID);
		}
		String taskId = taskService.createTask(agentId, spec, "debug");
		return ResponseEntity.ok(Map.of("taskId", taskId));
	}

	@GetMapping("/tasks/{taskId}/logs")
	public ResponseEntity<List<String>> logs(@PathVariable String taskId) {
		List<TaskLog> taskLogs = taskService.getTaskLogs(taskId);
		List<String> logs = taskLogs.stream()
				.map(log -> String.format("[%d][%s] %s", log.getSeqNum(), log.getStream(), log.getContent()))
				.collect(Collectors.toList());
		return ResponseEntity.ok(logs);
	}
}