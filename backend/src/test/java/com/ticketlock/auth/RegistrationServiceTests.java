package com.ticketlock.auth;

import com.ticketlock.SecurityConfig;
import com.ticketlock.auth.dto.RegisterRequest;
import com.ticketlock.auth.service.RegistrationService;
import com.ticketlock.user.entity.Role;
import com.ticketlock.user.entity.User;
import com.ticketlock.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegistrationServiceTests {
    private final UserRepository users = mock(UserRepository.class);
    private final org.springframework.security.crypto.password.PasswordEncoder encoder =
            new SecurityConfig().passwordEncoder();
    private final RegistrationService service = new RegistrationService(users, encoder);

    @Test
    void createsCustomerWithNormalizedEmailAndHashedPassword() {
        service.register(new RegisterRequest("Customer@Example.test", "password123"));
        var captor = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(captor.capture());
        User saved = captor.getValue();
        assertEquals("customer@example.test", saved.getEmail());
        assertEquals(Role.CUSTOMER, saved.getRole());
        assertTrue(saved.isEnabled());
        assertNotEquals("password123", saved.getPasswordHash());
        assertTrue(encoder.matches("password123", saved.getPasswordHash()));
    }

    @Test
    void rejectsExistingEmail() {
        when(users.existsByEmail("customer@example.test")).thenReturn(true);
        assertThrows(IllegalArgumentException.class,
                () -> service.register(new RegisterRequest("customer@example.test", "password123")));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void rejectsPasswordExceedingBcryptByteLimit() {
        assertThrows(IllegalArgumentException.class,
                () -> service.register(new RegisterRequest("customer@example.test", "ấ".repeat(25))));
        verify(users, never()).saveAndFlush(any());
    }
}
