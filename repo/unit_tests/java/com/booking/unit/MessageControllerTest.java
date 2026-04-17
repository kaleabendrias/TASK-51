package com.booking.unit;

import com.booking.controller.MessageController;
import com.booking.domain.Message;
import com.booking.domain.User;
import com.booking.mapper.ChatAttachmentMapper;
import com.booking.mapper.ConversationMapper;
import com.booking.mapper.MessageMapper;
import com.booking.service.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageControllerTest {

    @Mock MessageService messageService;
    @Mock ChatAttachmentMapper chatAttachmentMapper;
    @Mock ConversationMapper conversationMapper;
    @Mock MessageMapper messageMapper;

    private MessageController controller;
    private MockHttpSession customerSession;
    private MockHttpSession otherUserSession;

    @BeforeEach
    void setUp() throws Exception {
        // Provide an upload dir so Files.createDirectories doesn't fail
        controller = new MessageController(
                messageService, chatAttachmentMapper, conversationMapper, messageMapper,
                System.getProperty("java.io.tmpdir"));

        User customer = new User(); customer.setId(4L); customer.setRoleName("CUSTOMER");
        customerSession = new MockHttpSession();
        customerSession.setAttribute("currentUser", customer);

        User other = new User(); other.setId(7L); other.setRoleName("CUSTOMER");
        otherUserSession = new MockHttpSession();
        otherUserSession.setAttribute("currentUser", other);
    }

    // ── conversations ─────────────────────────────────────────────────────────

    @Test
    void conversations_delegatesToServiceAndReturns200() {
        when(messageService.getConversations(any())).thenReturn(List.of());

        ResponseEntity<?> resp = controller.conversations(customerSession);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        verify(messageService).getConversations(argThat(u -> u.getId().equals(4L)));
    }

    // ── messages ──────────────────────────────────────────────────────────────

    @Test
    void messages_securityException_returns403() {
        when(messageService.getMessages(anyLong(), any()))
                .thenThrow(new SecurityException("Not a participant"));

        ResponseEntity<?> resp = controller.messages(5L, customerSession);

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, ?> body = (Map<String, ?>) resp.getBody();
        assertNotNull(body.get("error"));
    }

    // ── send ──────────────────────────────────────────────────────────────────

    @Test
    void send_happyPath_returns200() {
        Message msg = new Message(); msg.setId(100L);
        when(messageService.sendMessage(anyLong(), anyString(), any(), any())).thenReturn(msg);

        Map<String, Object> body = Map.of("recipientId", 7, "content", "Hello there");
        ResponseEntity<?> resp = controller.send(body, customerSession);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertSame(msg, resp.getBody());
    }

    @Test
    void send_illegalArgument_returns400() {
        when(messageService.sendMessage(anyLong(), anyString(), any(), any()))
                .thenThrow(new IllegalArgumentException("Recipient not found"));

        Map<String, Object> body = Map.of("recipientId", 999, "content", "Hi");
        ResponseEntity<?> resp = controller.send(body, customerSession);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, ?> rb = (Map<String, ?>) resp.getBody();
        assertTrue(rb.get("error").toString().contains("Recipient not found"));
    }

    // ── reply ─────────────────────────────────────────────────────────────────

    @Test
    void reply_happyPath_returns200() {
        Message msg = new Message(); msg.setId(101L);
        when(messageService.sendToConversation(anyLong(), anyString(), any())).thenReturn(msg);

        ResponseEntity<?> resp = controller.reply(5L, Map.of("content", "Got it"), customerSession);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertSame(msg, resp.getBody());
    }

    @Test
    void reply_securityException_returns403() {
        when(messageService.sendToConversation(anyLong(), anyString(), any()))
                .thenThrow(new SecurityException("Not a participant"));

        ResponseEntity<?> resp = controller.reply(5L, Map.of("content", "Hi"), otherUserSession);

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    @Test
    void reply_illegalArgument_returns400() {
        when(messageService.sendToConversation(anyLong(), anyString(), any()))
                .thenThrow(new IllegalArgumentException("Empty content"));

        ResponseEntity<?> resp = controller.reply(5L, Map.of("content", ""), customerSession);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }
}
