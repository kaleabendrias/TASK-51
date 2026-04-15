package com.booking.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BookingServiceAttachmentApiIT extends BaseApiIT {

    // ---- REMOVED SURFACES: /api/bookings, /api/services, /api/attachments ----
    // Controllers have been physically deleted. Requests return 404.

    @Test
    void legacyBookingsEndpointRemoved() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/bookings").session(s)).andExpect(status().isNotFound());
        mvc.perform(get("/api/bookings/1").session(s)).andExpect(status().isNotFound());
        mvc.perform(post("/api/bookings").session(s)
                .header("Origin", TEST_ORIGIN).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("serviceId", 1)))).andExpect(status().isNotFound());
    }

    @Test
    void legacyServicesEndpointRemoved() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/services").session(s)).andExpect(status().isNotFound());
        mvc.perform(get("/api/services/1").session(s)).andExpect(status().isNotFound());
    }

    @Test
    void legacyAttachmentsEndpointRemoved() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/attachments/booking/1").session(s)).andExpect(status().isNotFound());
        mvc.perform(get("/api/attachments/1/download").session(s)).andExpect(status().isNotFound());
    }

    // ---- PAGE CONTROLLER ----
    @Test
    void rootRedirects() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection());
    }

    // ---- USERS ----
    @Test
    void listPhotographers() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/users/photographers").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(greaterThan(0)))
            .andExpect(jsonPath("$[0].id").isNumber())
            .andExpect(jsonPath("$[0].fullName").isNotEmpty())
            .andExpect(jsonPath("$[0].email").doesNotExist())
            .andExpect(jsonPath("$[0].phone").doesNotExist())
            .andExpect(jsonPath("$[0].enabled").doesNotExist())
            .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test
    void getUserSelf() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/users/4").session(s)).andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("cust1"));
    }

    @Test
    void getUserOtherDenied() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/users/1").session(s)).andExpect(status().isForbidden());
    }

    @Test
    void getUserNotFound() throws Exception {
        MockHttpSession s = loginAs("admin");
        mvc.perform(get("/api/users/9999").session(s)).andExpect(status().isNotFound());
    }

    @Test
    void enableDisableUser() throws Exception {
        MockHttpSession s = loginAs("admin");
        mvc.perform(patch("/api/users/5/enabled").session(s)
                .header("Origin", TEST_ORIGIN).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("enabled", false))))
            .andExpect(status().isOk());
        mvc.perform(patch("/api/users/5/enabled").session(s)
                .header("Origin", TEST_ORIGIN).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("enabled", true))))
            .andExpect(status().isOk());
    }

    @Test
    void updateUserNonAdminDenied() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(put("/api/users/4").session(s)
                .header("Origin", TEST_ORIGIN).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "x@t.com", "fullName", "X", "roleId", 1, "enabled", true))))
            .andExpect(status().isForbidden());
    }

    // ---- LISTINGS ----
    @Test
    void listActiveListings() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/listings").session(s)).andExpect(status().isOk());
    }

    @Test
    void createListing() throws Exception {
        MockHttpSession s = loginAs("photo1");
        mvc.perform(post("/api/listings").session(s)
                .header("Origin", TEST_ORIGIN).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("title", "API Test Listing", "price", 200.0,
                        "durationMinutes", 60, "category", "PORTRAIT"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("API Test Listing"));
    }

    @Test
    void updateListing() throws Exception {
        MockHttpSession s = loginAs("photo1");
        mvc.perform(put("/api/listings/1").session(s)
                .header("Origin", TEST_ORIGIN).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("title", "Updated Studio Portrait", "price", 160.0,
                        "durationMinutes", 60, "category", "PORTRAIT", "active", true,
                        "maxConcurrent", 1, "location", "Downtown"))))
            .andExpect(status().isOk());
    }

    @Test
    void createTimeSlot() throws Exception {
        MockHttpSession s = loginAs("photo1");
        mvc.perform(post("/api/timeslots").session(s)
                .header("Origin", TEST_ORIGIN).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 1, "slotDate", "2026-07-01",
                        "startTime", "10:00", "endTime", "11:00", "capacity", 2))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.capacity").value(2));
    }

    // ---- HEALTH ----
    @Test
    void healthEndpoint() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }
}
