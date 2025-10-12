package com.example.lightscript.server.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 统一处理系统中的各种异常，返回标准的错误响应格式
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        log.warn("Business exception: {}", e.getMessage());
        
        ErrorResponse response = ErrorResponse.builder()
                .success(false)
                .code(e.getErrorCode().getCode())
                .message(e.getMessage())
                .build();
        
        // 根据错误类型返回相应的HTTP状态码
        HttpStatus status = getHttpStatusByErrorCode(e.getErrorCode());
        return ResponseEntity.status(status).body(response);
    }
    
    /**
     * 处理参数验证异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        log.warn("Validation exception: {}", e.getMessage());
        
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        ErrorResponse response = ErrorResponse.builder()
                .success(false)
                .code(ErrorCode.INVALID_PARAMETER.getCode())
                .message("参数验证失败")
                .data(errors)
                .build();
        
        return ResponseEntity.badRequest().body(response);
    }
    
    /**
     * 处理绑定异常
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBindException(BindException e) {
        log.warn("Bind exception: {}", e.getMessage());
        
        String errorMessage = e.getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        
        ErrorResponse response = ErrorResponse.builder()
                .success(false)
                .code(ErrorCode.INVALID_PARAMETER.getCode())
                .message("参数绑定失败: " + errorMessage)
                .build();
        
        return ResponseEntity.badRequest().body(response);
    }
    
    /**
     * 处理约束违反异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(ConstraintViolationException e) {
        log.warn("Constraint violation exception: {}", e.getMessage());
        
        String errorMessage = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
        
        ErrorResponse response = ErrorResponse.builder()
                .success(false)
                .code(ErrorCode.INVALID_PARAMETER.getCode())
                .message("约束验证失败: " + errorMessage)
                .build();
        
        return ResponseEntity.badRequest().body(response);
    }
    
    /**
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("Illegal argument exception: {}", e.getMessage());
        
        ErrorResponse response = ErrorResponse.builder()
                .success(false)
                .code(ErrorCode.INVALID_PARAMETER.getCode())
                .message(e.getMessage())
                .build();
        
        return ResponseEntity.badRequest().body(response);
    }
    
    /**
     * 处理运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException e) {
        log.error("Runtime exception: ", e);
        
        ErrorResponse response = ErrorResponse.builder()
                .success(false)
                .code(ErrorCode.SYSTEM_ERROR.getCode())
                .message("系统内部错误")
                .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    /**
     * 处理通用异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("Unexpected exception: ", e);
        
        ErrorResponse response = ErrorResponse.builder()
                .success(false)
                .code(ErrorCode.SYSTEM_ERROR.getCode())
                .message("系统内部错误")
                .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    /**
     * 根据错误码获取HTTP状态码
     */
    private HttpStatus getHttpStatusByErrorCode(ErrorCode errorCode) {
        int code = errorCode.getCode();
        
        if (code >= 2000 && code < 3000) {
            // 认证授权错误
            return HttpStatus.UNAUTHORIZED;
        } else if (code >= 3000 && code < 4000) {
            // Agent相关错误
            return HttpStatus.BAD_REQUEST;
        } else if (code >= 4000 && code < 5000) {
            // 任务相关错误
            return HttpStatus.BAD_REQUEST;
        } else if (code >= 1000 && code < 2000) {
            // 通用错误
            return HttpStatus.BAD_REQUEST;
        } else {
            // 其他错误
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }
}
