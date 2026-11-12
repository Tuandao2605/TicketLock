package com.ticketlock.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.session.security.SpringSessionBackedSessionRegistry;

@Configuration
@Profile("redis")
public class RedisSessionConfig {
    @Bean
    public SessionRegistry sessionRegistry(RedisIndexedSessionRepository repository) {
        // Registry phải dùng cùng Redis repository với HTTP session.
        return new SpringSessionBackedSessionRegistry<>(repository);
    }
}
