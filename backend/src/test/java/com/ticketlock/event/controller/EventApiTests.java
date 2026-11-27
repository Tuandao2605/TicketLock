package com.ticketlock.event.controller;

import com.ticketlock.event.entity.Event;
import com.ticketlock.event.entity.Show;
import com.ticketlock.event.repository.EventRepository;
import com.ticketlock.event.repository.ShowRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EventApiTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired EventRepository events;
    @Autowired ShowRepository shows;

    @Test void adminCreatesAndPublishesPricedLayoutThenPublicCanRead() throws Exception {
        var csrfCookie = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        var admin = user("admin").roles("ADMIN");
        var created = mvc.perform(post("/api/v1/events").with(admin).cookie(csrfCookie).header("X-XSRF-TOKEN", csrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Galaxy\",\"description\":\"Demo\"}"))
                .andExpect(status().isCreated()).andReturn();
        String eventId = json.readTree(created.getResponse().getContentAsString()).get("id").asString();
        var result = mvc.perform(post("/api/v1/events/" + eventId + "/shows").with(admin).cookie(csrfCookie).header("X-XSRF-TOKEN", csrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON).content("{\"startsAt\":\"2099-01-01T12:00:00Z\",\"venue\":\"Hall\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT")).andReturn();
        String showId = json.readTree(result.getResponse().getContentAsString()).get("id").asString();
        mvc.perform(get("/api/v1/events/" + eventId)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/shows/" + showId)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/shows/" + showId + "/seats/import").with(admin).cookie(csrfCookie).header("X-XSRF-TOKEN", csrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON).content("{\"labels\":[\"A1\",\"A2\"]}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/shows/" + showId + "/publish").with(admin).cookie(csrfCookie).header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/v1/shows/" + showId + "/price").with(admin).cookie(csrfCookie).header("X-XSRF-TOKEN", csrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":150000,\"currency\":\"VND\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/shows/" + showId + "/publish").with(admin).cookie(csrfCookie).header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));
        mvc.perform(get("/api/v1/events")).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(eventId));
        mvc.perform(get("/api/v1/events/" + eventId + "/shows")).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(showId));
        mvc.perform(get("/api/v1/shows/" + showId + "/seats"))
                .andExpect(status().isOk()).andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.seats.length()").value(2))
                .andExpect(jsonPath("$.seats[0].price.amount").value(150000))
                .andExpect(jsonPath("$.seats[0].price.currency").value("VND"))
                .andExpect(jsonPath("$.seats[0].ownerId").doesNotExist());
        mvc.perform(post("/api/v1/shows/" + showId + "/seats/import").with(admin).cookie(csrfCookie).header("X-XSRF-TOKEN", csrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON).content("{\"labels\":[\"A3\"]}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/v1/shows/" + showId + "/price").with(admin).cookie(csrfCookie).header("X-XSRF-TOKEN", csrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1,\"currency\":\"VND\"}"))
                .andExpect(status().isConflict());
    }

    @Test void authorizationAndValidationAreEnforced() throws Exception {
        var csrfCookie = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        String body = "{\"name\":\"Galaxy\",\"description\":\"Demo\"}";
        mvc.perform(post("/api/v1/events").cookie(csrfCookie).header("X-XSRF-TOKEN", csrfCookie.getValue()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(post("/api/v1/events").with(user("customer").roles("CUSTOMER")).cookie(csrfCookie).header("X-XSRF-TOKEN", csrfCookie.getValue()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(post("/api/v1/events").with(user("admin").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/api/v1/events?page=-1")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/v1/shows/not-a-uuid")).andExpect(status().isBadRequest());
    }
}
