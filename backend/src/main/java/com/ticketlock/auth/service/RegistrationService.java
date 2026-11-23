package com.ticketlock.auth.service;

import com.ticketlock.auth.dto.RegisterRequest;
import com.ticketlock.user.api.UserAccounts;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {
    private final UserAccounts users;
    private final PasswordEncoder encoder;

    public RegistrationService(UserAccounts users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Transactional
    public void register(RegisterRequest request) {
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        // BCrypt giới hạn theo byte, không phải số ký tự Unicode.
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Mật khẩu không được vượt quá 72 ký tự.");
        }
        users.createCustomer(email, encoder.encode(request.password()));
    }
}
