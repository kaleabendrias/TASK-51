package com.booking.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Covers the seven previously-uncovered controller endpoints:
 *   PUT  /api/points/rules/{id}
 *   POST /api/points/award
 *   POST /api/notifications/export
 *   GET  /api/timeslots/listing/{listingId}
 *   GET  /api/blacklist/{id}
 *   POST /api/messages/conversations/{id}/reply
 *   POST /api/messages/conversations/{id}/image
 *
 * Also verifies that every seeded test account (admin, photo1, photo2, cust1, cust2)
 * can authenticate and perform its core role-specific operations.
 */
class UncoveredEndpointsApiIT extends BaseApiIT {

    // ─── helpers ──────────────────────────────────────────────────────────────

    /**
     * Creates a fresh time slot for listing 1 (owned by photo1), places an order
     * as cust1, then opens a conversation between cust1 and photo1 via the first
     * message. Returns the conversation id.
     */
    private long createConversationCust1Photo1() throws Exception {
        MockHttpSession photo = loginAs("photo1");
        MvcResult slotR = mvc.perform(post("/api/timeslots").session(photo)
                        .header("Origin", TEST_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "listingId", 1,
                                "slotDate", "2027-08-" + (10 + (int) (Math.random() * 15)),
                                "startTime", "10:00",
                                "endTime", "11:00",
                                "capacity", 1))))
                .andExpect(status().isOk()).andReturn();
        long slotId = ((Number) parseMap(slotR).get("id")).longValue();

        MockHttpSession cust = loginAs("cust1");
        MvcResult orderR = mvc.perform(post("/api/orders").session(cust)
                        .header("Origin", TEST_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "unc-conv-" + System.nanoTime())
                        .content(json(Map.of("listingId", 1, "timeSlotId", slotId))))
                .andExpect(status().isOk()).andReturn();
        long orderId = ((Number) parseMap(orderR).get("id")).longValue();

        MvcResult msgR = mvc.perform(post("/api/messages/send").session(cust)
                        .header("Origin", TEST_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "recipientId", 2,
                                "content", "Hello, setting up conversation",
                                "orderId", orderId))))
                .andExpect(status().isOk()).andReturn();

        return ((Number) parseMap(msgR).get("conversationId")).longValue();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Seeded accounts — login + basic role operations
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void allSeedUsersCanLogin() throws Exception {
        for (String username : new String[]{"admin", "photo1", "photo2", "cust1", "cust2"}) {
            MockHttpSession s = loginAs(username);   // throws if login returns non-200
            mvc.perform(get("/api/auth/me").session(s))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value(username));
        }
    }

    @Test
    void adminCanListAllUsers() throws Exception {
        MockHttpSession s = loginAs("admin");
        mvc.perform(get("/api/users").session(s))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(5)));
    }

    @Test
    void photo1CanListOwnListings() throws Exception {
        MockHttpSession s = loginAs("photo1");
        mvc.perform(get("/api/listings/my").session(s))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void photo2CanListOwnListings() throws Exception {
        MockHttpSession s = loginAs("photo2");
        mvc.perform(get("/api/listings/my").session(s))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$[0].id").isNumber());
    }

    @Test
    void cust1CanListAddressesAndBalance() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/addresses").session(s))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)));
        mvc.perform(get("/api/points/balance").session(s))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").isNumber());
    }

    @Test
    void cust2CanBrowseListings() throws Exception {
        MockHttpSession s = loginAs("cust2");
        mvc.perform(get("/api/listings/search").session(s))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.total").isNumber());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PUT /api/points/rules/{id}
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void updatePointsRule_adminSuccess() throws Exception {
        MockHttpSession admin = loginAs("admin");
        mvc.perform(put("/api/points/rules/1").session(admin)
                        .header("Origin", TEST_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "ORDER_PAYMENT",
                                "description", "Updated payment rule",
                                "points", 15,
                                "scope", "INDIVIDUAL",
                                "triggerEvent", "ORDER_PAID",
                                "active", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.points").value(15))
                .andExpect(jsonPath("$.description").value("Updated payment rule"));
    }

    @Test
    void updatePointsRule_customerForbidden() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        mvc.perform(put("/api/points/rules/1").session(cust)
                        .header("Origin", TEST_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "ORDER_PAYMENT",
                                "points", 999,
                                "scope", "INDIVIDUAL",
                                "triggerEvent", "ORDER_PAID"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void updatePointsRule_photographerForbidden() throws Exception {
        MockHttpSession photo = loginAs("photo1");
        mvc.perform(put("/api/points/rules/1").session(photo)
                        .header("Origin", TEST_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "ORDER_PAYMENT",
                                "points", 999,
                                "scope", "INDIVIDUAL",
                                "triggerEvent", "ORDER_PAID"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void updatePointsRule_secondRule_adminSuccess() throws Exception {
        MockHttpSession admin = loginAs("admin");
        mvc.perform(put("/api/points/rules/2").session(admin)
                        .header("Origin", TEST_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "ORDER_COMPLETED",
                                "description", "Completion bonus updated",
                                "points", 25,
                                "scope", "INDIVIDUAL",
                                "triggerEvent", "ORDER_COMPLETED",
                                "active", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points").value(25));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // POST /api/points/award
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void awardPoints_adminSuccess() throws Exception {
        MockHttpSession admin = loginAs("admin");
        mvc.perform(post("/api/points/award").session(admin)
                        .header("Origin", TEST_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "userId", 4,
                                "points", 25,
                                "description", "Welcome bonus for test"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points").value(25))
                .andExpect(jsonPath("$.userId").value(4))
                .andExpect(jsonPath("$.action").value("ADMIN_AWARD"));
    }

    @Test
    void awardPoints_photographerForbidden() throws Exception {
        MockHttpSession photo = loginAs("photo1");
        mvc.perform(post("/api/points/award").session(photo)
                        .header("Origin", TEST_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", 4, "points", 25, "description", "Unauthorized"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void awardPoints_customerForbidden() throws Exception {
        MockHttpSession cust = loginAs("cust2");
        mvc.perform(post("/api/points/award").session(cust)
                        .header("Origin", TEST_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", 4, "points", 100, "description", "Unauthorized"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void awardPoints_balanceReflectsAward() throws Exception {
        MockHttpSession admin = loginAs("admin");
        // Award a specific known amount to user 5 (cust2) and capture balanceAfter
        MvcResult awardR = mvc.perform(post("/api/points/award").session(admin)
                        .header("Origin", TEST_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", 5, "points", 50, "description", "Balance test"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points").value(50))
                .andReturn();

        int balanceAfterAward = ((Number) parseMap(awardR).get("balanceAfter")).intValue();

        // Verify user 5's (cust2) balance matches the reported balanceAfter
        MockHttpSession cust2 = loginAs("cust2");
        mvc.perform(get("/api/points/balance").session(cust2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(balanceAfterAward));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // POST /api/notifications/export
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void notificationsExport_adminWithValidIds() throws Exception {
        MockHttpSession admin = loginAs("admin");
        // The service counts each id regardless of whether a row was found,
        // so passing any non-empty list exercises the success branch.
        mvc.perform(post("/api/notifications/export").session(admin)
                        .header("Origin", TEST_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("ids", List.of(9999, 9998)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exported").value(2));
    }

    @Test
    void notificationsExport_emptyIdsBadRequest() throws Exception {
        MockHttpSession admin = loginAs("admin");
        mvc.perform(post("/api/notifications/export").session(admin)
                        .header("Origin", TEST_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("ids", List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("ids")));
    }

    @Test
    void notificationsExport_customerForbidden() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        mvc.perform(post("/api/notifications/export").session(cust)
                        .header("Origin", TEST_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("ids", List.of(1)))))
                .andExpect(status().isForbidden());
    }

    @Test
    void notificationsExport_photographerForbidden() throws Exception {
        MockHttpSession photo = loginAs("photo2");
        mvc.perform(post("/api/notifications/export").session(photo)
                        .header("Origin", TEST_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("ids", List.of(1)))))
                .andExpect(status().isForbidden());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GET /api/timeslots/listing/{listingId}
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void timeslotsByListing_authenticatedSuccess() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/timeslots/listing/1").session(s))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(2)));
    }

    @Test
    void timeslotsByListing_unauthenticated401() throws Exception {
        mvc.perform(get("/api/timeslots/listing/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void timeslotsByListing_photo2Listing() throws Exception {
        MockHttpSession s = loginAs("photo2");
        mvc.perform(get("/api/timeslots/listing/2").session(s))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void timeslotsByListing_adminCanAlsoAccess() throws Exception {
        MockHttpSession admin = loginAs("admin");
        mvc.perform(get("/api/timeslots/listing/1").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GET /api/blacklist/{id}
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void getBlacklistById_notFound() throws Exception {
        MockHttpSession admin = loginAs("admin");
        mvc.perform(get("/api/blacklist/99999").session(admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void getBlacklistById_customerForbidden() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        mvc.perform(get("/api/blacklist/1").session(cust))
                .andExpect(status().isForbidden());
    }

    @Test
    void getBlacklistById_photographerForbidden() throws Exception {
        MockHttpSession photo = loginAs("photo1");
        mvc.perform(get("/api/blacklist/1").session(photo))
                .andExpect(status().isForbidden());
    }

    @Test
    void getBlacklistById_adminSuccess() throws Exception {
        MockHttpSession admin = loginAs("admin");

        // Create a blacklist entry for cust2 (user id=5)
        MvcResult createR = mvc.perform(post("/api/blacklist").session(admin)
                        .header("Origin", TEST_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "userId", 5,
                                "reason", "GET-by-id endpoint test",
                                "durationDays", 1))))
                .andExpect(status().isOk()).andReturn();
        long entryId = ((Number) parseMap(createR).get("id")).longValue();

        // Retrieve by id — should return the entry with matching fields
        mvc.perform(get("/api/blacklist/" + entryId).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(entryId))
                .andExpect(jsonPath("$.userId").value(5))
                .andExpect(jsonPath("$.reason").value("GET-by-id endpoint test"))
                .andExpect(jsonPath("$.active").value(true));

        // Lift the entry and re-enable cust2 so subsequent tests are not affected
        mvc.perform(post("/api/blacklist/" + entryId + "/lift").session(admin)
                        .header("Origin", TEST_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Cleanup after GET-by-id test"))))
                .andExpect(status().isOk());

        mvc.perform(patch("/api/users/5/enabled").session(admin)
                        .header("Origin", TEST_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("enabled", true))))
                .andExpect(status().isOk());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // POST /api/messages/conversations/{id}/reply
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void replyToConversation_participantSuccess() throws Exception {
        long convId = createConversationCust1Photo1();

        MockHttpSession photo = loginAs("photo1");
        mvc.perform(post("/api/messages/conversations/" + convId + "/reply").session(photo)
                        .header("Origin", TEST_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "Thanks for reaching out!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(convId))
                .andExpect(jsonPath("$.content").value("Thanks for reaching out!"))
                .andExpect(jsonPath("$.senderId").value(2));
    }

    @Test
    void replyToConversation_nonParticipantForbidden() throws Exception {
        long convId = createConversationCust1Photo1();

        // cust2 is not a participant of cust1↔photo1 conversation
        MockHttpSession outsider = loginAs("cust2");
        mvc.perform(post("/api/messages/conversations/" + convId + "/reply").session(outsider)
                        .header("Origin", TEST_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "I should not be able to reply!"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void replyToConversation_emptyContentBadRequest() throws Exception {
        long convId = createConversationCust1Photo1();

        MockHttpSession cust = loginAs("cust1");
        mvc.perform(post("/api/messages/conversations/" + convId + "/reply").session(cust)
                        .header("Origin", TEST_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", ""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void replyToConversation_adminCanReply() throws Exception {
        long convId = createConversationCust1Photo1();

        // Administrator is allowed to participate in any conversation
        MockHttpSession admin = loginAs("admin");
        mvc.perform(post("/api/messages/conversations/" + convId + "/reply").session(admin)
                        .header("Origin", TEST_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "Admin message"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Admin message"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // POST /api/messages/conversations/{id}/image
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void uploadImageToConversation_jpegSuccess() throws Exception {
        long convId = createConversationCust1Photo1();

        MockHttpSession photo = loginAs("photo1");
        MockMultipartFile file = new MockMultipartFile(
                "file", "shoot.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00});
        mvc.perform(multipart("/api/messages/conversations/" + convId + "/image")
                        .file(file).session(photo)
                        .header("Origin", TEST_ORIGIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message.conversationId").value(convId))
                .andExpect(jsonPath("$.attachment.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.attachment.originalName").value("shoot.jpg"))
                .andExpect(jsonPath("$.attachment.fileSize").isNumber());
    }

    @Test
    void uploadImageToConversation_nonParticipantForbidden() throws Exception {
        long convId = createConversationCust1Photo1();

        // photo2 has no relation to this cust1↔photo1 conversation
        MockHttpSession outsider = loginAs("photo2");
        MockMultipartFile file = new MockMultipartFile(
                "file", "hack.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8});
        mvc.perform(multipart("/api/messages/conversations/" + convId + "/image")
                        .file(file).session(outsider)
                        .header("Origin", TEST_ORIGIN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", containsString("participant")));
    }

    @Test
    void uploadImageToConversation_unsupportedTypeBadRequest() throws Exception {
        long convId = createConversationCust1Photo1();

        MockHttpSession cust = loginAs("cust1");
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", "fake-pdf-content".getBytes());
        mvc.perform(multipart("/api/messages/conversations/" + convId + "/image")
                        .file(file).session(cust)
                        .header("Origin", TEST_ORIGIN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("JPEG and PNG")));
    }

    @Test
    void uploadImageToConversation_tooLargeBadRequest() throws Exception {
        long convId = createConversationCust1Photo1();

        MockHttpSession cust = loginAs("cust1");
        byte[] big = new byte[5 * 1024 * 1024 + 1]; // 5 MB + 1 byte
        MockMultipartFile file = new MockMultipartFile(
                "file", "oversized.jpg", "image/jpeg", big);
        mvc.perform(multipart("/api/messages/conversations/" + convId + "/image")
                        .file(file).session(cust)
                        .header("Origin", TEST_ORIGIN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("5 MB")));
    }
}
