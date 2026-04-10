package com.booking.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RbacApiIT extends BaseApiIT {

    @Test void customerCannotAccessUsersList() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/users").session(s)).andExpect(status().isForbidden());
    }

    @Test void customerCannotAccessBlacklist() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/blacklist").session(s)).andExpect(status().isForbidden());
    }

    @Test void customerCannotBlacklistAnyone() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(post("/api/blacklist").session(s)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("userId", 5, "reason", "test"))))
            .andExpect(status().isForbidden());
    }

    @Test void photographerCannotAccessUsersList() throws Exception {
        MockHttpSession s = loginAs("photo1");
        mvc.perform(get("/api/users").session(s)).andExpect(status().isForbidden());
    }

    @Test void servicesEndpointRemoved() throws Exception {
        MockHttpSession s = loginAs("photo1");
        mvc.perform(post("/api/services").session(s)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "X", "price", 50, "durationMinutes", 30))))
            .andExpect(status().isNotFound());
    }

    @Test void customerCannotCreateListing() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(post("/api/listings").session(s)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("title", "X", "price", 50, "durationMinutes", 30))))
            .andExpect(status().isForbidden());
    }

    @Test void photographerCanAccessOwnListings() throws Exception {
        MockHttpSession s = loginAs("photo1");
        mvc.perform(get("/api/listings/my").session(s)).andExpect(status().isOk());
    }

    @Test void adminCanAccessEverything() throws Exception {
        MockHttpSession s = loginAs("admin");
        mvc.perform(get("/api/users").session(s)).andExpect(status().isOk());
        mvc.perform(get("/api/blacklist").session(s)).andExpect(status().isOk());
        mvc.perform(get("/api/listings").session(s)).andExpect(status().isOk());
        mvc.perform(get("/api/points/rules").session(s)).andExpect(status().isOk());
        mvc.perform(get("/api/points/adjustments").session(s)).andExpect(status().isOk());
    }

    @Test void customerCannotAdjustPoints() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(post("/api/points/adjust").session(s)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("userId", 4, "points", 100, "reason", "test"))))
            .andExpect(status().isForbidden());
    }

    @Test void photographerSeesOnlyOwnOrders() throws Exception {
        // photo2 has no orders, should get empty list (not other photographer's orders)
        MockHttpSession s = loginAs("photo2");
        mvc.perform(get("/api/orders").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(0));
    }
}
