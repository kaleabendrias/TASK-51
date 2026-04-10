package com.booking.mapper;

import com.booking.domain.PointsLedgerEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PointsLedgerMapper {
    List<PointsLedgerEntry> findByUserId(@Param("userId") Long userId);
    int getBalance(@Param("userId") Long userId);
    void insert(PointsLedgerEntry entry);
    void updateUserBalance(@Param("userId") Long userId, @Param("balance") int balance);
    void adjustUserBalanceAtomic(@Param("userId") Long userId, @Param("delta") int delta);
    void adjustUserBalanceAtomicFloor(@Param("userId") Long userId, @Param("delta") int delta);
    List<java.util.Map<String, Object>> getLeaderboard();
}
