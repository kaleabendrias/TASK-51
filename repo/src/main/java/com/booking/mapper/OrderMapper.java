package com.booking.mapper;

import com.booking.domain.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderMapper {
    Order findById(@Param("id") Long id);
    Order findByOrderNumber(@Param("orderNumber") String orderNumber);
    List<Order> findByCustomerId(@Param("customerId") Long customerId);
    List<Order> findByPhotographerId(@Param("photographerId") Long photographerId);
    List<Order> findAll();
    List<Order> findByStatus(@Param("status") String status);
    List<Order> findUnpaidExpired(@Param("now") LocalDateTime now);
    void insert(Order order);
    void updateStatus(@Param("id") Long id, @Param("status") String status);
    void update(Order order);
    long countByCustomerId(@Param("customerId") Long customerId);
}
