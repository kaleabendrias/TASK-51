package com.booking.unit;

import com.booking.domain.User;
import com.booking.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionUtilTest {

    @Test void setAndGetCurrentUser() {
        HttpSession s = mock(HttpSession.class);
        User u = new User(); u.setId(1L);
        SessionUtil.setCurrentUser(s, u);
        verify(s).setAttribute("currentUser", u);
    }

    @Test void isAdminTrue() {
        HttpSession s = mock(HttpSession.class);
        User u = new User(); u.setRoleName("ADMINISTRATOR");
        when(s.getAttribute("currentUser")).thenReturn(u);
        assertTrue(SessionUtil.isAdmin(s));
    }

    @Test void isAdminFalse() {
        HttpSession s = mock(HttpSession.class);
        User u = new User(); u.setRoleName("CUSTOMER");
        when(s.getAttribute("currentUser")).thenReturn(u);
        assertFalse(SessionUtil.isAdmin(s));
    }

    @Test void isAdminNullSession() {
        HttpSession s = mock(HttpSession.class);
        when(s.getAttribute("currentUser")).thenReturn(null);
        assertFalse(SessionUtil.isAdmin(s));
    }

    @Test void hasRole() {
        HttpSession s = mock(HttpSession.class);
        User u = new User(); u.setRoleName("PHOTOGRAPHER");
        when(s.getAttribute("currentUser")).thenReturn(u);
        assertTrue(SessionUtil.hasRole(s, "PHOTOGRAPHER"));
        assertFalse(SessionUtil.hasRole(s, "ADMINISTRATOR"));
    }
}
