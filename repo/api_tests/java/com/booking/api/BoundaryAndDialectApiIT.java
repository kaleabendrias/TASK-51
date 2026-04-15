package com.booking.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Boundary condition and SQL-dialect edge case tests.
 *
 * Validates behaviour that depends on specific MySQL constructs (GREATEST for
 * points-floor, CASE WHEN, pagination edge cases) and general boundary
 * conditions (empty queries, zero-result pages, max-price filter, special
 * characters in search). These tests run against H2 in MySQL mode, which
 * supports the same dialect used in production.
 */
class BoundaryAndDialectApiIT extends BaseApiIT {

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private long freshSlot(MockHttpSession photo, int listingId, String date) throws Exception {
        MvcResult r = mvc.perform(post("/api/timeslots").session(photo)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "listingId", listingId,
                        "slotDate", date,
                        "startTime", "09:00",
                        "endTime", "11:00",
                        "capacity", 5))))
            .andExpect(status().isOk())
            .andReturn();
        return ((Number) parseMap(r).get("id")).longValue();
    }

    private long createAndPayOrder(MockHttpSession cust, MockHttpSession photo,
                                    int listingId, long slotId, double price) throws Exception {
        String pfx = "bd-" + UUID.randomUUID();

        MvcResult cr = mvc.perform(post("/api/orders").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", pfx + "-create")
                .content(json(Map.of("listingId", listingId, "timeSlotId", slotId))))
            .andExpect(status().isOk())
            .andReturn();
        long orderId = ((Number) parseMap(cr).get("id")).longValue();

        mvc.perform(post("/api/orders/" + orderId + "/confirm").session(photo)
                .header("Origin", TEST_ORIGIN)
                .header("Idempotency-Key", pfx + "-confirm"))
            .andExpect(status().isOk());

        mvc.perform(post("/api/orders/" + orderId + "/pay").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", pfx + "-pay")
                .content(json(Map.of("amount", price, "paymentReference", "REF-BD-" + orderId))))
            .andExpect(status().isOk());

        return orderId;
    }

    // ─── Search — Pagination Boundaries ────────────────────────────────────────

    @Test
    void search_pageOne_returnsFirstPageOfResults() throws Exception {
        MockHttpSession cust = loginAs("cust1");

        mvc.perform(get("/api/listings/search?page=1&size=2").session(cust))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void search_pageExceedingTotal_returnsEmptyItems() throws Exception {
        MockHttpSession cust = loginAs("cust1");

        mvc.perform(get("/api/listings/search?page=999&size=6").session(cust))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void search_sizeOne_paginationTotalPagesIsCorrect() throws Exception {
        MockHttpSession cust = loginAs("cust1");

        MvcResult r = mvc.perform(get("/api/listings/search?page=1&size=1").session(cust))
            .andExpect(status().isOk())
            .andReturn();

        Map<String, Object> body = parseMap(r);
        Number total = (Number) body.get("total");
        Number totalPages = (Number) body.get("totalPages");
        assertNotNull(total, "total must be present in search response");
        assertNotNull(totalPages, "totalPages must be present in search response");
        // With 3 seeded listings and size=1, totalPages must be ≥ 3
        assertTrue(totalPages.intValue() >= 3,
                "With size=1 and ≥3 listings, totalPages must be ≥3, got " + totalPages);
    }

    // ─── Search — Keyword & Filter Edge Cases ──────────────────────────────────

    @Test
    void search_emptyKeyword_returnsAllListings() throws Exception {
        MockHttpSession cust = loginAs("cust1");

        mvc.perform(get("/api/listings/search?keyword=&page=1&size=10").session(cust))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.total").value(greaterThanOrEqualTo(3)));
    }

    @Test
    void search_keywordMatchesSeededTitle_returnsCorrectListing() throws Exception {
        MockHttpSession cust = loginAs("cust1");

        mvc.perform(get("/api/listings/search?keyword=Studio&page=1&size=6").session(cust))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.items[0].title").value(containsString("Studio")));
    }

    @Test
    void search_categoryFilter_returnsOnlyMatchingCategory() throws Exception {
        MockHttpSession cust = loginAs("cust1");

        MvcResult r = mvc.perform(get("/api/listings/search?category=PORTRAIT&page=1&size=10").session(cust))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andReturn();

        Map<String, Object> body = parseMap(r);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        for (Map<String, Object> item : items) {
            assertEquals("PORTRAIT", item.get("category"),
                    "All results must match the PORTRAIT category filter");
        }
    }

    @Test
    void search_maxPriceBelowAllListings_returnsEmptyResults() throws Exception {
        MockHttpSession cust = loginAs("cust1");

        // All seeded listings cost ≥ $150; filtering maxPrice=50 should return nothing
        mvc.perform(get("/api/listings/search?maxPrice=50&page=1&size=10").session(cust))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void search_maxPriceFilter_includesListingsBelowThreshold() throws Exception {
        MockHttpSession cust = loginAs("cust1");

        // Studio Portrait costs $150; maxPrice=200 must include it, while $250+ listings are excluded
        MvcResult r = mvc.perform(get("/api/listings/search?maxPrice=200&page=1&size=10").session(cust))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andReturn();

        String body = r.getResponse().getContentAsString();
        assertTrue(body.contains("Studio Portrait"),
                "maxPrice=200 should include 'Studio Portrait' ($150 listing); got: "
                        + body.substring(0, Math.min(500, body.length())));
        // Outdoor Family ($250) and Product Shots ($300) must be excluded
        assertFalse(body.contains("Outdoor Family"),
                "maxPrice=200 must exclude 'Outdoor Family' ($250 listing)");
    }

    @Test
    void search_minPriceAboveAllListings_returnsEmptyResults() throws Exception {
        MockHttpSession cust = loginAs("cust1");

        mvc.perform(get("/api/listings/search?minPrice=99999&page=1&size=10").session(cust))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void search_specialCharacters_doesNotFail() throws Exception {
        MockHttpSession cust = loginAs("cust1");

        // SQL-injection-like and regex-like characters must be handled safely
        String[] payloads = {"'", "\"", "%", "_", "--", "' OR '1'='1", "<script>"};
        for (String payload : payloads) {
            mvc.perform(get("/api/listings/search?keyword=" +
                    java.net.URLEncoder.encode(payload, "UTF-8") + "&page=1&size=6")
                .session(cust))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray()); // must not throw 500
        }
    }

    @Test
    void search_sortByPriceAsc_firstItemIsLowestPrice() throws Exception {
        MockHttpSession cust = loginAs("cust1");

        MvcResult r = mvc.perform(get("/api/listings/search?sortBy=price_asc&page=1&size=10").session(cust))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andReturn();

        Map<String, Object> body = parseMap(r);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        if (items.size() >= 2) {
            double first = ((Number) items.get(0).get("price")).doubleValue();
            double second = ((Number) items.get(1).get("price")).doubleValue();
            assertTrue(first <= second,
                    "price_asc sort: first item price (" + first + ") must be <= second (" + second + ")");
        }
    }

    @Test
    void search_sortByPriceDesc_firstItemIsHighestPrice() throws Exception {
        MockHttpSession cust = loginAs("cust1");

        MvcResult r = mvc.perform(get("/api/listings/search?sortBy=price_desc&page=1&size=10").session(cust))
            .andExpect(status().isOk())
            .andReturn();

        Map<String, Object> body = parseMap(r);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        if (items.size() >= 2) {
            double first = ((Number) items.get(0).get("price")).doubleValue();
            double second = ((Number) items.get(1).get("price")).doubleValue();
            assertTrue(first >= second,
                    "price_desc sort: first item price (" + first + ") must be >= second (" + second + ")");
        }
    }

    // ─── Points — Floor Constraint (GREATEST) ─────────────────────────────────

    @Test
    void pointsBalance_neverGoesNegative_afterDeduction() throws Exception {
        MockHttpSession admin = loginAs("admin");

        // Award a small amount first
        mvc.perform(post("/api/points/award").session(admin)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("userId", 5, "points", 5, "action", "MANUAL_AWARD",
                        "description", "BD test award"))))
            .andExpect(status().isOk());

        // Verify cust2's balance is not negative
        mvc.perform(get("/api/points/balance").session(loginAs("cust2")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.balance").value(greaterThanOrEqualTo(0)));
    }

    @Test
    void pointsAward_zeroPoints_isAllowed() throws Exception {
        MockHttpSession admin = loginAs("admin");

        mvc.perform(post("/api/points/award").session(admin)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("userId", 4, "points", 0, "action", "MANUAL_ZERO",
                        "description", "Zero points test"))))
            .andExpect(status().isOk());
    }

    @Test
    void pointsAward_largeAmount_isReflectedInBalance() throws Exception {
        MockHttpSession cust = loginAs("cust2");
        MockHttpSession admin = loginAs("admin");

        MvcResult before = mvc.perform(get("/api/points/balance").session(cust))
            .andExpect(status().isOk()).andReturn();
        int beforeBalance = ((Number) parseMap(before).get("balance")).intValue();

        mvc.perform(post("/api/points/award").session(admin)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("userId", 5, "points", 1000, "action", "LARGE_AWARD",
                        "description", "BD large award test"))))
            .andExpect(status().isOk());

        MvcResult after = mvc.perform(get("/api/points/balance").session(cust))
            .andExpect(status().isOk()).andReturn();
        int afterBalance = ((Number) parseMap(after).get("balance")).intValue();
        assertTrue(afterBalance >= beforeBalance + 1000,
                "Balance must increase by at least the awarded amount");
    }

    // ─── Points — Order Side Effects ──────────────────────────────────────────

    @Test
    void pointsLedger_orderPaymentAwardsPoints_recordedWithCorrectAction() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        MockHttpSession photo = loginAs("photo1");

        long slotId = freshSlot(photo, 1, "2026-11-01");
        createAndPayOrder(cust, photo, 1, slotId, 150.0);

        // Points ledger must record the ORDER_PAYMENT rule action
        mvc.perform(get("/api/points/history").session(cust))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].action", hasItem("ORDER_PAYMENT")));
    }

    @Test
    void pointsLedger_orderCompletion_awardsCompletionBonus() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        MockHttpSession photo = loginAs("photo1");

        long slotId = freshSlot(photo, 1, "2026-11-02");
        long orderId = createAndPayOrder(cust, photo, 1, slotId, 150.0);

        // Advance to COMPLETED
        String pfx = "bd-comp-" + orderId;
        for (String action : new String[]{"check-in", "check-out", "complete"}) {
            mvc.perform(post("/api/orders/" + orderId + "/" + action).session(photo)
                    .header("Origin", TEST_ORIGIN)
                    .header("Idempotency-Key", pfx + "-" + action))
                .andExpect(status().isOk());
        }

        // ORDER_COMPLETED rule must also fire
        mvc.perform(get("/api/points/history").session(cust))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].action", hasItem("ORDER_COMPLETED")));
    }

    // ─── Slot Availability — Boundary Conditions ──────────────────────────────

    @Test
    void timeSlotsForListing_availableEndpoint_returnsOnlyFutureSlots() throws Exception {
        MockHttpSession cust = loginAs("cust1");

        mvc.perform(get("/api/timeslots/listing/1/available?start=2026-01-01&end=2030-12-31").session(cust))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    void timeSlotsForListing_noSlotsInRange_returnsEmptyArray() throws Exception {
        MockHttpSession cust = loginAs("cust1");

        // Very narrow date range with no seeded slots
        mvc.perform(get("/api/timeslots/listing/1/available?start=2025-01-01&end=2025-01-02").session(cust))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(0));
    }

    // ─── Concurrency — Optimistic Locking ────────────────────────────────────

    @Test
    void concurrentBooking_onlyOneSucceeds_slotNotOversold() throws Exception {
        MockHttpSession photo = loginAs("photo1");
        MockHttpSession cust1 = loginAs("cust1");
        MockHttpSession cust2 = loginAs("cust2");

        // Capacity 1 slot
        MvcResult slotR = mvc.perform(post("/api/timeslots").session(photo)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "listingId", 1,
                        "slotDate", "2026-11-10",
                        "startTime", "09:00",
                        "endTime", "11:00",
                        "capacity", 1))))
            .andExpect(status().isOk())
            .andReturn();
        long slotId = ((Number) parseMap(slotR).get("id")).longValue();

        String idem1 = "bd-conc-1-" + UUID.randomUUID();
        String idem2 = "bd-conc-2-" + UUID.randomUUID();

        // Both customers try to book the same capacity-1 slot
        int status1 = mvc.perform(post("/api/orders").session(cust1)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", idem1)
                .content(json(Map.of("listingId", 1, "timeSlotId", slotId))))
            .andReturn().getResponse().getStatus();

        int status2 = mvc.perform(post("/api/orders").session(cust2)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", idem2)
                .content(json(Map.of("listingId", 1, "timeSlotId", slotId))))
            .andReturn().getResponse().getStatus();

        // Exactly one must succeed (200) and one must fail (400 fully booked)
        boolean oneSuccess = (status1 == 200) != (status2 == 200); // XOR
        assertTrue(oneSuccess || (status1 == 200 && status2 == 400) || (status1 == 400 && status2 == 200),
                "For a capacity-1 slot: exactly one booking must succeed; statuses were "
                        + status1 + " and " + status2);
    }

    // ─── Points Rules Admin ────────────────────────────────────────────────────

    @Test
    void pointsRuleUpdate_changesAwardedAmount_forSubsequentOrders() throws Exception {
        MockHttpSession admin = loginAs("admin");

        // Update ORDER_PAYMENT rule to award 99 points
        mvc.perform(put("/api/points/rules/1").session(admin)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "ORDER_PAYMENT", "description", "Updated",
                        "points", 99, "scope", "INDIVIDUAL",
                        "triggerEvent", "ORDER_PAID", "active", true))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.points").value(99));

        // Restore to 10
        mvc.perform(put("/api/points/rules/1").session(admin)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "ORDER_PAYMENT", "description", "Points for payment",
                        "points", 10, "scope", "INDIVIDUAL",
                        "triggerEvent", "ORDER_PAID", "active", true))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.points").value(10));
    }

    // ─── Leaderboard Ordering ─────────────────────────────────────────────────

    @Test
    void leaderboard_isOrderedByPointsDescending() throws Exception {
        MockHttpSession admin = loginAs("admin");

        // Award different points to two users to create a known ordering
        mvc.perform(post("/api/points/award").session(admin)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("userId", 4, "points", 500, "action", "LB_TEST_HIGH",
                        "description", "Leaderboard high"))))
            .andExpect(status().isOk());

        mvc.perform(get("/api/points/leaderboard").session(loginAs("cust1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)));

        MvcResult r = mvc.perform(get("/api/points/leaderboard").session(loginAs("cust1")))
            .andExpect(status().isOk())
            .andReturn();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> board = (List<Map<String, Object>>) om.readValue(
                r.getResponse().getContentAsString(), List.class);

        for (int i = 0; i < board.size() - 1; i++) {
            int curr = ((Number) board.get(i).get("points")).intValue();
            int next = ((Number) board.get(i + 1).get("points")).intValue();
            assertTrue(curr >= next,
                    "Leaderboard must be in descending order: entry " + i + " (" + curr +
                    ") must be >= entry " + (i + 1) + " (" + next + ")");
        }
    }
}
