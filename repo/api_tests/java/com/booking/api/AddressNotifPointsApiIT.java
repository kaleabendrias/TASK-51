package com.booking.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AddressNotifPointsApiIT extends BaseApiIT {

    // ---- ADDRESS MANAGEMENT ----

    @Test
    void listAddresses() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/addresses").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(2)));
    }

    @Test
    void createAddress() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(post("/api/addresses").session(s)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("label", "Studio", "street", "789 Art Ln",
                        "city", "Chicago", "state", "IL", "postalCode", "60601",
                        "country", "US", "isDefault", false))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.label").value("Studio"))
            .andExpect(jsonPath("$.userId").value(4));
    }

    @Test
    void updateAddress() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(put("/api/addresses/1").session(s)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("label", "Updated Home", "street", "123 Main St Updated",
                        "city", "Springfield", "state", "IL", "postalCode", "62701",
                        "country", "US", "isDefault", true))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.label").value("Updated Home"));
    }

    @Test
    void otherUserCannotUpdateMyAddress() throws Exception {
        MockHttpSession s = loginAs("cust2");
        mvc.perform(put("/api/addresses/1").session(s)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("label", "Hacked", "street", "x", "city", "x",
                        "state", "x", "postalCode", "x", "country", "US", "isDefault", false))))
            .andExpect(status().isForbidden());
    }

    // Creates its own address so it is not order-dependent on seeded data
    @Test
    void deleteAddress() throws Exception {
        MockHttpSession s = loginAs("cust1");
        // Create a fresh address to delete — never relies on a seeded ID
        MvcResult createR = mvc.perform(post("/api/addresses").session(s)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("label", "TempToDelete", "street", "999 Delete St",
                        "city", "Chicago", "state", "IL", "postalCode", "60601",
                        "country", "US", "isDefault", false))))
            .andExpect(status().isOk()).andReturn();
        int newAddrId = ((Number) parseMap(createR).get("id")).intValue();

        mvc.perform(delete("/api/addresses/" + newAddrId).session(s)
                .header("Origin", TEST_ORIGIN))
            .andExpect(status().isOk());

        // Verify the address no longer appears in the list
        mvc.perform(get("/api/addresses").session(s))
            .andExpect(jsonPath("$[?(@.id==" + newAddrId + ")]").doesNotExist());
    }

    // ---- NOTIFICATION PREFERENCES ----

    @Test
    void getNotificationPreferences() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/notifications/preferences").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.compliance").value(true))
            .andExpect(jsonPath("$.orderUpdates").value(true));
    }

    @Test
    void updatePreferencesComplianceCannotBeMuted() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(put("/api/notifications/preferences").session(s)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("orderUpdates", false, "holds", false,
                        "reminders", false, "approvals", false,
                        "compliance", false, "muteNonCritical", true))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.compliance").value(true))  // forced back to true
            .andExpect(jsonPath("$.muteNonCritical").value(true))
            .andExpect(jsonPath("$.reminders").value(false));
    }

    // ---- Notifications list returns a structured array ----

    @Test
    void notificationsListReturnsStructuredArray() throws Exception {
        // First create an order so there is at least one notification for cust1
        MockHttpSession photo = loginAs("photo1");
        MockHttpSession cust = loginAs("cust1");

        // Unmute cust1 to ensure notifications arrive
        mvc.perform(put("/api/notifications/preferences").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("orderUpdates", true, "holds", true, "reminders", true,
                        "approvals", true, "compliance", true, "muteNonCritical", false))))
            .andExpect(status().isOk());

        MvcResult slotR = mvc.perform(post("/api/timeslots").session(photo)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 1, "slotDate", "2027-11-01",
                        "startTime", "09:00", "endTime", "10:00", "capacity", 5))))
            .andExpect(status().isOk()).andReturn();
        int slotId = ((Number) parseMap(slotR).get("id")).intValue();

        mvc.perform(post("/api/orders").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "notif-mask-" + java.util.UUID.randomUUID())
                .content(json(Map.of("listingId", 1, "timeSlotId", slotId))))
            .andExpect(status().isOk());

        // Verify list is an array with proper per-item structure
        mvc.perform(get("/api/notifications").session(cust))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$[0].id").isNumber())
            .andExpect(jsonPath("$[0].status").isString());
    }

    // ---- POINTS & AWARDS ----

    @Test
    void pointsBalance() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/points/balance").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.balance").isNumber());
    }

    // ---- Leaderboard is sorted descending by points ----

    @Test
    void pointsLeaderboardIsDescending() throws Exception {
        // Award cust1 a large amount to ensure a non-trivial leaderboard
        MockHttpSession admin = loginAs("admin");
        mvc.perform(post("/api/points/award").session(admin)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("userId", 4, "points", 500, "description", "Leaderboard sort check"))))
            .andExpect(status().isOk());

        MockHttpSession s = loginAs("cust1");
        MvcResult r = mvc.perform(get("/api/points/leaderboard").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andReturn();

        List<Map<String, Object>> entries = om.readValue(
                r.getResponse().getContentAsString(),
                om.getTypeFactory().constructCollectionType(List.class, Map.class));
        org.junit.jupiter.api.Assertions.assertFalse(entries.isEmpty(), "Leaderboard must not be empty");
        int prev = Integer.MAX_VALUE;
        for (Map<String, Object> e : entries) {
            int pts = ((Number) e.get("points")).intValue();
            org.junit.jupiter.api.Assertions.assertTrue(pts <= prev,
                    "Leaderboard must be descending; found " + pts + " after " + prev);
            prev = pts;
        }
    }

    @Test
    void adminManualAdjustmentRequiresNote() throws Exception {
        MockHttpSession s = loginAs("admin");
        // Empty reason -> rejected
        mvc.perform(post("/api/points/adjust").session(s)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("userId", 4, "points", 50, "reason", ""))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("mandatory")));
    }

    @Test
    void adminManualAdjustmentSuccess() throws Exception {
        MockHttpSession s = loginAs("admin");
        mvc.perform(post("/api/points/adjust").session(s)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("userId", 4, "points", 50, "reason", "API test bonus"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.balanceAfter").isNumber())
            .andExpect(jsonPath("$.reason").value("API test bonus"));
    }

    @Test
    void adjustmentAuditLogImmutable() throws Exception {
        MockHttpSession s = loginAs("admin");
        // Make adjustment
        mvc.perform(post("/api/points/adjust").session(s)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("userId", 4, "points", -10, "reason", "Audit test"))))
            .andExpect(status().isOk());
        // Verify audit log has it
        mvc.perform(get("/api/points/adjustments").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.reason=='Audit test')]").exists());
    }

    @Test
    void pointsRulesCRUD() throws Exception {
        MockHttpSession s = loginAs("admin");
        // List
        mvc.perform(get("/api/points/rules").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(2)));
        // Create
        mvc.perform(post("/api/points/rules").session(s)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "TEST_RULE", "description", "Test",
                        "points", 5, "scope", "TEAM", "triggerEvent", "TEST_EVENT"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scope").value("TEAM"));
    }

    @Test
    void customerCannotAccessRules() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/points/rules").session(s))
            .andExpect(status().isForbidden());
    }

    // ---- BLACKLIST INTEGRATION ----

    @Test
    void blacklistBlocksApiAccess() throws Exception {
        MockHttpSession admin = loginAs("admin");
        // Blacklist cust2
        mvc.perform(post("/api/blacklist").session(admin)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("userId", 5, "reason", "API test block", "durationDays", 1))))
            .andExpect(status().isOk());

        // Re-enable for login to work (blacklist disables user)
        mvc.perform(patch("/api/users/5/enabled").session(admin)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("enabled", true))))
            .andExpect(status().isOk());

        // cust2 can login but API calls are blocked
        MockHttpSession cust2 = loginAs("cust2");
        mvc.perform(get("/api/orders").session(cust2))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error", containsString("blacklisted")));

        // Retrieve the dynamic blacklist entry ID for cust2 (userId=5)
        MvcResult blList = mvc.perform(get("/api/blacklist").session(admin))
            .andExpect(status().isOk()).andReturn();
        List<Map<String, Object>> blEntries = om.readValue(
                blList.getResponse().getContentAsString(),
                om.getTypeFactory().constructCollectionType(List.class, Map.class));
        int blId = blEntries.stream()
            .filter(e -> Boolean.TRUE.equals(e.get("active"))
                    && ((Number) e.get("userId")).intValue() == 5)
            .mapToInt(e -> ((Number) e.get("id")).intValue())
            .findFirst()
            .orElseThrow(() -> new AssertionError("Expected active blacklist entry for userId=5"));

        // Lift using the dynamic ID
        mvc.perform(post("/api/blacklist/" + blId + "/lift").session(admin)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("reason", "Reinstated after test"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));
    }
}
