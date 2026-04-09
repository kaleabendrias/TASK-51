package com.booking.filter;

import com.booking.domain.BlacklistEntry;
import com.booking.domain.User;
import com.booking.mapper.BlacklistMapper;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class AuthFilter implements Filter {

    private final BlacklistMapper blacklistMapper;

    public AuthFilter(BlacklistMapper blacklistMapper) {
        this.blacklistMapper = blacklistMapper;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpResp = (HttpServletResponse) response;

        String path = httpReq.getRequestURI();

        // Allow auth endpoints without session
        if (path.startsWith("/api/auth/")) {
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = httpReq.getSession(false);
        if (session == null || session.getAttribute("currentUser") == null) {
            httpResp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResp.setContentType("application/json");
            httpResp.getWriter().write("{\"error\":\"Authentication required\"}");
            return;
        }

        // Check blacklist status
        User user = (User) session.getAttribute("currentUser");
        BlacklistEntry bl = blacklistMapper.findActiveByUserId(user.getId());
        if (bl != null && !bl.isExpired()) {
            httpResp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            httpResp.setContentType("application/json");
            httpResp.getWriter().write("{\"error\":\"Account is blacklisted: " +
                    bl.getReason().replace("\"", "'") + "\"}");
            return;
        }

        chain.doFilter(request, response);
    }
}
