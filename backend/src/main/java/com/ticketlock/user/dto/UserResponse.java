package com.ticketlock.user.dto;

import com.ticketlock.user.entity.Role;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String email, Role role, Instant createdAt) {
}
