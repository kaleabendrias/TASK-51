package com.booking.mapper;

import com.booking.domain.Service;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ServiceMapper {
    Service findById(@Param("id") Long id);
    List<Service> findAll();
    List<Service> findActive();
    void insert(Service service);
    void update(Service service);
}
