package com.example.lightscript.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

class AgentApi {
	private final String baseUrl;
	private final CloseableHttpClient httpClient;
	private final ObjectMapper mapper;

	AgentApi(String baseUrl, CloseableHttpClient httpClient, ObjectMapper mapper) {
		this.baseUrl = baseUrl;
		this.httpClient = httpClient;
		this.mapper = mapper;
	}

	Map<String, Object> register(String registerToken, String hostname, String osType) throws Exception {
		Map<String, Object> payload = new HashMap<>();
		payload.put("registerToken", registerToken);
		payload.put("hostname", hostname);
		payload.put("osType", osType);
		payload.put("ip", "");
		
		HttpPost post = new HttpPost(baseUrl + "/api/agent/register");
		post.setHeader("Content-Type", "application/json");
		post.setEntity(new StringEntity(mapper.writeValueAsString(payload), "UTF-8"));
		
		try (CloseableHttpResponse response = httpClient.execute(post)) {
			int statusCode = response.getStatusLine().getStatusCode();
			String responseBody = EntityUtils.toString(response.getEntity(), "UTF-8");
			
			if (statusCode != 200) {
				throw new RuntimeException("Registration failed: " + responseBody);
			}
			return mapper.readValue(responseBody, new TypeReference<Map<String, Object>>() {});
		}
	}

	void heartbeat(String agentId, String agentToken) throws Exception {
		Map<String, Object> payload = new HashMap<>();
		payload.put("agentId", agentId);
		payload.put("agentToken", agentToken);
		payload.put("time", java.time.Instant.now().toString());
		
		HttpPost post = new HttpPost(baseUrl + "/api/agent/heartbeat");
		post.setHeader("Content-Type", "application/json");
		post.setEntity(new StringEntity(mapper.writeValueAsString(payload), "UTF-8"));
		
		try (CloseableHttpResponse response = httpClient.execute(post)) {
			int statusCode = response.getStatusLine().getStatusCode();
			// 检查响应状态码，401/403表示令牌无效
			if (statusCode == 401 || statusCode == 403) {
				throw new RuntimeException("Agent token invalid, need re-register");
			}
			if (statusCode != 200) {
				String responseBody = EntityUtils.toString(response.getEntity(), "UTF-8");
				throw new RuntimeException("Heartbeat failed: " + responseBody);
			}
		}
	}

	Map<String, Object> pullTasks(String agentId, String agentToken) throws Exception {
		String url = baseUrl + "/api/agent/tasks/pull?agentId=" + agentId + "&agentToken=" + agentToken;
		HttpGet get = new HttpGet(url);
		
		try (CloseableHttpResponse response = httpClient.execute(get)) {
			int statusCode = response.getStatusLine().getStatusCode();
			String responseBody = EntityUtils.toString(response.getEntity(), "UTF-8");
			
			if (statusCode == 401 || statusCode == 403) {
				throw new RuntimeException("Agent token invalid, need re-register");
			}
			if (statusCode != 200) {
				throw new RuntimeException("Pull tasks failed: " + responseBody);
			}
			return mapper.readValue(responseBody, new TypeReference<Map<String, Object>>() {});
		}
	}

	void sendLog(String agentId, String agentToken, String taskId, int seq, String stream, String data) throws Exception {
		Map<String, Object> payload = new HashMap<>();
		payload.put("agentId", agentId);
		payload.put("agentToken", agentToken);
		payload.put("taskId", taskId);
		payload.put("seq", seq);
		payload.put("stream", stream);
		payload.put("data", data);
		
		HttpPost post = new HttpPost(baseUrl + "/api/agent/tasks/" + taskId + "/log");
		post.setHeader("Content-Type", "application/json");
		post.setEntity(new StringEntity(mapper.writeValueAsString(payload), "UTF-8"));
		
		try (CloseableHttpResponse response = httpClient.execute(post)) {
			// 日志上传不需要检查响应，失败了也继续
		}
	}

	void ackTask(String agentId, String agentToken, String taskId) throws Exception {
		String url = baseUrl + "/api/agent/tasks/" + taskId + "/ack?agentId=" + agentId + "&agentToken=" + agentToken;
		HttpPost post = new HttpPost(url);
		
		try (CloseableHttpResponse response = httpClient.execute(post)) {
			int statusCode = response.getStatusLine().getStatusCode();
			if (statusCode == 401 || statusCode == 403) {
				throw new RuntimeException("Agent token invalid, need re-register");
			}
			if (statusCode != 200) {
				String responseBody = EntityUtils.toString(response.getEntity(), "UTF-8");
				throw new RuntimeException("ACK task failed: " + responseBody);
			}
		}
	}

	void finish(String agentId, String agentToken, String taskId, int exitCode, String status, String summary) throws Exception {
		Map<String, Object> payload = new HashMap<>();
		payload.put("agentId", agentId);
		payload.put("agentToken", agentToken);
		payload.put("taskId", taskId);
		payload.put("exitCode", exitCode);
		payload.put("status", status);
		payload.put("summary", summary);
		
		HttpPost post = new HttpPost(baseUrl + "/api/agent/tasks/" + taskId + "/finish");
		post.setHeader("Content-Type", "application/json");
		post.setEntity(new StringEntity(mapper.writeValueAsString(payload), "UTF-8"));
		
		try (CloseableHttpResponse response = httpClient.execute(post)) {
			// 完成通知不需要检查响应
		}
	}
}