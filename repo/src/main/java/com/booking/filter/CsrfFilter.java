package com.booking.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

/**
 * CSRF defense via strict Origin/Referer validation on state-changing requests.
 * Rejects POST/PUT/PATCH/DELETE requests whose Origin (or Referer fallback)
 * does not match the request's own host, unless the request targets a
 * public auth endpoint.
 */
@Component
public class CsrfFilter implements Filter {

    private static final Set<String> STATE_CHANGING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpResp = (HttpServletResponse) response;

        String method = httpReq.getMethod().toUpperCase();

        if (!STATE_CHANGING_METHODS.contains(method)) {
            chain.doFilter(request, response);
            return;
        }

        // Allow auth endpoints (login/register/logout) without CSRF check
        String path = httpReq.getRequestURI();
        if (path.startsWith("/api/auth/")) {
            chain.doFilter(request, response);
            return;
        }

        String origin = httpReq.getHeader("Origin");
        String referer = httpReq.getHeader("Referer");

        String sourceHost = extractHost(origin);
        if (sourceHost == null) {
            sourceHost = extractHost(referer);
        }

        if (sourceHost == null) {
            httpResp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            httpResp.setContentType("application/json");
            httpResp.getWriter().write("{\"error\":\"CSRF validation failed: missing Origin/Referer header\"}");
            return;
        }

        String targetHost = httpReq.getServerName();
        int targetPort = httpReq.getServerPort();
        String targetHostPort = targetHost + (targetPort == 80 || targetPort == 443 ? "" : ":" + targetPort);

        if (!sourceHost.equals(targetHostPort) && !sourceHost.equals(targetHost)) {
            httpResp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            httpResp.setContentType("application/json");
            httpResp.getWriter().write("{\"error\":\"CSRF validation failed: origin mismatch\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private String extractHost(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) return null;
        try {
            URI uri = URI.create(headerValue.trim());
            String host = uri.getHost();
            if (host == null) return null;
            int port = uri.getPort();
            return host + (port > 0 && port != 80 && port != 443 ? ":" + port : "");
        } catch (Exception e) {
            return null;
        }
    }
}
