package com.example.lightscript.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class AgentApi {
	private final String baseUrl;
	private final CloseableHttpClient httpClient;
	private final ObjectMapper mapper;
	private AgentEncryptionContext encryptionContext;

	AgentApi(String baseUrl, CloseableHttpClient httpClient, ObjectMapper mapper) {
		this.baseUrl = baseUrl;
		this.httpClient = httpClient;
		this.mapper = mapper;
	}
	
	// 设置加密上下文
	public void setEncryptionContext(AgentEncryptionContext encryptionContext) {
		this.encryptionContext = encryptionContext;
	}

	// 添加访问器方法供BatchLogCollector使用
	public String getBaseUrl() {
		return baseUrl;
	}

	public CloseableHttpClient getHttpClient() {
		return httpClient;
	}

	public ObjectMapper getObjectMapper() {
		return mapper;
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
		// 检查是否启用加密
		AgentConfig config = AgentConfig.getInstance();
		if (config.isEncryptionEnabled()) {
			return pullTasksEncrypted(agentId, agentToken, max);
		} else {
			return pullTasksPlaintext(agentId, agentToken, max);
		}
	}
	
	/**
	 * 明文任务拉取（原有逻辑）
	 */
	private Map<String, Object> pullTasksPlaintext(String agentId, String agentToken, int max) throws Exception {
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
	
	/**
	 * 加密任务拉取
	 */
	private Map<String, Object> pullTasksEncrypted(String agentId, String agentToken, int max) throws Exception {
		String url = baseUrl + "/api/agent/tasks/encrypted-pull?agentId=" + agentId + "&agentToken=" + agentToken + "&max=" + max;
		HttpGet get = new HttpGet(url);
		
		try (CloseableHttpResponse response = httpClient.execute(get)) {
			int statusCode = response.getStatusLine().getStatusCode();
			String responseBody = EntityUtils.toString(response.getEntity(), "UTF-8");
			
			if (statusCode == 401 || statusCode == 403) {
				throw new RuntimeException("Agent token invalid or encryption not configured, need re-register");
			}
			
			// 处理公钥损坏的情况
			if (statusCode == 422) {
				String errorCode = response.getFirstHeader("X-Error-Code") != null ? 
					response.getFirstHeader("X-Error-Code").getValue() : "";
				String errorMessage = response.getFirstHeader("X-Error-Message") != null ? 
					response.getFirstHeader("X-Error-Message").getValue() : "";
				
				if ("CORRUPTED_PUBLIC_KEY".equals(errorCode)) {
					System.err.println("[AgentApi] 服务器检测到公钥损坏，需要重新注册: " + errorMessage);
					
					// 触发公钥重新注册
					AgentEncryptionContext encryptionContext = getEncryptionContext(agentId);
					if (encryptionContext != null) {
						System.out.println("[AgentApi] 正在重新生成和注册公钥...");
						encryptionContext.rotateKeys();
						encryptionContext.updateCredentials(agentId, agentToken);
						
						// 重试任务拉取
						System.out.println("[AgentApi] 公钥重新注册完成，重试任务拉取...");
						return pullTasksEncrypted(agentId, agentToken, max);
					}
				}
				
				throw new RuntimeException("Server rejected request: " + errorMessage);
			}
			
			if (statusCode != 200) {
				throw new RuntimeException("Pull encrypted tasks failed: " + responseBody);
			}
			
			// 解密响应数据
			return decryptTasksResponse(responseBody, agentId);
		}
	}
	
	/**
	 * 解密任务响应数据
	 */
	private Map<String, Object> decryptTasksResponse(String encryptedResponse, String agentId) throws Exception {
		// 获取加密上下文
		AgentEncryptionContext encryptionContext = getEncryptionContext(agentId);
		if (encryptionContext == null || !encryptionContext.isEncryptionConfigured()) {
			throw new RuntimeException("Agent加密未配置，无法解密任务数据");
		}
		
		// 解析加密载荷
		EncryptionService.EncryptedPayload payload = mapper.readValue(encryptedResponse, EncryptionService.EncryptedPayload.class);
		
		// 解密数据
		EncryptionService encryptionService = new EncryptionService();
		byte[] decryptedData = encryptionService.decrypt(
			payload,
			encryptionContext.getAgentPrivateKey(),
			encryptionContext.getServerPublicKey()
		);
		
		// 解压缩数据
		byte[] decompressedData = gzipDecompress(decryptedData);
		String jsonData = new String(decompressedData, "UTF-8");
		
		// 反序列化任务数据
		return mapper.readValue(jsonData, new TypeReference<Map<String, Object>>() {});
	}
	
	/**
	 * 获取加密上下文
	 */
	private AgentEncryptionContext getEncryptionContext(String agentId) {
		return encryptionContext;
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
	
	public void offline(String agentId, String agentToken) throws Exception {
		HttpPost post = new HttpPost(baseUrl + "/api/agent/offline");
		RequestConfig requestConfig = RequestConfig.custom()
			.setConnectTimeout(3000)
			.setConnectionRequestTimeout(3000)
			.setSocketTimeout(3000)
			.build();
		post.setConfig(requestConfig);
		List<NameValuePair> params = new ArrayList<>();
		params.add(new BasicNameValuePair("agentId", agentId));
		params.add(new BasicNameValuePair("agentToken", agentToken));
		post.setEntity(new UrlEncodedFormEntity(params));

		try (CloseableHttpResponse response = httpClient.execute(post)) {
			if (response.getStatusLine().getStatusCode() != 200) {
				throw new RuntimeException("Failed to mark agent offline: " + response.getStatusLine());
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

	String uploadArtifact(String agentId, String agentToken, Long executionId, File archiveFile) throws Exception {
		String url = String.format("%s/api/agent/tasks/executions/%d/upload-artifact?agentId=%s&agentToken=%s&archiveName=%s",
			baseUrl,
			executionId,
			java.net.URLEncoder.encode(agentId, "UTF-8"),
			java.net.URLEncoder.encode(agentToken, "UTF-8"),
			java.net.URLEncoder.encode(archiveFile.getName(), "UTF-8"));
		HttpPost post = new HttpPost(url);
		post.setEntity(new FileEntity(archiveFile, ContentType.APPLICATION_OCTET_STREAM));

		try (CloseableHttpResponse response = httpClient.execute(post)) {
			int statusCode = response.getStatusLine().getStatusCode();
			String responseBody = response.getEntity() != null ? EntityUtils.toString(response.getEntity(), "UTF-8") : "";

			if (statusCode == 401 || statusCode == 403) {
				throw new RuntimeException("Agent token invalid, need re-register");
			}
			if (statusCode != 200) {
				throw new RuntimeException("Upload artifact failed: " + responseBody);
			}

			Map<String, Object> result = mapper.readValue(responseBody, new TypeReference<Map<String, Object>>() {});
			return String.valueOf(result.get("storedPath"));
		}
	}

	void submitLogManifest(String agentId, String agentToken, Long executionId, Long logCollectionId,
						   List<Map<String, Object>> files) throws Exception {
		Map<String, Object> payload = new HashMap<>();
		payload.put("agentId", agentId);
		payload.put("agentToken", agentToken);
		payload.put("executionId", executionId);
		payload.put("logCollectionId", logCollectionId);
		payload.put("files", files);

		HttpPost post = new HttpPost(baseUrl + "/api/agent/tasks/executions/" + executionId + "/log-manifest");
		post.setHeader("Content-Type", "application/json");
		post.setEntity(new StringEntity(mapper.writeValueAsString(payload), "UTF-8"));

		try (CloseableHttpResponse response = httpClient.execute(post)) {
			int statusCode = response.getStatusLine().getStatusCode();
			String responseBody = response.getEntity() != null ? EntityUtils.toString(response.getEntity(), "UTF-8") : "";
			if (statusCode == 401 || statusCode == 403) {
				throw new RuntimeException("Agent token invalid, need re-register");
			}
			if (statusCode != 200) {
				throw new RuntimeException("Submit log manifest failed: " + responseBody);
			}
		}
	}
	/**
	 * 推送截图帧到服务器（屏幕监控）
	 * @return true=继续截图，false=服务器要求停止（无人观看）
	 */
	boolean uploadScreen(String agentId, String agentToken, String base64ImageData) {
		try {
			java.util.Map<String, Object> payload = new java.util.HashMap<>();
			payload.put("agentId", agentId);
			payload.put("agentToken", agentToken);
			payload.put("imageData", base64ImageData);
			payload.put("timestamp", java.time.Instant.now().toString());

			HttpPost post = new HttpPost(baseUrl + "/api/agent/screen/" + agentId);
			post.setHeader("Content-Type", "application/json");
			post.setEntity(new StringEntity(mapper.writeValueAsString(payload), "UTF-8"));

			try (CloseableHttpResponse response = httpClient.execute(post)) {
				int status = response.getStatusLine().getStatusCode();
				if (status == 200) {
					String body = EntityUtils.toString(response.getEntity(), "UTF-8");
					// {"skip":true} 表示无人观看，通知截图线程停止
					if (body != null && body.contains("\"skip\":true")) {
						return false;
					}
				}
			}
		} catch (Exception e) {
			// 静默失败，不打印堆栈，避免日志污染
			System.err.println("[Screen] uploadScreen failed: " + e.getMessage());
		}
		return true;
	}

	/**
	 * 获取服务器公钥 - 用于自动密钥分发
	 */
	public Map<String, Object> getServerPublicKey(String agentId, String agentToken) throws Exception {
		String url = baseUrl + "/api/agent/encryption/server-public-key?agentId=" + agentId + "&agentToken=" + agentToken;
		HttpGet get = new HttpGet(url);

		try (CloseableHttpResponse response = httpClient.execute(get)) {
			int statusCode = response.getStatusLine().getStatusCode();
			String responseBody = EntityUtils.toString(response.getEntity(), "UTF-8");

			if (statusCode == 401 || statusCode == 403) {
				throw new RuntimeException("Agent token invalid, need re-register");
			}
			if (statusCode == 400) {
				throw new RuntimeException("服务器端加密未启用");
			}
			if (statusCode != 200) {
				throw new RuntimeException("获取服务器公钥失败: " + responseBody);
			}

			// 解析JSON响应
			ObjectMapper mapper = new ObjectMapper();
			@SuppressWarnings("unchecked")
			Map<String, Object> result = mapper.readValue(responseBody, Map.class);

			System.out.println("[AgentApi] 成功获取服务器公钥，密钥年龄: " + result.get("keyAgeDays") + " 天");
			return result;

		} catch (Exception e) {
			System.err.println("[AgentApi] 获取服务器公钥失败: " + e.getMessage());
			throw e;
		}
	}
	
	/**
	 * 注册Agent公钥到服务器
	 */
	public Map<String, Object> registerAgentPublicKey(String agentId, String agentToken, String publicKey) throws Exception {
		Map<String, Object> payload = new HashMap<>();
		payload.put("publicKey", publicKey);
		
		String url = baseUrl + "/api/agent/encryption/register-public-key?agentId=" + agentId + "&agentToken=" + agentToken;
		HttpPost post = new HttpPost(url);
		post.setHeader("Content-Type", "application/json");
		post.setEntity(new StringEntity(mapper.writeValueAsString(payload), "UTF-8"));

		try (CloseableHttpResponse response = httpClient.execute(post)) {
			int statusCode = response.getStatusLine().getStatusCode();
			String responseBody = EntityUtils.toString(response.getEntity(), "UTF-8");

			if (statusCode == 401 || statusCode == 403) {
				throw new RuntimeException("Agent token invalid, need re-register");
			}
			if (statusCode != 200) {
				throw new RuntimeException("注册Agent公钥失败: " + responseBody);
			}

			// 解析JSON响应
			@SuppressWarnings("unchecked")
			Map<String, Object> result = mapper.readValue(responseBody, Map.class);

			System.out.println("[AgentApi] Agent公钥注册成功");
			return result;

		} catch (Exception e) {
			System.err.println("[AgentApi] 注册Agent公钥失败: " + e.getMessage());
			throw e;
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
			return AgentPaths.getAgentHome().toString();
		} catch (Exception e) {
			return null;
		}
	}
	
	// 获取磁盘总空间(GB)
	private Long getDiskSpaceGb() {
		try {
			java.io.File root = AgentPaths.getAgentHome().toFile();
			long totalSpace = root.getTotalSpace();
			return totalSpace / (1024 * 1024 * 1024); // 转换为GB
		} catch (Exception e) {
			return null;
		}
	}
	
	// 获取磁盘可用空间(GB)
	private Long getFreeSpaceGb() {
		try {
			java.io.File root = AgentPaths.getAgentHome().toFile();
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
