package com.booking.unit;

import com.booking.domain.User;
import com.booking.util.RoleGuard;
import com.booking.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoleGuardTest {

    private HttpSession sessionWith(String role, Long id) {
        HttpSession session = mock(HttpSession.class);
        User u = new User();
        u.setId(id);
        u.setRoleName(role);
        when(session.getAttribute("currentUser")).thenReturn(u);
        return session;
    }

    @Test void requireLoginSucceeds() {
        assertNotNull(RoleGuard.requireLogin(sessionWith("CUSTOMER", 1L)));
    }
    @Test void requireLoginNoSession() {
        HttpSession s = mock(HttpSession.class);
        when(s.getAttribute("currentUser")).thenReturn(null);
        assertThrows(SecurityException.class, () -> RoleGuard.requireLogin(s));
    }
    @Test void requireAdminSucceeds() {
        assertNotNull(RoleGuard.requireAdmin(sessionWith("ADMINISTRATOR", 1L)));
    }
    @Test void requireAdminDenied() {
        assertThrows(SecurityException.class, () -> RoleGuard.requireAdmin(sessionWith("CUSTOMER", 1L)));
    }
    @Test void requireRoleMatches() {
        assertNotNull(RoleGuard.requireRole(sessionWith("PHOTOGRAPHER", 2L), "PHOTOGRAPHER", "ADMINISTRATOR"));
    }
    @Test void requireRoleNoMatch() {
        assertThrows(SecurityException.class, () -> RoleGuard.requireRole(sessionWith("CUSTOMER", 4L), "PHOTOGRAPHER"));
    }
    @Test void requireOwnerOrAdminOwner() {
        assertDoesNotThrow(() -> RoleGuard.requireOwnerOrAdmin(sessionWith("CUSTOMER", 4L), 4L));
    }
    @Test void requireOwnerOrAdminAdmin() {
        assertDoesNotThrow(() -> RoleGuard.requireOwnerOrAdmin(sessionWith("ADMINISTRATOR", 1L), 99L));
    }
    @Test void requireOwnerOrAdminDenied() {
        assertThrows(SecurityException.class, () -> RoleGuard.requireOwnerOrAdmin(sessionWith("CUSTOMER", 4L), 5L));
    }
}
