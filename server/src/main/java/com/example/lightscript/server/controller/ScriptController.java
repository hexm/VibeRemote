package com.example.lightscript.server.controller;

import com.example.lightscript.server.model.ScriptModels.*;
import com.example.lightscript.server.security.RequirePermission;
import com.example.lightscript.server.service.ScriptService;
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

@Slf4j
@RestController
@RequestMapping("/api/web/scripts")
@RequiredArgsConstructor
public class ScriptController {
    
    private final ScriptService scriptService;
    
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
     * 获取脚本列表（用于任务创建）
     */
    @GetMapping("/for-task")
    @RequirePermission("script:list")
    public List<ScriptForTaskDTO> getScriptsForTask() {
        return scriptService.getScriptsForTask();
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
     * 创建脚本（手动录入）
     */
    @PostMapping
    @RequirePermission("script:create")
    public ScriptDTO createScript(@Valid @RequestBody CreateScriptRequest request,
                                 Authentication authentication) {
        String username = authentication.getName();
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
     * 更新脚本
     */
    @PutMapping("/{scriptId}")
    @RequirePermission("script:edit")
    public ScriptDTO updateScript(@PathVariable String scriptId,
                                 @Valid @RequestBody UpdateScriptRequest request) {
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
     * 获取脚本内容（用于查看）
     */
    @GetMapping("/{scriptId}/content")
    @RequirePermission("script:view")
    public ResponseEntity<String> getScriptContent(@PathVariable String scriptId) {
        String content = scriptService.getScriptContent(scriptId);
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_PLAIN)
            .body(content);
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