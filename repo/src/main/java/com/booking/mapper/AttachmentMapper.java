package com.booking.mapper;

import com.booking.domain.Attachment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AttachmentMapper {
    Attachment findById(@Param("id") Long id);
    List<Attachment> findByBookingId(@Param("bookingId") Long bookingId);
    void insert(Attachment attachment);
    void delete(@Param("id") Long id);
}
