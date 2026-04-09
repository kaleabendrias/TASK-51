package com.booking.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CoverageBoostApiIT extends BaseApiIT {

    @Test void updateServiceById() throws Exception {
        MockHttpSession s = loginAs("admin");
        mvc.perform(put("/api/services/1").session(s).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Updated Portrait", "price", 120.0,
                        "durationMinutes", 60, "active", true))))
            .andExpect(status().isOk());
    }

    @Test void updateBookingStatus() throws Exception {
        MockHttpSession s = loginAs("admin");
        String d = LocalDate.now().plusDays(50).toString();
        MvcResult r = mvc.perform(post("/api/bookings").session(s).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("serviceId", 1, "customerId", 4, "bookingDate", d,
                        "startTime", "09:00", "endTime", "10:00"))))
            .andExpect(status().isOk()).andReturn();
        int id = ((Number) parseMap(r).get("id")).intValue();
        mvc.perform(patch("/api/bookings/" + id + "/status").session(s)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "CANCELLED"))))
            .andExpect(status().isOk());
    }

    @Test void orderRefundByAdmin() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        MockHttpSession admin = loginAs("admin");
        MockHttpSession photo = loginAs("photo1");
        // Create a fresh slot
        MvcResult slotR = mvc.perform(post("/api/timeslots").session(photo)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 3, "slotDate", "2026-09-10",
                        "startTime", "09:00", "endTime", "11:00", "capacity", 1))))
            .andExpect(status().isOk()).andReturn();
        int freshSlot = ((Number) parseMap(slotR).get("id")).intValue();

        MvcResult r = mvc.perform(post("/api/orders").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "cov-refund-create")
                .content(json(Map.of("listingId", 3, "timeSlotId", freshSlot))))
            .andExpect(status().isOk()).andReturn();
        int id = ((Number) parseMap(r).get("id")).intValue();
        // Full flow to COMPLETED
        mvc.perform(post("/api/orders/" + id + "/confirm").session(photo)
                .header("Idempotency-Key", "cov-refund-c")).andExpect(status().isOk());
        mvc.perform(post("/api/orders/" + id + "/pay").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "cov-refund-p")
                .content(json(Map.of("amount", 300.0, "paymentReference", "R1")))).andExpect(status().isOk());
        mvc.perform(post("/api/orders/" + id + "/check-in").session(photo)
                .header("Idempotency-Key", "cov-refund-ci")).andExpect(status().isOk());
        mvc.perform(post("/api/orders/" + id + "/check-out").session(photo)
                .header("Idempotency-Key", "cov-refund-co")).andExpect(status().isOk());
        mvc.perform(post("/api/orders/" + id + "/complete").session(photo)
                .header("Idempotency-Key", "cov-refund-done")).andExpect(status().isOk());
        // Admin refund
        mvc.perform(post("/api/orders/" + id + "/refund").session(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "cov-refund-refund")
                .content(json(Map.of("amount", 300.0, "reason", "Quality issue"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test void rescheduleOrder() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        MockHttpSession photo = loginAs("photo1");
        // Create two fresh time slots for listing 1
        MvcResult slot1R = mvc.perform(post("/api/timeslots").session(photo)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 1, "slotDate", "2026-08-01",
                        "startTime", "14:00", "endTime", "15:00", "capacity", 1))))
            .andExpect(status().isOk()).andReturn();
        int slotA = ((Number) parseMap(slot1R).get("id")).intValue();

        MvcResult slot2R = mvc.perform(post("/api/timeslots").session(photo)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 1, "slotDate", "2026-08-02",
                        "startTime", "14:00", "endTime", "15:00", "capacity", 1))))
            .andExpect(status().isOk()).andReturn();
        int slotB = ((Number) parseMap(slot2R).get("id")).intValue();

        // Create order on slot A
        MvcResult r = mvc.perform(post("/api/orders").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "cov-resched-create")
                .content(json(Map.of("listingId", 1, "timeSlotId", slotA))))
            .andExpect(status().isOk()).andReturn();
        int orderId = ((Number) parseMap(r).get("id")).intValue();

        // Reschedule to slot B
        mvc.perform(post("/api/orders/" + orderId + "/reschedule").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "cov-resched-do")
                .content(json(Map.of("newTimeSlotId", slotB))))
            .andExpect(status().isOk());
    }

    @Test void uploadAndDownloadAttachment() throws Exception {
        MockHttpSession s = loginAs("cust1");
        String d = LocalDate.now().plusDays(55).toString();
        MvcResult bookR = mvc.perform(post("/api/bookings").session(s)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("serviceId", 1, "bookingDate", d,
                        "startTime", "10:00", "endTime", "11:00"))))
            .andExpect(status().isOk()).andReturn();
        int bookingId = ((Number) parseMap(bookR).get("id")).intValue();

        MockMultipartFile file = new MockMultipartFile("file", "report.pdf",
                "application/pdf", "fake pdf content".getBytes());
        MvcResult upR = mvc.perform(multipart("/api/attachments/booking/" + bookingId)
                .file(file).session(s))
            .andExpect(status().isOk()).andReturn();
        int attachId = ((Number) parseMap(upR).get("id")).intValue();

        // Download
        mvc.perform(get("/api/attachments/" + attachId + "/download").session(s))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", containsString("report.pdf")));

        // Delete
        mvc.perform(delete("/api/attachments/" + attachId).session(s))
            .andExpect(status().isOk());
    }

    @Test void chatImageDownload() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        MvcResult sendR = mvc.perform(post("/api/messages/send").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("recipientId", 2, "content", "hi for download"))))
            .andExpect(status().isOk()).andReturn();
        long convId = ((Number) parseMap(sendR).get("conversationId")).longValue();

        MockMultipartFile img = new MockMultipartFile("file", "dl.jpg",
                "image/jpeg", new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF});
        MvcResult imgR = mvc.perform(multipart("/api/messages/conversations/" + convId + "/image")
                .file(img).session(cust))
            .andExpect(status().isOk()).andReturn();
        Map<String, Object> att = (Map<String, Object>) parseMap(imgR).get("attachment");
        int attId = ((Number) att.get("id")).intValue();

        mvc.perform(get("/api/messages/attachments/" + attId + "/download").session(cust))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", containsString("dl.jpg")));
    }

    @Test void chatImageNotFound() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/messages/attachments/99999/download").session(s))
            .andExpect(status().isNotFound());
    }

    @Test void orderNotFound() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/orders/99999").session(s))
            .andExpect(status().isNotFound());
    }

    @Test void addressNotFound() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/addresses/99999").session(s))
            .andExpect(status().isNotFound());
    }

    @Test void deleteAddressNotOwned() throws Exception {
        MockHttpSession s = loginAs("cust2");
        mvc.perform(delete("/api/addresses/1").session(s))
            .andExpect(status().isForbidden());
    }

    @Test void pointsHistoryForUser() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/points/history").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test void notificationPreferencesUpdate() throws Exception {
        MockHttpSession s = loginAs("photo1");
        mvc.perform(get("/api/notifications/preferences").session(s))
            .andExpect(status().isOk());
        mvc.perform(put("/api/notifications/preferences").session(s)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("orderUpdates", true, "holds", false,
                        "reminders", true, "approvals", true,
                        "compliance", true, "muteNonCritical", false))))
            .andExpect(status().isOk());
    }

    @Test void blacklistCheckUser() throws Exception {
        MockHttpSession s = loginAs("admin");
        mvc.perform(get("/api/blacklist/user/4").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.blacklisted").value(false));
    }

    @Test void createListingByCustomerDenied() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(post("/api/listings").session(s)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("title", "X", "price", 50, "durationMinutes", 30))))
            .andExpect(status().isForbidden());
    }

    @Test void updateListingByNonOwnerDenied() throws Exception {
        MockHttpSession s = loginAs("photo2");
        mvc.perform(put("/api/listings/1").session(s)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("title", "Hacked", "price", 1, "durationMinutes", 1,
                        "maxConcurrent", 1, "active", true))))
            .andExpect(status().isForbidden());
    }

    @Test void getBookingAccessDenied() throws Exception {
        // cust2 trying to access cust1's booking
        MockHttpSession admin = loginAs("admin");
        String d = LocalDate.now().plusDays(60).toString();
        MvcResult r = mvc.perform(post("/api/bookings").session(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("serviceId", 1, "customerId", 4, "bookingDate", d,
                        "startTime", "09:00", "endTime", "10:00"))))
            .andExpect(status().isOk()).andReturn();
        int id = ((Number) parseMap(r).get("id")).intValue();

        MockHttpSession cust2 = loginAs("cust2");
        mvc.perform(get("/api/bookings/" + id).session(cust2))
            .andExpect(status().isForbidden());
    }
}
