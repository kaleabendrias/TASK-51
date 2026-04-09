package com.booking.mapper;

import com.booking.domain.IdempotencyToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface IdempotencyTokenMapper {
    IdempotencyToken findByToken(@Param("token") String token);
    void insert(IdempotencyToken token);
    void updateResponse(@Param("token") String token,
                        @Param("responseStatus") Integer responseStatus,
                        @Param("responseBody") String responseBody);
    void deleteExpired(@Param("now") LocalDateTime now);
}
