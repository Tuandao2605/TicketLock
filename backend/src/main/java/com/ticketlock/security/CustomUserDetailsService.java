package com.ticketlock.security;

import com.ticketlock.user.api.UserAccounts;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserAccounts userRepository;

    public CustomUserDetailsService(UserAccounts userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // Lookup bằng email, sau xác thực principal mang UUID ổn định.
        var account = userRepository.findByEmail(email.strip().toLowerCase(java.util.Locale.ROOT))
                .orElseThrow(() -> new UsernameNotFoundException("Thông tin đăng nhập không hợp lệ"));

        return User.withUsername(account.id().toString())
                .password(account.passwordHash())
                .roles(account.role())
                .disabled(!account.enabled())
                .build();
    }
}
