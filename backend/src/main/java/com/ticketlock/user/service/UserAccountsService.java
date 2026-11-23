package com.ticketlock.user.service;

import com.ticketlock.user.api.UserAccounts;
import com.ticketlock.user.entity.Role;
import com.ticketlock.user.entity.User;
import com.ticketlock.user.repository.UserRepository;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountsService implements UserAccounts {
    private final UserRepository users;
    public UserAccountsService(UserRepository users) { this.users = users; }

    @Override
    @Transactional(readOnly = true)
    public Optional<Account> findByEmail(String email) {
        return users.findByEmail(email.strip().toLowerCase(Locale.ROOT)).map(UserAccountsService::account);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Account> findById(UUID id) { return users.findById(id).map(UserAccountsService::account); }

    @Override
    @Transactional
    public void createCustomer(String email, String passwordHash) {
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(normalized)) throw new IllegalArgumentException("Email này đã được sử dụng. Vui lòng chọn email khác.");
        users.saveAndFlush(new User(normalized, passwordHash, Role.CUSTOMER));
    }

    private static Account account(User user) {
        return new Account(user.getId(), user.getEmail(), user.getPasswordHash(), user.getRole().name(), user.isEnabled());
    }
}
