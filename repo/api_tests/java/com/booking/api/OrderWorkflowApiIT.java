package com.booking.api;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderWorkflowApiIT extends BaseApiIT {

    @Test @Order(1)
    void createOrderHappyPath() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        mvc.perform(post("/api/orders").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "api-test-create-1")
                .content(json(Map.of("listingId", 1, "timeSlotId", 1, "addressId", 1, "notes", "API test"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CREATED"))
            .andExpect(jsonPath("$.orderNumber").isNotEmpty())
            .andExpect(jsonPath("$.paymentDeadline").isNotEmpty())
            .andExpect(jsonPath("$.totalPrice").isNumber());
    }

    @Test @Order(2)
    void idempotencyReturnsCachedResponse() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        // First call
        mvc.perform(post("/api/orders").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "api-test-idem-dup")
                .content(json(Map.of("listingId", 1, "timeSlotId", 2))))
            .andExpect(status().isOk());
        // Retry with same key
        mvc.perform(post("/api/orders").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "api-test-idem-dup")
                .content(json(Map.of("listingId", 1, "timeSlotId", 2))))
            .andExpect(status().isOk());
    }

    @Test @Order(3)
    void oversellPrevention() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        // slot 1 capacity=1, should be taken from test 1
        mvc.perform(post("/api/orders").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "api-test-oversell")
                .content(json(Map.of("listingId", 1, "timeSlotId", 1))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("fully booked")));
    }

    @Test @Order(4)
    void fullLifecycleConfirmPayCheckInOutComplete() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        MockHttpSession photo = loginAs("photo1");

        // Create
        MvcResult cr = mvc.perform(post("/api/orders").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "api-lifecycle-create")
                .content(json(Map.of("listingId", 1, "timeSlotId", 5))))
            .andExpect(status().isOk()).andReturn();
        int orderId = ((Number) parseMap(cr).get("id")).intValue();

        // Confirm
        mvc.perform(post("/api/orders/" + orderId + "/confirm").session(photo)
                .header("Idempotency-Key", "api-lc-confirm"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // Pay
        mvc.perform(post("/api/orders/" + orderId + "/pay").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "api-lc-pay")
                .content(json(Map.of("amount", 150.0, "paymentReference", "REF-API"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PAID"));

        // Check-in
        mvc.perform(post("/api/orders/" + orderId + "/check-in").session(photo)
                .header("Idempotency-Key", "api-lc-checkin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CHECKED_IN"));

        // Check-out
        mvc.perform(post("/api/orders/" + orderId + "/check-out").session(photo)
                .header("Idempotency-Key", "api-lc-checkout"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CHECKED_OUT"));

        // Complete
        mvc.perform(post("/api/orders/" + orderId + "/complete").session(photo)
                .header("Idempotency-Key", "api-lc-complete"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Audit trail
        mvc.perform(get("/api/orders/" + orderId + "/audit").session(cust))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(6)));
    }

    @Test @Order(5)
    void cancelBranchAutoRefundsIfPaid() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        MockHttpSession photo = loginAs("photo1");

        // Create a fresh slot for listing 3
        MvcResult slotR = mvc.perform(post("/api/timeslots").session(photo)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 3, "slotDate", "2026-09-01",
                        "startTime", "09:00", "endTime", "11:00", "capacity", 1))))
            .andExpect(status().isOk()).andReturn();
        int freshSlot = ((Number) parseMap(slotR).get("id")).intValue();

        MvcResult cr = mvc.perform(post("/api/orders").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "api-cancel-create")
                .content(json(Map.of("listingId", 3, "timeSlotId", freshSlot))))
            .andExpect(status().isOk()).andReturn();
        int orderId = ((Number) parseMap(cr).get("id")).intValue();

        // Confirm + Pay
        mvc.perform(post("/api/orders/" + orderId + "/confirm").session(photo)
                .header("Idempotency-Key", "api-cancel-confirm"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/orders/" + orderId + "/pay").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "api-cancel-pay")
                .content(json(Map.of("amount", 300.0, "paymentReference", "REF-C"))))
            .andExpect(status().isOk());

        // Cancel from PAID state -> auto-refund
        mvc.perform(post("/api/orders/" + orderId + "/cancel").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "api-cancel-do")
                .content(json(Map.of("reason", "Changed plans"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.refundAmount").value(300.0));
    }

    @Test @Order(6)
    void invalidTransitionBlocked() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        // Create order on slot 3 (listing 2, capacity 2)
        MvcResult cr = mvc.perform(post("/api/orders").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "api-invalid-create")
                .content(json(Map.of("listingId", 2, "timeSlotId", 3))))
            .andExpect(status().isOk()).andReturn();
        int orderId = ((Number) parseMap(cr).get("id")).intValue();

        // Try to skip to COMPLETED from CREATED -> 400
        MockHttpSession photo = loginAs("photo2");
        mvc.perform(post("/api/orders/" + orderId + "/complete").session(photo)
                .header("Idempotency-Key", "api-invalid-complete"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("Invalid transition")));
    }

    @Test @Order(7)
    void customerCannotConfirm() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        MvcResult cr = mvc.perform(post("/api/orders").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "api-rbac-confirm-create")
                .content(json(Map.of("listingId", 2, "timeSlotId", 3))))
            .andExpect(status().isOk()).andReturn();
        int orderId = ((Number) parseMap(cr).get("id")).intValue();

        mvc.perform(post("/api/orders/" + orderId + "/confirm").session(cust)
                .header("Idempotency-Key", "api-rbac-confirm-do"))
            .andExpect(status().isForbidden());
    }

    @Test @Order(8)
    void otherPhotographerCannotConfirm() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        MockHttpSession photo1 = loginAs("photo1");

        // Create a fresh slot for listing 3 (owned by photo1)
        MvcResult slotR = mvc.perform(post("/api/timeslots").session(photo1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 3, "slotDate", "2026-09-05",
                        "startTime", "10:00", "endTime", "12:00", "capacity", 1))))
            .andExpect(status().isOk()).andReturn();
        int freshSlot = ((Number) parseMap(slotR).get("id")).intValue();

        MvcResult cr = mvc.perform(post("/api/orders").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "api-wrong-photo-create")
                .content(json(Map.of("listingId", 3, "timeSlotId", freshSlot))))
            .andExpect(status().isOk()).andReturn();
        int orderId = ((Number) parseMap(cr).get("id")).intValue();

        // photo2 tries to confirm photo1's order
        MockHttpSession photo2 = loginAs("photo2");
        mvc.perform(post("/api/orders/" + orderId + "/confirm").session(photo2)
                .header("Idempotency-Key", "api-wrong-photo-confirm"))
            .andExpect(status().isForbidden());
    }
}
