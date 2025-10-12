package com.example.lightscript.server.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一错误响应格式
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    
    /**
     * 操作是否成功
     */
    private boolean success;
    
    /**
     * 错误码
     */
    private int code;
    
    /**
     * 错误信息
     */
    private String message;
    
    /**
     * 附加数据
     */
    private Object data;
    
    /**
     * 时间戳
     */
    @Builder.Default
    private long timestamp = System.currentTimeMillis();
    
    /**
     * 创建成功响应
     */
    public static ErrorResponse success() {
        return ErrorResponse.builder()
                .success(true)
                .code(ErrorCode.SUCCESS.getCode())
                .message(ErrorCode.SUCCESS.getMessage())
                .build();
    }
    
    /**
     * 创建成功响应（带数据）
     */
    public static ErrorResponse success(Object data) {
        return ErrorResponse.builder()
                .success(true)
                .code(ErrorCode.SUCCESS.getCode())
                .message(ErrorCode.SUCCESS.getMessage())
                .data(data)
                .build();
    }
    
    /**
     * 创建错误响应
     */
    public static ErrorResponse error(ErrorCode errorCode) {
        return ErrorResponse.builder()
                .success(false)
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();
    }
    
    /**
     * 创建错误响应（带消息）
     */
    public static ErrorResponse error(ErrorCode errorCode, String message) {
        return ErrorResponse.builder()
                .success(false)
                .code(errorCode.getCode())
                .message(message)
                .build();
    }
}
