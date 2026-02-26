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
		payload.put("ip", getLocalIpAddress());
		
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
		Double cpuLoad = getCpuLoad();
		Long freeMemMb = getFreeMemoryMb();
		Long totalMemMb = getTotalMemoryMb();
		
		Map<String, Object> payload = new HashMap<>();
		payload.put("agentId", agentId);
		payload.put("agentToken", agentToken);
		payload.put("time", java.time.Instant.now().toString());
		payload.put("cpuLoad", cpuLoad);
		payload.put("freeMemMb", freeMemMb);
		payload.put("totalMemMb", totalMemMb);
		
		// 打印资源使用情况
		System.out.println(String.format("Resource Usage - CPU: %.2f%%, Memory: %d/%d MB (%.2f%% used)", 
			cpuLoad != null ? cpuLoad * 100 : 0.0,
			freeMemMb != null && totalMemMb != null ? (totalMemMb - freeMemMb) : 0,
			totalMemMb != null ? totalMemMb : 0,
			freeMemMb != null && totalMemMb != null && totalMemMb > 0 
				? ((totalMemMb - freeMemMb) * 100.0 / totalMemMb) : 0.0
		));
		
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
	
	// 获取本机IP地址
	private String getLocalIpAddress() {
		try {
			return java.net.InetAddress.getLocalHost().getHostAddress();
		} catch (Exception e) {
			return "unknown";
		}
	}
	
	// 获取CPU负载（0.0-1.0）
	private Double getCpuLoad() {
		try {
			java.lang.management.OperatingSystemMXBean osBean = 
				java.lang.management.ManagementFactory.getOperatingSystemMXBean();
			if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
				com.sun.management.OperatingSystemMXBean sunOsBean = 
					(com.sun.management.OperatingSystemMXBean) osBean;
				double load = sunOsBean.getSystemCpuLoad();
				// 如果返回负数，表示不可用
				return load >= 0 ? load : null;
			}
			// 使用系统平均负载作为替代
			double loadAverage = osBean.getSystemLoadAverage();
			if (loadAverage >= 0) {
				int processors = osBean.getAvailableProcessors();
				return Math.min(loadAverage / processors, 1.0);
			}
			return null;
		} catch (Exception e) {
			return null;
		}
	}
	
	// 获取空闲内存（MB）
	private Long getFreeMemoryMb() {
		try {
			java.lang.management.OperatingSystemMXBean osBean = 
				java.lang.management.ManagementFactory.getOperatingSystemMXBean();
			if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
				com.sun.management.OperatingSystemMXBean sunOsBean = 
					(com.sun.management.OperatingSystemMXBean) osBean;
				long freeMemory = sunOsBean.getFreePhysicalMemorySize();
				return freeMemory / (1024 * 1024); // 转换为MB
			}
			return null;
		} catch (Exception e) {
			return null;
		}
	}
	
	// 获取总内存（MB）
	private Long getTotalMemoryMb() {
		try {
			java.lang.management.OperatingSystemMXBean osBean = 
				java.lang.management.ManagementFactory.getOperatingSystemMXBean();
			if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
				com.sun.management.OperatingSystemMXBean sunOsBean = 
					(com.sun.management.OperatingSystemMXBean) osBean;
				long totalMemory = sunOsBean.getTotalPhysicalMemorySize();
				return totalMemory / (1024 * 1024); // 转换为MB
			}
			return null;
		} catch (Exception e) {
			return null;
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

	void sendLog(String agentId, String agentToken, Long executionId, int seq, String stream, String data) throws Exception {
		Map<String, Object> payload = new HashMap<>();
		payload.put("agentId", agentId);
		payload.put("agentToken", agentToken);
		payload.put("executionId", executionId);
		payload.put("seq", seq);
		payload.put("stream", stream);
		payload.put("data", data);
		
		HttpPost post = new HttpPost(baseUrl + "/api/agent/tasks/executions/" + executionId + "/log");
		post.setHeader("Content-Type", "application/json");
		post.setEntity(new StringEntity(mapper.writeValueAsString(payload), "UTF-8"));
		
		try (CloseableHttpResponse response = httpClient.execute(post)) {
			// 日志上传不需要检查响应，失败了也继续
		}
	}

	void ackTask(String agentId, String agentToken, Long executionId) throws Exception {
		String url = baseUrl + "/api/agent/tasks/executions/" + executionId + "/ack?agentId=" + agentId + "&agentToken=" + agentToken;
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

	void finish(String agentId, String agentToken, Long executionId, int exitCode, String status, String summary) throws Exception {
		Map<String, Object> payload = new HashMap<>();
		payload.put("agentId", agentId);
		payload.put("agentToken", agentToken);
		payload.put("executionId", executionId);
		payload.put("exitCode", exitCode);
		payload.put("status", status);
		payload.put("summary", summary);
		
		HttpPost post = new HttpPost(baseUrl + "/api/agent/tasks/executions/" + executionId + "/finish");
		post.setHeader("Content-Type", "application/json");
		post.setEntity(new StringEntity(mapper.writeValueAsString(payload), "UTF-8"));
		
		try (CloseableHttpResponse response = httpClient.execute(post)) {
			// 完成通知不需要检查响应
		}
	}
}