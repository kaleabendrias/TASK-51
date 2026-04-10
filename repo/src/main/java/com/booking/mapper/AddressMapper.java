package com.booking.mapper;

import com.booking.domain.Address;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AddressMapper {
    Address findById(@Param("id") Long id);
    List<Address> findByUserId(@Param("userId") Long userId);
    Address findDefaultByUserId(@Param("userId") Long userId);
    void insert(Address address);
    void update(Address address);
    void clearDefault(@Param("userId") Long userId);
    List<Address> lockByUserId(@Param("userId") Long userId);
    void delete(@Param("id") Long id);
}
