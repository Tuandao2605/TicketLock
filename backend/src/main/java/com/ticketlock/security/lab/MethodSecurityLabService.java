package com.ticketlock.security.lab;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class MethodSecurityLabService {

    @PreAuthorize("hasRole('ADMIN')")
    public String adminMessage() {
        return "ADMIN đã vượt qua kiểm tra quyền tại tầng service.";
    }
}
