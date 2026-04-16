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

class RbacApiIT extends BaseApiIT {

    // ---- Customer access restrictions ----

    @Test void customerCannotAccessUsersList() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/users").session(s))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").isString());
    }

    @Test void customerCannotAccessBlacklist() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/blacklist").session(s))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").isString());
    }

    @Test void customerCannotBlacklistAnyone() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(post("/api/blacklist").session(s)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("userId", 5, "reason", "test"))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").isString());
    }

    @Test void photographerCannotAccessUsersList() throws Exception {
        MockHttpSession s = loginAs("photo1");
        mvc.perform(get("/api/users").session(s))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").isString());
    }

    @Test void customerCannotAdjustPoints() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(post("/api/points/adjust").session(s)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("userId", 4, "points", 100, "reason", "test"))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").isString());
    }

    @Test void customerCannotAccessPointsRules() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/points/rules").session(s))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").isString());
    }

    @Test void customerCannotCreatePointsRule() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(post("/api/points/rules").session(s)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "EVIL_RULE", "description", "hack",
                        "points", 9999, "scope", "INDIVIDUAL", "triggerEvent", "EVIL"))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").isString());
    }

    @Test void customerCannotCreateListing() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(post("/api/listings").session(s)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("title", "X", "price", 50, "durationMinutes", 30))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").isString());
    }

    // ---- Photographer access ----

    @Test void photographerCanAccessOwnListings() throws Exception {
        MockHttpSession s = loginAs("photo1");
        mvc.perform(get("/api/listings/my").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test void photographerSeesOnlyOwnOrders() throws Exception {
        // photo2 (userId=3) should only ever see orders where they are the photographer
        MockHttpSession s = loginAs("photo2");
        MvcResult result = mvc.perform(get("/api/orders").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andReturn();

        List<?> orders = om.readValue(result.getResponse().getContentAsString(), List.class);
        for (Object entry : orders) {
            @SuppressWarnings("unchecked")
            Map<String, Object> order = (Map<String, Object>) entry;
            assertEquals(3, ((Number) order.get("photographerId")).intValue(),
                "photo2 should only see orders assigned to their own userId (3)");
        }
    }

    @Test void photographerCannotAccessAnotherPhotographersListingEdit() throws Exception {
        // photo2 cannot update photo1's listing (listing 1 belongs to photo1)
        MockHttpSession s = loginAs("photo2");
        mvc.perform(put("/api/listings/1").session(s)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("title", "Stolen", "description", "hack",
                        "price", 1, "durationMinutes", 60, "category", "portrait",
                        "locationCity", "X", "locationState", "CA"))))
            .andExpect(status().isForbidden());
    }

    // ---- Removed endpoints return 404 ----

    @Test void servicesEndpointRemoved() throws Exception {
        MockHttpSession s = loginAs("photo1");
        mvc.perform(post("/api/services").session(s)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "X", "price", 50, "durationMinutes", 30))))
            .andExpect(status().isNotFound());
    }

    // ---- Admin can access everything ----

    @Test void adminCanAccessUsersWithMaskedPhone() throws Exception {
        MockHttpSession s = loginAs("admin");
        mvc.perform(get("/api/users").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(3)))
            // Phone must be masked — never raw digits
            .andExpect(jsonPath("$[0].phone").value(startsWith("***")))
            // Password hash must never be exposed
            .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test void adminCanAccessBlacklistWithExpectedFields() throws Exception {
        MockHttpSession s = loginAs("admin");
        mvc.perform(get("/api/blacklist").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test void adminCanAccessPointsRulesWithExpectedFields() throws Exception {
        MockHttpSession s = loginAs("admin");
        mvc.perform(get("/api/points/rules").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(2)))
            .andExpect(jsonPath("$[0].id").isNumber())
            .andExpect(jsonPath("$[0].name").isString())
            .andExpect(jsonPath("$[0].points").isNumber());
    }

    @Test void adminCanAccessPointsAdjustmentsWithExpectedFields() throws Exception {
        MockHttpSession s = loginAs("admin");
        mvc.perform(get("/api/points/adjustments").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    // ---- Customer can access their own resources ----

    @Test void customerCanReadOwnNotificationPreferences() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/notifications/preferences").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.compliance").isBoolean())
            .andExpect(jsonPath("$.orderUpdates").isBoolean())
            .andExpect(jsonPath("$.muteNonCritical").isBoolean());
    }

    @Test void customerCanReadOwnAddresses() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/addresses").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(2)))
            .andExpect(jsonPath("$[0].id").isNumber())
            .andExpect(jsonPath("$[0].label").isString())
            .andExpect(jsonPath("$[0].street").isString());
    }

    @Test void customerCanReadOwnPointsBalance() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/points/balance").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.balance").isNumber())
            .andExpect(jsonPath("$.balance").value(greaterThanOrEqualTo(0)));
    }

    @Test void customerCanReadPublicListingById() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/listings/1").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.price").isNumber())
            .andExpect(jsonPath("$.title").isString());
    }

    @Test void customerCanViewTimeSlotsForListing() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/timeslots/listing/1").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test void customerCanReadPublicPhotographerList() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/users/photographers").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)));
    }
}
