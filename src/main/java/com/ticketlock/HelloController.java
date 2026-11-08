package com.ticketlock;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

@RestController
public class HelloController {

    @GetMapping("/hello")
    public String hello() {
        return "Chào mừng bạn đến với TicketLock!";
    }

    @GetMapping("/public/hello")
    public String publicHello() {
        return "Đây là trang công khai!";
    }

    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        return Map.of(
                "username", authentication.getName(),
                "authorities", authentication.getAuthorities().stream()
                        .map(authority -> authority.getAuthority())
                        .toList());
    }
}
