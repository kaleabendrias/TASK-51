package com.booking.service;

import com.booking.domain.Attachment;
import com.booking.domain.Booking;
import com.booking.domain.User;
import com.booking.mapper.AttachmentMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
public class AttachmentService {

    private final AttachmentMapper attachmentMapper;
    private final BookingService bookingService;
    private final Path uploadDir;

    public AttachmentService(AttachmentMapper attachmentMapper,
                             BookingService bookingService,
                             @Value("${app.upload-dir}") String uploadDir) {
        this.attachmentMapper = attachmentMapper;
        this.bookingService = bookingService;
        this.uploadDir = Paths.get(uploadDir);
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory", e);
        }
    }

    public List<Attachment> getByBookingId(Long bookingId) {
        return attachmentMapper.findByBookingId(bookingId);
    }

    public Attachment getById(Long id) {
        return attachmentMapper.findById(id);
    }

    public Path getFilePath(Attachment attachment) {
        return uploadDir.resolve(attachment.getFileName());
    }

    public Attachment upload(Long bookingId, MultipartFile file, User uploader) {
        Booking booking = bookingService.getById(bookingId);
        if (booking == null) {
            throw new IllegalArgumentException("Booking not found");
        }
        if (!bookingService.canUserAccess(booking, uploader)) {
            throw new SecurityException("Access denied to this booking");
        }

        String originalName = file.getOriginalFilename();
        String storedName = UUID.randomUUID() + "_" + sanitizeFileName(originalName);

        try {
            Path target = uploadDir.resolve(storedName);
            Files.copy(file.getInputStream(), target);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file", e);
        }

        Attachment attachment = new Attachment();
        attachment.setBookingId(bookingId);
        attachment.setFileName(storedName);
        attachment.setOriginalName(originalName);
        attachment.setContentType(file.getContentType());
        attachment.setFileSize(file.getSize());
        attachment.setUploadedBy(uploader.getId());
        attachmentMapper.insert(attachment);

        return attachmentMapper.findById(attachment.getId());
    }

    public void delete(Long attachmentId, User actor) {
        Attachment attachment = attachmentMapper.findById(attachmentId);
        if (attachment == null) {
            throw new IllegalArgumentException("Attachment not found");
        }

        Booking booking = bookingService.getById(attachment.getBookingId());
        if (!bookingService.canUserAccess(booking, actor)) {
            throw new SecurityException("Access denied");
        }

        try {
            Files.deleteIfExists(uploadDir.resolve(attachment.getFileName()));
        } catch (IOException e) {
            // Log but don't fail — DB record removal is more important
        }
        attachmentMapper.delete(attachmentId);
    }

    private String sanitizeFileName(String name) {
        if (name == null) return "file";
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
