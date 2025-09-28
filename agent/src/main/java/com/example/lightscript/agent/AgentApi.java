package com.example.lightscript.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

class AgentApi {
	private final String baseUrl;
	private final HttpClient httpClient;
	private final ObjectMapper mapper;

	AgentApi(String baseUrl, HttpClient httpClient, ObjectMapper mapper) {
		this.baseUrl = baseUrl;
		this.httpClient = httpClient;
		this.mapper = mapper;
	}

	Map<String, Object> register(String registerToken, String hostname, String osType) throws Exception {
		Map<String, Object> payload = Map.of(
				"registerToken", registerToken,
				"hostname", hostname,
				"osType", osType,
				"ip", ""
		);
		HttpRequest req = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + "/api/agent/register"))
				.header("Content-Type", "application/json")
				.timeout(Duration.ofSeconds(5))
				.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
				.build();
		HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
		if (resp.statusCode() != 200) throw new RuntimeException("register failed: " + resp.statusCode());
		return mapper.readValue(resp.body(), new TypeReference<Map<String, Object>>() {});
	}

	void heartbeat(String agentId, String agentToken) throws Exception {
		Map<String, Object> payload = Map.of(
				"agentId", agentId,
				"agentToken", agentToken,
				"time", java.time.Instant.now().toString()
		);
		HttpRequest req = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + "/api/agent/heartbeat"))
				.header("Content-Type", "application/json")
				.timeout(Duration.ofSeconds(5))
				.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
				.build();
		httpClient.send(req, HttpResponse.BodyHandlers.discarding());
	}

	Map<String, Object> pull(String agentId, String agentToken, int max) throws Exception {
		String url = String.format("%s/api/agent/tasks/pull?agentId=%s&agentToken=%s&max=%d", baseUrl, agentId, agentToken, max);
		HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build();
		HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
		if (resp.statusCode() != 200) throw new RuntimeException("pull failed: " + resp.statusCode());
		return mapper.readValue(resp.body(), new TypeReference<Map<String, Object>>() {});
	}

	void sendLog(String agentId, String agentToken, String taskId, int seq, String stream, String data) throws Exception {
		Map<String, Object> payload = Map.of(
				"agentId", agentId,
				"agentToken", agentToken,
				"taskId", taskId,
				"seq", seq,
				"stream", stream,
				"data", data
		);
		HttpRequest req = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + "/api/agent/tasks/" + taskId + "/log"))
				.header("Content-Type", "application/json")
				.timeout(Duration.ofSeconds(10))
				.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
				.build();
		httpClient.send(req, HttpResponse.BodyHandlers.discarding());
	}

	void finish(String agentId, String agentToken, String taskId, int exitCode, String status, String summary) throws Exception {
		Map<String, Object> payload = Map.of(
				"agentId", agentId,
				"agentToken", agentToken,
				"taskId", taskId,
				"exitCode", exitCode,
				"status", status,
				"summary", summary
		);
		HttpRequest req = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + "/api/agent/tasks/" + taskId + "/finish"))
				.header("Content-Type", "application/json")
				.timeout(Duration.ofSeconds(10))
				.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
				.build();
		httpClient.send(req, HttpResponse.BodyHandlers.discarding());
	}
} 