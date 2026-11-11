package com.ticketlock.security;

import com.ticketlock.user.entity.Role;
import com.ticketlock.user.entity.User;
import com.ticketlock.user.repository.UserRepository;
import com.ticketlock.user.service.UserService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserOwnershipTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired UserService service;

    @Test
    void customerCanReadSelfButNotAnotherUserAndNeverReceivesPasswordHash() throws Exception {
        User owner = account(Role.CUSTOMER);
        User other = account(Role.CUSTOMER);
        mvc.perform(get("/api/users/me").with(user(owner.getEmail()).roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(owner.getId().toString()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(get("/api/users/" + owner.getId()).with(user(owner.getEmail()).roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(owner.getEmail()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(get("/api/users/" + other.getId()).with(user(owner.getEmail()).roles("CUSTOMER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/users/" + UUID.randomUUID()).with(user(owner.getEmail()).roles("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminDoesNotAutomaticallyOwnOtherProfiles() throws Exception {
        User admin = account(Role.ADMIN);
        User customer = account(Role.CUSTOMER);
        mvc.perform(get("/api/users/" + customer.getId()).with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotReadProfiles() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "unknown@example.test", roles = "CUSTOMER")
    void serviceIsProtectedEvenWithoutController() {
        assertThrows(AccessDeniedException.class, () -> service.profile(UUID.randomUUID()));
    }

    private User account(Role role) {
        return users.saveAndFlush(new User("ownership-" + UUID.randomUUID() + "@example.test",
                "test-only-not-a-login-hash", role));
    }
}
