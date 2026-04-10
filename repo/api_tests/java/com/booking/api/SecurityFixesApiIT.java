package com.booking.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SecurityFixesApiIT extends BaseApiIT {

    // ---- Legacy attachment endpoint removed (controller deleted — returns 404) ----

    @Test void attachmentEndpointRemoved() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/attachments/booking/1").session(s))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/attachments/1/download").session(s))
            .andExpect(status().isNotFound());
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

    // ---- Photographer DTO shields sensitive data ----

    @Test void photographerDiscoveryHidesSensitiveFields() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/users/photographers").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").isNumber())
            .andExpect(jsonPath("$[0].fullName").isNotEmpty())
            .andExpect(jsonPath("$[0].email").doesNotExist())
            .andExpect(jsonPath("$[0].phone").doesNotExist())
            .andExpect(jsonPath("$[0].enabled").doesNotExist())
            .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
            .andExpect(jsonPath("$[0].roleId").doesNotExist())
            .andExpect(jsonPath("$[0].department").doesNotExist());
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

        MvcResult cr = mvc.perform(post("/api/orders").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "scoped-test-key")
                .content(json(Map.of("listingId", 3, "timeSlotId", 4))))
            .andExpect(status().isOk()).andReturn();
        int orderId = ((Number) parseMap(cr).get("id")).intValue();

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
        mvc.perform(post("/api/orders").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "notif-test-1")
                .content(json(Map.of("listingId", 3, "timeSlotId", 4))))
            .andExpect(status().isOk());

        MvcResult nr = mvc.perform(get("/api/notifications").session(cust))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray()).andReturn();

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
