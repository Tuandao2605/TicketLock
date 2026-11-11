package com.ticketlock.user.controller;

import com.ticketlock.user.dto.UserResponse;
import com.ticketlock.user.service.UserService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @GetMapping("/me")
    public UserResponse me() {
        return users.currentProfile();
    }

    @GetMapping("/{userId}")
    public UserResponse profile(@PathVariable("userId") UUID userId) {
        return users.profile(userId);
    }
}
