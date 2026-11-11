package com.ticketlock.auth.controller;

import com.ticketlock.auth.dto.LoginRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestController
public class LoginController {
    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy sessionStrategy;
    private final SecurityContextRepository contextRepository;

    public LoginController(AuthenticationManager authenticationManager,
            SessionAuthenticationStrategy sessionStrategy, SecurityContextRepository contextRepository) {
        this.authenticationManager = authenticationManager;
        this.sessionStrategy = sessionStrategy;
        this.contextRepository = contextRepository;
    }

    @PostMapping(value = "/api/auth/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> login(@Valid @RequestBody LoginRequest credentials,
            HttpServletRequest request, HttpServletResponse response) {
        if (credentials.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            return ResponseEntity.badRequest().build();
        }
        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(credentials.email(), credentials.password()));

        // Controller login phải thực hiện các bước mà form-login filter từng làm.
        sessionStrategy.onAuthentication(authentication, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, String>> authenticationFailed() {
        return ResponseEntity.status(401).body(Map.of("message", "Email hoặc mật khẩu không chính xác."));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> invalidRequest() {
        return ResponseEntity.badRequest().body(Map.of("message", "Dữ liệu đăng nhập không hợp lệ."));
    }
}
