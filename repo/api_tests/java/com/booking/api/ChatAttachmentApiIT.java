package com.booking.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ChatAttachmentApiIT extends BaseApiIT {

    /** Creates an order and returns its ID, so messages have a valid orderId */
    private int createOrderForChat() throws Exception {
        MockHttpSession photo = loginAs("photo1");
        MvcResult slotR = mvc.perform(post("/api/timeslots").session(photo)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 1, "slotDate", "2027-05-" + (10 + (int)(Math.random()*20)),
                        "startTime", "09:00", "endTime", "10:00", "capacity", 1))))
            .andExpect(status().isOk()).andReturn();
        int slotId = ((Number) parseMap(slotR).get("id")).intValue();
        MockHttpSession cust = loginAs("cust1");
        MvcResult r = mvc.perform(post("/api/orders").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "chat-order-" + System.nanoTime())
                .content(json(Map.of("listingId", 1, "timeSlotId", slotId))))
            .andExpect(status().isOk()).andReturn();
        return ((Number) parseMap(r).get("id")).intValue();
    }

    private long createConversation() throws Exception {
        int orderId = createOrderForChat();
        MockHttpSession cust = loginAs("cust1");
        MvcResult r = mvc.perform(post("/api/messages/send").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("recipientId", 2, "content", "Hi", "orderId", orderId))))
            .andExpect(status().isOk()).andReturn();
        return ((Number) parseMap(r).get("conversationId")).longValue();
    }

    @Test void sendTextMessage() throws Exception {
        int orderId = createOrderForChat();
        MockHttpSession cust = loginAs("cust1");
        mvc.perform(post("/api/messages/send").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("recipientId", 2, "content", "Hello", "orderId", orderId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.conversationId").isNumber());
    }

    @Test void sendEmptyMessageFails() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        mvc.perform(post("/api/messages/send").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("recipientId", 2, "content", ""))))
            .andExpect(status().isBadRequest());
    }

    @Test void uploadJpegSuccess() throws Exception {
        long convId = createConversation();
        MockHttpSession cust = loginAs("cust1");
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg",
                "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
        mvc.perform(multipart("/api/messages/conversations/" + convId + "/image")
                .file(file).session(cust))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.attachment.contentType").value("image/jpeg"));
    }

    @Test void uploadPngSuccess() throws Exception {
        long convId = createConversation();
        MockHttpSession cust = loginAs("cust1");
        MockMultipartFile file = new MockMultipartFile("file", "img.png",
                "image/png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});
        mvc.perform(multipart("/api/messages/conversations/" + convId + "/image")
                .file(file).session(cust))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.attachment.contentType").value("image/png"));
    }

    @Test void uploadNonImageRejected() throws Exception {
        long convId = createConversation();
        MockHttpSession cust = loginAs("cust1");
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf",
                "application/pdf", "fake pdf".getBytes());
        mvc.perform(multipart("/api/messages/conversations/" + convId + "/image")
                .file(file).session(cust))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("JPEG and PNG")));
    }

    @Test void uploadTooLargeRejected() throws Exception {
        long convId = createConversation();
        MockHttpSession cust = loginAs("cust1");
        byte[] bigFile = new byte[5 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile("file", "big.jpg",
                "image/jpeg", bigFile);
        mvc.perform(multipart("/api/messages/conversations/" + convId + "/image")
                .file(file).session(cust))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("5 MB")));
    }

    @Test void nonParticipantCannotUpload() throws Exception {
        long convId = createConversation();
        MockHttpSession cust2 = loginAs("cust2");
        MockMultipartFile file = new MockMultipartFile("file", "x.jpg",
                "image/jpeg", new byte[]{1, 2, 3});
        mvc.perform(multipart("/api/messages/conversations/" + convId + "/image")
                .file(file).session(cust2))
            .andExpect(status().isForbidden());
    }

    @Test void messagesIncludeAttachmentInfo() throws Exception {
        long convId = createConversation();
        MockHttpSession cust = loginAs("cust1");
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg",
                "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8});
        mvc.perform(multipart("/api/messages/conversations/" + convId + "/image")
                .file(file).session(cust))
            .andExpect(status().isOk());
        mvc.perform(get("/api/messages/conversations/" + convId).session(cust))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[-1].attachments").isArray());
    }

    @Test void readUnreadTracking() throws Exception {
        int orderId = createOrderForChat();
        MockHttpSession cust = loginAs("cust1");
        MvcResult r = mvc.perform(post("/api/messages/send").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("recipientId", 2, "content", "Unread test", "orderId", orderId))))
            .andExpect(status().isOk()).andReturn();
        long convId = ((Number) parseMap(r).get("conversationId")).longValue();

        MockHttpSession photo = loginAs("photo1");
        mvc.perform(get("/api/messages/conversations").session(photo))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id==" + convId + ")].unreadCount", hasItem(greaterThan(0))));

        mvc.perform(get("/api/messages/conversations/" + convId).session(photo))
            .andExpect(status().isOk());
    }

    @Test void conversationNotFoundReturns404() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        MockMultipartFile file = new MockMultipartFile("file", "x.jpg", "image/jpeg", new byte[]{1});
        mvc.perform(multipart("/api/messages/conversations/99999/image")
                .file(file).session(cust))
            .andExpect(status().isNotFound());
    }
}
