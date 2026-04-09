package com.booking.unit;

import com.booking.domain.User;
import com.booking.mapper.UserMapper;
import com.booking.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserMapper userMapper;
    @Spy BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    @InjectMocks AuthService authService;

    private User makeUser(boolean enabled) {
        User u = new User(); u.setId(1L); u.setUsername("test");
        u.setPasswordHash(new BCryptPasswordEncoder().encode("pass123"));
        u.setEnabled(enabled); u.setEmail("t@t.com"); u.setRoleName("CUSTOMER");
        return u;
    }

    @Test void authenticateSuccess() {
        when(userMapper.findByUsername("test")).thenReturn(makeUser(true));
        assertNotNull(authService.authenticate("test", "pass123"));
    }

    @Test void authenticateWrongPassword() {
        when(userMapper.findByUsername("test")).thenReturn(makeUser(true));
        assertNull(authService.authenticate("test", "wrong"));
    }

    @Test void authenticateDisabledUser() {
        when(userMapper.findByUsername("test")).thenReturn(makeUser(false));
        assertNull(authService.authenticate("test", "pass123"));
    }

    @Test void authenticateUnknownUser() {
        when(userMapper.findByUsername("ghost")).thenReturn(null);
        assertNull(authService.authenticate("ghost", "pass"));
    }

    @Test void registerSuccess() {
        when(userMapper.findByUsername("newuser")).thenReturn(null);
        when(userMapper.findByEmail("new@test.com")).thenReturn(null);
        User result = authService.register("newuser", "new@test.com", "pass123", "New", "+1", 1L);
        assertNotNull(result);
        verify(userMapper).insert(argThat(u -> u.getUsername().equals("newuser") && u.getPasswordHash().startsWith("$2a$")));
    }

    @Test void registerDuplicateUsername() {
        when(userMapper.findByUsername("taken")).thenReturn(new User());
        assertThrows(IllegalArgumentException.class, () -> authService.register("taken", "e@t.com", "p", "N", null, 1L));
    }

    @Test void registerDuplicateEmail() {
        when(userMapper.findByUsername("newu")).thenReturn(null);
        when(userMapper.findByEmail("taken@t.com")).thenReturn(new User());
        assertThrows(IllegalArgumentException.class, () -> authService.register("newu", "taken@t.com", "p", "N", null, 1L));
    }

    @Test void changePasswordSuccess() {
        User u = makeUser(true);
        String oldHash = u.getPasswordHash();
        when(userMapper.findById(1L)).thenReturn(u);
        authService.changePassword(1L, "pass123", "newpass");
        verify(userMapper).update(argThat(updated -> updated.getPasswordHash() != null
                && !updated.getPasswordHash().equals(oldHash)));
    }

    @Test void changePasswordWrongCurrent() {
        when(userMapper.findById(1L)).thenReturn(makeUser(true));
        assertThrows(IllegalArgumentException.class, () -> authService.changePassword(1L, "wrong", "new"));
    }

    @Test void changePasswordUserNotFound() {
        when(userMapper.findById(99L)).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> authService.changePassword(99L, "x", "y"));
    }
}
