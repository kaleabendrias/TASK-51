package com.booking.controller;

import com.booking.domain.ChatAttachment;
import com.booking.domain.Conversation;
import com.booking.domain.Message;
import com.booking.domain.User;
import com.booking.mapper.ChatAttachmentMapper;
import com.booking.mapper.ConversationMapper;
import com.booking.service.MessageService;
import com.booking.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.booking.domain.Message;
import com.booking.mapper.MessageMapper;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png");
    private static final long MAX_SIZE = 5 * 1024 * 1024; // 5MB

    private final MessageService messageService;
    private final ChatAttachmentMapper chatAttachmentMapper;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final Path uploadDir;

    public MessageController(MessageService messageService,
                             ChatAttachmentMapper chatAttachmentMapper,
                             ConversationMapper conversationMapper,
                             MessageMapper messageMapper,
                             @Value("${app.upload-dir}") String uploadDir) {
        this.messageService = messageService;
        this.chatAttachmentMapper = chatAttachmentMapper;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.uploadDir = Paths.get(uploadDir, "chat");
        try { Files.createDirectories(this.uploadDir); } catch (IOException ignored) {}
    }

    @GetMapping("/conversations")
    public ResponseEntity<?> conversations(HttpSession session) {
        User user = SessionUtil.getCurrentUser(session);
        return ResponseEntity.ok(messageService.getConversations(user));
    }

    @GetMapping("/conversations/{id}")
    public ResponseEntity<?> messages(@PathVariable Long id, HttpSession session) {
        User user = SessionUtil.getCurrentUser(session);
        try {
            List<Message> msgs = messageService.getMessages(id, user);
            // Attach chat attachment info to each message
            List<Map<String, Object>> result = new ArrayList<>();
            for (Message m : msgs) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", m.getId());
                entry.put("conversationId", m.getConversationId());
                entry.put("senderId", m.getSenderId());
                entry.put("senderName", m.getSenderName());
                entry.put("content", m.getContent());
                entry.put("readAt", m.getReadAt());
                entry.put("createdAt", m.getCreatedAt());
                List<ChatAttachment> attachments = chatAttachmentMapper.findByMessageId(m.getId());
                entry.put("attachments", attachments);
                result.add(entry);
            }
            return ResponseEntity.ok(result);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/send")
    public ResponseEntity<?> send(@RequestBody Map<String, Object> body, HttpSession session) {
        User user = SessionUtil.getCurrentUser(session);
        try {
            Long recipientId = ((Number) body.get("recipientId")).longValue();
            String content = (String) body.get("content");
            Long orderId = body.get("orderId") != null ? ((Number) body.get("orderId")).longValue() : null;
            Message msg = messageService.sendMessage(recipientId, content, orderId, user);
            return ResponseEntity.ok(msg);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/conversations/{id}/reply")
    public ResponseEntity<?> reply(@PathVariable Long id, @RequestBody Map<String, String> body,
                                   HttpSession session) {
        User user = SessionUtil.getCurrentUser(session);
        try {
            Message msg = messageService.sendToConversation(id, body.get("content"), user);
            return ResponseEntity.ok(msg);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/conversations/{id}/image")
    public ResponseEntity<?> uploadImage(@PathVariable Long id,
                                         @RequestParam("file") MultipartFile file,
                                         HttpSession session) {
        User user = SessionUtil.getCurrentUser(session);

        Conversation conv = conversationMapper.findById(id);
        if (conv == null) return ResponseEntity.notFound().build();
        if (!conv.getParticipantOne().equals(user.getId()) &&
            !conv.getParticipantTwo().equals(user.getId()) &&
            !"ADMINISTRATOR".equals(user.getRoleName())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not a participant"));
        }

        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Only JPEG and PNG images are allowed"));
        }
        if (file.getSize() > MAX_SIZE) {
            return ResponseEntity.badRequest().body(Map.of("error", "File size must not exceed 5 MB"));
        }

        // Create message with image indicator
        Message msg = messageService.sendToConversation(id, "[Image]", user);

        String storedName = UUID.randomUUID() + "_" + file.getOriginalFilename()
                .replaceAll("[^a-zA-Z0-9._-]", "_");
        try {
            Files.copy(file.getInputStream(), uploadDir.resolve(storedName));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to store image"));
        }

        ChatAttachment att = new ChatAttachment();
        att.setMessageId(msg.getId());
        att.setFileName(storedName);
        att.setOriginalName(file.getOriginalFilename());
        att.setContentType(file.getContentType());
        att.setFileSize(file.getSize());
        chatAttachmentMapper.insert(att);

        return ResponseEntity.ok(Map.of("message", msg, "attachment", att));
    }

    @GetMapping("/attachments/{id}/download")
    public ResponseEntity<?> downloadImage(@PathVariable Long id, HttpSession session) {
        User user = SessionUtil.getCurrentUser(session);
        ChatAttachment att = chatAttachmentMapper.findById(id);
        if (att == null) return ResponseEntity.notFound().build();

        // IDOR fix: verify caller is a participant of the conversation this attachment belongs to
        Message msg = messageMapper.findById(att.getMessageId());
        if (msg == null) return ResponseEntity.notFound().build();
        Conversation conv = conversationMapper.findById(msg.getConversationId());
        if (conv == null) return ResponseEntity.notFound().build();
        if (!conv.getParticipantOne().equals(user.getId()) &&
            !conv.getParticipantTwo().equals(user.getId()) &&
            !"ADMINISTRATOR".equals(user.getRoleName())) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        try {
            Resource resource = new UrlResource(uploadDir.resolve(att.getFileName()).toUri());
            if (!resource.exists()) return ResponseEntity.notFound().build();
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(att.getContentType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + att.getOriginalName() + "\"")
                    .body(resource);
        } catch (MalformedURLException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
