package com.booking.service;

import com.booking.domain.User;
import com.booking.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public User getById(Long id) {
        return userMapper.findById(id);
    }

    public List<User> getAll() {
        return userMapper.findAll();
    }

    public List<User> getPhotographers() {
        return userMapper.findByRoleId(2L);
    }

    public void update(User user) {
        User existing = userMapper.findById(user.getId());
        if (existing == null) {
            throw new IllegalArgumentException("User not found");
        }
        userMapper.update(user);
    }

    public void setEnabled(Long id, boolean enabled) {
        User existing = userMapper.findById(id);
        if (existing == null) {
            throw new IllegalArgumentException("User not found");
        }
        userMapper.updateEnabled(id, enabled);
    }
}
