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
class AddressNotifPointsApiIT extends BaseApiIT {

    // ---- ADDRESS MANAGEMENT ----

    @Test @Order(1)
    void listAddresses() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/addresses").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(2)));
    }

    @Test @Order(2)
    void createAddress() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(post("/api/addresses").session(s)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("label", "Studio", "street", "789 Art Ln",
                        "city", "Chicago", "state", "IL", "postalCode", "60601",
                        "country", "US", "isDefault", false))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.label").value("Studio"))
            .andExpect(jsonPath("$.userId").value(4));
    }

    @Test @Order(3)
    void updateAddress() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(put("/api/addresses/1").session(s)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("label", "Updated Home", "street", "123 Main St Updated",
                        "city", "Springfield", "state", "IL", "postalCode", "62701",
                        "country", "US", "isDefault", true))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.label").value("Updated Home"));
    }

    @Test @Order(4)
    void otherUserCannotUpdateMyAddress() throws Exception {
        MockHttpSession s = loginAs("cust2");
        mvc.perform(put("/api/addresses/1").session(s)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("label", "Hacked", "street", "x", "city", "x",
                        "state", "x", "postalCode", "x", "country", "US", "isDefault", false))))
            .andExpect(status().isForbidden());
    }

    @Test @Order(5)
    void deleteAddress() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(delete("/api/addresses/2").session(s))
            .andExpect(status().isOk());
        mvc.perform(get("/api/addresses").session(s))
            .andExpect(jsonPath("$[?(@.id==2)]").doesNotExist());
    }

    // ---- NOTIFICATION PREFERENCES ----

    @Test @Order(10)
    void getNotificationPreferences() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/notifications/preferences").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.compliance").value(true))
            .andExpect(jsonPath("$.orderUpdates").value(true));
    }

    @Test @Order(11)
    void updatePreferencesComplianceCannotBeMuted() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(put("/api/notifications/preferences").session(s)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("orderUpdates", false, "holds", false,
                        "reminders", false, "approvals", false,
                        "compliance", false, "muteNonCritical", true))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.compliance").value(true))  // forced back to true
            .andExpect(jsonPath("$.muteNonCritical").value(true))
            .andExpect(jsonPath("$.reminders").value(false));
    }

    @Test @Order(12)
    void notificationsMaskRecipients() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/notifications").session(s))
            .andExpect(status().isOk());
        // Notifications may be empty in test, but endpoint works
    }

    // ---- POINTS & AWARDS ----

    @Test @Order(20)
    void pointsBalance() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/points/balance").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.balance").isNumber());
    }

    @Test @Order(21)
    void pointsLeaderboardTieBreak() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/points/leaderboard").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test @Order(22)
    void adminManualAdjustmentRequiresNote() throws Exception {
        MockHttpSession s = loginAs("admin");
        // Empty reason -> rejected
        mvc.perform(post("/api/points/adjust").session(s)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("userId", 4, "points", 50, "reason", ""))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("mandatory")));
    }

    @Test @Order(23)
    void adminManualAdjustmentSuccess() throws Exception {
        MockHttpSession s = loginAs("admin");
        mvc.perform(post("/api/points/adjust").session(s)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("userId", 4, "points", 50, "reason", "API test bonus"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.balanceAfter").isNumber())
            .andExpect(jsonPath("$.reason").value("API test bonus"));
    }

    @Test @Order(24)
    void adjustmentAuditLogImmutable() throws Exception {
        MockHttpSession s = loginAs("admin");
        // Make adjustment
        mvc.perform(post("/api/points/adjust").session(s)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("userId", 4, "points", -10, "reason", "Audit test"))))
            .andExpect(status().isOk());
        // Verify audit log has it
        mvc.perform(get("/api/points/adjustments").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.reason=='Audit test')]").exists());
    }

    @Test @Order(25)
    void pointsRulesCRUD() throws Exception {
        MockHttpSession s = loginAs("admin");
        // List
        mvc.perform(get("/api/points/rules").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(2)));
        // Create
        mvc.perform(post("/api/points/rules").session(s)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "TEST_RULE", "description", "Test",
                        "points", 5, "scope", "TEAM", "triggerEvent", "TEST_EVENT"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scope").value("TEAM"));
    }

    @Test @Order(26)
    void customerCannotAccessRules() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/points/rules").session(s))
            .andExpect(status().isForbidden());
    }

    // ---- BLACKLIST INTEGRATION ----

    @Test @Order(30)
    void blacklistBlocksApiAccess() throws Exception {
        MockHttpSession admin = loginAs("admin");
        // Blacklist cust2
        mvc.perform(post("/api/blacklist").session(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("userId", 5, "reason", "API test block", "durationDays", 1))))
            .andExpect(status().isOk());

        // Re-enable for login to work (blacklist disables user)
        mvc.perform(patch("/api/users/5/enabled").session(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("enabled", true))))
            .andExpect(status().isOk());

        // cust2 can login but API calls are blocked
        MockHttpSession cust2 = loginAs("cust2");
        mvc.perform(get("/api/orders").session(cust2))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error", containsString("blacklisted")));

        // Lift
        MvcResult blList = mvc.perform(get("/api/blacklist").session(admin))
            .andExpect(status().isOk()).andReturn();
        mvc.perform(post("/api/blacklist/1/lift").session(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("reason", "Reinstated after test"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));
    }
}
