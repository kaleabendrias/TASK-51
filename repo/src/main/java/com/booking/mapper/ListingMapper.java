package com.booking.mapper;

import com.booking.domain.Listing;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ListingMapper {
    Listing findById(@Param("id") Long id);
    List<Listing> findAll();
    List<Listing> findActive();
    List<Listing> findByPhotographerId(@Param("photographerId") Long photographerId);
    List<Listing> search(@Param("keyword") String keyword,
                         @Param("category") String category,
                         @Param("minPrice") java.math.BigDecimal minPrice,
                         @Param("maxPrice") java.math.BigDecimal maxPrice,
                         @Param("location") String location);
    void insert(Listing listing);
    void update(Listing listing);
}
