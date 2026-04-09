package com.booking.mapper;

import com.booking.domain.BlacklistEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface BlacklistMapper {
    BlacklistEntry findById(@Param("id") Long id);
    BlacklistEntry findActiveByUserId(@Param("userId") Long userId);
    List<BlacklistEntry> findAll();
    List<BlacklistEntry> findExpiredActive(@Param("now") LocalDateTime now);
    void insert(BlacklistEntry entry);
    void deactivate(@Param("id") Long id, @Param("liftedBy") Long liftedBy,
                    @Param("liftReason") String liftReason);
}
