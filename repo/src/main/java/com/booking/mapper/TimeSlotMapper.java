package com.booking.mapper;

import com.booking.domain.TimeSlot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface TimeSlotMapper {
    TimeSlot findById(@Param("id") Long id);
    TimeSlot findByIdForUpdate(@Param("id") Long id);
    List<TimeSlot> findByListingId(@Param("listingId") Long listingId);
    List<TimeSlot> findAvailable(@Param("listingId") Long listingId,
                                 @Param("startDate") LocalDate startDate,
                                 @Param("endDate") LocalDate endDate);
    void insert(TimeSlot timeSlot);
    int incrementBookedCount(@Param("id") Long id, @Param("version") Integer version);
    int decrementBookedCount(@Param("id") Long id, @Param("version") Integer version);
    void delete(@Param("id") Long id);
}
