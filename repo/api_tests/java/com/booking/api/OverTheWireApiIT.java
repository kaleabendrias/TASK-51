package com.booking.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Over-the-wire HTTP integration tests using TestRestTemplate.
 *
 * Unlike MockMvc tests (which exercise Spring MVC dispatch in-process),
 * these tests send real TCP/HTTP requests to a live embedded server on a
 * random port. They exercise:
 *
 *   • Real HTTP transport: headers, status lines, cookies, redirects
 *   • Content-Type negotiation and response encoding
 *   • Session cookie round-trip: Set-Cookie on login, Cookie on subsequent calls
 *   • Unauthenticated access rejections at the HTTP layer (not just at the filter)
 *   • Response body deserialisation from actual HTTP payloads
 *
 * Session management uses the raw {@code Set-Cookie / Cookie} header pair so
 * that tests are independent of Spring's MockHttpSession abstractions.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            // Use a dedicated H2 instance to avoid PK conflicts with the
            // MockMvc 'testdb' context that also runs data-test.sql
            "spring.datasource.url=jdbc:h2:mem:otwtestdb;" +
                "MODE=MySQL;DB_CLOSE_DELAY=-1;" +
                "DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE"
        }
)
@ActiveProfiles("test")
class OverTheWireApiIT {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper om;

    /**
     * RestTemplate backed by Java 11's HttpClient (JdkClientHttpRequestFactory).
     * Unlike HttpURLConnection, it does NOT retry on 401 (no built-in auth negotiation),
     * does NOT restrict the "Origin" header, does NOT follow redirects automatically,
     * and a no-op ResponseErrorHandler prevents exceptions on 4xx/5xx responses.
     */
    private RestTemplate restTemplate;

    /**
     * The Origin header value that passes the CSRF filter.
     * The filter compares sourceHost against httpReq.getServerName() ("localhost").
     * Using "http://localhost" (no port) satisfies the `sourceHost.equals(targetHost)`
     * branch even when the embedded server runs on a non-standard port.
     */
    private static final String CSRF_ORIGIN = "http://localhost";

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        // Use Java 11's HttpClient via JdkClientHttpRequestFactory:
        //  - Does NOT retry on 401 (no built-in auth negotiation)
        //  - Does NOT restrict the "Origin" header (unlike HttpURLConnection)
        //  - Does NOT follow redirects automatically
        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        restTemplate = new RestTemplate(new JdkClientHttpRequestFactory(httpClient));
        // Never throw on 4xx/5xx — tests assert on the response status directly
        restTemplate.setErrorHandler(new ResponseErrorHandler() {
            @Override public boolean hasError(ClientHttpResponse r) { return false; }
            @Override public void handleError(ClientHttpResponse r) { }
        });
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Logs in and returns the {@code JSESSIONID} cookie value for use in
     * subsequent requests.
     */
    private String login(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = Map.of("username", username, "password", password);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl + "/api/auth/login",
                new HttpEntity<>(body, headers),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Login must return 200 OK for valid credentials");

        String cookieHeader = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(cookieHeader, "Login must set a session cookie (Set-Cookie header)");
        // Return just the first directive (JSESSIONID=xxx)
        return cookieHeader.split(";")[0];
    }

    private HttpHeaders withCookie(String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, cookie);
        return headers;
    }

    private HttpHeaders withCookieAndJson(String cookie) {
        HttpHeaders headers = withCookie(cookie);
        headers.setContentType(MediaType.APPLICATION_JSON);
        // CSRF_ORIGIN matches the serverName "localhost" in the CSRF filter check
        headers.set("Origin", CSRF_ORIGIN);
        return headers;
    }

    // ─── Authentication — transport layer ─────────────────────────────────────

    @Test
    void login_validCredentials_returns200AndSetsCookie() {
        String cookie = login("cust1", "password123");
        assertTrue(cookie.startsWith("JSESSIONID="),
                "Session cookie must be a JSESSIONID; got: " + cookie);
    }

    @Test
    void login_wrongPassword_returns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = Map.of("username", "cust1", "password", "WRONG");

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl + "/api/auth/login",
                new HttpEntity<>(body, headers),
                Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
                "Wrong password must return 401 Unauthorized");
    }

    @Test
    void getMe_withValidSession_returns200AndCorrectUser() {
        String cookie = login("cust1", "password123");

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/api/auth/me",
                HttpMethod.GET,
                new HttpEntity<>(withCookie(cookie)),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(), "/api/auth/me must return 200");
        assertNotNull(response.getBody(), "Response body must not be null");
        assertEquals("cust1", response.getBody().get("username"),
                "/api/auth/me must return the logged-in user's username");
    }

    @Test
    void getMe_withoutSession_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/api/auth/me",
                String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
                "/api/auth/me without a session must return 401");
    }

    @Test
    void logout_invalidatesSession_subsequentCallReturns401() {
        String cookie = login("cust1", "password123");

        // Logout
        restTemplate.exchange(
                baseUrl + "/api/auth/logout",
                HttpMethod.POST,
                new HttpEntity<>(withCookie(cookie)),
                Void.class);

        // After logout, the same session cookie must no longer be valid
        ResponseEntity<String> afterLogout = restTemplate.exchange(
                baseUrl + "/api/auth/me",
                HttpMethod.GET,
                new HttpEntity<>(withCookie(cookie)),
                String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, afterLogout.getStatusCode(),
                "After logout the old session cookie must be rejected with 401");
    }

    // ─── Root redirect ────────────────────────────────────────────────────────

    @Test
    void rootPath_redirectsToIndex() {
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl + "/", String.class);

        assertTrue(
                response.getStatusCode() == HttpStatus.FOUND ||
                response.getStatusCode() == HttpStatus.MOVED_PERMANENTLY ||
                response.getStatusCode() == HttpStatus.OK,
                "Root path must redirect or serve index; got: " + response.getStatusCode());
    }

    // ─── Listings — unauthenticated access ────────────────────────────────────

    @Test
    void searchListings_withoutSession_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/api/listings/search?keyword=studio",
                String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
                "Search endpoint must require authentication; got: " + response.getStatusCode());
    }

    @Test
    void searchListings_withSession_returnsJsonArray() {
        String cookie = login("cust1", "password123");

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/api/listings/search?page=1&size=10",
                HttpMethod.GET,
                new HttpEntity<>(withCookie(cookie)),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(), "Search must return 200");
        assertNotNull(response.getBody(), "Search response body must not be null");
        assertTrue(response.getBody().containsKey("items"),
                "Search response must contain 'items' key; got keys: " + response.getBody().keySet());

        // Content-Type must be application/json
        MediaType ct = response.getHeaders().getContentType();
        assertNotNull(ct, "Content-Type header must be set");
        assertTrue(ct.isCompatibleWith(MediaType.APPLICATION_JSON),
                "Content-Type must be application/json; got: " + ct);
    }

    @Test
    void searchListings_returnsSeededResults() {
        String cookie = login("cust1", "password123");

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/api/listings/search?page=1&size=10",
                HttpMethod.GET,
                new HttpEntity<>(withCookie(cookie)),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) response.getBody().get("items");
        assertFalse(items.isEmpty(), "Search must return at least one seeded listing");

        Number total = (Number) response.getBody().get("total");
        assertNotNull(total, "Response must include 'total' count");
        assertTrue(total.intValue() >= 3,
                "Total must be ≥ 3 seeded listings; got " + total);
    }

    // ─── Order creation — HTTP transport layer ─────────────────────────────────

    @Test
    void createOrder_missingIdempotencyKey_returns400() {
        String cookie = login("cust1", "password123");

        HttpHeaders headers = withCookieAndJson(cookie);
        // No Idempotency-Key header

        Map<String, Object> body = Map.of("listingId", 1, "timeSlotId", 6);
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/api/orders",
                new HttpEntity<>(body, headers),
                String.class);

        // Must reject (400 Bad Request) because Idempotency-Key is required
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
                "POST /api/orders without Idempotency-Key must return 400");
    }

    @Test
    void createOrder_withAllRequiredFields_returns200AndOrderNumber() throws Exception {
        String cookie = login("cust1", "password123");

        // First create a slot via the API (using admin session so we don't need
        // a photographer session managed separately)
        String photoCookie = login("photo1", "password123");
        HttpHeaders photoHeaders = withCookieAndJson(photoCookie);
        Map<String, Object> slotBody = Map.of(
                "listingId", 1, "slotDate", "2026-09-01",
                "startTime", "09:00", "endTime", "11:00", "capacity", 5);

        ResponseEntity<Map> slotResponse = restTemplate.postForEntity(
                baseUrl + "/api/timeslots",
                new HttpEntity<>(slotBody, photoHeaders),
                Map.class);
        assertEquals(HttpStatus.OK, slotResponse.getStatusCode(), "Slot creation must succeed");
        long slotId = ((Number) slotResponse.getBody().get("id")).longValue();

        // Now create an order via real HTTP
        HttpHeaders custHeaders = withCookieAndJson(cookie);
        custHeaders.set("Idempotency-Key", "otw-create-" + UUID.randomUUID());
        Map<String, Object> orderBody = Map.of("listingId", 1, "timeSlotId", slotId);

        ResponseEntity<Map> orderResponse = restTemplate.postForEntity(
                baseUrl + "/api/orders",
                new HttpEntity<>(orderBody, custHeaders),
                Map.class);

        assertEquals(HttpStatus.OK, orderResponse.getStatusCode(),
                "POST /api/orders must return 200; got: " + orderResponse.getStatusCode());
        assertNotNull(orderResponse.getBody(), "Order response body must not be null");
        assertNotNull(orderResponse.getBody().get("orderNumber"),
                "Response must include orderNumber");
        assertEquals("CREATED", orderResponse.getBody().get("status"),
                "New order must have CREATED status");
    }

    // ─── CSRF protection ─────────────────────────────────────────────────────

    @Test
    void stateChangingRequest_withoutOriginHeader_isRejected() {
        String cookie = login("cust1", "password123");

        // POST without Origin header (CSRF guard should reject)
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.COOKIE, cookie);
        headers.set("Idempotency-Key", "otw-csrf-" + UUID.randomUUID());
        // No Origin header!

        Map<String, Object> body = Map.of("listingId", 1, "timeSlotId", 6);
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/api/orders",
                new HttpEntity<>(body, headers),
                String.class);

        // CSRF filter must reject without Origin
        assertNotEquals(HttpStatus.OK, response.getStatusCode(),
                "POST without Origin header must be rejected by CSRF guard");
    }

    // ─── Points — unauthenticated rejection ───────────────────────────────────

    @Test
    void pointsBalance_withoutSession_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/api/points/balance",
                String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
                "/api/points/balance must require authentication");
    }

    @Test
    void pointsBalance_withSession_returnsNonNegativeBalance() {
        String cookie = login("cust1", "password123");

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/api/points/balance",
                HttpMethod.GET,
                new HttpEntity<>(withCookie(cookie)),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Number balance = (Number) response.getBody().get("balance");
        assertNotNull(balance, "Points balance response must include 'balance' field");
        assertTrue(balance.intValue() >= 0, "Points balance must be non-negative");
    }

    // ─── Actuator health ─────────────────────────────────────────────────────

    @Test
    void actuatorHealth_isPublicAndReturnsUp() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                baseUrl + "/actuator/health",
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "/actuator/health must be publicly accessible and return 200");
        assertEquals("UP", response.getBody().get("status"),
                "Health status must be 'UP'");
    }

    // ─── Role-based access ────────────────────────────────────────────────────

    @Test
    void adminEndpoint_withCustomerSession_returns403() {
        String cookie = login("cust1", "password123");

        // /api/users (list all) is admin-only
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/api/users",
                HttpMethod.GET,
                new HttpEntity<>(withCookie(cookie)),
                String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(),
                "Admin-only endpoint must return 403 for customer session");
    }

    @Test
    void adminEndpoint_withAdminSession_returns200() {
        String cookie = login("admin", "password123");

        ResponseEntity<List> response = restTemplate.exchange(
                baseUrl + "/api/users",
                HttpMethod.GET,
                new HttpEntity<>(withCookie(cookie)),
                List.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "/api/users must return 200 for admin session");
        assertFalse(response.getBody().isEmpty(),
                "Admin user list must not be empty (seeded accounts present)");
    }
}
