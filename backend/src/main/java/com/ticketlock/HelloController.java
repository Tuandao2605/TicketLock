package com.ticketlock;

import java.util.Map;
import com.ticketlock.user.service.UserService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

@RestController
public class HelloController {
    private final UserService users;

    public HelloController(UserService users) { this.users = users; }

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
        var profile = users.currentProfile();
        return Map.of(
                "userId", profile.id().toString(),
                "username", profile.email(),
                "authorities", authentication.getAuthorities().stream()
                        .map(authority -> authority.getAuthority())
                        .toList());
    }

    @GetMapping("/blocked")
    public String blocked() {
        return "Bạn không nên nhìn thấy nội dung này!";
    }

    @GetMapping("/api/admin/hello")
    public String adminHello() {
        return "Xin chào Admin!";
    }

    @GetMapping("/api/customer/hello")
    public String customerHello() {
        return "Xin chào Customer!";
    }
}
