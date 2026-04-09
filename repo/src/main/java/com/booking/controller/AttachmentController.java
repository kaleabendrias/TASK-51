package com.booking.controller;

import com.booking.domain.Attachment;
import com.booking.domain.User;
import com.booking.service.AttachmentService;
import com.booking.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<?> listForBooking(@PathVariable Long bookingId) {
        return ResponseEntity.ok(attachmentService.getByBookingId(bookingId));
    }

    @PostMapping("/booking/{bookingId}")
    public ResponseEntity<?> upload(@PathVariable Long bookingId,
                                    @RequestParam("file") MultipartFile file,
                                    HttpSession session) {
        User user = SessionUtil.getCurrentUser(session);
        try {
            Attachment attachment = attachmentService.upload(bookingId, file, user);
            return ResponseEntity.ok(attachment);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<?> download(@PathVariable Long id) {
        Attachment attachment = attachmentService.getById(id);
        if (attachment == null) {
            return ResponseEntity.notFound().build();
        }

        Path filePath = attachmentService.getFilePath(attachment);
        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }

            String contentType = attachment.getContentType() != null
                    ? attachment.getContentType() : "application/octet-stream";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + attachment.getOriginalName() + "\"")
                    .body(resource);
        } catch (MalformedURLException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "File not accessible"));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, HttpSession session) {
        User user = SessionUtil.getCurrentUser(session);
        try {
            attachmentService.delete(id, user);
            return ResponseEntity.ok(Map.of("message", "Attachment deleted"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }
}
