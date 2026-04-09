package com.booking.service;

import com.booking.domain.Booking;
import com.booking.domain.BookingStatus;
import com.booking.domain.User;
import com.booking.mapper.BookingMapper;
import com.booking.mapper.ServiceMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class BookingService {

    private final BookingMapper bookingMapper;
    private final ServiceMapper serviceMapper;

    public BookingService(BookingMapper bookingMapper, ServiceMapper serviceMapper) {
        this.bookingMapper = bookingMapper;
        this.serviceMapper = serviceMapper;
    }

    public Booking getById(Long id) {
        return bookingMapper.findById(id);
    }

    public List<Booking> getAll() {
        return bookingMapper.findAll();
    }

    public List<Booking> getForUser(User user) {
        return switch (user.getRoleName()) {
            case "CUSTOMER" -> bookingMapper.findByCustomerId(user.getId());
            case "PHOTOGRAPHER" -> bookingMapper.findByPhotographerId(user.getId());
            default -> bookingMapper.findAll();
        };
    }

    public Booking create(Booking booking) {
        validateBookingDates(booking);
        checkPhotographerConflicts(booking);

        com.booking.domain.Service svc = serviceMapper.findById(booking.getServiceId());
        if (svc == null || !svc.getActive()) {
            throw new IllegalArgumentException("Invalid or inactive service");
        }

        booking.setStatus(BookingStatus.PENDING.name());
        if (booking.getTotalPrice() == null) {
            booking.setTotalPrice(svc.getPrice());
        }

        bookingMapper.insert(booking);
        return bookingMapper.findById(booking.getId());
    }

    public Booking update(Booking booking, User actor) {
        Booking existing = bookingMapper.findById(booking.getId());
        if (existing == null) {
            throw new IllegalArgumentException("Booking not found");
        }

        enforceOwnershipOrAdmin(existing, actor);
        validateBookingDates(booking);
        checkPhotographerConflicts(booking);

        bookingMapper.update(booking);
        return bookingMapper.findById(booking.getId());
    }

    public void updateStatus(Long bookingId, String newStatus, User actor) {
        Booking existing = bookingMapper.findById(bookingId);
        if (existing == null) {
            throw new IllegalArgumentException("Booking not found");
        }

        enforceOwnershipOrAdmin(existing, actor);

        BookingStatus currentStatus = BookingStatus.valueOf(existing.getStatus());
        BookingStatus targetStatus = BookingStatus.valueOf(newStatus);

        if (!currentStatus.canTransitionTo(targetStatus)) {
            throw new IllegalStateException(
                    "Cannot transition from " + currentStatus + " to " + targetStatus);
        }

        bookingMapper.updateStatus(bookingId, newStatus);
    }

    public boolean canUserAccess(Booking booking, User user) {
        if ("ADMINISTRATOR".equals(user.getRoleName())) return true;
        if ("CUSTOMER".equals(user.getRoleName()) && booking.getCustomerId().equals(user.getId())) return true;
        if ("PHOTOGRAPHER".equals(user.getRoleName()) && user.getId().equals(booking.getPhotographerId())) return true;
        return false;
    }

    private void enforceOwnershipOrAdmin(Booking booking, User actor) {
        if (!canUserAccess(booking, actor)) {
            throw new SecurityException("Access denied to this booking");
        }
    }

    private void validateBookingDates(Booking booking) {
        if (booking.getBookingDate() == null) {
            throw new IllegalArgumentException("Booking date is required");
        }
        if (booking.getStartTime() == null || booking.getEndTime() == null) {
            throw new IllegalArgumentException("Start and end times are required");
        }
        if (!booking.getEndTime().isAfter(booking.getStartTime())) {
            throw new IllegalArgumentException("End time must be after start time");
        }
        if (booking.getBookingDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Booking date cannot be in the past");
        }
    }

    private void checkPhotographerConflicts(Booking booking) {
        if (booking.getPhotographerId() == null) return;

        List<Booking> conflicts = bookingMapper.findConflicting(
                booking.getPhotographerId(),
                booking.getBookingDate(),
                booking.getStartTime().toString(),
                booking.getEndTime().toString(),
                booking.getId()
        );

        if (!conflicts.isEmpty()) {
            throw new IllegalStateException("Photographer has a conflicting booking at this time");
        }
    }
}
