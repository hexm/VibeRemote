package com.example.lightscript.server.controller;

import com.example.lightscript.server.model.ScriptModels.*;
import com.example.lightscript.server.security.RequirePermission;
import com.example.lightscript.server.service.ScriptService;
import com.example.lightscript.server.service.WebEncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Slf4j
@RestController
@RequestMapping("/api/web/scripts")
@RequiredArgsConstructor
public class ScriptController {
    
    private final ScriptService scriptService;
    private final WebEncryptionService webEncryptionService;
    
    /**
     * 分页查询脚本列表
     */
    @GetMapping
    @RequirePermission("script:list")
    public Page<ScriptDTO> getScripts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        
        ScriptListRequest request = new ScriptListRequest();
        request.setKeyword(keyword);
        request.setType(type);
        request.setPage(page);
        request.setSize(size);
        
        return scriptService.getScripts(request);
    }
    
    /**
     * 获取脚本列表（用于任务创建），脚本内容加密返回
     */
    @GetMapping("/for-task")
    @RequirePermission("script:list")
    public List<ScriptForTaskDTO> getScriptsForTask(Authentication authentication) {
        List<ScriptForTaskDTO> scripts = scriptService.getScriptsForTask();
        if (authentication != null) {
            String username = authentication.getName();
            if (webEncryptionService.hasSessionKey(username)) {
                scripts.forEach(dto -> {
                    if (dto.getContent() != null && !dto.getContent().isEmpty()) {
                        try {
                            dto.setContent(webEncryptionService.encrypt(username, dto.getContent()));
                        } catch (Exception e) {
                            log.warn("[WebEncryption] for-task 脚本内容加密失败: {}", e.getMessage());
                        }
                    }
                });
            }
        }
        return scripts;
    }
    
    /**
     * 获取脚本详情
     */
    @GetMapping("/{scriptId}")
    @RequirePermission("script:view")
    public ScriptDTO getScript(@PathVariable String scriptId) {
        return scriptService.getScriptById(scriptId);
    }
    
    /**
     * 创建脚本（手动录入），解密 content 字段
     */
    @PostMapping
    @RequirePermission("script:create")
    public ScriptDTO createScript(@Valid @RequestBody CreateScriptRequest request,
                                 Authentication authentication) {
        String username = authentication.getName();
        if (request.getContent() != null && webEncryptionService.hasSessionKey(username)) {
            try {
                request.setContent(webEncryptionService.decrypt(username, request.getContent()));
            } catch (Exception e) {
                log.warn("[WebEncryption] createScript content 解密失败，使用原始内容: {}", e.getMessage());
            }
        }
        return scriptService.createScript(request, username);
    }
    
    /**
     * 上传脚本文件
     */
    @PostMapping("/upload")
    @RequirePermission("script:create")
    public ScriptDTO uploadScript(@RequestParam("file") MultipartFile file,
                                 @RequestParam("name") String name,
                                 @RequestParam("type") String type,
                                 @RequestParam(value = "description", required = false) String description,
                                 Authentication authentication) {
        
        UploadScriptRequest request = new UploadScriptRequest();
        request.setName(name);
        request.setType(type);
        request.setDescription(description);
        
        String username = authentication.getName();
        return scriptService.uploadScript(request, file, username);
    }
    
    /**
     * 更新脚本，解密 content 字段
     */
    @PutMapping("/{scriptId}")
    @RequirePermission("script:edit")
    public ScriptDTO updateScript(@PathVariable String scriptId,
                                 @Valid @RequestBody UpdateScriptRequest request,
                                 Authentication authentication) {
        if (request.getContent() != null && authentication != null
                && webEncryptionService.hasSessionKey(authentication.getName())) {
            try {
                request.setContent(webEncryptionService.decrypt(authentication.getName(), request.getContent()));
            } catch (Exception e) {
                log.warn("[WebEncryption] updateScript content 解密失败，使用原始内容: {}", e.getMessage());
            }
        }
        return scriptService.updateScript(scriptId, request);
    }
    
    /**
     * 重新上传脚本文件
     */
    @PostMapping("/{scriptId}/reupload")
    @RequirePermission("script:edit")
    public ScriptDTO reuploadScript(@PathVariable String scriptId,
                                   @RequestParam("file") MultipartFile file,
                                   @RequestParam("name") String name,
                                   @RequestParam("type") String type,
                                   @RequestParam(value = "description", required = false) String description) {
        
        UploadScriptRequest request = new UploadScriptRequest();
        request.setName(name);
        request.setType(type);
        request.setDescription(description);
        
        return scriptService.reuploadScript(scriptId, request, file);
    }
    
    /**
     * 删除脚本
     */
    @DeleteMapping("/{scriptId}")
    @RequirePermission("script:delete")
    public ResponseEntity<Void> deleteScript(@PathVariable String scriptId) {
        scriptService.deleteScript(scriptId);
        return ResponseEntity.ok().build();
    }
    
    /**
     * 获取脚本内容（用于查看），加密返回
     */
    @GetMapping("/{scriptId}/content")
    @RequirePermission("script:view")
    public ResponseEntity<Map<String, Object>> getScriptContent(@PathVariable String scriptId,
                                                                 Authentication authentication) {
        String content = scriptService.getScriptContent(scriptId);
        Map<String, Object> result = new HashMap<>();
        if (authentication != null && webEncryptionService.hasSessionKey(authentication.getName())) {
            try {
                result.put("content", webEncryptionService.encrypt(authentication.getName(), content));
                result.put("encrypted", true);
            } catch (Exception e) {
                log.warn("[WebEncryption] getScriptContent 加密失败，返回明文: {}", e.getMessage());
                result.put("content", content);
                result.put("encrypted", false);
            }
        } else {
            result.put("content", content);
            result.put("encrypted", false);
        }
        return ResponseEntity.ok(result);
    }
    
    /**
     * 下载脚本文件
     */
    @GetMapping("/{scriptId}/download")
    @RequirePermission("script:view")
    public ResponseEntity<String> downloadScript(@PathVariable String scriptId) {
        ScriptDTO script = scriptService.getScriptById(scriptId);
        String content = scriptService.getScriptContent(scriptId);
        
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, 
                   "attachment; filename=\"" + script.getFilename() + "\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(content);
    }
}