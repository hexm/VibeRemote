package com.example.lightscript.server.repository;

import com.example.lightscript.server.entity.File;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileRepository extends JpaRepository<File, Long> {
    
    /**
     * 根据文件ID查找文件
     */
    Optional<File> findByFileId(String fileId);
    
    /**
     * 检查文件名是否存在
     */
    boolean existsByName(String name);
    
    /**
     * 检查文件ID是否存在
     */
    boolean existsByFileId(String fileId);
    
    /**
     * 根据MD5查找文件（用于去重）
     */
    Optional<File> findByMd5(String md5);
    
    /**
     * 根据SHA256查找文件（用于去重）
     */
    Optional<File> findBySha256(String sha256);
    
    /**
     * 根据分类查找文件
     */
    List<File> findByCategory(String category);
    
    /**
     * 根据上传者查找文件
     */
    List<File> findByUploadBy(String uploadBy);
    
    /**
     * 分页查询文件，支持关键词和分类过滤
     */
    @Query("SELECT f FROM File f WHERE " +
           "(:keyword IS NULL OR f.name LIKE %:keyword% OR f.description LIKE %:keyword% OR f.tags LIKE %:keyword%) AND " +
           "(:category IS NULL OR f.category = :category) " +
           "ORDER BY f.createdAt DESC")
    Page<File> findFilesWithFilters(@Param("keyword") String keyword, 
                                   @Param("category") String category, 
                                   Pageable pageable);
    
    /**
     * 获取所有文件分类
     */
    @Query("SELECT DISTINCT f.category FROM File f WHERE f.category IS NOT NULL ORDER BY f.category")
    List<String> findAllCategories();
    
    /**
     * 统计各分类的文件数量
     */
    @Query("SELECT f.category, COUNT(f), SUM(f.fileSize) FROM File f GROUP BY f.category")
    List<Object[]> getCategoryStats();
    
    /**
     * 获取下一个文件ID编号
     */
    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(f.fileId, 2) AS int)), 0) + 1 FROM File f WHERE f.fileId LIKE 'F%'")
    Integer getNextFileIdNumber();
    
    /**
     * 根据文件大小范围查询
     */
    @Query("SELECT f FROM File f WHERE f.fileSize BETWEEN :minSize AND :maxSize ORDER BY f.createdAt DESC")
    List<File> findByFileSizeBetween(@Param("minSize") Long minSize, @Param("maxSize") Long maxSize);
    
    /**
     * 查找重复文件（相同MD5或SHA256）
     */
    @Query("SELECT f FROM File f WHERE f.md5 = :checksum OR f.sha256 = :checksum")
    List<File> findDuplicateFiles(@Param("checksum") String checksum);
}