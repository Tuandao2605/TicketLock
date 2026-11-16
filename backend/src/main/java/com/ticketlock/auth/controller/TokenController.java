package com.ticketlock.auth.controller;

import com.ticketlock.auth.dto.LoginRequest;
import com.ticketlock.auth.dto.LogoutRequest;
import com.ticketlock.auth.dto.RefreshRequest;
import com.ticketlock.auth.dto.TokenResponse;
import com.ticketlock.auth.service.AccessTokenService;
import com.ticketlock.auth.service.RefreshTokenService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("jwt")
public class TokenController {
    private final AccessTokenService tokens;
    private final RefreshTokenService refreshTokens;

    public TokenController(AccessTokenService tokens, RefreshTokenService refreshTokens) {
        this.tokens = tokens;
        this.refreshTokens = refreshTokens;
    }

    @PostMapping(value = "/api/token/login", consumes = "application/json")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        var user = tokens.authenticate(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(refreshTokens.startFamily(user));
    }

    @PostMapping(value = "/api/token/refresh", consumes = "application/json")
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshRequest request) {
        return refreshTokens.rotate(request.refreshToken())
                .<ResponseEntity<?>>map(response -> ResponseEntity.ok().cacheControl(CacheControl.noStore())
                        .body(response))
                .orElseGet(() -> ResponseEntity.status(401).cacheControl(CacheControl.noStore())
                        .body(Map.of("message", "Refresh token không hợp lệ hoặc đã hết hạn.")));
    }

    @PostMapping(value = "/api/token/logout", consumes = "application/json")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        refreshTokens.revokeFamilyFor(request.refreshToken());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, String>> invalidCredentials() {
        return ResponseEntity.status(401).cacheControl(CacheControl.noStore())
                .body(Map.of("message", "Email hoặc mật khẩu không chính xác."));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> invalidRequest() {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
                .body(Map.of("message", "Dữ liệu không hợp lệ."));
    }
}
