package com.ticketlock.auth;

import com.ticketlock.auth.dto.RegisterRequest;
import com.ticketlock.auth.dto.LoginRequest;
import com.ticketlock.auth.service.RegistrationService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import jakarta.servlet.http.Cookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.session.data.redis.namespace=ticketlock:test:mockmvc")
@AutoConfigureMockMvc
@Transactional
class ConcurrentSessionTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RegistrationService registration;

    @Test
    void secondLoginExpiresFirstSessionButNotAnotherAccount() throws Exception {
        String firstEmail = "concurrent-" + UUID.randomUUID() + "@example.test";
        String otherEmail = "other-" + UUID.randomUUID() + "@example.test";
        registration.register(new RegisterRequest(firstEmail, "TestPassword123!"));
        registration.register(new RegisterRequest(otherEmail, "TestPassword123!"));
        var first = login(firstEmail);
        var other = login(otherEmail);
        mvc.perform(get("/me").cookie(first)).andExpect(status().isOk());

        var second = login(firstEmail);

        mvc.perform(get("/me").cookie(first)).andExpect(status().isUnauthorized());
        mvc.perform(get("/me").cookie(second)).andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(firstEmail));
        mvc.perform(get("/me").cookie(other)).andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(otherEmail));
    }

    private Cookie login(String email) throws Exception {
        var csrfResult = mvc.perform(get("/api/auth/csrf")).andReturn();
        var csrf = json.readTree(csrfResult.getResponse().getContentAsString());
        var csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(csrfCookie);
        var result = mvc.perform(post("/api/auth/login").cookie(csrfCookie)
                .header(csrf.get("headerName").asString(), csrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LoginRequest(email, "TestPassword123!"))))
                .andExpect(status().isNoContent()).andReturn();
        var sessionCookie = result.getResponse().getCookie("JSESSIONID");
        assertNotNull(sessionCookie);
        return sessionCookie;
    }
}
