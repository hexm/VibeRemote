package com.example.lightscript.server.service;

import com.example.lightscript.server.entity.Script;
import com.example.lightscript.server.exception.BusinessException;
import com.example.lightscript.server.exception.ErrorCode;
import com.example.lightscript.server.model.ScriptModels.*;
import com.example.lightscript.server.repository.ScriptRepository;
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
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptService {
    
    private final ScriptRepository scriptRepository;
    
    // 脚本文件存储目录
    private static final String SCRIPT_STORAGE_DIR = "scripts";
    
    /**
     * 分页查询脚本列表
     */
    public Page<ScriptDTO> getScripts(ScriptListRequest request) {
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize());
        Page<Script> scripts = scriptRepository.findScriptsWithFilters(
            request.getKeyword(), request.getType(), pageable);
        
        return scripts.map(this::convertToDTO);
    }
    
    /**
     * 获取所有脚本（用于任务创建）
     */
    public List<ScriptForTaskDTO> getScriptsForTask() {
        List<Script> scripts = scriptRepository.findAll();
        return scripts.stream()
            .map(this::convertToTaskDTO)
            .collect(Collectors.toList());
    }
    
    /**
     * 根据ID获取脚本详情
     */
    public ScriptDTO getScriptById(String scriptId) {
        Script script = scriptRepository.findByScriptId(scriptId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "脚本不存在"));
        return convertToDTO(script);
    }
    
    /**
     * 创建脚本（手动录入）
     */
    @Transactional
    public ScriptDTO createScript(CreateScriptRequest request, String createdBy) {
        // 检查名称是否重复
        if (scriptRepository.existsByName(request.getName())) {
            throw new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS, "脚本名称已存在");
        }
        
        Script script = new Script();
        BeanUtils.copyProperties(request, script);
        script.setScriptId(generateScriptId());
        script.setCreatedBy(createdBy);
        script.setIsUploaded(false);
        script.setFileSize((long) request.getContent().getBytes().length);
        
        script = scriptRepository.save(script);
        log.info("创建脚本成功: {} by {}", script.getName(), createdBy);
        
        return convertToDTO(script);
    }
    
    /**
     * 上传脚本文件
     */
    @Transactional
    public ScriptDTO uploadScript(UploadScriptRequest request, MultipartFile file, String createdBy) {
        // 检查名称是否重复
        if (scriptRepository.existsByName(request.getName())) {
            throw new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS, "脚本名称已存在");
        }
        
        // 验证文件
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "上传文件不能为空");
        }
        
        // 保存文件
        String filePath = saveUploadedFile(file);
        
        Script script = new Script();
        BeanUtils.copyProperties(request, script);
        script.setScriptId(generateScriptId());
        script.setFilename(file.getOriginalFilename());
        script.setFilePath(filePath);
        script.setFileSize(file.getSize());
        script.setCreatedBy(createdBy);
        script.setIsUploaded(true);
        
        script = scriptRepository.save(script);
        log.info("上传脚本成功: {} by {}", script.getName(), createdBy);
        
        return convertToDTO(script);
    }
    
    /**
     * 更新脚本
     */
    @Transactional
    public ScriptDTO updateScript(String scriptId, UpdateScriptRequest request) {
        Script script = scriptRepository.findByScriptId(scriptId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "脚本不存在"));
        
        // 如果是上传的脚本，不允许修改内容
        if (script.getIsUploaded()) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "上传的脚本不支持内容修改，请重新上传");
        }
        
        // 检查名称是否重复（排除自己）
        if (!script.getName().equals(request.getName()) && 
            scriptRepository.existsByName(request.getName())) {
            throw new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS, "脚本名称已存在");
        }
        
        BeanUtils.copyProperties(request, script);
        script.setFileSize((long) request.getContent().getBytes().length);
        
        script = scriptRepository.save(script);
        log.info("更新脚本成功: {}", script.getName());
        
        return convertToDTO(script);
    }
    
    /**
     * 重新上传脚本文件
     */
    @Transactional
    public ScriptDTO reuploadScript(String scriptId, UploadScriptRequest request, MultipartFile file) {
        Script script = scriptRepository.findByScriptId(scriptId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "脚本不存在"));
        
        if (!script.getIsUploaded()) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "只有上传的脚本才支持重新上传");
        }
        
        // 验证文件
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "上传文件不能为空");
        }
        
        // 删除旧文件
        if (script.getFilePath() != null) {
            deleteFile(script.getFilePath());
        }
        
        // 保存新文件
        String filePath = saveUploadedFile(file);
        
        // 检查名称是否重复（排除自己）
        if (!script.getName().equals(request.getName()) && 
            scriptRepository.existsByName(request.getName())) {
            throw new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS, "脚本名称已存在");
        }
        
        BeanUtils.copyProperties(request, script);
        script.setFilename(file.getOriginalFilename());
        script.setFilePath(filePath);
        script.setFileSize(file.getSize());
        
        script = scriptRepository.save(script);
        log.info("重新上传脚本成功: {}", script.getName());
        
        return convertToDTO(script);
    }
    
    /**
     * 删除脚本
     */
    @Transactional
    public void deleteScript(String scriptId) {
        Script script = scriptRepository.findByScriptId(scriptId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "脚本不存在"));
        
        // 删除文件（如果是上传的脚本）
        if (script.getIsUploaded() && script.getFilePath() != null) {
            deleteFile(script.getFilePath());
        }
        
        scriptRepository.delete(script);
        log.info("删除脚本成功: {}", script.getName());
    }
    
    /**
     * 获取脚本内容（用于查看和下载）
     */
    public String getScriptContent(String scriptId) {
        Script script = scriptRepository.findByScriptId(scriptId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "脚本不存在"));
        
        if (script.getIsUploaded()) {
            // 从文件读取内容
            return readFileContent(script.getFilePath());
        } else {
            // 直接返回数据库中的内容
            return script.getContent();
        }
    }
    
    /**
     * 增加脚本使用次数
     */
    @Transactional
    public void incrementUsageCount(String scriptId) {
        Script script = scriptRepository.findByScriptId(scriptId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "脚本不存在"));
        
        script.setUsageCount(script.getUsageCount() + 1);
        scriptRepository.save(script);
    }
    
    // 私有方法
    
    private String generateScriptId() {
        Integer nextNumber = scriptRepository.getNextScriptIdNumber();
        return String.format("S%03d", nextNumber);
    }
    
    private String saveUploadedFile(MultipartFile file) {
        try {
            // 创建存储目录
            Path storageDir = Paths.get(SCRIPT_STORAGE_DIR);
            if (!Files.exists(storageDir)) {
                Files.createDirectories(storageDir);
            }
            
            // 生成唯一文件名
            String filename = System.currentTimeMillis() + "_" + file.getOriginalFilename();
            Path filePath = storageDir.resolve(filename);
            
            // 保存文件
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            
            return filePath.toString();
        } catch (IOException e) {
            log.error("保存上传文件失败", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件保存失败");
        }
    }
    
    private void deleteFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (Files.exists(path)) {
                Files.delete(path);
            }
        } catch (IOException e) {
            log.warn("删除文件失败: {}", filePath, e);
        }
    }
    
    private String readFileContent(String filePath) {
        try {
            Path path = Paths.get(filePath);
            
            // 检查文件是否存在
            if (!Files.exists(path)) {
                log.warn("脚本文件不存在: {}", filePath);
                return "# 文件不存在\n# 文件路径: " + filePath + "\n# 请重新上传脚本文件";
            }
            
            // 检查是否为文件（不是目录）
            if (!Files.isRegularFile(path)) {
                log.warn("路径不是有效文件: {}", filePath);
                return "# 无效的文件路径\n# 路径: " + filePath + "\n# 请检查文件路径";
            }
            
            // Java 8兼容的文件读取方式
            byte[] bytes = Files.readAllBytes(path);
            return new String(bytes, "UTF-8");
        } catch (IOException e) {
            log.error("读取文件内容失败: {}", filePath, e);
            return "# 读取文件失败\n# 文件路径: " + filePath + "\n# 错误信息: " + e.getMessage() + "\n# 请检查文件是否存在且有读取权限";
        } catch (Exception e) {
            log.error("读取文件时发生未知错误: {}", filePath, e);
            return "# 读取文件时发生未知错误\n# 文件路径: " + filePath + "\n# 错误信息: " + e.getMessage();
        }
    }
    
    private ScriptDTO convertToDTO(Script script) {
        ScriptDTO dto = new ScriptDTO();
        BeanUtils.copyProperties(script, dto);
        
        // 格式化文件大小显示
        if (script.getFileSize() != null) {
            dto.setSizeDisplay(formatFileSize(script.getFileSize()));
        }
        
        return dto;
    }
    
    private ScriptForTaskDTO convertToTaskDTO(Script script) {
        ScriptForTaskDTO dto = new ScriptForTaskDTO();
        dto.setScriptId(script.getScriptId());
        dto.setName(script.getName());
        dto.setType(convertTypeForTask(script.getType()));
        dto.setFilename(script.getFilename());
        dto.setIsUploaded(script.getIsUploaded());
        
        // 获取脚本内容
        if (script.getIsUploaded()) {
            String filePath = script.getFilePath();
            if (filePath == null || filePath.trim().isEmpty()) {
                log.warn("上传脚本的文件路径为空: scriptId={}, name={}", script.getScriptId(), script.getName());
                dto.setContent("# 文件路径为空\n# 脚本ID: " + script.getScriptId() + "\n# 脚本名称: " + script.getName() + "\n# 请重新上传脚本文件");
            } else {
                dto.setContent(readFileContent(filePath));
            }
        } else {
            dto.setContent(script.getContent());
        }
        
        return dto;
    }
    
    private String convertTypeForTask(String type) {
        // 转换脚本类型名称以适配任务系统
        switch (type) {
            case "bash":
                return "shell";
            case "powershell":
                return "powershell";
            case "cmd":
                return "cmd";
            case "python":
                return "python";
            case "javascript":
                return "javascript";
            case "typescript":
                return "typescript";
            default:
                return "shell";
        }
    }
    
    private String formatFileSize(Long size) {
        if (size == null || size == 0) {
            return "0 B";
        }
        
        String[] units = {"B", "KB", "MB", "GB"};
        int unitIndex = 0;
        double fileSize = size.doubleValue();
        
        while (fileSize >= 1024 && unitIndex < units.length - 1) {
            fileSize /= 1024;
            unitIndex++;
        }
        
        return String.format("%.1f %s", fileSize, units[unitIndex]);
    }
}