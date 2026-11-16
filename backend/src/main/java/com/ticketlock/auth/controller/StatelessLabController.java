package com.ticketlock.auth.controller;

import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("jwt")
public class StatelessLabController {
    @GetMapping("/api/stateless/me")
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("userId", jwt.getSubject(), "roles", jwt.getClaimAsStringList("roles"),
                "expiresAt", jwt.getExpiresAt());
    }

    @GetMapping("/api/stateless/admin/hello")
    public Map<String, String> admin() {
        return Map.of("message", "ADMIN truy cập bằng JWT thành công.");
    }
}
