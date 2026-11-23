package com.ticketlock.user.api;

import java.util.Optional;
import java.util.UUID;

/** Internal contract for authentication; credentials must never be returned to HTTP clients. */
public interface UserAccounts {
    Optional<Account> findByEmail(String email);
    Optional<Account> findById(UUID id);
    void createCustomer(String email, String passwordHash);

    record Account(UUID id, String email, String passwordHash, String role, boolean enabled) { }
}
