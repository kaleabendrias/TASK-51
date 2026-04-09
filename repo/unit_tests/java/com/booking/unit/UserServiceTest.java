package com.booking.unit;

import com.booking.domain.User;
import com.booking.mapper.UserMapper;
import com.booking.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserMapper userMapper;
    @InjectMocks UserService userService;

    @Test void getByIdDelegates() {
        when(userMapper.findById(1L)).thenReturn(new User());
        assertNotNull(userService.getById(1L));
    }

    @Test void getAllDelegates() {
        when(userMapper.findAll()).thenReturn(List.of());
        assertEquals(0, userService.getAll().size());
    }

    @Test void getPhotographersDelegates() {
        when(userMapper.findByRoleId(2L)).thenReturn(List.of());
        assertEquals(0, userService.getPhotographers().size());
    }

    @Test void updateNotFound() {
        User u = new User(); u.setId(99L);
        when(userMapper.findById(99L)).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> userService.update(u));
    }

    @Test void updateSuccess() {
        User u = new User(); u.setId(1L);
        when(userMapper.findById(1L)).thenReturn(u);
        userService.update(u);
        verify(userMapper).update(u);
    }

    @Test void setEnabledNotFound() {
        when(userMapper.findById(99L)).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> userService.setEnabled(99L, true));
    }

    @Test void setEnabledSuccess() {
        when(userMapper.findById(1L)).thenReturn(new User());
        userService.setEnabled(1L, false);
        verify(userMapper).updateEnabled(1L, false);
    }
}
