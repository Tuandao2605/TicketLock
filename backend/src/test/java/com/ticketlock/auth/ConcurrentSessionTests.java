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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
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
        mvc.perform(get("/me").session(first)).andExpect(status().isOk());

        var second = login(firstEmail);

        mvc.perform(get("/me").session(first)).andExpect(status().isUnauthorized());
        assertTrue(first.isInvalid());
        mvc.perform(get("/me").session(second)).andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(firstEmail));
        mvc.perform(get("/me").session(other)).andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(otherEmail));
    }

    private MockHttpSession login(String email) throws Exception {
        var csrfResult = mvc.perform(get("/api/auth/csrf")).andReturn();
        var csrf = json.readTree(csrfResult.getResponse().getContentAsString());
        var session = (MockHttpSession) csrfResult.getRequest().getSession(false);
        String id = session.getId();
        var result = mvc.perform(post("/api/auth/login").session(session)
                .with(request -> {
                    request.setRequestedSessionId(id);
                    request.setRequestedSessionIdValid(true);
                    return request;
                })
                .header(csrf.get("headerName").asString(), csrf.get("token").asString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LoginRequest(email, "TestPassword123!"))))
                .andExpect(status().isNoContent()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
