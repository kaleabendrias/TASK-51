package com.booking.service;

import com.booking.domain.Listing;
import com.booking.domain.TimeSlot;
import com.booking.domain.User;
import com.booking.mapper.ListingMapper;
import com.booking.mapper.TimeSlotMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class TimeSlotService {

    private final TimeSlotMapper timeSlotMapper;
    private final ListingMapper listingMapper;

    public TimeSlotService(TimeSlotMapper timeSlotMapper, ListingMapper listingMapper) {
        this.timeSlotMapper = timeSlotMapper;
        this.listingMapper = listingMapper;
    }

    public List<TimeSlot> getByListing(Long listingId) {
        return timeSlotMapper.findByListingId(listingId);
    }

    public List<TimeSlot> getAvailable(Long listingId, LocalDate start, LocalDate end) {
        return timeSlotMapper.findAvailable(listingId, start, end);
    }

    public TimeSlot create(TimeSlot slot, User actor) {
        Listing listing = listingMapper.findById(slot.getListingId());
        if (listing == null) throw new IllegalArgumentException("Listing not found");
        if (!"ADMINISTRATOR".equals(actor.getRoleName()) &&
            !listing.getPhotographerId().equals(actor.getId())) {
            throw new SecurityException("Only the listing owner can manage time slots");
        }
        if (slot.getCapacity() == null) slot.setCapacity(listing.getMaxConcurrent());
        if (slot.getBookedCount() == null) slot.setBookedCount(0);
        timeSlotMapper.insert(slot);
        return timeSlotMapper.findById(slot.getId());
    }

    @Transactional
    public TimeSlot reserveSlot(Long slotId) {
        TimeSlot slot = timeSlotMapper.findByIdForUpdate(slotId);
        if (slot == null) throw new IllegalArgumentException("Time slot not found");
        if (!slot.hasAvailability()) {
            throw new IllegalStateException("Time slot is fully booked");
        }
        int updated = timeSlotMapper.incrementBookedCount(slotId, slot.getVersion());
        if (updated == 0) {
            throw new IllegalStateException("Concurrent booking conflict — please retry");
        }
        return timeSlotMapper.findById(slotId);
    }

    @Transactional
    public void releaseSlot(Long slotId) {
        TimeSlot slot = timeSlotMapper.findByIdForUpdate(slotId);
        if (slot == null) return;
        timeSlotMapper.decrementBookedCount(slotId, slot.getVersion());
    }
}
