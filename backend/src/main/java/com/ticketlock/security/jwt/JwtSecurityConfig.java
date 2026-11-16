package com.ticketlock.security.jwt;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Profile("jwt")
public class JwtSecurityConfig {
    @Bean
    public KeyPair jwtKeyPair(@Value("${ticketlock.jwt.private-key}") Resource privateKey,
            @Value("${ticketlock.jwt.public-key}") Resource publicKey) throws Exception {
        try (var privateStream = privateKey.getInputStream(); var publicStream = publicKey.getInputStream()) {
            return new KeyPair(RsaKeyConverters.x509().convert(publicStream),
                    RsaKeyConverters.pkcs8().convert(privateStream));
        }
    }

    @Bean
    public JwtEncoder jwtEncoder(KeyPair keys) {
        var key = new RSAKey.Builder((RSAPublicKey) keys.getPublic())
                .privateKey((RSAPrivateKey) keys.getPrivate()).keyID("ticketlock-local-rsa").build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }

    @Bean
    public JwtDecoder jwtDecoder(KeyPair keys, @Value("${ticketlock.jwt.issuer}") String issuer,
            @Value("${ticketlock.jwt.audience}") String audience) {
        var decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic())
                .signatureAlgorithm(SignatureAlgorithm.RS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                new JwtClaimValidator<java.util.List<String>>("aud", audiences ->
                        audiences != null && audiences.contains(audience)),
                new JwtClaimValidator<String>("sub", subject -> subject != null && !subject.isBlank()),
                new JwtClaimValidator<Object>("exp", expiration -> expiration != null)));
        return decoder;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain jwtFilterChain(HttpSecurity http, JwtDecoder decoder) throws Exception {
        var roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        var authentication = new JwtAuthenticationConverter();
        authentication.setJwtGrantedAuthoritiesConverter(roles);

        return http.securityMatcher("/api/token/**", "/api/stateless/**")
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                // Các endpoint này chỉ nhận bearer token, không xác thực bằng cookie.
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/token/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/token/refresh", "/api/token/logout").permitAll()
                        .requestMatchers("/api/stateless/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/stateless/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.decoder(decoder)
                        .jwtAuthenticationConverter(authentication)))
                .build();
    }
}
