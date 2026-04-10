package com.booking.unit;

import com.booking.filter.CsrfFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class CsrfFilterTest {

    private CsrfFilter csrfFilter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach void setup() {
        csrfFilter = new CsrfFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
        request.setServerName("localhost");
        request.setServerPort(80);
    }

    @Test void getRequestPassesWithoutOrigin() throws Exception {
        request.setMethod("GET");
        request.setRequestURI("/api/orders");
        csrfFilter.doFilter(request, response, chain);
        assertEquals(200, response.getStatus());
    }

    @Test void authEndpointsSkipCsrfCheck() throws Exception {
        request.setMethod("POST");
        request.setRequestURI("/api/auth/login");
        csrfFilter.doFilter(request, response, chain);
        assertEquals(200, response.getStatus());
    }

    @Test void postWithoutOriginOrRefererIsRejected() throws Exception {
        request.setMethod("POST");
        request.setRequestURI("/api/orders");
        csrfFilter.doFilter(request, response, chain);
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("CSRF"));
    }

    @Test void postWithMatchingOriginPasses() throws Exception {
        request.setMethod("POST");
        request.setRequestURI("/api/orders");
        request.addHeader("Origin", "http://localhost");
        csrfFilter.doFilter(request, response, chain);
        assertEquals(200, response.getStatus());
    }

    @Test void postWithMismatchedOriginIsRejected() throws Exception {
        request.setMethod("POST");
        request.setRequestURI("/api/orders");
        request.addHeader("Origin", "http://evil.com");
        csrfFilter.doFilter(request, response, chain);
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("origin mismatch"));
    }

    @Test void postWithMatchingRefererPasses() throws Exception {
        request.setMethod("POST");
        request.setRequestURI("/api/orders");
        request.addHeader("Referer", "http://localhost/dashboard");
        csrfFilter.doFilter(request, response, chain);
        assertEquals(200, response.getStatus());
    }

    @Test void putRequestSubjectToCsrf() throws Exception {
        request.setMethod("PUT");
        request.setRequestURI("/api/listings/1");
        csrfFilter.doFilter(request, response, chain);
        assertEquals(403, response.getStatus());
    }

    @Test void deleteRequestSubjectToCsrf() throws Exception {
        request.setMethod("DELETE");
        request.setRequestURI("/api/addresses/1");
        csrfFilter.doFilter(request, response, chain);
        assertEquals(403, response.getStatus());
    }

    @Test void patchRequestSubjectToCsrf() throws Exception {
        request.setMethod("PATCH");
        request.setRequestURI("/api/users/1");
        csrfFilter.doFilter(request, response, chain);
        assertEquals(403, response.getStatus());
    }

    @Test void originWithPortMatchesServerWithPort() throws Exception {
        request.setMethod("POST");
        request.setRequestURI("/api/orders");
        request.setServerPort(8080);
        request.addHeader("Origin", "http://localhost:8080");
        csrfFilter.doFilter(request, response, chain);
        assertEquals(200, response.getStatus());
    }

    @Test void originPortMismatchIsRejected() throws Exception {
        request.setMethod("POST");
        request.setRequestURI("/api/orders");
        request.setServerPort(8080);
        request.addHeader("Origin", "http://localhost:9999");
        csrfFilter.doFilter(request, response, chain);
        assertEquals(403, response.getStatus());
    }
}
