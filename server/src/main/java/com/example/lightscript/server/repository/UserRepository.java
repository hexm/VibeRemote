package com.example.lightscript.server.repository;

import com.example.lightscript.server.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    Optional<User> findByUsername(String username);
    
    boolean existsByUsername(String username);
    
    Page<User> findByStatus(String status, Pageable pageable);
    
    Page<User> findByUsernameContainingOrRealNameContaining(
        String username, String realName, Pageable pageable);
    
    Page<User> findByStatusAndUsernameContainingOrStatusAndRealNameContaining(
        String status1, String username, String status2, String realName, Pageable pageable);
    
    long countByStatus(String status);
}
