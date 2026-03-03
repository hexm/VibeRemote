package com.example.lightscript.server.repository;

import com.example.lightscript.server.entity.UserPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserPermissionRepository extends JpaRepository<UserPermission, Long> {
    
    List<UserPermission> findByUserId(Long userId);
    
    @Modifying
    @Query("DELETE FROM UserPermission up WHERE up.userId = ?1")
    void deleteByUserId(Long userId);
    
    @Query("SELECT up.permissionCode FROM UserPermission up WHERE up.userId = ?1")
    List<String> findPermissionCodesByUserId(Long userId);
}
