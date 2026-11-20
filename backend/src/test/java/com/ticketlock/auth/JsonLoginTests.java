package com.ticketlock.auth;

import com.ticketlock.auth.dto.RegisterRequest;
import com.ticketlock.auth.service.RegistrationService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.session.data.redis.namespace=ticketlock:test:mockmvc")
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
        var csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(csrfCookie);
        assertNull(csrfResult.getRequest().getSession(false), "CSRF token should not create an HTTP session");
        var sessionResult = mvc.perform(get("/lab/session")).andReturn();
        var sessionCookie = sessionResult.getResponse().getCookie("JSESSIONID");
        assertNotNull(sessionCookie);
        String oldId = sessionCookie.getValue();

        var loginResult = mvc.perform(post("/api/auth/login").cookie(sessionCookie, csrfCookie)
                .header(csrf.get("headerName").asString(), csrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new RegisterRequest(email, "TestPassword123!"))))
                .andExpect(status().isNoContent()).andReturn();

        sessionCookie = loginResult.getResponse().getCookie("JSESSIONID");
        assertNotNull(sessionCookie);
        assertNotEquals(oldId, sessionCookie.getValue());
        mvc.perform(get("/me").cookie(sessionCookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value(email));

        var refreshed = mvc.perform(get("/api/auth/csrf").cookie(sessionCookie)).andReturn();
        var nextCsrf = json.readTree(refreshed.getResponse().getContentAsString());
        var refreshedCookie = refreshed.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(refreshedCookie);
        mvc.perform(post("/api/auth/logout").cookie(sessionCookie, refreshedCookie)
                .header(nextCsrf.get("headerName").asString(), refreshedCookie.getValue()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/me").cookie(sessionCookie)).andExpect(status().isUnauthorized());
        mvc.perform(get("/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsMissingCsrfInvalidJsonAndBadCredentials() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{}")) .andExpect(status().isForbidden());

        var result = mvc.perform(get("/api/auth/csrf")).andReturn();
        var token = json.readTree(result.getResponse().getContentAsString());
        var csrfCookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(csrfCookie);
        assertNull(result.getRequest().getSession(false), "CSRF token should not create an HTTP session");
        String header = token.get("headerName").asString();
        String value = csrfCookie.getValue();

        mvc.perform(post("/api/auth/login").cookie(csrfCookie).header(header, value)
                .contentType(MediaType.APPLICATION_JSON).content("not-json"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/auth/login").cookie(csrfCookie).header(header, value)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new RegisterRequest(
                        "missing-" + UUID.randomUUID() + "@example.test", "wrong-password"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/me")).andExpect(status().isUnauthorized());
    }
}
