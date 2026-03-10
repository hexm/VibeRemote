package com.example.lightscript.server.repository;

import com.example.lightscript.server.entity.Script;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScriptRepository extends JpaRepository<Script, Long> {
    
    /**
     * 根据脚本ID查找
     */
    Optional<Script> findByScriptId(String scriptId);
    
    /**
     * 检查脚本ID是否存在
     */
    boolean existsByScriptId(String scriptId);
    
    /**
     * 检查脚本名称是否存在
     */
    boolean existsByName(String name);
    
    /**
     * 根据类型查找脚本
     */
    List<Script> findByType(String type);
    
    /**
     * 根据创建者查找脚本
     */
    List<Script> findByCreatedBy(String createdBy);
    
    /**
     * 根据是否上传查找脚本
     */
    List<Script> findByIsUploaded(Boolean isUploaded);
    
    /**
     * 分页查询脚本，支持名称和描述模糊搜索
     */
    @Query("SELECT s FROM Script s WHERE " +
           "(:keyword IS NULL OR s.name LIKE %:keyword% OR s.description LIKE %:keyword%) AND " +
           "(:type IS NULL OR s.type = :type) " +
           "ORDER BY s.createdAt DESC")
    Page<Script> findScriptsWithFilters(@Param("keyword") String keyword, 
                                       @Param("type") String type, 
                                       Pageable pageable);
    
    /**
     * 获取下一个可用的脚本ID
     */
    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(s.scriptId, 2) AS int)), 0) + 1 FROM Script s WHERE s.scriptId LIKE 'S%'")
    Integer getNextScriptIdNumber();
}