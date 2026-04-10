package com.booking.controller;

import com.booking.domain.Listing;
import com.booking.domain.SearchTerm;
import com.booking.domain.User;
import com.booking.service.ListingService;
import com.booking.service.SearchTermService;
import com.booking.util.RoleGuard;
import com.booking.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/listings")
public class ListingController {

    private final ListingService listingService;
    private final SearchTermService searchTermService;

    public ListingController(ListingService listingService, SearchTermService searchTermService) {
        this.listingService = listingService;
        this.searchTermService = searchTermService;
    }

    @GetMapping
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(listingService.getActive());
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) String category,
                                    @RequestParam(required = false) BigDecimal minPrice,
                                    @RequestParam(required = false) BigDecimal maxPrice,
                                    @RequestParam(required = false) String location,
                                    @RequestParam(required = false) String locationState,
                                    @RequestParam(required = false) String locationCity,
                                    @RequestParam(required = false) String locationNeighborhood,
                                    @RequestParam(required = false) String theme,
                                    @RequestParam(required = false) String transportMode,
                                    @RequestParam(required = false) BigDecimal minRating,
                                    @RequestParam(required = false) String availableDate,
                                    @RequestParam(required = false) String sortBy,
                                    @RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        // Record search term server-side for popular suggestions
        if (keyword != null && !keyword.isBlank()) {
            searchTermService.recordTerm(keyword);
        }
        return ResponseEntity.ok(listingService.search(keyword, category, minPrice, maxPrice,
                location, locationState, locationCity, locationNeighborhood,
                theme, transportMode, minRating, availableDate, sortBy, page, size));
    }

    @GetMapping("/search/suggestions")
    public ResponseEntity<?> searchSuggestions(@RequestParam(defaultValue = "15") int limit) {
        List<SearchTerm> popular = searchTermService.getPopular(limit);
        return ResponseEntity.ok(popular.stream().map(SearchTerm::getTerm).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        Listing listing = listingService.getById(id);
        if (listing == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(listing);
    }

    @GetMapping("/my")
    public ResponseEntity<?> myListings(HttpSession session) {
        User user = RoleGuard.requireRole(session, "PHOTOGRAPHER", "SERVICE_PROVIDER", "ADMINISTRATOR");
        return ResponseEntity.ok(listingService.getByPhotographer(user.getId()));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Listing listing, HttpSession session) {
        User user = SessionUtil.getCurrentUser(session);
        try {
            return ResponseEntity.ok(listingService.create(listing, user));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Listing listing,
                                    HttpSession session) {
        User user = SessionUtil.getCurrentUser(session);
        try {
            listing.setId(id);
            return ResponseEntity.ok(listingService.update(listing, user));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
