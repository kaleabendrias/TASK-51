package com.booking.service;

import com.booking.domain.Listing;
import com.booking.domain.User;
import com.booking.mapper.ListingMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ListingService {

    private final ListingMapper listingMapper;

    public ListingService(ListingMapper listingMapper) {
        this.listingMapper = listingMapper;
    }

    public Listing getById(Long id) {
        return listingMapper.findById(id);
    }

    public List<Listing> getActive() {
        return listingMapper.findActive();
    }

    public List<Listing> getByPhotographer(Long photographerId) {
        return listingMapper.findByPhotographerId(photographerId);
    }

    public List<Listing> search(String keyword, String category,
                                BigDecimal minPrice, BigDecimal maxPrice, String location) {
        return listingMapper.search(keyword, category, minPrice, maxPrice, location);
    }

    public Listing create(Listing listing, User photographer) {
        if (!"PHOTOGRAPHER".equals(photographer.getRoleName()) &&
            !"ADMINISTRATOR".equals(photographer.getRoleName())) {
            throw new SecurityException("Only photographers can create listings");
        }
        if (listing.getPhotographerId() == null) {
            listing.setPhotographerId(photographer.getId());
        }
        if (!"ADMINISTRATOR".equals(photographer.getRoleName()) &&
            !listing.getPhotographerId().equals(photographer.getId())) {
            throw new SecurityException("Cannot create listings for other photographers");
        }
        validateListing(listing);
        if (listing.getActive() == null) listing.setActive(true);
        if (listing.getMaxConcurrent() == null) listing.setMaxConcurrent(1);
        listingMapper.insert(listing);
        return listingMapper.findById(listing.getId());
    }

    public Listing update(Listing listing, User actor) {
        Listing existing = listingMapper.findById(listing.getId());
        if (existing == null) throw new IllegalArgumentException("Listing not found");
        enforceOwnerOrAdmin(existing, actor);
        validateListing(listing);
        listingMapper.update(listing);
        return listingMapper.findById(listing.getId());
    }

    public boolean isOwner(Long listingId, Long userId) {
        Listing listing = listingMapper.findById(listingId);
        return listing != null && listing.getPhotographerId().equals(userId);
    }

    private void enforceOwnerOrAdmin(Listing listing, User actor) {
        if (!"ADMINISTRATOR".equals(actor.getRoleName()) &&
            !listing.getPhotographerId().equals(actor.getId())) {
            throw new SecurityException("Access denied to this listing");
        }
    }

    private void validateListing(Listing listing) {
        if (listing.getTitle() == null || listing.getTitle().isBlank())
            throw new IllegalArgumentException("Title is required");
        if (listing.getPrice() == null || listing.getPrice().signum() <= 0)
            throw new IllegalArgumentException("Price must be positive");
        if (listing.getDurationMinutes() == null || listing.getDurationMinutes() <= 0)
            throw new IllegalArgumentException("Duration must be positive");
    }
}
