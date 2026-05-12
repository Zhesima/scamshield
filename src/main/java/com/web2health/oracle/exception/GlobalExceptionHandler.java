package com.web2health.oracle.exception;

import com.web2health.oracle.dto.response.ApiError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ProjectNotFoundException.class)
    public ResponseEntity<ApiError> handleProjectNotFound(ProjectNotFoundException ex) {
        log.warn("项目未找到: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.builder()
                        .code("PROJECT_NOT_FOUND")
                        .message(ex.getMessage())
                        .build());
    }

    @ExceptionHandler(AllSourcesFailedException.class)
    public ResponseEntity<ApiError> handleAllSourcesFailed(AllSourcesFailedException ex) {
        log.error("所有数据源采集失败: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.builder()
                        .code("COLLECTION_FAILED")
                        .message("所有数据源采集失败，无法计算健康评分")
                        .build());
    }

    /** 让 ResponseStatusException（admin 鉴权 / 入参校验）保留原始状态码 */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException ex) {
        log.warn("请求被拒绝: status={}, reason={}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode())
                .body(ApiError.builder()
                        .code(ex.getStatusCode().toString())
                        .message(ex.getReason() != null ? ex.getReason() : "请求被拒绝")
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneral(Exception ex) {
        log.error("未预期异常", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.builder()
                        .code("INTERNAL_ERROR")
                        .message("服务器内部错误")
                        .build());
    }
}
