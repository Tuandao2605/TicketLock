package com.ticketlock.security;

import com.ticketlock.security.lab.MethodSecurityLabService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class MethodSecurityTests {
    @Autowired MockMvc mvc;
    @Autowired MethodSecurityLabService service;

    @Test
    void anonymousIsRejectedBeforeController() throws Exception {
        mvc.perform(get("/api/method-security/admin-message"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerIsRejectedByServiceEvenWithoutHttpRoleRule() throws Exception {
        assertThrows(AccessDeniedException.class, () -> service.adminMessage());
        mvc.perform(get("/api/method-security/admin-message"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanCallEndpointAndService() throws Exception {
        String message = service.adminMessage();
        mvc.perform(get("/api/method-security/admin-message"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(message));
    }
}
