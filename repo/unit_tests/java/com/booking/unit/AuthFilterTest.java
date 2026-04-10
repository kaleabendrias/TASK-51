package com.booking.unit;

import com.booking.domain.User;
import com.booking.filter.AuthFilter;
import com.booking.mapper.BlacklistMapper;
import com.booking.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthFilterTest {

    @Mock BlacklistMapper blacklistMapper;
    @Mock UserMapper userMapper;
    @InjectMocks AuthFilter authFilter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach void setup() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
    }

    @Test void authEndpointAllowedWithoutSession() throws Exception {
        request.setRequestURI("/api/auth/login");
        authFilter.doFilter(request, response, chain);
        assertEquals(200, response.getStatus());
    }

    @Test void noSessionReturns401() throws Exception {
        request.setRequestURI("/api/orders");
        authFilter.doFilter(request, response, chain);
        assertEquals(401, response.getStatus());
    }

    @Test void disabledUserReturns403() throws Exception {
        request.setRequestURI("/api/orders");
        User sessionUser = new User();
        sessionUser.setId(5L);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentUser", sessionUser);
        request.setSession(session);

        User freshUser = new User();
        freshUser.setId(5L);
        freshUser.setEnabled(false);
        when(userMapper.findById(5L)).thenReturn(freshUser);

        authFilter.doFilter(request, response, chain);
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("disabled"));
    }

    @Test void enabledUserPassesThrough() throws Exception {
        request.setRequestURI("/api/orders");
        User sessionUser = new User();
        sessionUser.setId(4L);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentUser", sessionUser);
        request.setSession(session);

        User freshUser = new User();
        freshUser.setId(4L);
        freshUser.setEnabled(true);
        when(userMapper.findById(4L)).thenReturn(freshUser);
        when(blacklistMapper.findActiveByUserId(4L)).thenReturn(null);

        authFilter.doFilter(request, response, chain);
        assertEquals(200, response.getStatus());
    }

    @Test void deletedUserReturns403() throws Exception {
        request.setRequestURI("/api/orders");
        User sessionUser = new User();
        sessionUser.setId(99L);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentUser", sessionUser);
        request.setSession(session);

        when(userMapper.findById(99L)).thenReturn(null);

        authFilter.doFilter(request, response, chain);
        assertEquals(403, response.getStatus());
    }
}
