package com.booking.mapper;

import com.booking.domain.NotificationRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NotificationMapper {
    NotificationRecord findById(@Param("id") Long id);
    List<NotificationRecord> findByUserId(@Param("userId") Long userId);
    List<NotificationRecord> findQueued();
    void insert(NotificationRecord record);
    void updateStatus(@Param("id") Long id, @Param("status") String status);
}
