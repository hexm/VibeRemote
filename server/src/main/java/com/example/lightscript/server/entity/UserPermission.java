package com.example.lightscript.server.entity;

import lombok.Data;

import javax.persistence.*;

@Entity
@Table(name = "user_permission")
@Data
public class UserPermission {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    @Column(name = "permission_code", length = 50, nullable = false)
    private String permissionCode;
}
