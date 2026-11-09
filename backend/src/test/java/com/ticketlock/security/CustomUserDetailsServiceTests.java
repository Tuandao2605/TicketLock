package com.ticketlock.security;

import com.ticketlock.SecurityConfig;
import com.ticketlock.user.entity.Role;
import com.ticketlock.user.entity.User;
import com.ticketlock.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomUserDetailsServiceTests {

    private final UserRepository repository = mock(UserRepository.class);
    private final CustomUserDetailsService service = new CustomUserDetailsService(repository);

    @Test
    void authenticatesEmailAndBcryptPasswordAndRejectsWrongPassword() {
        var encoder = new SecurityConfig().passwordEncoder();
        var account = new User("customer@example.test", encoder.encode("test-password"), Role.CUSTOMER);
        when(repository.findByEmail(account.getEmail())).thenReturn(Optional.of(account));
        var provider = new DaoAuthenticationProvider(service);
        provider.setPasswordEncoder(encoder);

        var result = provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(
                account.getEmail(), "test-password"));

        assertTrue(result.isAuthenticated());
        assertEquals(account.getEmail(), result.getName());
        assertTrue(result.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_CUSTOMER")));
        assertThrows(BadCredentialsException.class, () -> provider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(account.getEmail(), "wrong-password")));
    }

    @Test
    void rejectsUnknownEmail() {
        when(repository.findByEmail("missing@example.test")).thenReturn(Optional.empty());
        assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername("missing@example.test"));
    }

    @Test
    void rejectsDisabledAccount() {
        var account = mock(User.class);
        when(account.getEmail()).thenReturn("disabled@example.test");
        when(account.getPasswordHash()).thenReturn("unused-hash");
        when(account.getRole()).thenReturn(Role.CUSTOMER);
        when(account.isEnabled()).thenReturn(false);
        when(repository.findByEmail(account.getEmail())).thenReturn(Optional.of(account));
        var provider = new DaoAuthenticationProvider(service);
        provider.setPasswordEncoder(new SecurityConfig().passwordEncoder());

        assertThrows(DisabledException.class, () -> provider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(account.getEmail(), "any-password")));
    }
}
