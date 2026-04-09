package com.booking.mapper;

import com.booking.domain.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RoleMapper {
    Role findById(@Param("id") Long id);
    Role findByName(@Param("name") String name);
    List<Role> findAll();
}
