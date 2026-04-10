package com.booking.service;

import com.booking.domain.User;
import com.booking.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

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

    public List<User> getProviders() {
        List<User> photographers = userMapper.findByRoleId(2L);
        List<User> serviceProviders = userMapper.findByRoleId(4L);
        List<User> all = new java.util.ArrayList<>(photographers);
        all.addAll(serviceProviders);
        return all;
    }

    /**
     * Patch-style selective update — only modifies fields present in the map.
     * Never overwrites password_hash or encrypted phone.
     */
    public void patchUpdate(Long id, Map<String, Object> fields) {
        User existing = userMapper.findById(id);
        if (existing == null) throw new IllegalArgumentException("User not found");

        if (fields.containsKey("email")) existing.setEmail((String) fields.get("email"));
        if (fields.containsKey("fullName")) existing.setFullName((String) fields.get("fullName"));
        if (fields.containsKey("roleId")) existing.setRoleId(((Number) fields.get("roleId")).longValue());
        if (fields.containsKey("enabled")) existing.setEnabled((Boolean) fields.get("enabled"));
        // phone and passwordHash are never overwritten through this path
        userMapper.updateProfile(existing);
    }

    public void update(User user) {
        User existing = userMapper.findById(user.getId());
        if (existing == null) throw new IllegalArgumentException("User not found");
        userMapper.update(user);
    }

    public void setEnabled(Long id, boolean enabled) {
        User existing = userMapper.findById(id);
        if (existing == null) throw new IllegalArgumentException("User not found");
        userMapper.updateEnabled(id, enabled);
    }
}
