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
		Map<String, Object> response = heartbeat(agentId, agentToken, false);
		// 兼容性方法，忽略响应
	}
	
	Map<String, Object> heartbeat(String agentId, String agentToken, boolean includeSystemInfo) throws Exception {
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
		
		// 如果需要包含系统信息（首次心跳或定期更新）
		if (includeSystemInfo) {
			try {
				payload.put("startUser", getStartUser());
				payload.put("workingDir", getWorkingDirectory());
				payload.put("diskSpaceGb", getDiskSpaceGb());
				payload.put("freeSpaceGb", getFreeSpaceGb());
				payload.put("osVersion", getOsVersion());
				payload.put("javaVersion", getJavaVersion());
				payload.put("agentVersion", getAgentVersion());
			} catch (Exception e) {
				System.err.println("Failed to collect system info: " + e.getMessage());
				// 继续发送心跳，即使系统信息收集失败
			}
		}
		
		// 总是包含Agent版本信息，用于版本检查
		payload.put("agentVersion", getAgentVersion());
		
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
			
			// 解析响应，检查版本更新信息
			String responseBody = EntityUtils.toString(response.getEntity(), "UTF-8");
			if (responseBody != null && !responseBody.trim().isEmpty()) {
				try {
					return mapper.readValue(responseBody, new TypeReference<Map<String, Object>>() {});
				} catch (Exception e) {
					System.err.println("Failed to parse heartbeat response: " + e.getMessage());
				}
			}
			return new HashMap<>();
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
		return pullTasks(agentId, agentToken, AgentConfig.getInstance().getTaskPullMax());
	}
	
	Map<String, Object> pullTasks(String agentId, String agentToken, int max) throws Exception {
		String url = baseUrl + "/api/agent/tasks/pull?agentId=" + agentId + "&agentToken=" + agentToken + "&max=" + max;
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
	
	/**
	 * 设置Agent状态为升级中
	 */
	void setUpgrading(String agentId, String agentToken) throws Exception {
		HttpPost post = new HttpPost(baseUrl + "/api/agent/status/upgrading?agentId=" + agentId + "&agentToken=" + agentToken);
		post.setHeader("Content-Type", "application/json");
		
		try (CloseableHttpResponse response = httpClient.execute(post)) {
			int statusCode = response.getStatusLine().getStatusCode();
			if (statusCode != 200) {
				throw new Exception("Failed to set upgrading status: " + statusCode);
			}
		}
	}
	
	boolean downloadFile(String agentId, String agentToken, String fileId, String targetPath, 
	                    boolean overwriteExisting, boolean verifyChecksum) throws Exception {
		String url = baseUrl + "/api/agent/files/" + fileId + "/download?agentId=" + agentId + "&agentToken=" + agentToken;
		HttpGet get = new HttpGet(url);
		
		try (CloseableHttpResponse response = httpClient.execute(get)) {
			int statusCode = response.getStatusLine().getStatusCode();
			
			if (statusCode == 401 || statusCode == 403) {
				throw new RuntimeException("Agent token invalid, need re-register");
			}
			if (statusCode != 200) {
				String responseBody = EntityUtils.toString(response.getEntity(), "UTF-8");
				throw new RuntimeException("Download file failed: " + responseBody);
			}
			
			// 获取文件大小
			long contentLength = response.getEntity().getContentLength();
			String contentLengthHeader = response.getFirstHeader("Content-Length") != null ? 
				response.getFirstHeader("Content-Length").getValue() : null;
			
			if (contentLengthHeader != null) {
				contentLength = Long.parseLong(contentLengthHeader);
			}
			
			// 检查文件大小限制
			if (contentLength > 0 && contentLength > 100 * 1024 * 1024L) { // 100MB限制
				throw new RuntimeException("File too large: " + contentLength + " bytes (max 100MB)");
			}
			
			// 检查目标文件是否存在
			java.io.File targetFile = new java.io.File(targetPath);
			if (targetFile.exists() && !overwriteExisting) {
				throw new RuntimeException("Target file already exists and overwrite is disabled: " + targetPath);
			}
			
			// 创建目标目录
			java.io.File parentDir = targetFile.getParentFile();
			if (parentDir != null && !parentDir.exists()) {
				parentDir.mkdirs();
			}
			
			// 使用流式下载，避免内存溢出
			try (java.io.InputStream inputStream = response.getEntity().getContent();
			     java.io.FileOutputStream outputStream = new java.io.FileOutputStream(targetFile);
			     java.io.BufferedInputStream bufferedInput = new java.io.BufferedInputStream(inputStream);
			     java.io.BufferedOutputStream bufferedOutput = new java.io.BufferedOutputStream(outputStream)) {
				
				byte[] buffer = new byte[64 * 1024]; // 64KB缓冲区，提高性能
				int bytesRead;
				long totalBytes = 0;
				long lastProgressReport = 0;
				
				System.out.println("Starting file download: " + targetPath);
				
				while ((bytesRead = bufferedInput.read(buffer)) != -1) {
					bufferedOutput.write(buffer, 0, bytesRead);
					totalBytes += bytesRead;
					
					// 每下载10MB显示一次进度
					if (totalBytes - lastProgressReport >= 10 * 1024 * 1024L) {
						double progressMB = totalBytes / 1024.0 / 1024.0;
						if (contentLength > 0) {
							double progressPercent = (totalBytes * 100.0) / contentLength;
							System.out.printf("Download progress: %.1f MB (%.1f%%)\n", progressMB, progressPercent);
						} else {
							System.out.printf("Download progress: %.1f MB\n", progressMB);
						}
						lastProgressReport = totalBytes;
					}
				}
				
				// 确保数据写入磁盘
				bufferedOutput.flush();
				outputStream.getFD().sync();
				
				System.out.println("File downloaded successfully: " + targetPath + " (" + totalBytes + " bytes)");
				
				// 可选：验证文件完整性
				if (verifyChecksum && contentLength > 0 && totalBytes != contentLength) {
					throw new RuntimeException("File size mismatch: expected " + contentLength + ", got " + totalBytes);
				}
				
				return true;
			}
			
		} catch (Exception e) {
			System.err.println("Failed to download file: " + e.getMessage());
			// 如果下载失败，清理部分下载的文件
			java.io.File targetFile = new java.io.File(targetPath);
			if (targetFile.exists()) {
				targetFile.delete();
			}
			return false;
		}
	}
	
	// 获取启动用户
	private String getStartUser() {
		try {
			return System.getProperty("user.name");
		} catch (Exception e) {
			return null;
		}
	}
	
	// 获取工作目录
	private String getWorkingDirectory() {
		try {
			return System.getProperty("user.dir");
		} catch (Exception e) {
			return null;
		}
	}
	
	// 获取磁盘总空间(GB)
	private Long getDiskSpaceGb() {
		try {
			java.io.File root = new java.io.File(System.getProperty("user.dir"));
			long totalSpace = root.getTotalSpace();
			return totalSpace / (1024 * 1024 * 1024); // 转换为GB
		} catch (Exception e) {
			return null;
		}
	}
	
	// 获取磁盘可用空间(GB)
	private Long getFreeSpaceGb() {
		try {
			java.io.File root = new java.io.File(System.getProperty("user.dir"));
			long freeSpace = root.getFreeSpace();
			return freeSpace / (1024 * 1024 * 1024); // 转换为GB
		} catch (Exception e) {
			return null;
		}
	}
	
	// 获取操作系统版本
	private String getOsVersion() {
		try {
			String osName = System.getProperty("os.name");
			String osVersion = System.getProperty("os.version");
			String osArch = System.getProperty("os.arch");
			return osName + " " + osVersion + " (" + osArch + ")";
		} catch (Exception e) {
			return null;
		}
	}
	
	// 获取Java版本
	private String getJavaVersion() {
		try {
			String javaVersion = System.getProperty("java.version");
			String javaVendor = System.getProperty("java.vendor");
			return javaVersion + " (" + javaVendor + ")";
		} catch (Exception e) {
			return null;
		}
	}
	
	// 获取Agent版本
	private String getAgentVersion() {
		return VersionUtil.getCurrentVersion();
	}
}