package com.ticketlock.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RoleAuthorizationTests {
    @Autowired
    private MockMvc mvc;

    @Test
    void anonymousCannotAccessEitherRole() throws Exception {
        mvc.perform(get("/api/admin/hello")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/customer/hello")).andExpect(status().isUnauthorized());
    }

    @Test
    void customerCanAccessOnlyCustomerEndpoint() throws Exception {
        mvc.perform(get("/api/customer/hello").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(content().string("Xin chào Customer!"));
        mvc.perform(get("/api/admin/hello").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanAccessOnlyAdminEndpoint() throws Exception {
        mvc.perform(get("/api/admin/hello").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string("Xin chào Admin!"));
        mvc.perform(get("/api/customer/hello").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }
}
