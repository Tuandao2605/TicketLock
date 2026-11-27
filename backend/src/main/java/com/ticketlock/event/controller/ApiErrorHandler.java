package com.ticketlock.event.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.NoSuchElementException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = EventController.class)
class ApiErrorHandler {
    record ApiError(String code, String message, String traceId) { }
    private ResponseEntity<ApiError> error(HttpServletRequest request, HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiError(code, message, (String) request.getAttribute("ticketlock.traceId")));
    }
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ApiError> invalid(Exception ex, HttpServletRequest r) { return error(r, HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Dữ liệu không hợp lệ."); }
    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<ApiError> notFound(Exception ex, HttpServletRequest r) { return error(r, HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy tài nguyên."); }
    @ExceptionHandler({IllegalStateException.class, DataIntegrityViolationException.class})
    ResponseEntity<ApiError> conflict(Exception ex, HttpServletRequest r) { return error(r, HttpStatus.CONFLICT, "STATE_CONFLICT", "Trạng thái không cho phép thao tác."); }
}
