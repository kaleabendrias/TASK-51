package com.booking.mapper;

import com.booking.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper {
    User findById(@Param("id") Long id);
    User findByUsername(@Param("username") String username);
    User findByEmail(@Param("email") String email);
    List<User> findAll();
    List<User> findByRoleId(@Param("roleId") Long roleId);
    void insert(User user);
    void update(User user);
    void updateEnabled(@Param("id") Long id, @Param("enabled") Boolean enabled);
    void updatePasswordHash(@Param("id") Long id, @Param("passwordHash") String passwordHash);
    void updatePhone(@Param("id") Long id, @Param("phone") String phone);
    List<User> findByDepartment(@Param("department") String department);
    List<User> findByTeam(@Param("team") String team);
}
