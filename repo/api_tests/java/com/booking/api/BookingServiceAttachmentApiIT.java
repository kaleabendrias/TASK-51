package com.booking.api;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BookingServiceAttachmentApiIT extends BaseApiIT {

    // ---- BOOKINGS ----
    @Test @Order(1) void listBookings() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/bookings").session(s)).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
    }

    @Test @Order(2) void createBooking() throws Exception {
        MockHttpSession s = loginAs("cust1");
        String futureDate = LocalDate.now().plusDays(30).toString();
        mvc.perform(post("/api/bookings").session(s).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("serviceId", 1, "bookingDate", futureDate,
                        "startTime", "10:00", "endTime", "11:00", "location", "Test"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test @Order(3) void createBookingPastDateFails() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(post("/api/bookings").session(s).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("serviceId", 1, "bookingDate", "2020-01-01",
                        "startTime", "10:00", "endTime", "11:00"))))
            .andExpect(status().isBadRequest());
    }

    @Test @Order(4) void getBookingById() throws Exception {
        // Create a booking first
        MockHttpSession s = loginAs("cust1");
        String futureDate = LocalDate.now().plusDays(31).toString();
        MvcResult r = mvc.perform(post("/api/bookings").session(s).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("serviceId", 1, "bookingDate", futureDate,
                        "startTime", "14:00", "endTime", "15:00"))))
            .andExpect(status().isOk()).andReturn();
        int id = ((Number) parseMap(r).get("id")).intValue();
        mvc.perform(get("/api/bookings/" + id).session(s)).andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id));
    }

    @Test @Order(5) void getBookingNotFound() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/bookings/99999").session(s)).andExpect(status().isNotFound());
    }

    @Test @Order(6) void updateBookingStatus() throws Exception {
        MockHttpSession s = loginAs("admin");
        String futureDate = LocalDate.now().plusDays(32).toString();
        MvcResult r = mvc.perform(post("/api/bookings").session(s).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("serviceId", 1, "customerId", 4, "bookingDate", futureDate,
                        "startTime", "09:00", "endTime", "10:00"))))
            .andExpect(status().isOk()).andReturn();
        int id = ((Number) parseMap(r).get("id")).intValue();
        mvc.perform(patch("/api/bookings/" + id + "/status").session(s).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "CONFIRMED"))))
            .andExpect(status().isOk());
    }

    @Test @Order(7) void updateBookingStatusInvalid() throws Exception {
        MockHttpSession s = loginAs("admin");
        String futureDate = LocalDate.now().plusDays(33).toString();
        MvcResult r = mvc.perform(post("/api/bookings").session(s).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("serviceId", 1, "customerId", 4, "bookingDate", futureDate,
                        "startTime", "09:00", "endTime", "10:00"))))
            .andExpect(status().isOk()).andReturn();
        int id = ((Number) parseMap(r).get("id")).intValue();
        mvc.perform(patch("/api/bookings/" + id + "/status").session(s).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "COMPLETED"))))
            .andExpect(status().is4xxClientError());
    }

    // ---- SERVICES ----
    @Test @Order(10) void listActiveServices() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/services").session(s)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(greaterThan(0)));
    }

    @Test @Order(11) void listAllServicesAdminOnly() throws Exception {
        MockHttpSession admin = loginAs("admin");
        mvc.perform(get("/api/services/all").session(admin)).andExpect(status().isOk());
        MockHttpSession cust = loginAs("cust1");
        mvc.perform(get("/api/services/all").session(cust)).andExpect(status().isForbidden());
    }

    @Test @Order(12) void createService() throws Exception {
        MockHttpSession s = loginAs("admin");
        mvc.perform(post("/api/services").session(s).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "API Test Svc", "price", 75.0, "durationMinutes", 45))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("API Test Svc"));
    }

    @Test @Order(13) void getServiceById() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/services/1").session(s)).andExpect(status().isOk())
            .andExpect(jsonPath("$.name").isNotEmpty());
    }

    @Test @Order(14) void getServiceNotFound() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/services/9999").session(s)).andExpect(status().isNotFound());
    }

    // ---- ATTACHMENTS ----
    @Test @Order(20) void uploadAttachment() throws Exception {
        MockHttpSession s = loginAs("cust1");
        // First create a booking
        String futureDate = LocalDate.now().plusDays(34).toString();
        MvcResult r = mvc.perform(post("/api/bookings").session(s).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("serviceId", 1, "bookingDate", futureDate,
                        "startTime", "10:00", "endTime", "11:00"))))
            .andExpect(status().isOk()).andReturn();
        int bookingId = ((Number) parseMap(r).get("id")).intValue();

        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "hello".getBytes());
        mvc.perform(multipart("/api/attachments/booking/" + bookingId).file(file).session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.originalName").value("test.txt"));
    }

    @Test @Order(21) void listAttachments() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/attachments/booking/1").session(s)).andExpect(status().isOk());
    }

    // ---- PAGE CONTROLLER ----
    @Test @Order(30) void rootRedirects() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection());
    }

    // ---- USERS ----
    @Test @Order(40) void listPhotographers() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/users/photographers").session(s)).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(greaterThan(0)));
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
