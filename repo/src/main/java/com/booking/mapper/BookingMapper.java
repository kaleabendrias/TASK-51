package com.booking.mapper;

import com.booking.domain.Booking;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface BookingMapper {
    Booking findById(@Param("id") Long id);
    List<Booking> findAll();
    List<Booking> findByCustomerId(@Param("customerId") Long customerId);
    List<Booking> findByPhotographerId(@Param("photographerId") Long photographerId);
    List<Booking> findByStatus(@Param("status") String status);
    List<Booking> findByDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    List<Booking> findConflicting(@Param("photographerId") Long photographerId,
                                  @Param("bookingDate") LocalDate bookingDate,
                                  @Param("startTime") String startTime,
                                  @Param("endTime") String endTime,
                                  @Param("excludeId") Long excludeId);
    void insert(Booking booking);
    void update(Booking booking);
    void updateStatus(@Param("id") Long id, @Param("status") String status);
    long countAll();
}
