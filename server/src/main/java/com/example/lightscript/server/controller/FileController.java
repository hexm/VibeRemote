package com.example.lightscript.server.controller;

import com.example.lightscript.server.model.FileModels.*;
import com.example.lightscript.server.security.RequirePermission;
import com.example.lightscript.server.service.FileService;
import com.example.lightscript.server.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/web/files")
@RequiredArgsConstructor
public class FileController {
    
    private final FileService fileService;
    
    /**
     * 分页查询文件列表
     */
    @GetMapping
    @RequirePermission("file:list")
    public Page<FileDTO> getFiles(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        
        FileListRequest request = new FileListRequest();
        request.setKeyword(keyword);
        request.setCategory(category);
        request.setPage(page);
        request.setSize(size);
        
        return fileService.getFiles(request);
    }
    
    /**
     * 获取文件列表（用于任务创建）
     */
    @GetMapping("/for-task")
    @RequirePermission("file:list")
    public List<FileForTaskDTO> getFilesForTask() {
        return fileService.getFilesForTask();
    }
    
    /**
     * 获取文件详情
     */
    @GetMapping("/{fileId}")
    @RequirePermission("file:view")
    public FileDTO getFile(@PathVariable String fileId) {
        return fileService.getFileById(fileId);
    }
    
    /**
     * 上传文件
     */
    @PostMapping("/upload")
    @RequirePermission("file:upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file,
                             @RequestParam("name") String name,
                             @RequestParam("category") String category,
                             @RequestParam(value = "description", required = false) String description,
                             @RequestParam(value = "tags", required = false) String tags,
                             @RequestParam(value = "version", required = false, defaultValue = "1.0") String version,
                             Authentication authentication) {
        
        try {
            log.info("Uploading file: name={}, originalName={}, size={}, category={}", 
                    name, file.getOriginalFilename(), file.getSize(), category);
            
            UploadFileRequest request = new UploadFileRequest();
            request.setName(name);
            request.setCategory(category);
            request.setDescription(description);
            request.setTags(tags);
            request.setVersion(version);
            
            String username = authentication.getName();
            FileDTO result = fileService.uploadFile(request, file, username);
            
            log.info("File uploaded successfully: fileId={}", result.getFileId());
            return ResponseEntity.ok(result);
            
        } catch (BusinessException e) {
            log.warn("File upload failed with business error: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getErrorCode().name());
            errorResponse.put("message", e.getMessage());
            errorResponse.put("status", 400);
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            log.error("File upload failed with unexpected error", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "INTERNAL_SERVER_ERROR");
            errorResponse.put("message", "服务器内部错误: " + e.getMessage());
            errorResponse.put("status", 500);
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * 删除文件
     */
    @DeleteMapping("/{fileId}")
    @RequirePermission("file:delete")
    public ResponseEntity<Void> deleteFile(@PathVariable String fileId) {
        fileService.deleteFile(fileId);
        return ResponseEntity.ok().build();
    }
    
    /**
     * 下载文件
     */
    @GetMapping("/{fileId}/download")
    @RequirePermission("file:download")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileId) {
        FileDTO file = fileService.getFileById(fileId);
        byte[] content = fileService.getFileContent(fileId);
        
        ByteArrayResource resource = new ByteArrayResource(content);
        
        // 编码文件名以支持中文
        String encodedFilename;
        try {
            encodedFilename = URLEncoder.encode(file.getOriginalName(), StandardCharsets.UTF_8.toString())
                .replaceAll("\\+", "%20");
        } catch (Exception e) {
            encodedFilename = file.getOriginalName();
        }
        
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, 
                   "attachment; filename=\"" + file.getOriginalName() + "\"; filename*=UTF-8''" + encodedFilename)
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .contentLength(content.length)
            .body(resource);
    }
    
    /**
     * 获取文件分类列表
     */
    @GetMapping("/categories")
    @RequirePermission("file:list")
    public List<String> getCategories() {
        return fileService.getCategories();
    }
    
    /**
     * 获取分类统计信息
     */
    @GetMapping("/stats")
    @RequirePermission("file:list")
    public List<FileCategoryStats> getCategoryStats() {
        return fileService.getCategoryStats();
    }
    
    /**
     * 验证文件完整性
     */
    @PostMapping("/{fileId}/verify")
    @RequirePermission("file:view")
    public ResponseEntity<Boolean> verifyFile(@PathVariable String fileId,
                                            @RequestParam String checksum) {
        boolean isValid = fileService.verifyFileIntegrity(fileId, checksum);
        return ResponseEntity.ok(isValid);
    }
    
    /**
     * Agent下载文件接口（用于文件传输任务）
     */
    @GetMapping("/{fileId}/download-for-agent")
    public ResponseEntity<Resource> downloadFileForAgent(@PathVariable String fileId,
                                                        @RequestHeader("Agent-ID") String agentId) {
        // TODO: 验证Agent权限和下载令牌
        log.info("Agent {} 请求下载文件: {}", agentId, fileId);
        
        FileDTO file = fileService.getFileById(fileId);
        byte[] content = fileService.getFileContent(fileId);
        
        ByteArrayResource resource = new ByteArrayResource(content);
        
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getOriginalName() + "\"")
            .header("X-File-MD5", file.getMd5())
            .header("X-File-SHA256", file.getSha256())
            .header("X-File-Size", String.valueOf(file.getFileSize()))
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .contentLength(content.length)
            .body(resource);
    }
}