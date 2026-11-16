package com.ticketlock.auth.service;

import com.ticketlock.auth.dto.LoginRequest;
import com.ticketlock.user.entity.User;
import com.ticketlock.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("jwt")
public class AccessTokenService {
    private final AuthenticationManager authenticationManager;
    private final UserRepository users;
    private final JwtEncoder encoder;
    private final String issuer;
    private final String audience;
    private final Duration ttl;

    public AccessTokenService(AuthenticationManager authenticationManager, UserRepository users,
            JwtEncoder encoder, @Value("${ticketlock.jwt.issuer}") String issuer,
            @Value("${ticketlock.jwt.audience}") String audience,
            @Value("${ticketlock.jwt.access-ttl}") Duration ttl) {
        if (ttl.isNegative() || ttl.isZero() || ttl.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("Access token TTL phải lớn hơn 0 và không quá một giờ.");
        }
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.encoder = encoder;
        this.issuer = issuer;
        this.audience = audience;
        this.ttl = ttl;
    }

    @Transactional(readOnly = true)
    public User authenticate(LoginRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadCredentialsException("Thông tin đăng nhập không hợp lệ");
        }
        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
        var account = users.findByEmail(authentication.getName()).filter(user -> user.isEnabled())
                .orElseThrow(() -> new BadCredentialsException("Thông tin đăng nhập không hợp lệ"));
        return account;
    }

    public String issueFor(User account) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(issuer).subject(account.getId().toString())
                .audience(List.of(audience)).issuedAt(now).expiresAt(now.plus(ttl))
                .id(UUID.randomUUID().toString()).claim("roles", List.of(account.getRole().name())).build();
        var header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long accessTtlSeconds() { return ttl.toSeconds(); }
}
