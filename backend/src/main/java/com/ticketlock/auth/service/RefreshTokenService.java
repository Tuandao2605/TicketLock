package com.ticketlock.auth.service;

import com.ticketlock.auth.dto.TokenResponse;
import com.ticketlock.auth.entity.RefreshToken;
import com.ticketlock.auth.repository.RefreshTokenRepository;
import com.ticketlock.user.entity.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("jwt")
public class RefreshTokenService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository tokens;
    private final AccessTokenService accessTokens;
    private final Duration refreshTtl;

    public RefreshTokenService(RefreshTokenRepository tokens, AccessTokenService accessTokens,
            @Value("${ticketlock.jwt.refresh-ttl}") Duration refreshTtl) {
        if (refreshTtl.isNegative() || refreshTtl.isZero() || refreshTtl.compareTo(Duration.ofDays(30)) > 0) {
            throw new IllegalArgumentException("Refresh token TTL phải lớn hơn 0 và không quá 30 ngày.");
        }
        this.tokens = tokens;
        this.accessTokens = accessTokens;
        this.refreshTtl = refreshTtl;
    }

    @Transactional
    public TokenResponse startFamily(User user) {
        Instant now = Instant.now();
        UUID familyId = UUID.randomUUID();
        Instant expiresAt = now.plus(refreshTtl);
        String rawToken = newRawToken();
        tokens.save(new RefreshToken(hash(rawToken), familyId, user, now, expiresAt));
        return response(user, rawToken, expiresAt, now);
    }

    @Transactional
    public Optional<TokenResponse> rotate(String rawToken) {
        Instant now = Instant.now();
        var current = tokens.findByTokenHashForUpdate(hash(rawToken));
        if (current.isEmpty()) return Optional.empty();

        RefreshToken old = current.get();
        if (old.getUsedAt() != null) {
            // Return normally so this family revocation commits; throwing here would roll it back.
            tokens.revokeFamily(old.getFamilyId(), now);
            return Optional.empty();
        }
        if (old.getRevokedAt() != null || !old.getExpiresAt().isAfter(now)) return Optional.empty();

        User user = old.getUser();
        if (!user.isEnabled()) {
            tokens.revokeFamily(old.getFamilyId(), now);
            return Optional.empty();
        }

        old.markUsed(now);
        String replacement = newRawToken();
        tokens.save(new RefreshToken(hash(replacement), old.getFamilyId(), user, now, old.getExpiresAt()));
        return Optional.of(response(user, replacement, old.getExpiresAt(), now));
    }

    @Transactional
    public void revokeFamilyFor(String rawToken) {
        var token = tokens.findByTokenHashForUpdate(hash(rawToken));
        token.ifPresent(value -> tokens.revokeFamily(value.getFamilyId(), Instant.now()));
    }

    private TokenResponse response(User user, String rawRefreshToken, Instant familyExpiresAt, Instant now) {
        return new TokenResponse(accessTokens.issueFor(user), "Bearer", accessTokens.accessTtlSeconds(),
                rawRefreshToken, Math.max(0, Duration.between(now, familyExpiresAt).toSeconds()));
    }

    private static String newRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
