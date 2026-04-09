package com.booking.mapper;

import com.booking.domain.NotificationPreference;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface NotificationPreferenceMapper {
    NotificationPreference findByUserId(@Param("userId") Long userId);
    void insert(NotificationPreference pref);
    void update(NotificationPreference pref);
}
