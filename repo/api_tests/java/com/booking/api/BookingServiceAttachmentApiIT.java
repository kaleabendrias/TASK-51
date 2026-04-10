package com.booking.api;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BookingServiceAttachmentApiIT extends BaseApiIT {

    // ---- BOOKINGS (legacy surface returns 410 GONE) ----
    @Test @Order(1) void listBookingsReturnsGone() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/bookings").session(s))
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.error", containsString("/api/orders")));
    }

    @Test @Order(2) void createBookingReturnsGone() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(post("/api/bookings").session(s).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("serviceId", 1))))
            .andExpect(status().isGone());
    }

    @Test @Order(3) void getBookingReturnsGone() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/bookings/1").session(s))
            .andExpect(status().isGone());
    }

    @Test @Order(4) void updateBookingStatusReturnsGone() throws Exception {
        MockHttpSession s = loginAs("admin");
        mvc.perform(patch("/api/bookings/1/status").session(s).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "CONFIRMED"))))
            .andExpect(status().isGone());
    }

    @Test @Order(5) void updateBookingReturnsGone() throws Exception {
        MockHttpSession s = loginAs("admin");
        mvc.perform(put("/api/bookings/1").session(s).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("serviceId", 1))))
            .andExpect(status().isGone());
    }

    // ---- SERVICES (legacy surface returns 410 GONE) ----
    @Test @Order(10) void listServicesReturnsGone() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/services").session(s))
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.error", containsString("/api/listings")));
    }

    @Test @Order(11) void listAllServicesReturnsGone() throws Exception {
        MockHttpSession admin = loginAs("admin");
        mvc.perform(get("/api/services/all").session(admin))
            .andExpect(status().isGone());
    }

    @Test @Order(12) void createServiceReturnsGone() throws Exception {
        MockHttpSession s = loginAs("admin");
        mvc.perform(post("/api/services").session(s).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "X", "price", 75.0, "durationMinutes", 45))))
            .andExpect(status().isGone());
    }

    @Test @Order(13) void getServiceReturnsGone() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/services/1").session(s))
            .andExpect(status().isGone());
    }

    @Test @Order(14) void updateServiceReturnsGone() throws Exception {
        MockHttpSession s = loginAs("admin");
        mvc.perform(put("/api/services/1").session(s).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "X"))))
            .andExpect(status().isGone());
    }

    // ---- ATTACHMENTS (legacy surface returns 410 GONE) ----
    @Test @Order(20) void uploadAttachmentReturnsGone() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(post("/api/attachments/booking/1").session(s))
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.error", containsString("/api/messages")));
    }

    @Test @Order(21) void listAttachmentsReturnsGone() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/attachments/booking/1").session(s))
            .andExpect(status().isGone());
    }

    @Test @Order(22) void downloadAttachmentReturnsGone() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/attachments/1/download").session(s))
            .andExpect(status().isGone());
    }

    @Test @Order(23) void deleteAttachmentReturnsGone() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(delete("/api/attachments/1").session(s))
            .andExpect(status().isGone());
    }

    // ---- PAGE CONTROLLER ----
    @Test @Order(30) void rootRedirects() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection());
    }

    // ---- USERS ----
    @Test @Order(40) void listPhotographers() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/users/photographers").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(greaterThan(0)))
            // DTO should only expose id, username, fullName — no email, phone, enabled
            .andExpect(jsonPath("$[0].id").isNumber())
            .andExpect(jsonPath("$[0].fullName").isNotEmpty())
            .andExpect(jsonPath("$[0].email").doesNotExist())
            .andExpect(jsonPath("$[0].phone").doesNotExist())
            .andExpect(jsonPath("$[0].enabled").doesNotExist())
            .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test @Order(41) void getUserSelf() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/users/4").session(s)).andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("cust1"));
    }

    @Test @Order(42) void getUserOtherDenied() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/users/1").session(s)).andExpect(status().isForbidden());
    }

    @Test @Order(43) void getUserNotFound() throws Exception {
        MockHttpSession s = loginAs("admin");
        mvc.perform(get("/api/users/9999").session(s)).andExpect(status().isNotFound());
    }

    @Test @Order(44) void enableDisableUser() throws Exception {
        MockHttpSession s = loginAs("admin");
        mvc.perform(patch("/api/users/5/enabled").session(s).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("enabled", false))))
            .andExpect(status().isOk());
        mvc.perform(patch("/api/users/5/enabled").session(s).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("enabled", true))))
            .andExpect(status().isOk());
    }

    @Test @Order(45) void updateUserNonAdminDenied() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(put("/api/users/4").session(s).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "x@t.com", "fullName", "X", "roleId", 1, "enabled", true))))
            .andExpect(status().isForbidden());
    }

    // ---- LISTINGS ----
    @Test @Order(50) void listActiveListings() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/listings").session(s)).andExpect(status().isOk());
    }

    @Test @Order(51) void createListing() throws Exception {
        MockHttpSession s = loginAs("photo1");
        mvc.perform(post("/api/listings").session(s).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("title", "API Test Listing", "price", 200.0,
                        "durationMinutes", 60, "category", "PORTRAIT"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("API Test Listing"));
    }

    @Test @Order(52) void updateListing() throws Exception {
        MockHttpSession s = loginAs("photo1");
        mvc.perform(put("/api/listings/1").session(s).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("title", "Updated Studio Portrait", "price", 160.0,
                        "durationMinutes", 60, "category", "PORTRAIT", "active", true,
                        "maxConcurrent", 1, "location", "Downtown"))))
            .andExpect(status().isOk());
    }

    @Test @Order(53) void createTimeSlot() throws Exception {
        MockHttpSession s = loginAs("photo1");
        mvc.perform(post("/api/timeslots").session(s).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 1, "slotDate", "2026-07-01",
                        "startTime", "10:00", "endTime", "11:00", "capacity", 2))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.capacity").value(2));
    }

    // ---- HEALTH ----
    @Test @Order(60) void healthEndpoint() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }
}
