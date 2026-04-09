package com.booking.mapper;

import com.booking.domain.PointsAdjustment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PointsAdjustmentMapper {
    List<PointsAdjustment> findByUserId(@Param("userId") Long userId);
    List<PointsAdjustment> findAll();
    void insert(PointsAdjustment adjustment);
}
