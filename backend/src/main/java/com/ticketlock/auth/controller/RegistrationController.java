package com.ticketlock.auth.controller;

import com.ticketlock.auth.dto.RegisterRequest;
import com.ticketlock.auth.service.RegistrationService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class RegistrationController {
    private final RegistrationService registration;

    public RegistrationController(RegistrationService registration) {
        this.registration = registration;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@Valid @RequestBody RegisterRequest request) {
        registration.register(request);
        return ResponseEntity.status(201).body(Map.of("message", "Đăng ký thành công. Bạn có thể đăng nhập."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> invalidInput() {
        return ResponseEntity.badRequest().body(Map.of("message", "Nhập email hợp lệ và mật khẩu từ 8 đến 72 ký tự (tối đa 72 byte UTF-8)."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalidRegistration(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> duplicateEmail() {
        return ResponseEntity.status(409).body(Map.of("message", "Không thể tạo tài khoản. Email có thể đã được sử dụng."));
    }
}
