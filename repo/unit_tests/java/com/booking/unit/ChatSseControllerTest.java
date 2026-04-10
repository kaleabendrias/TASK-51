package com.booking.unit;

import com.booking.controller.ChatSseController;
import com.booking.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChatSseControllerTest {

    private ChatSseController controller;

    @BeforeEach void setup() {
        controller = new ChatSseController();
    }

    @Test void streamReturnsSseEmitter() {
        MockHttpSession session = new MockHttpSession();
        User user = new User();
        user.setId(1L);
        session.setAttribute("currentUser", user);
        SseEmitter emitter = controller.stream(session);
        assertNotNull(emitter);
    }

    @Test void pushMessageEventToNoSubscribersDoesNotThrow() {
        // No subscribers — should be a no-op
        assertDoesNotThrow(() ->
            controller.pushMessageEvent(999L, Map.of("content", "hello")));
    }

    @Test void pushMessageEventToSubscriber() {
        MockHttpSession session = new MockHttpSession();
        User user = new User();
        user.setId(42L);
        session.setAttribute("currentUser", user);

        // Subscribe
        SseEmitter emitter = controller.stream(session);
        assertNotNull(emitter);

        // Push event — shouldn't throw
        assertDoesNotThrow(() ->
            controller.pushMessageEvent(42L, Map.of("conversationId", 1, "content", "hi")));
    }

    @Test void pushToCompletedEmitterDoesNotThrow() {
        MockHttpSession session = new MockHttpSession();
        User user = new User();
        user.setId(7L);
        session.setAttribute("currentUser", user);

        controller.stream(session);

        // Pushing to a user with registered emitters should not throw
        // even if the underlying connection has issues
        assertDoesNotThrow(() ->
            controller.pushMessageEvent(7L, Map.of("content", "test")));
    }
}
