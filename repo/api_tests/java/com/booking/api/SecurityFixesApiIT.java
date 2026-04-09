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

class SecurityFixesApiIT extends BaseApiIT {

    // ---- IDOR: Attachment download requires booking access ----

    @Test void attachmentDownloadDeniedForNonOwner() throws Exception {
        MockHttpSession admin = loginAs("admin");
        MockHttpSession cust2 = loginAs("cust2");
        String d = LocalDate.now().plusDays(70).toString();

        // Create booking as admin for cust1
        MvcResult br = mvc.perform(post("/api/bookings").session(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("serviceId", 1, "customerId", 4, "bookingDate", d,
                        "startTime", "09:00", "endTime", "10:00"))))
            .andExpect(status().isOk()).andReturn();
        int bookingId = ((Number) parseMap(br).get("id")).intValue();

        // Upload attachment as admin
        MockMultipartFile file = new MockMultipartFile("file", "secret.pdf", "application/pdf", "secret".getBytes());
        MvcResult ar = mvc.perform(multipart("/api/attachments/booking/" + bookingId).file(file).session(admin))
            .andExpect(status().isOk()).andReturn();
        int attachId = ((Number) parseMap(ar).get("id")).intValue();

        // cust2 tries to download — should be denied (IDOR fix)
        mvc.perform(get("/api/attachments/" + attachId + "/download").session(cust2))
            .andExpect(status().isForbidden());
    }

    @Test void attachmentListDeniedForNonOwner() throws Exception {
        MockHttpSession cust2 = loginAs("cust2");
        // Booking 1 belongs to cust1 seed data — cust2 should not see its attachments
        // (If there's no booking with that ID in this test run, 404 is also acceptable)
        mvc.perform(get("/api/attachments/booking/1").session(cust2))
            .andExpect(status().is4xxClientError());
    }

    // ---- Data redaction: phone masked in User responses ----

    @Test void userPhoneMaskedInResponse() throws Exception {
        MockHttpSession admin = loginAs("admin");
        mvc.perform(get("/api/users").session(admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].phone", startsWith("***")))
            .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test void authMeDoesNotLeakPhone() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/auth/me").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.phone").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    // ---- Health endpoint: details not exposed without auth ----

    @Test void healthEndpointNoDetailsForAnonymous() throws Exception {
        mvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components").doesNotExist());
    }

    // ---- Blacklist: empty reason rejected ----

    @Test void blacklistEmptyReasonRejected() throws Exception {
        MockHttpSession admin = loginAs("admin");
        mvc.perform(post("/api/blacklist").session(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("userId", 5, "reason", ""))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("required")));
    }

    @Test void blacklistNullReasonRejected() throws Exception {
        MockHttpSession admin = loginAs("admin");
        mvc.perform(post("/api/blacklist").session(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("userId", 5))))
            .andExpect(status().isBadRequest());
    }

    // ---- Idempotency: scoped per order+action ----

    @Test void idempotencyTokenScopedPerAction() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        MockHttpSession photo = loginAs("photo1");

        // Create order
        MvcResult cr = mvc.perform(post("/api/orders").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "scoped-test-key")
                .content(json(Map.of("listingId", 3, "timeSlotId", 4))))
            .andExpect(status().isOk()).andReturn();
        int orderId = ((Number) parseMap(cr).get("id")).intValue();

        // Same key but different action (CONFIRM) should NOT collide
        mvc.perform(post("/api/orders/" + orderId + "/confirm").session(photo)
                .header("Idempotency-Key", "scoped-test-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    // ---- Address: server-side ZIP/state validation ----

    @Test void addressInvalidZipRejected() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(post("/api/addresses").session(s)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("label", "Bad", "street", "1 St", "city", "X",
                        "state", "IL", "postalCode", "ABCDE", "country", "US"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("ZIP")));
    }

    @Test void addressInvalidStateRejected() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(post("/api/addresses").session(s)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("label", "Bad", "street", "1 St", "city", "X",
                        "state", "ZZ", "postalCode", "12345", "country", "US"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("state")));
    }

    @Test void addressValidZipAccepted() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(post("/api/addresses").session(s)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("label", "Good", "street", "1 St", "city", "X",
                        "state", "CA", "postalCode", "90210", "country", "US"))))
            .andExpect(status().isOk());
    }

    // ---- Delivery mode: ONSITE/PICKUP/COURIER ----

    @Test void orderWithDeliveryMode() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        mvc.perform(post("/api/orders").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "delivery-pickup-1")
                .content(json(Map.of("listingId", 3, "timeSlotId", 4, "deliveryMode", "PICKUP"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deliveryMode").value("PICKUP"));
    }

    @Test void orderCourierRequiresAddress() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        mvc.perform(post("/api/orders").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "delivery-courier-noaddr")
                .content(json(Map.of("listingId", 3, "timeSlotId", 4, "deliveryMode", "COURIER"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("Address is required")));
    }

    @Test void orderInvalidDeliveryModeRejected() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        mvc.perform(post("/api/orders").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "delivery-bad-mode")
                .content(json(Map.of("listingId", 3, "timeSlotId", 4, "deliveryMode", "DRONE"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("Invalid delivery mode")));
    }

    // ---- Pagination on search ----

    @Test void searchReturnsPaginatedResult() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/listings/search?page=1&size=2").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.size").value(2))
            .andExpect(jsonPath("$.total").isNumber())
            .andExpect(jsonPath("$.totalPages").isNumber());
    }

    // ---- Notification read/archive ----

    @Test void notificationReadAndArchive() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        // Trigger a notification by creating an order
        mvc.perform(post("/api/orders").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "notif-test-1")
                .content(json(Map.of("listingId", 3, "timeSlotId", 4))))
            .andExpect(status().isOk());

        // List notifications
        MvcResult nr = mvc.perform(get("/api/notifications").session(cust))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray()).andReturn();

        // If any notifications exist, test mark read + archive
        var notifs = om.readValue(nr.getResponse().getContentAsString(), java.util.List.class);
        if (!notifs.isEmpty()) {
            int notifId = ((Number) ((java.util.Map<?,?>) notifs.get(0)).get("id")).intValue();
            mvc.perform(post("/api/notifications/" + notifId + "/read").session(cust))
                .andExpect(status().isOk());
            mvc.perform(post("/api/notifications/" + notifId + "/archive").session(cust))
                .andExpect(status().isOk());
        }
    }
}
