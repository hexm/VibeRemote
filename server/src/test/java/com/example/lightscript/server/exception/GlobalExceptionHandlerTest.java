package com.example.lightscript.server.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * GlobalExceptionHandler单元测试
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler globalExceptionHandler;

    @Test
    void testHandleBusinessException() {
        // Given
        BusinessException exception = new BusinessException(ErrorCode.AGENT_NOT_FOUND, "test-agent-id");

        // When
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleBusinessException(exception);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertFalse(errorResponse.isSuccess());
        assertEquals(ErrorCode.AGENT_NOT_FOUND.getCode(), errorResponse.getCode());
        assertTrue(errorResponse.getMessage().contains("test-agent-id"));
    }

    @Test
    void testHandleBusinessExceptionWithAuthError() {
        // Given
        BusinessException exception = new BusinessException(ErrorCode.UNAUTHORIZED);

        // When
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleBusinessException(exception);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertFalse(errorResponse.isSuccess());
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), errorResponse.getCode());
    }

    @Test
    void testHandleValidationException() {
        // Given
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("testObject", "testField", "测试错误信息");
        
        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getAllErrors()).thenReturn(Collections.singletonList(fieldError));

        // When
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleValidationException(exception);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertFalse(errorResponse.isSuccess());
        assertEquals(ErrorCode.INVALID_PARAMETER.getCode(), errorResponse.getCode());
        assertEquals("参数验证失败", errorResponse.getMessage());
    }

    @Test
    void testHandleIllegalArgumentException() {
        // Given
        IllegalArgumentException exception = new IllegalArgumentException("非法参数");

        // When
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleIllegalArgumentException(exception);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertFalse(errorResponse.isSuccess());
        assertEquals(ErrorCode.INVALID_PARAMETER.getCode(), errorResponse.getCode());
        assertEquals("非法参数", errorResponse.getMessage());
    }

    @Test
    void testHandleRuntimeException() {
        // Given
        RuntimeException exception = new RuntimeException("运行时异常");

        // When
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleRuntimeException(exception);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertFalse(errorResponse.isSuccess());
        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), errorResponse.getCode());
        assertEquals("系统内部错误", errorResponse.getMessage());
    }

    @Test
    void testHandleGenericException() {
        // Given
        Exception exception = new Exception("通用异常");

        // When
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleException(exception);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertFalse(errorResponse.isSuccess());
        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), errorResponse.getCode());
        assertEquals("系统内部错误", errorResponse.getMessage());
    }
}
