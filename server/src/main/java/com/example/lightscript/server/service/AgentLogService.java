package com.example.lightscript.server.service;

import com.example.lightscript.server.entity.AgentLogCollection;
import com.example.lightscript.server.entity.AgentLogFile;
import com.example.lightscript.server.entity.Task;
import com.example.lightscript.server.entity.TaskExecution;
import com.example.lightscript.server.model.AgentLogModels;
import com.example.lightscript.server.model.AgentModels;
import com.example.lightscript.server.repository.AgentLogCollectionRepository;
import com.example.lightscript.server.repository.AgentLogFileRepository;
import com.example.lightscript.server.repository.TaskExecutionRepository;
import com.example.lightscript.server.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentLogService {
    private static final int LOG_VIEW_MAX_BYTES = 1024 * 1024;

    private final AgentLogCollectionRepository agentLogCollectionRepository;
    private final AgentLogFileRepository agentLogFileRepository;
    private final TaskRepository taskRepository;
    private final TaskExecutionRepository taskExecutionRepository;
    private final TaskService taskService;
    private final AgentService agentService;

    @Transactional(readOnly = true)
    public AgentLogModels.AgentLogCollectionDTO getLatestCollection(String agentId) {
        return agentLogCollectionRepository.findFirstByAgentIdOrderByCreatedAtDesc(agentId)
            .map(this::toCollectionDtoWithFiles)
            .orElse(null);
    }

    @Transactional
    public AgentLogModels.AgentLogCollectionDTO triggerCollection(String agentId, String triggeredBy) {
        if (agentService.getAgentById(agentId) == null) {
            throw new IllegalArgumentException("客户端不存在: " + agentId);
        }

        Optional<AgentLogCollection> latestOpt = agentLogCollectionRepository.findFirstByAgentIdOrderByCreatedAtDesc(agentId);
        if (latestOpt.isPresent() && isCollectionInProgress(latestOpt.get().getStatus())) {
            return toCollectionDtoWithFiles(latestOpt.get());
        }

        AgentLogCollection collection = new AgentLogCollection();
        collection.setAgentId(agentId);
        collection.setTriggeredBy(triggeredBy);
        collection.setStatus("COLLECTING");
        collection = agentLogCollectionRepository.save(collection);

        AgentModels.TaskSpec taskSpec = new AgentModels.TaskSpec();
        taskSpec.setTaskName("内部-收集Agent日志清单-" + agentId);
        taskSpec.setTaskType("AGENT_LOG_INDEX");
        taskSpec.setTimeoutSec(180);
        taskSpec.setLogCollectionId(collection.getId());

        TaskExecution execution = taskService.createInternalTaskExecution(agentId, taskSpec, triggeredBy);
        collection.setManifestTaskId(execution.getTaskId());
        collection.setManifestExecutionId(execution.getId());
        collection = agentLogCollectionRepository.save(collection);

        log.info("Triggered agent log collection: collectionId={}, agentId={}, executionId={}",
            collection.getId(), agentId, execution.getId());
        return toCollectionDtoWithFiles(collection);
    }

    @Transactional
    public AgentLogModels.AgentLogCollectionDTO recollectFile(String agentId, Long collectionId, Long fileId, String triggeredBy) {
        if (agentService.getAgentById(agentId) == null) {
            throw new IllegalArgumentException("客户端不存在: " + agentId);
        }

        AgentLogCollection collection = getCollectionOwnedByAgent(agentId, collectionId);
        AgentLogFile logFile = agentLogFileRepository.findById(fileId)
            .orElseThrow(() -> new IllegalArgumentException("日志文件不存在: " + fileId));
        if (!collectionId.equals(logFile.getCollectionId())) {
            throw new IllegalArgumentException("日志文件不属于当前批次");
        }

        if ("QUEUED".equals(logFile.getUploadStatus()) || "UPLOADING".equals(logFile.getUploadStatus())) {
            return toCollectionDtoWithFiles(collection);
        }

        if (triggeredBy != null && !triggeredBy.trim().isEmpty()) {
            collection.setTriggeredBy(triggeredBy);
        }
        collection.setFinishedAt(null);
        collection.setErrorMessage(null);
        agentLogCollectionRepository.save(collection);

        logFile.setUploadStatus("PENDING");
        logFile.setUploadedFilePath(null);
        logFile.setUploadedAt(null);
        logFile.setErrorMessage(null);
        agentLogFileRepository.save(logFile);

        queueUploadTask(collection, logFile);
        updateCollectionStatus(collection.getId());

        log.info("Re-triggered agent log upload: collectionId={}, fileId={}, agentId={}",
            collection.getId(), logFile.getId(), agentId);
        return toCollectionDtoWithFiles(collection);
    }

    @Transactional
    public void submitManifest(AgentModels.AgentLogManifestRequest request) {
        AgentLogCollection collection = agentLogCollectionRepository.findById(request.getLogCollectionId())
            .orElseThrow(() -> new IllegalArgumentException("日志收集批次不存在: " + request.getLogCollectionId()));

        TaskExecution execution = taskExecutionRepository.findById(request.getExecutionId())
            .orElseThrow(() -> new IllegalArgumentException("执行实例不存在: " + request.getExecutionId()));

        if (!collection.getAgentId().equals(request.getAgentId()) || !request.getAgentId().equals(execution.getAgentId())) {
            throw new IllegalArgumentException("日志清单所属Agent不匹配");
        }

        List<AgentLogFile> existingFiles = agentLogFileRepository.findByCollectionIdOrderByFileNameAsc(collection.getId());
        if (!existingFiles.isEmpty()) {
            log.info("Manifest already processed for collection {}, skipping duplicate submit", collection.getId());
            return;
        }

        List<AgentLogFile> savedFiles = new ArrayList<>();
        for (AgentModels.AgentLogManifestItem item : request.getFiles()) {
            AgentLogFile logFile = new AgentLogFile();
            logFile.setCollectionId(collection.getId());
            logFile.setAgentId(request.getAgentId());
            logFile.setFileName(item.getFileName());
            logFile.setRelativePath(item.getRelativePath());
            logFile.setFileSize(item.getFileSize());
            logFile.setModifiedAt(toLocalDateTime(item.getModifiedAt()));
            logFile.setUploadStatus("PENDING");
            savedFiles.add(agentLogFileRepository.save(logFile));
        }

        for (AgentLogFile logFile : savedFiles) {
            queueUploadTask(collection, logFile);
        }

        updateCollectionStatus(collection.getId());
        log.info("Accepted agent log manifest: collectionId={}, fileCount={}", collection.getId(), savedFiles.size());
    }

    @Transactional
    public void handleTaskFinished(Long executionId, AgentModels.FinishRequest request) {
        TaskExecution execution = taskExecutionRepository.findById(executionId).orElse(null);
        if (execution == null) {
            return;
        }
        Task task = taskRepository.findById(execution.getTaskId()).orElse(null);
        if (task == null || !Boolean.TRUE.equals(task.getInternalTask())) {
            return;
        }

        if ("AGENT_LOG_INDEX".equals(task.getTaskType())) {
            handleIndexFinished(execution, request);
            return;
        }
        if ("AGENT_LOG_UPLOAD".equals(task.getTaskType())) {
            handleUploadFinished(execution, request);
        }
    }

    @Transactional
    public void handleArtifactUploaded(Long executionId, String storedPath) {
        TaskExecution execution = taskExecutionRepository.findById(executionId).orElse(null);
        if (execution == null || execution.getLogFileId() == null) {
            return;
        }
        Task task = taskRepository.findById(execution.getTaskId()).orElse(null);
        if (task == null || !"AGENT_LOG_UPLOAD".equals(task.getTaskType())) {
            return;
        }

        AgentLogFile logFile = agentLogFileRepository.findById(execution.getLogFileId()).orElse(null);
        if (logFile == null) {
            return;
        }

        logFile.setUploadedFilePath(storedPath);
        logFile.setUploadedAt(null);
        if (!"FAILED".equals(logFile.getUploadStatus())) {
            logFile.setUploadStatus("UPLOADING");
        }
        agentLogFileRepository.save(logFile);
    }

    @Transactional(readOnly = true)
    public List<AgentLogModels.AgentLogFileDTO> getCollectionFiles(String agentId, Long collectionId) {
        AgentLogCollection collection = getCollectionOwnedByAgent(agentId, collectionId);
        List<AgentLogFile> files = agentLogFileRepository.findByCollectionIdOrderByFileNameAsc(collection.getId());
        files.sort(Comparator.comparing(AgentLogFile::getModifiedAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(AgentLogFile::getFileName, Comparator.nullsLast(String::compareTo)));
        List<AgentLogModels.AgentLogFileDTO> result = new ArrayList<>();
        for (AgentLogFile file : files) {
            result.add(toFileDto(file));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public AgentLogModels.AgentLogFileDTO getCollectionFile(String agentId, Long collectionId, Long fileId) {
        getCollectionOwnedByAgent(agentId, collectionId);
        AgentLogFile logFile = agentLogFileRepository.findById(fileId)
            .orElseThrow(() -> new IllegalArgumentException("日志文件不存在: " + fileId));
        if (!collectionId.equals(logFile.getCollectionId())) {
            throw new IllegalArgumentException("日志文件不属于当前批次");
        }
        return toFileDto(logFile);
    }

    @Transactional(readOnly = true)
    public String readLogFileContent(String agentId, Long collectionId, Long fileId) {
        getCollectionOwnedByAgent(agentId, collectionId);
        AgentLogFile logFile = agentLogFileRepository.findById(fileId)
            .orElseThrow(() -> new IllegalArgumentException("日志文件不存在: " + fileId));
        if (!collectionId.equals(logFile.getCollectionId())) {
            throw new IllegalArgumentException("日志文件不属于当前批次");
        }
        if (logFile.getUploadedFilePath() == null || logFile.getUploadedFilePath().trim().isEmpty()) {
            throw new IllegalStateException("日志文件尚未上传完成");
        }

        Path path = Paths.get(logFile.getUploadedFilePath());
        if (!Files.exists(path)) {
            throw new IllegalStateException("日志文件不存在: " + path);
        }

        try {
            long size = Files.size(path);
            if (size <= LOG_VIEW_MAX_BYTES) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }

            byte[] tail = new byte[LOG_VIEW_MAX_BYTES];
            try (java.io.RandomAccessFile randomAccessFile = new java.io.RandomAccessFile(path.toFile(), "r")) {
                randomAccessFile.seek(size - LOG_VIEW_MAX_BYTES);
                randomAccessFile.readFully(tail);
            }
            return "[内容过长，仅显示最后 1 MB]\n\n" + new String(tail, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取日志文件失败: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public Path getLogFilePath(String agentId, Long collectionId, Long fileId) {
        getCollectionOwnedByAgent(agentId, collectionId);
        AgentLogFile logFile = agentLogFileRepository.findById(fileId)
            .orElseThrow(() -> new IllegalArgumentException("日志文件不存在: " + fileId));
        if (!collectionId.equals(logFile.getCollectionId())) {
            throw new IllegalArgumentException("日志文件不属于当前批次");
        }
        return Paths.get(logFile.getUploadedFilePath());
    }

    private void handleIndexFinished(TaskExecution execution, AgentModels.FinishRequest request) {
        if (execution.getLogCollectionId() == null) {
            return;
        }
        AgentLogCollection collection = agentLogCollectionRepository.findById(execution.getLogCollectionId()).orElse(null);
        if (collection == null) {
            return;
        }
        if (!"SUCCESS".equals(request.getStatus())) {
            collection.setStatus("FAILED");
            collection.setErrorMessage(request.getSummary());
            collection.setFinishedAt(LocalDateTime.now());
            agentLogCollectionRepository.save(collection);
            return;
        }

        if (agentLogFileRepository.countByCollectionId(collection.getId()) == 0) {
            collection.setStatus("READY");
            collection.setFinishedAt(LocalDateTime.now());
            agentLogCollectionRepository.save(collection);
        }
    }

    private void handleUploadFinished(TaskExecution execution, AgentModels.FinishRequest request) {
        if (execution.getLogFileId() == null || execution.getLogCollectionId() == null) {
            return;
        }
        AgentLogFile logFile = agentLogFileRepository.findById(execution.getLogFileId()).orElse(null);
        if (logFile == null) {
            return;
        }

        if ("SUCCESS".equals(request.getStatus()) && logFile.getUploadedFilePath() != null && !logFile.getUploadedFilePath().trim().isEmpty()) {
            logFile.setUploadStatus("SUCCESS");
            logFile.setUploadedAt(LocalDateTime.now());
            logFile.setErrorMessage(null);
        } else if ("SUCCESS".equals(request.getStatus())) {
            logFile.setUploadStatus("FAILED");
            logFile.setUploadedAt(null);
            logFile.setErrorMessage("日志文件上传完成但服务器未收到文件");
        } else {
            logFile.setUploadStatus("FAILED");
            logFile.setUploadedAt(null);
            logFile.setErrorMessage(request.getSummary());
        }
        agentLogFileRepository.save(logFile);
        updateCollectionStatus(execution.getLogCollectionId());
    }

    private void queueUploadTask(AgentLogCollection collection, AgentLogFile logFile) {
        AgentModels.TaskSpec taskSpec = new AgentModels.TaskSpec();
        taskSpec.setTaskName("内部-上传Agent日志-" + logFile.getFileName());
        taskSpec.setTaskType("AGENT_LOG_UPLOAD");
        taskSpec.setTimeoutSec(300);
        taskSpec.setSourcePath(logFile.getRelativePath());
        taskSpec.setRelativePath(logFile.getRelativePath());
        taskSpec.setLogCollectionId(collection.getId());
        taskSpec.setLogFileId(logFile.getId());

        TaskExecution execution = taskService.createInternalTaskExecution(collection.getAgentId(), taskSpec,
            collection.getTriggeredBy() != null ? collection.getTriggeredBy() : "system");
        logFile.setUploadTaskId(execution.getTaskId());
        logFile.setUploadExecutionId(execution.getId());
        logFile.setUploadStatus("QUEUED");
        agentLogFileRepository.save(logFile);
    }

    private void updateCollectionStatus(Long collectionId) {
        AgentLogCollection collection = agentLogCollectionRepository.findById(collectionId).orElse(null);
        if (collection == null) {
            return;
        }

        long total = agentLogFileRepository.countByCollectionId(collectionId);
        long success = agentLogFileRepository.countByCollectionIdAndUploadStatus(collectionId, "SUCCESS");
        long failed = agentLogFileRepository.countByCollectionIdAndUploadStatus(collectionId, "FAILED");
        long pending = total - success - failed;

        if (total == 0) {
            collection.setStatus("READY");
            collection.setFinishedAt(LocalDateTime.now());
        } else if (pending > 0 && success == 0 && failed == 0) {
            collection.setStatus("COLLECTING");
            collection.setFinishedAt(null);
        } else if (pending > 0) {
            collection.setStatus("PARTIAL_READY");
            collection.setFinishedAt(null);
        } else if (failed > 0 && success == 0) {
            collection.setStatus("FAILED");
            collection.setFinishedAt(LocalDateTime.now());
        } else if (failed > 0) {
            collection.setStatus("PARTIAL_READY");
            collection.setFinishedAt(LocalDateTime.now());
        } else {
            collection.setStatus("READY");
            collection.setFinishedAt(LocalDateTime.now());
        }

        agentLogCollectionRepository.save(collection);
    }

    private AgentLogCollection getCollectionOwnedByAgent(String agentId, Long collectionId) {
        AgentLogCollection collection = agentLogCollectionRepository.findById(collectionId)
            .orElseThrow(() -> new IllegalArgumentException("日志收集批次不存在: " + collectionId));
        if (!collection.getAgentId().equals(agentId)) {
            throw new IllegalArgumentException("日志收集批次不属于当前Agent");
        }
        return collection;
    }

    private AgentLogModels.AgentLogCollectionDTO toCollectionDtoWithFiles(AgentLogCollection collection) {
        List<AgentLogModels.AgentLogFileDTO> files = getCollectionFiles(collection.getAgentId(), collection.getId());
        AgentLogModels.AgentLogCollectionDTO dto = new AgentLogModels.AgentLogCollectionDTO();
        dto.setId(collection.getId());
        dto.setAgentId(collection.getAgentId());
        dto.setStatus(collection.getStatus());
        dto.setTriggeredBy(collection.getTriggeredBy());
        dto.setErrorMessage(collection.getErrorMessage());
        dto.setManifestTaskId(collection.getManifestTaskId());
        dto.setManifestExecutionId(collection.getManifestExecutionId());
        dto.setCreatedAt(collection.getCreatedAt());
        dto.setFinishedAt(collection.getFinishedAt());
        dto.setFiles(files);
        dto.setTotalFiles(files.size());
        dto.setSuccessFiles((int) files.stream().filter(file -> "SUCCESS".equals(file.getUploadStatus())).count());
        dto.setFailedFiles((int) files.stream().filter(file -> "FAILED".equals(file.getUploadStatus())).count());
        dto.setPendingFiles(files.size() - dto.getSuccessFiles() - dto.getFailedFiles());
        return dto;
    }

    private AgentLogModels.AgentLogFileDTO toFileDto(AgentLogFile file) {
        AgentLogModels.AgentLogFileDTO dto = new AgentLogModels.AgentLogFileDTO();
        dto.setId(file.getId());
        dto.setCollectionId(file.getCollectionId());
        dto.setAgentId(file.getAgentId());
        dto.setFileName(file.getFileName());
        dto.setRelativePath(file.getRelativePath());
        dto.setFileSize(file.getFileSize());
        dto.setModifiedAt(file.getModifiedAt());
        dto.setUploadStatus(file.getUploadStatus());
        dto.setUploadedFilePath(file.getUploadedFilePath());
        dto.setUploadedAt(file.getUploadedAt() != null ? file.getUploadedAt() : ("SUCCESS".equals(file.getUploadStatus()) ? file.getUpdatedAt() : null));
        dto.setUploadTaskId(file.getUploadTaskId());
        dto.setUploadExecutionId(file.getUploadExecutionId());
        dto.setErrorMessage(file.getErrorMessage());
        dto.setCreatedAt(file.getCreatedAt());
        dto.setUpdatedAt(file.getUpdatedAt());
        return dto;
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    private boolean isCollectionInProgress(String status) {
        return "COLLECTING".equals(status);
    }
}
