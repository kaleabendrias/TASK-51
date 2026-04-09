package com.booking.mapper;

import com.booking.domain.PointsRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PointsRuleMapper {
    PointsRule findById(@Param("id") Long id);
    List<PointsRule> findAll();
    List<PointsRule> findActive();
    PointsRule findByTrigger(@Param("triggerEvent") String triggerEvent);
    void insert(PointsRule rule);
    void update(PointsRule rule);
}
