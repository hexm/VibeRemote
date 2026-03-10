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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
     * 上传文件
     */
    @Transactional
    public FileDTO uploadFile(UploadFileRequest request, MultipartFile multipartFile, String uploadBy) {
        // 验证文件
        if (multipartFile.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "上传文件不能为空");
        }
        
        // 检查文件名是否重复
        if (fileRepository.existsByName(request.getName())) {
            throw new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS, "文件名已存在");
        }
        
        try {
            // 计算文件校验和
            FileChecksumInfo checksumInfo = calculateChecksum(multipartFile);
            
            // 检查是否有重复文件
            if (fileRepository.findByMd5(checksumInfo.getMd5()).isPresent()) {
                log.warn("发现重复文件 (MD5: {}), 但允许上传", checksumInfo.getMd5());
            }
            
            // 保存文件
            String filePath = saveUploadedFile(multipartFile);
            
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
            
            file = fileRepository.save(file);
            log.info("文件上传成功: {} by {}", file.getName(), uploadBy);
            
            return convertToDTO(file);
        } catch (IOException | NoSuchAlgorithmException e) {
            log.error("文件上传失败", e);
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
     */
    public byte[] getFileContent(String fileId) {
        File file = fileRepository.findByFileId(fileId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "文件不存在"));
        
        return readFileContent(file.getFilePath());
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
    
    // 私有方法
    
    private String generateFileId() {
        Integer nextNumber = fileRepository.getNextFileIdNumber();
        return String.format("F%03d", nextNumber);
    }
    
    private String saveUploadedFile(MultipartFile file) throws IOException {
        // 创建存储目录
        Path storageDir = Paths.get(FILE_STORAGE_DIR);
        if (!Files.exists(storageDir)) {
            Files.createDirectories(storageDir);
        }
        
        // 生成唯一文件名
        String filename = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        Path filePath = storageDir.resolve(filename);
        
        // 保存文件
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        
        return filePath.toString();
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