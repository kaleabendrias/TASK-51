package com.booking.controller;

import com.booking.domain.User;
import com.booking.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Server-Sent Events endpoint for real-time chat push notifications.
 * Replaces the legacy 5-second polling approach.
 */
@RestController
@RequestMapping("/api/messages")
public class ChatSseController {

    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(HttpSession session) {
        User user = SessionUtil.getCurrentUser(session);
        SseEmitter emitter = new SseEmitter(300_000L); // 5-minute timeout

        emitters.computeIfAbsent(user.getId(), k -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable cleanup = () -> {
            List<SseEmitter> list = emitters.get(user.getId());
            if (list != null) {
                list.remove(emitter);
                if (list.isEmpty()) emitters.remove(user.getId());
            }
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        // Send initial keepalive so the client knows the stream is open
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            emitter.complete();
        }

        return emitter;
    }

    /**
     * Push a new-message event to all active SSE connections for a given user.
     */
    public void pushMessageEvent(Long userId, Object payload) {
        List<SseEmitter> list = emitters.get(userId);
        if (list == null || list.isEmpty()) return;
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("new-message").data(payload));
            } catch (IOException e) {
                emitter.complete();
            }
        }
    }
}
