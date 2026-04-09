package com.booking.service;

import com.booking.domain.Listing;
import com.booking.domain.User;
import com.booking.mapper.ListingMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ListingService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

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

    private static final java.util.Set<String> VALID_SORTS = java.util.Set.of(
            "newest", "price_asc", "price_desc", "rating", "duration");

    public Map<String, Object> search(String keyword, String category,
                                      BigDecimal minPrice, BigDecimal maxPrice,
                                      String location, String locationState, String locationCity,
                                      String locationNeighborhood,
                                      String theme, String transportMode,
                                      BigDecimal minRating, String availableDate,
                                      String sortBy, int page, int size) {
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int offset = Math.max(page - 1, 0) * pageSize;
        String sort = (sortBy != null && VALID_SORTS.contains(sortBy)) ? sortBy : "newest";

        List<Listing> items = listingMapper.search(keyword, category, minPrice, maxPrice,
                location, locationState, locationCity, locationNeighborhood,
                theme, transportMode, minRating, availableDate, sort, offset, pageSize);
        long total = listingMapper.searchCount(keyword, category, minPrice, maxPrice,
                location, locationState, locationCity, locationNeighborhood,
                theme, transportMode, minRating, availableDate);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("page", page);
        result.put("size", pageSize);
        result.put("total", total);
        result.put("totalPages", (int) Math.ceil((double) total / pageSize));
        return result;
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
