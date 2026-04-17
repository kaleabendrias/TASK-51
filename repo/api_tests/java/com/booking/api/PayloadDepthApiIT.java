package com.booking.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies that API responses carry complete, well-typed payloads — not just a
 * status code.  Where existing tests in other suites only check 1-2 fields this
 * class asserts the full field set, absence of sensitive keys, and typed
 * assertions (isNumber, isBoolean, etc.) on every tested endpoint.
 */
class PayloadDepthApiIT extends BaseApiIT {

    // ── Auth: login ───────────────────────────────────────────────────────────

    @Test
    void login_responseContainsAllFiveUserFields() throws Exception {
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", "cust1", "password", "password123"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.username").value("cust1"))
            .andExpect(jsonPath("$.email").isString())
            .andExpect(jsonPath("$.fullName").isString())
            .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void login_responseDoesNotExposePasswordHashOrRawPassword() throws Exception {
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", "admin", "password", "password123"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andExpect(jsonPath("$.password").doesNotExist());
    }

    // ── Auth: register ────────────────────────────────────────────────────────

    @Test
    void register_responseContainsUserIdAndMessage() throws Exception {
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "username", "pdepth_reg1",
                        "email",    "pdepth_reg1@t.com",
                        "password", "pass123",
                        "fullName", "Payload Depth",
                        "phone",    "+1-555-9876"))))  // phone required so phone masking tests pass regardless of sort order
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").isNumber())
            .andExpect(jsonPath("$.message").isString());
    }

    // ── Auth: me ──────────────────────────────────────────────────────────────

    @Test
    void me_responseContainsAllUserFieldsForPhotographer() throws Exception {
        MockHttpSession s = loginAs("photo1");
        mvc.perform(get("/api/auth/me").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.username").value("photo1"))
            .andExpect(jsonPath("$.email").isString())
            .andExpect(jsonPath("$.fullName").isString())
            .andExpect(jsonPath("$.role").value("PHOTOGRAPHER"))
            .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    // ── Addresses ─────────────────────────────────────────────────────────────

    @Test
    void createAddress_responseContainsAllAddressFields() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(post("/api/addresses").session(s)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "label",      "Payload Test",
                        "street",     "1 Depth Ave",
                        "city",       "Chicago",
                        "state",      "IL",
                        "postalCode", "60601",
                        "country",    "US",
                        "isDefault",  false))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.label").value("Payload Test"))
            .andExpect(jsonPath("$.street").value("1 Depth Ave"))
            .andExpect(jsonPath("$.city").value("Chicago"))
            .andExpect(jsonPath("$.state").value("IL"))
            .andExpect(jsonPath("$.postalCode").value("60601"))
            .andExpect(jsonPath("$.country").value("US"))
            .andExpect(jsonPath("$.isDefault").isBoolean());
    }

    // ── Listings: search ──────────────────────────────────────────────────────

    @Test
    void listingSearch_responseContainsPaginationEnvelope() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/listings/search?page=1&size=5").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.page").isNumber())
            .andExpect(jsonPath("$.size").isNumber())
            .andExpect(jsonPath("$.total").isNumber())
            .andExpect(jsonPath("$.totalPages").isNumber());
    }

    @Test
    void listingSearch_itemsContainExpectedFields() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/listings/search?page=1&size=20").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.items[0].id").isNumber())
            .andExpect(jsonPath("$.items[0].title").isString())
            .andExpect(jsonPath("$.items[0].price").isNumber())
            .andExpect(jsonPath("$.items[0].durationMinutes").isNumber())
            .andExpect(jsonPath("$.items[0].photographerName").isString());
    }

    // ── Notifications: preferences ────────────────────────────────────────────

    @Test
    void notificationPreferences_responseContainsAllSixBooleanFields() throws Exception {
        MockHttpSession s = loginAs("cust2");
        mvc.perform(get("/api/notifications/preferences").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderUpdates").isBoolean())
            .andExpect(jsonPath("$.holds").isBoolean())
            .andExpect(jsonPath("$.reminders").isBoolean())
            .andExpect(jsonPath("$.approvals").isBoolean())
            .andExpect(jsonPath("$.compliance").isBoolean())
            .andExpect(jsonPath("$.muteNonCritical").isBoolean());
    }

    @Test
    void updateNotificationPreferences_complianceAlwaysTrueRegardlessOfInput() throws Exception {
        MockHttpSession s = loginAs("cust1");
        String body = json(Map.of(
                "orderUpdates", true, "holds", false, "reminders", true,
                "approvals", false, "compliance", false, "muteNonCritical", false));
        mvc.perform(put("/api/notifications/preferences").session(s)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.compliance").value(true));
    }

    // ── Points ────────────────────────────────────────────────────────────────

    @Test
    void pointsBalance_responseContainsNumericBalanceField() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/points/balance").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.balance").isNumber());
    }

    @Test
    void pointsAdjust_emptyReason_errorBodyMentionsMandatory() throws Exception {
        MockHttpSession s = loginAs("admin");
        mvc.perform(post("/api/points/adjust").session(s)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("userId", 4, "points", 10, "reason", ""))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value(containsStringIgnoringCase("mandatory")));
    }

    // ── Users ─────────────────────────────────────────────────────────────────

    @Test
    void userList_admin_responseIsArrayWithUserFields() throws Exception {
        MockHttpSession s = loginAs("admin");
        mvc.perform(get("/api/users").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].id").isNumber())
            .andExpect(jsonPath("$[0].username").isString())
            .andExpect(jsonPath("$[0].roleName").isString())
            .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    // ── Blacklist ─────────────────────────────────────────────────────────────

    @Test
    void blacklistList_admin_responseIsArray() throws Exception {
        MockHttpSession s = loginAs("admin");
        mvc.perform(get("/api/blacklist").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }
}
