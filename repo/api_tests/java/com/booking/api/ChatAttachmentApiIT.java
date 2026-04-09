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

    private long createConversation() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        MvcResult r = mvc.perform(post("/api/messages/send").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("recipientId", 2, "content", "Hi from chat test"))))
            .andExpect(status().isOk()).andReturn();
        return ((Number) parseMap(r).get("conversationId")).longValue();
    }

    @Test void sendTextMessage() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        mvc.perform(post("/api/messages/send").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("recipientId", 2, "content", "Hello"))))
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
        byte[] bigFile = new byte[5 * 1024 * 1024 + 1]; // 5MB + 1 byte
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
        // Upload image
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg",
                "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8});
        mvc.perform(multipart("/api/messages/conversations/" + convId + "/image")
                .file(file).session(cust))
            .andExpect(status().isOk());
        // Fetch messages
        mvc.perform(get("/api/messages/conversations/" + convId).session(cust))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[-1].attachments").isArray());
    }

    @Test void readUnreadTracking() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        // Send message
        MvcResult r = mvc.perform(post("/api/messages/send").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("recipientId", 2, "content", "Unread test"))))
            .andExpect(status().isOk()).andReturn();
        long convId = ((Number) parseMap(r).get("conversationId")).longValue();

        // Photographer gets conversations -> should have unread
        MockHttpSession photo = loginAs("photo1");
        mvc.perform(get("/api/messages/conversations").session(photo))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id==" + convId + ")].unreadCount", hasItem(greaterThan(0))));

        // Photographer reads messages -> marks as read
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
