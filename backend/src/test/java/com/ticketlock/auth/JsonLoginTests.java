package com.ticketlock.auth;

import com.ticketlock.auth.dto.RegisterRequest;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class JsonLoginTests {
    @Autowired MockMvc mvc;
    @Autowired RegistrationService registration;
    @Autowired ObjectMapper json;

    @Test
    void rotatesSessionPersistsAuthenticationAndLogsOut() throws Exception {
        String email = "login-" + UUID.randomUUID() + "@example.test";
        registration.register(new RegisterRequest(email, "TestPassword123!"));
        var csrfResult = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        var csrf = json.readTree(csrfResult.getResponse().getContentAsString());
        var session = (MockHttpSession) csrfResult.getRequest().getSession(false);
        String oldId = session.getId();

        mvc.perform(post("/api/auth/login").session(session)
                .with(request -> {
                    request.setRequestedSessionId(oldId);
                    request.setRequestedSessionIdValid(true);
                    return request;
                })
                .header(csrf.get("headerName").asText(), csrf.get("token").asText())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new RegisterRequest(email, "TestPassword123!"))))
                .andExpect(status().isNoContent());

        assertNotEquals(oldId, session.getId());
        mvc.perform(get("/me").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value(email));

        var refreshed = mvc.perform(get("/api/auth/csrf").session(session)).andReturn();
        var nextCsrf = json.readTree(refreshed.getResponse().getContentAsString());
        mvc.perform(post("/api/auth/logout").session(session)
                .header(nextCsrf.get("headerName").asText(), nextCsrf.get("token").asText()))
                .andExpect(status().isNoContent());
        assertTrue(session.isInvalid());
        mvc.perform(get("/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsMissingCsrfInvalidJsonAndBadCredentials() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{}")) .andExpect(status().isForbidden());

        var result = mvc.perform(get("/api/auth/csrf")).andReturn();
        var token = json.readTree(result.getResponse().getContentAsString());
        var session = (MockHttpSession) result.getRequest().getSession(false);
        String header = token.get("headerName").asText();
        String value = token.get("token").asText();

        mvc.perform(post("/api/auth/login").session(session).header(header, value)
                .contentType(MediaType.APPLICATION_JSON).content("not-json"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/auth/login").session(session).header(header, value)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new RegisterRequest(
                        "missing-" + UUID.randomUUID() + "@example.test", "wrong-password"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/me").session(session)).andExpect(status().isUnauthorized());
    }
}
