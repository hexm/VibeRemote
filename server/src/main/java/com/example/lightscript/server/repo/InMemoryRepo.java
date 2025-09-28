package com.example.lightscript.server.repo;

import com.example.lightscript.server.model.AgentModels.AgentInfo;
import com.example.lightscript.server.model.AgentModels.TaskSpec;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class InMemoryRepo {
	private final Map<String, AgentInfo> agents = new ConcurrentHashMap<>();
	private final Map<String, Queue<TaskSpec>> pendingTasksByAgent = new ConcurrentHashMap<>();
	private final Map<String, List<String>> logsByTask = new ConcurrentHashMap<>();

	public AgentInfo registerAgent(AgentInfo info) {
		agents.put(info.getAgentId(), info);
		pendingTasksByAgent.putIfAbsent(info.getAgentId(), new ConcurrentLinkedQueue<>());
		return info;
	}

	public Optional<AgentInfo> findAgent(String agentId) {
		return Optional.ofNullable(agents.get(agentId));
	}

	public boolean validateAgent(String agentId, String token) {
		AgentInfo info = agents.get(agentId);
		return info != null && Objects.equals(info.getAgentToken(), token);
	}

	public void updateHeartbeat(String agentId) {
		AgentInfo info = agents.get(agentId);
		if (info != null) {
			info.setLastHeartbeat(Instant.now());
		}
	}

	public void enqueueTask(String agentId, TaskSpec taskSpec) {
		pendingTasksByAgent.computeIfAbsent(agentId, k -> new ConcurrentLinkedQueue<>()).add(taskSpec);
	}

	public List<TaskSpec> pullTasks(String agentId, int max) {
		Queue<TaskSpec> q = pendingTasksByAgent.computeIfAbsent(agentId, k -> new ConcurrentLinkedQueue<>());
		List<TaskSpec> res = new ArrayList<>();
		for (int i = 0; i < max; i++) {
			TaskSpec t = q.poll();
			if (t == null) break;
			res.add(t);
		}
		return res;
	}

	public void appendLog(String taskId, String line) {
		logsByTask.computeIfAbsent(taskId, k -> Collections.synchronizedList(new ArrayList<>())).add(line);
	}

	public List<String> getLogs(String taskId) {
		return logsByTask.getOrDefault(taskId, List.of());
	}
} 