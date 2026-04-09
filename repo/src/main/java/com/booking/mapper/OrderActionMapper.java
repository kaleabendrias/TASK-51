package com.booking.mapper;

import com.booking.domain.OrderAction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrderActionMapper {
    List<OrderAction> findByOrderId(@Param("orderId") Long orderId);
    void insert(OrderAction action);
}
