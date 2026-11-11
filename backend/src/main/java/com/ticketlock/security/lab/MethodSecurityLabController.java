package com.ticketlock.security.lab;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MethodSecurityLabController {
    private final MethodSecurityLabService service;

    public MethodSecurityLabController(MethodSecurityLabService service) {
        this.service = service;
    }

    // Không nằm dưới /api/admin/**: HTTP chỉ yêu cầu authenticated().
    @GetMapping("/api/method-security/admin-message")
    public Map<String, String> adminMessage() {
        return Map.of("message", service.adminMessage());
    }
}
