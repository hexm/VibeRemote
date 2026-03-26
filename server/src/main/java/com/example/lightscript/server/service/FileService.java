package com.example.lightscript.server.service;

import com.example.lightscript.server.entity.File;
import com.example.lightscript.server.exception.BusinessException;
import com.example.lightscript.server.exception.ErrorCode;
import com.example.lightscript.server.model.FileModels.*;
import com.example.lightscript.server.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {
    
    private final FileRepository fileRepository;
    
    // 文件存储目录
    private static final String FILE_STORAGE_DIR = "files";
    private static final String AGENT_UPLOAD_STORAGE_DIR = "agent-uploads";
    
    // 文件大小限制 (100MB)
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024L;
    
    /**
     * 分页查询文件列表
     */
    public Page<FileDTO> getFiles(FileListRequest request) {
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize());
        Page<File> files = fileRepository.findFilesWithFilters(
            request.getKeyword(), request.getCategory(), pageable);
        
        return files.map(this::convertToDTO);
    }
    
    /**
     * 获取所有文件（用于任务创建）
     */
    public List<FileForTaskDTO> getFilesForTask() {
        List<File> files = fileRepository.findAll();
        return files.stream()
            .map(this::convertToTaskDTO)
            .collect(Collectors.toList());
    }
    
    /**
     * 根据ID获取文件详情
     */
    public FileDTO getFileById(String fileId) {
        File file = fileRepository.findByFileId(fileId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "文件不存在"));
        return convertToDTO(file);
    }
    /**
     * 检查文件是否存在
     */
    public boolean existsById(String fileId) {
        return fileRepository.findByFileId(fileId).isPresent();
    }
    
    /**
     * 上传文件
     */
    @Transactional
    public FileDTO uploadFile(UploadFileRequest request, MultipartFile multipartFile, String uploadBy) {
        log.info("Starting file upload: name={}, originalName={}, size={}, category={}, uploadBy={}", 
                request.getName(), multipartFile.getOriginalFilename(), multipartFile.getSize(), 
                request.getCategory(), uploadBy);
        
        try {
            // 验证文件
            if (multipartFile.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "上传文件不能为空");
            }
            
            // 验证文件大小
            if (multipartFile.getSize() > MAX_FILE_SIZE) {
                throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED, 
                    String.format("文件大小超过限制，最大允许 %s，当前文件 %s", 
                        formatFileSize(MAX_FILE_SIZE), 
                        formatFileSize(multipartFile.getSize())));
            }
            
            // 检查文件名是否重复 - 暂时跳过以避免数据库问题
            // TODO: 修复数据库重复数据问题
            /*
            try {
                if (fileRepository.existsByName(request.getName())) {
                    throw new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS, "文件名已存在");
                }
            } catch (javax.persistence.NonUniqueResultException e) {
                log.warn("Multiple files found with name: {}, this indicates data inconsistency", request.getName());
                throw new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS, "文件名已存在（数据不一致）");
            }
            */
            
            log.debug("File validation passed, calculating checksum...");
            
            // 计算文件校验和
            FileChecksumInfo checksumInfo = calculateChecksum(multipartFile);
            log.debug("Checksum calculated: MD5={}, SHA256={}", checksumInfo.getMd5(), checksumInfo.getSha256());
            
            // 检查是否有重复文件 - 暂时跳过以避免数据库问题
            // TODO: 修复数据库重复数据问题
            /*
            if (fileRepository.findByMd5(checksumInfo.getMd5()).isPresent()) {
                log.warn("发现重复文件 (MD5: {}), 但允许上传", checksumInfo.getMd5());
            }
            */
            
            log.debug("Saving file to storage...");
            
            // 保存文件
            String filePath = saveUploadedFile(multipartFile);
            log.debug("File saved to: {}", filePath);
            
            // 创建文件记录
            File file = new File();
            BeanUtils.copyProperties(request, file);
            file.setFileId(generateFileId());
            file.setOriginalName(multipartFile.getOriginalFilename());
            file.setFilePath(filePath);
            file.setFileSize(multipartFile.getSize());
            file.setFileType(multipartFile.getContentType());
            file.setMd5(checksumInfo.getMd5());
            file.setSha256(checksumInfo.getSha256());
            file.setUploadBy(uploadBy);
            
            log.debug("Saving file record to database...");
            file = fileRepository.save(file);
            log.info("文件上传成功: {} by {}, fileId: {}", file.getName(), uploadBy, file.getFileId());
            
            return convertToDTO(file);
            
        } catch (BusinessException e) {
            log.warn("File upload failed with business exception: {}", e.getMessage());
            throw e;
        } catch (IOException e) {
            log.error("File upload failed with IO exception", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件保存失败: " + e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            log.error("File upload failed with algorithm exception", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件校验失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("File upload failed with unexpected exception", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件上传失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除文件
     */
    @Transactional
    public void deleteFile(String fileId) {
        File file = fileRepository.findByFileId(fileId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "文件不存在"));
        
        // 删除物理文件
        deletePhysicalFile(file.getFilePath());
        
        // 删除数据库记录
        fileRepository.delete(file);
        log.info("文件删除成功: {}", file.getName());
    }
    
    /**
     * 获取文件内容（用于下载）
     * @deprecated 使用getFilePath()和流式传输代替，避免内存溢出
     */
    @Deprecated
    public byte[] getFileContent(String fileId) {
        File file = fileRepository.findByFileId(fileId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "文件不存在"));
        
        return readFileContent(file.getFilePath());
    }
    
    /**
     * 获取文件路径（用于流式下载）
     */
    public String getFilePath(String fileId) {
        File file = fileRepository.findByFileId(fileId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "文件不存在"));
        
        return file.getFilePath();
    }
    
    /**
     * 获取文件分类列表
     */
    public List<String> getCategories() {
        return fileRepository.findAllCategories();
    }
    
    /**
     * 获取分类统计信息
     */
    public List<FileCategoryStats> getCategoryStats() {
        List<Object[]> results = fileRepository.getCategoryStats();
        return results.stream()
            .map(row -> {
                FileCategoryStats stats = new FileCategoryStats();
                stats.setCategory((String) row[0]);
                stats.setCount((Long) row[1]);
                stats.setTotalSize((Long) row[2]);
                return stats;
            })
            .collect(Collectors.toList());
    }
    
    /**
     * 检查文件完整性
     */
    public boolean verifyFileIntegrity(String fileId, String expectedChecksum) {
        File file = fileRepository.findByFileId(fileId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "文件不存在"));
        
        // 支持MD5和SHA256校验
        return expectedChecksum.equals(file.getMd5()) || expectedChecksum.equals(file.getSha256());
    }

    /**
     * 保存Agent上传的任务产物
     */
    public String saveAgentUpload(String agentId, String taskId, Long executionId, String archiveName, InputStream inputStream) {
        return saveAgentUpload(agentId, taskId, executionId, archiveName, inputStream, null);
    }

    public String saveAgentUpload(String agentId, String taskId, Long executionId, String archiveName, InputStream inputStream, Long maxSizeBytes) {
        try {
            String safeAgentId = sanitizePathSegment(agentId);
            String safeTaskId = sanitizePathSegment(taskId);
            String safeArchiveName = sanitizeFileName(archiveName);

            Path uploadDir = Paths.get(AGENT_UPLOAD_STORAGE_DIR, safeAgentId, safeTaskId, String.valueOf(executionId));
            Files.createDirectories(uploadDir);

            Path targetPath = uploadDir.resolve(safeArchiveName);
            copyWithLimit(inputStream, targetPath, maxSizeBytes);
            return targetPath.toAbsolutePath().toString();
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "保存Agent上传文件失败: " + e.getMessage());
        }
    }
    
    // 私有方法
    
    private String generateFileId() {
        // 使用时间戳生成唯一ID
        return "F" + System.currentTimeMillis();
    }

    private String sanitizePathSegment(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "unknown";
        }
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return "upload.zip";
        }
        String normalized = fileName.replace("\\", "/");
        int lastSlash = normalized.lastIndexOf('/');
        if (lastSlash >= 0) {
            normalized = normalized.substring(lastSlash + 1);
        }
        normalized = normalized.replaceAll("[^a-zA-Z0-9._-]", "_");
        return normalized.isEmpty() ? "upload.zip" : normalized;
    }

    private void copyWithLimit(InputStream inputStream, Path targetPath, Long maxSizeBytes) throws IOException {
        long totalBytes = 0L;
        byte[] buffer = new byte[8192];

        try (java.io.OutputStream outputStream = Files.newOutputStream(
            targetPath,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )) {
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                totalBytes += read;
                if (maxSizeBytes != null && maxSizeBytes > 0 && totalBytes > maxSizeBytes) {
                    throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED,
                        String.format("最大允许 %d MB", Math.max(1L, maxSizeBytes / 1024 / 1024)));
                }
                outputStream.write(buffer, 0, read);
            }
        } catch (BusinessException e) {
            Files.deleteIfExists(targetPath);
            throw e;
        } catch (IOException e) {
            Files.deleteIfExists(targetPath);
            throw e;
        }
    }
    
    private String saveUploadedFile(MultipartFile file) throws IOException {
        try {
            // 创建存储目录
            Path storageDir = Paths.get(FILE_STORAGE_DIR);
            log.debug("Storage directory: {}", storageDir.toAbsolutePath());
            
            if (!Files.exists(storageDir)) {
                log.info("Creating storage directory: {}", storageDir.toAbsolutePath());
                Files.createDirectories(storageDir);
            }
            
            // 检查目录权限
            if (!Files.isWritable(storageDir)) {
                throw new IOException("Storage directory is not writable: " + storageDir.toAbsolutePath());
            }
            
            // 生成唯一文件名
            String filename = System.currentTimeMillis() + "_" + file.getOriginalFilename();
            Path filePath = storageDir.resolve(filename);
            
            log.debug("Saving file to: {}", filePath.toAbsolutePath());
            
            // 保存文件
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            
            log.info("File saved successfully: {}", filePath.toAbsolutePath());
            return filePath.toString();
            
        } catch (IOException e) {
            log.error("Failed to save uploaded file: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    private void deletePhysicalFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (Files.exists(path)) {
                Files.delete(path);
            }
        } catch (IOException e) {
            log.warn("删除物理文件失败: {}", filePath, e);
        }
    }
    
    private byte[] readFileContent(String filePath) {
        try {
            Path path = Paths.get(filePath);
            return Files.readAllBytes(path);
        } catch (IOException e) {
            log.error("读取文件内容失败: {}", filePath, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "读取文件内容失败");
        }
    }
    
    private FileChecksumInfo calculateChecksum(MultipartFile file) throws IOException, NoSuchAlgorithmException {
        byte[] fileBytes = file.getBytes();
        
        // 计算MD5
        MessageDigest md5Digest = MessageDigest.getInstance("MD5");
        byte[] md5Hash = md5Digest.digest(fileBytes);
        String md5 = bytesToHex(md5Hash);
        
        // 计算SHA256
        MessageDigest sha256Digest = MessageDigest.getInstance("SHA-256");
        byte[] sha256Hash = sha256Digest.digest(fileBytes);
        String sha256 = bytesToHex(sha256Hash);
        
        FileChecksumInfo checksumInfo = new FileChecksumInfo();
        checksumInfo.setMd5(md5);
        checksumInfo.setSha256(sha256);
        checksumInfo.setFileSize(file.getSize());
        
        return checksumInfo;
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
    
    private FileDTO convertToDTO(File file) {
        FileDTO dto = new FileDTO();
        BeanUtils.copyProperties(file, dto);
        
        // 格式化文件大小显示
        if (file.getFileSize() != null) {
            dto.setSizeDisplay(formatFileSize(file.getFileSize()));
        }
        
        return dto;
    }
    
    private FileForTaskDTO convertToTaskDTO(File file) {
        FileForTaskDTO dto = new FileForTaskDTO();
        dto.setFileId(file.getFileId());
        dto.setName(file.getName());
        dto.setOriginalName(file.getOriginalName());
        dto.setFileSize(file.getFileSize());
        dto.setSizeDisplay(formatFileSize(file.getFileSize()));
        dto.setCategory(file.getCategory());
        dto.setMd5(file.getMd5());
        dto.setSha256(file.getSha256());
        
        return dto;
    }
    
    private String formatFileSize(Long size) {
        if (size == null || size == 0) {
            return "0 B";
        }
        
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        double fileSize = size.doubleValue();
        
        while (fileSize >= 1024 && unitIndex < units.length - 1) {
            fileSize /= 1024;
            unitIndex++;
        }
        
        return String.format("%.1f %s", fileSize, units[unitIndex]);
    }
}
