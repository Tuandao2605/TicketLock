package com.ticketlock.user;

import com.ticketlock.user.entity.Role;
import com.ticketlock.user.entity.User;
import com.ticketlock.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class UserUuidTests {
    @Autowired
    private UserRepository users;

    @Test
    void persistsAndFindsUserWithUuidVersionSeven() {
        var account = users.saveAndFlush(new User(
                "uuid-test-" + UUID.randomUUID() + "@example.test", "test-only-not-a-login-hash", Role.CUSTOMER));
        assertNotNull(account.getId());
        assertEquals(7, account.getId().version());
        assertEquals(2, account.getId().variant());
        assertEquals(account.getEmail(), users.findById(account.getId()).orElseThrow().getEmail());
    }
}
