package com.ticketlock.user.service;

import com.ticketlock.user.dto.UserResponse;
import com.ticketlock.user.mapper.UserMapper;
import com.ticketlock.user.repository.UserRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository users;
    private final UserMapper mapper;

    public UserService(UserRepository users, UserMapper mapper) {
        this.users = users;
        this.mapper = mapper;
    }

    @PreAuthorize("isAuthenticated()")
    public UserResponse currentProfile() {
        String principal = SecurityContextHolder.getContext().getAuthentication().getName();
        UUID userId;
        try {
            userId = UUID.fromString(principal);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return users.findById(userId)
                .filter(user -> user.isEnabled())
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PreAuthorize("@userSecurity.isSelf(#userId, authentication)")
    public UserResponse profile(@P("userId") UUID userId) {
        return users.findById(userId).map(mapper::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
