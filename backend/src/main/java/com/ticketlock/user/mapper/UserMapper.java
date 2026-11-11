package com.ticketlock.user.mapper;

import com.ticketlock.user.dto.UserResponse;
import com.ticketlock.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {
    public UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole(), user.getCreatedAt());
    }
}
