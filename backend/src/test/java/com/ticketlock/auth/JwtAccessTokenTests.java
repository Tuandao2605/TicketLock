package com.ticketlock.auth;

import com.ticketlock.auth.dto.LoginRequest;
import com.ticketlock.user.entity.Role;
import com.ticketlock.user.entity.User;
import com.ticketlock.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.session.data.redis.namespace=ticketlock:test:mockmvc")
@AutoConfigureMockMvc
@ActiveProfiles("jwt")
@Transactional
class JwtAccessTokenTests {
    private static final Path KEY_DIRECTORY = createTestKeys();
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    @Autowired JwtEncoder encoder;

    @DynamicPropertySource
    static void keys(DynamicPropertyRegistry registry) {
        registry.add("ticketlock.jwt.private-key", () -> KEY_DIRECTORY.resolve("private.pem").toUri().toString());
        registry.add("ticketlock.jwt.public-key", () -> KEY_DIRECTORY.resolve("public.pem").toUri().toString());
    }

    @Test
    void loginIssuesTokenAndBearerWorksWithoutCreatingHttpSession() throws Exception {
        var account = account(Role.CUSTOMER);
        var result = mvc.perform(post("/api/token/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LoginRequest(account.getEmail(), "TestPassword123!"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(header().string("Cache-Control", "no-store")).andReturn();
        assertNull(result.getRequest().getSession(false));
        assertNull(result.getResponse().getHeader("Set-Cookie"));
        String token = json.readTree(result.getResponse().getContentAsString()).get("accessToken").asString();
        var me = mvc.perform(get("/api/stateless/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(account.getId().toString()))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER")).andReturn();
        assertNull(me.getRequest().getSession(false));
        mvc.perform(get("/api/stateless/admin/hello").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sessionLoginCannotAuthenticateStatelessApi() throws Exception {
        var account = account(Role.CUSTOMER);
        var csrfResult = mvc.perform(get("/api/auth/csrf")).andReturn();
        var csrf = json.readTree(csrfResult.getResponse().getContentAsString());
        var csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(csrfCookie);
        var loginResult = mvc.perform(post("/api/auth/login").cookie(csrfCookie)
                .header(csrf.get("headerName").asString(), csrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LoginRequest(account.getEmail(), "TestPassword123!"))))
                .andExpect(status().isNoContent()).andReturn();
        var sessionCookie = loginResult.getResponse().getCookie("JSESSIONID");
        assertNotNull(sessionCookie);
        mvc.perform(get("/me").cookie(sessionCookie)).andExpect(status().isOk());
        mvc.perform(get("/api/stateless/me").cookie(sessionCookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInvalidClaimsAndChangedPayloadAndAllowsAdminRole() throws Exception {
        mvc.perform(get("/api/stateless/me")).andExpect(status().isUnauthorized());
        for (String token : List.of(
                token("wrong-issuer", "ticketlock-api", Instant.now().plusSeconds(600), "CUSTOMER"),
                token("ticketlock-local", "wrong-audience", Instant.now().plusSeconds(600), "CUSTOMER"),
                token("ticketlock-local", "ticketlock-api", Instant.now().minusSeconds(300), "CUSTOMER"),
                token("ticketlock-local", "ticketlock-api", null, "CUSTOMER"))) {
            mvc.perform(get("/api/stateless/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
        String valid = token("ticketlock-local", "ticketlock-api", Instant.now().plusSeconds(600), "CUSTOMER");
        String[] parts = valid.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replace("CUSTOMER", "ADMIN");
        String changed = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];
        mvc.perform(get("/api/stateless/me").header("Authorization", "Bearer " + changed))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/stateless/admin/hello").header("Authorization", "Bearer "
                + token("ticketlock-local", "ticketlock-api", Instant.now().plusSeconds(600), "ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsBadPasswordAndMalformedInput() throws Exception {
        var account = account(Role.CUSTOMER);
        mvc.perform(post("/api/token/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LoginRequest(account.getEmail(), "wrong-password"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/token/login").contentType(MediaType.APPLICATION_JSON).content("not-json"))
                .andExpect(status().isBadRequest());
    }

    private User account(Role role) {
        return users.saveAndFlush(new User("jwt-" + UUID.randomUUID() + "@example.test",
                passwords.encode("TestPassword123!"), role));
    }

    private String token(String issuer, String audience, Instant expiresAt, String role) {
        var claims = JwtClaimsSet.builder().issuer(issuer).subject(UUID.randomUUID().toString())
                .audience(List.of(audience)).issuedAt(Instant.now().minusSeconds(600))
                .claim("roles", List.of(role));
        if (expiresAt != null) claims.expiresAt(expiresAt);
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(),
                claims.build())).getTokenValue();
    }

    private static Path createTestKeys() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var keys = generator.generateKeyPair();
            var directory = Files.createTempDirectory("ticketlock-jwt-test-");
            var base64 = Base64.getMimeEncoder(64, new byte[]{'\n'});
            Files.writeString(directory.resolve("private.pem"), "-----BEGIN PRIVATE KEY-----\n"
                    + base64.encodeToString(keys.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----\n");
            Files.writeString(directory.resolve("public.pem"), "-----BEGIN PUBLIC KEY-----\n"
                    + base64.encodeToString(keys.getPublic().getEncoded()) + "\n-----END PUBLIC KEY-----\n");
            return directory;
        } catch (Exception exception) {
            throw new IllegalStateException("Không tạo được khóa JWT test", exception);
        }
    }

    @AfterAll
    static void cleanupKeys() throws Exception {
        Files.deleteIfExists(KEY_DIRECTORY.resolve("private.pem"));
        Files.deleteIfExists(KEY_DIRECTORY.resolve("public.pem"));
        Files.deleteIfExists(KEY_DIRECTORY);
    }
}
