package com.booking.mapper;

import com.booking.domain.Listing;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface ListingMapper {
    Listing findById(@Param("id") Long id);
    List<Listing> findAll();
    List<Listing> findActive();
    List<Listing> findByPhotographerId(@Param("photographerId") Long photographerId);
    List<Listing> search(@Param("keyword") String keyword,
                         @Param("category") String category,
                         @Param("minPrice") BigDecimal minPrice,
                         @Param("maxPrice") BigDecimal maxPrice,
                         @Param("location") String location,
                         @Param("theme") String theme,
                         @Param("transportMode") String transportMode,
                         @Param("minRating") BigDecimal minRating,
                         @Param("availableDate") String availableDate,
                         @Param("offset") int offset,
                         @Param("limit") int limit);
    long searchCount(@Param("keyword") String keyword,
                     @Param("category") String category,
                     @Param("minPrice") BigDecimal minPrice,
                     @Param("maxPrice") BigDecimal maxPrice,
                     @Param("location") String location,
                     @Param("theme") String theme,
                     @Param("transportMode") String transportMode,
                     @Param("minRating") BigDecimal minRating,
                     @Param("availableDate") String availableDate);
    void insert(Listing listing);
    void update(Listing listing);
}
