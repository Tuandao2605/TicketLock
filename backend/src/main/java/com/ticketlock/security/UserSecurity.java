package com.ticketlock.security;

import com.ticketlock.user.repository.UserRepository;
import java.util.UUID;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("userSecurity")
public class UserSecurity {
    private final UserRepository users;

    public UserSecurity(UserRepository users) {
        this.users = users;
    }

    public boolean isSelf(UUID userId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return false;
        }
        // Đối chiếu với tài khoản của principal, không tin UUID từ client.
        try {
            UUID principalId = UUID.fromString(authentication.getName());
            return principalId.equals(userId) && users.findById(principalId)
                    .filter(user -> user.isEnabled()).isPresent();
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
