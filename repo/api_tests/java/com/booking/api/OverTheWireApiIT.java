package com.booking.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Over-the-wire HTTP integration tests using RestTemplate.
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
     * Using "http://localhost" (no port) satisfies the {@code sourceHost.equals(targetHost)}
     * branch even when the embedded server runs on a non-standard port.
     */
    private static final String CSRF_ORIGIN = "http://localhost";

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
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
        return cookieHeader.split(";")[0];
    }

    private String loginAdmin()  { return login("admin",  "password123"); }
    private String loginPhoto1() { return login("photo1", "password123"); }
    private String loginCust1()  { return login("cust1",  "password123"); }

    private HttpHeaders withCookie(String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, cookie);
        return headers;
    }

    private HttpHeaders withCookieAndJson(String cookie) {
        HttpHeaders headers = withCookie(cookie);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Origin", CSRF_ORIGIN);
        return headers;
    }

    /** Creates a timeslot and returns its ID. */
    @SuppressWarnings("unchecked")
    private long createSlotOtw(String photoCookie, int listingId, String date) {
        HttpHeaders h = withCookieAndJson(photoCookie);
        Map<String, Object> body = Map.of("listingId", listingId, "slotDate", date,
                "startTime", "09:00", "endTime", "10:00", "capacity", 5);
        ResponseEntity<Map> r = restTemplate.postForEntity(
                baseUrl + "/api/timeslots", new HttpEntity<>(body, h), Map.class);
        assertEquals(HttpStatus.OK, r.getStatusCode(), "Slot creation must succeed; got " + r.getStatusCode());
        return ((Number) r.getBody().get("id")).longValue();
    }

    /** Creates an order for listingId on slotId and returns the order ID. */
    @SuppressWarnings("unchecked")
    private long createOrderOtw(String custCookie, int listingId, long slotId, String pfx) {
        HttpHeaders h = withCookieAndJson(custCookie);
        h.set("Idempotency-Key", pfx + "-" + UUID.randomUUID());
        Map<String, Object> body = Map.of("listingId", listingId, "timeSlotId", slotId);
        ResponseEntity<Map> r = restTemplate.postForEntity(
                baseUrl + "/api/orders", new HttpEntity<>(body, h), Map.class);
        assertEquals(HttpStatus.OK, r.getStatusCode(), "Order creation must succeed; got " + r.getStatusCode());
        return ((Number) r.getBody().get("id")).longValue();
    }

    /** POSTs to path with an Idempotency-Key header and a JSON body; asserts 200. */
    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> postWithKey(String cookie, String path, String keyPfx, Map<String, Object> body) {
        HttpHeaders h = withCookieAndJson(cookie);
        h.set("Idempotency-Key", keyPfx + "-" + UUID.randomUUID());
        ResponseEntity<Map> r = restTemplate.postForEntity(
                baseUrl + path, new HttpEntity<>(body != null ? body : Map.of(), h), Map.class);
        assertEquals(HttpStatus.OK, r.getStatusCode(), "POST " + path + " must return 200; got " + r.getStatusCode());
        return r;
    }

    /**
     * POSTs to path with an Idempotency-Key header and NO body (mirrors MockMvc
     * tests that call lifecycle endpoints without Content-Type or body).
     */
    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> postNoBody(String cookie, String path, String keyPfx) {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.COOKIE, cookie);
        h.set("Origin", CSRF_ORIGIN);
        h.set("Idempotency-Key", keyPfx + "-" + UUID.randomUUID());
        ResponseEntity<Map> r = restTemplate.postForEntity(
                baseUrl + path, new HttpEntity<>(null, h), Map.class);
        assertEquals(HttpStatus.OK, r.getStatusCode(), "POST " + path + " must return 200; got " + r.getStatusCode());
        return r;
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
    void register_newUser_returns200AndUserId() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Origin", CSRF_ORIGIN);
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> body = Map.of(
                "username", "otwuser_" + unique,
                "password", "password123",
                "email", "otwuser_" + unique + "@example.com",
                "fullName", "OTW Tester",
                "phone", "555-1234");

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl + "/api/auth/register",
                new HttpEntity<>(body, headers),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(), "Registration must return 200");
        assertNotNull(response.getBody(), "Registration response body must not be null");
        assertNotNull(response.getBody().get("userId"),
                "Registration response must include userId; got keys: " + response.getBody().keySet());
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

        restTemplate.exchange(
                baseUrl + "/api/auth/logout",
                HttpMethod.POST,
                new HttpEntity<>(withCookie(cookie)),
                Void.class);

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
    void rootPath_redirectsOrServesIndex() {
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl + "/", String.class);

        assertTrue(
                response.getStatusCode() == HttpStatus.FOUND ||
                response.getStatusCode() == HttpStatus.MOVED_PERMANENTLY ||
                response.getStatusCode() == HttpStatus.OK,
                "Root path must redirect or serve index; got: " + response.getStatusCode());
    }

    // ─── Listings ─────────────────────────────────────────────────────────────

    @Test
    void searchListings_withoutSession_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/api/listings/search?keyword=studio",
                String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
                "Search endpoint must require authentication; got: " + response.getStatusCode());
    }

    @Test
    void searchListings_withSession_returnsJsonPaginatedResult() {
        String cookie = loginCust1();

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/api/listings/search?page=1&size=10",
                HttpMethod.GET,
                new HttpEntity<>(withCookie(cookie)),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(), "Search must return 200");
        assertNotNull(response.getBody(), "Search response body must not be null");
        assertTrue(response.getBody().containsKey("items"),
                "Search response must contain 'items' key");
        assertTrue(response.getBody().containsKey("total"),
                "Search response must contain 'total' key");
        assertTrue(response.getBody().containsKey("page"),
                "Search response must contain 'page' key");
        MediaType ct = response.getHeaders().getContentType();
        assertNotNull(ct, "Content-Type header must be set");
        assertTrue(ct.isCompatibleWith(MediaType.APPLICATION_JSON),
                "Content-Type must be application/json; got: " + ct);
    }

    @Test
    void searchListings_returnsAtLeastThreeSeededResults() {
        String cookie = loginCust1();

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
        assertTrue(total.intValue() >= 3, "Total must be ≥ 3 seeded listings; got " + total);
    }

    @Test
    void listAllListings_withSession_returns200AndArray() {
        String cookie = loginAdmin();

        ResponseEntity<List> response = restTemplate.exchange(
                baseUrl + "/api/listings",
                HttpMethod.GET,
                new HttpEntity<>(withCookie(cookie)),
                List.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(), "GET /api/listings must return 200");
        assertNotNull(response.getBody(), "Listings list must not be null");
        assertFalse(response.getBody().isEmpty(), "Listings list must have seeded items");
    }

    @Test
    void getListingById_withSession_returns200AndPriceField() {
        String cookie = loginCust1();

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/api/listings/1",
                HttpMethod.GET,
                new HttpEntity<>(withCookie(cookie)),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(), "GET /api/listings/1 must return 200");
        assertNotNull(response.getBody().get("id"), "Listing must include 'id'");
        assertNotNull(response.getBody().get("price"), "Listing must include 'price'");
        assertNotNull(response.getBody().get("title"), "Listing must include 'title'");
    }

    @Test
    void searchSuggestions_withSession_returns200AndStringArray() {
        String cookie = loginCust1();

        ResponseEntity<List> response = restTemplate.exchange(
                baseUrl + "/api/listings/search/suggestions",
                HttpMethod.GET,
                new HttpEntity<>(withCookie(cookie)),
                List.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(), "Suggestions must return 200");
        assertNotNull(response.getBody(), "Suggestions response must not be null");
    }

    @Test
    void myListings_photographerSession_returns200AndOwnListings() {
        String cookie = loginPhoto1();

        ResponseEntity<List> response = restTemplate.exchange(
                baseUrl + "/api/listings/my",
                HttpMethod.GET,
                new HttpEntity<>(withCookie(cookie)),
                List.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(), "GET /api/listings/my must return 200");
        assertNotNull(response.getBody(), "My listings must not be null");
    }

    @Test
    @SuppressWarnings("unchecked")
    void createListing_photographerSession_returns200AndId() {
        String cookie = loginPhoto1();
        HttpHeaders h = withCookieAndJson(cookie);
        Map<String, Object> body = Map.of(
                "title", "OTW Session Studio " + UUID.randomUUID().toString().substring(0, 6),
                "description", "Test listing from OTW suite",
                "price", 120,
                "durationMinutes", 60,
                "category", "portrait",
                "locationCity", "Chicago",
                "locationState", "IL",
                "locationNeighborhood", "Lincoln Park",
                "maxCapacity", 2);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl + "/api/listings",
                new HttpEntity<>(body, h),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(), "POST /api/listings must return 200");
        assertNotNull(response.getBody().get("id"), "Created listing must include 'id'");
        assertNotNull(response.getBody().get("title"), "Created listing must include 'title'");
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateListing_photographerSession_returns200AndUpdatedTitle() {
        String cookie = loginPhoto1();
        HttpHeaders h = withCookieAndJson(cookie);
        String newTitle = "Updated OTW Title " + UUID.randomUUID().toString().substring(0, 6);
        Map<String, Object> body = Map.of(
                "title", newTitle,
                "description", "Updated desc",
                "price", 130,
                "durationMinutes", 60,
                "category", "portrait",
                "locationCity", "Chicago",
                "locationState", "IL",
                "locationNeighborhood", "Wicker Park",
                "maxConcurrent", 2,
                "active", true);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/api/listings/1",
                HttpMethod.PUT,
                new HttpEntity<>(body, h),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(), "PUT /api/listings/1 must return 200");
        assertEquals(newTitle, response.getBody().get("title"),
                "Updated listing must reflect new title");
    }

    @Test
    void createListing_customerSession_returns403() {
        String cookie = loginCust1();
        HttpHeaders h = withCookieAndJson(cookie);

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/api/listings",
                new HttpEntity<>(Map.of("title", "X", "price", 50, "durationMinutes", 30), h),
                String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(),
                "Customers must not be able to create listings");
    }

    // ─── TimeSlots ────────────────────────────────────────────────────────────

    @Test
    void getTimeSlotsForListing_withSession_returns200AndArray() {
        String cookie = loginCust1();

        ResponseEntity<List> response = restTemplate.exchange(
                baseUrl + "/api/timeslots/listing/1",
                HttpMethod.GET,
                new HttpEntity<>(withCookie(cookie)),
                List.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "GET /api/timeslots/listing/1 must return 200");
        assertNotNull(response.getBody(), "Time-slot list must not be null");
    }

    @Test
    void getAvailableTimeSlotsForListing_withSession_returns200() {
        String cookie = loginCust1();

        // Endpoint requires start and end date parameters; deserialise as String to
        // avoid type-mismatch if the server returns a list or a wrapped object
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/api/timeslots/listing/1/available?start=2026-01-01&end=2028-12-31",
                HttpMethod.GET,
                new HttpEntity<>(withCookie(cookie)),
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "GET /api/timeslots/listing/1/available must return 200");
        assertNotNull(response.getBody(), "Available time-slot response must not be null");
        assertFalse(response.getBody().isBlank(), "Available time-slot response must not be blank");
    }

    // ─── Orders ───────────────────────────────────────────────────────────────

    @Test
    void createOrder_missingIdempotencyKey_returns400() {
        String cookie = loginCust1();
        HttpHeaders headers = withCookieAndJson(cookie);
        // No Idempotency-Key header

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/api/orders",
                new HttpEntity<>(Map.of("listingId", 1, "timeSlotId", 6), headers),
                String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
                "POST /api/orders without Idempotency-Key must return 400");
    }

    @Test
    @SuppressWarnings("unchecked")
    void createOrder_withAllRequiredFields_returns200AndOrderNumber() {
        String photoCookie = loginPhoto1();
        String custCookie = loginCust1();

        long slotId = createSlotOtw(photoCookie, 1, "2026-09-01");

        HttpHeaders custHeaders = withCookieAndJson(custCookie);
        custHeaders.set("Idempotency-Key", "otw-create-" + UUID.randomUUID());

        ResponseEntity<Map> orderResponse = restTemplate.postForEntity(
                baseUrl + "/api/orders",
                new HttpEntity<>(Map.of("listingId", 1, "timeSlotId", slotId), custHeaders),
                Map.class);

        assertEquals(HttpStatus.OK, orderResponse.getStatusCode(),
                "POST /api/orders must return 200");
        assertNotNull(orderResponse.getBody().get("orderNumber"),
                "Response must include orderNumber");
        assertEquals("CREATED", orderResponse.getBody().get("status"),
                "New order must have CREATED status");
    }

    @Test
    @SuppressWarnings("unchecked")
    void listOrders_customerSession_returns200Array() {
        String cookie = loginCust1();

        ResponseEntity<List> response = restTemplate.exchange(
                baseUrl + "/api/orders",
                HttpMethod.GET,
                new HttpEntity<>(withCookie(cookie)),
                List.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(), "GET /api/orders must return 200");
        assertNotNull(response.getBody(), "Orders list must not be null");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getOrderById_returns200AndIdField() {
        String photoCookie = loginPhoto1();
        String custCookie = loginCust1();
        long slotId = createSlotOtw(photoCookie, 1, "2028-01-10");
        long orderId = createOrderOtw(custCookie, 1, slotId, "otw-getid");

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/api/orders/" + orderId,
                HttpMethod.GET,
                new HttpEntity<>(withCookie(custCookie)),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(), "GET /api/orders/{id} must return 200");
        assertEquals(((Number) response.getBody().get("id")).longValue(), orderId,
                "Order id must match requested id");
        assertNotNull(response.getBody().get("status"), "Order must include 'status'");
        assertNotNull(response.getBody().get("orderNumber"), "Order must include 'orderNumber'");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getOrderAudit_returns200AndAuditArray() {
        String photoCookie = loginPhoto1();
        String custCookie = loginCust1();
        long slotId = createSlotOtw(photoCookie, 1, "2028-01-11");
        long orderId = createOrderOtw(custCookie, 1, slotId, "otw-audit");

        ResponseEntity<List> response = restTemplate.exchange(
                baseUrl + "/api/orders/" + orderId + "/audit",
                HttpMethod.GET,
                new HttpEntity<>(withCookie(custCookie)),
                List.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(), "GET /api/orders/{id}/audit must return 200");
        assertNotNull(response.getBody(), "Audit log must not be null");
        assertFalse(response.getBody().isEmpty(), "Audit log must contain at least one entry (order creation)");
    }

    @Test
    @SuppressWarnings("unchecked")
    void cancelOrder_beforeConfirm_returns200AndCancelledStatus() {
        String photoCookie = loginPhoto1();
        String custCookie = loginCust1();
        long slotId = createSlotOtw(photoCookie, 1, "2028-01-12");
        long orderId = createOrderOtw(custCookie, 1, slotId, "otw-cancel");

        ResponseEntity<Map> cancelR = postWithKey(custCookie,
                "/api/orders/" + orderId + "/cancel", "otw-cancel-act", Map.of());

        assertEquals("CANCELLED", cancelR.getBody().get("status"),
                "Cancelled order must have CANCELLED status");
    }

    @Test
    @SuppressWarnings("unchecked")
    void orderFullLifecycle_confirmPayCheckinCheckoutComplete_returnsCorrectFinalStatus() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        String photoCookie = loginPhoto1();
        String custCookie = loginCust1();

        long slotId = createSlotOtw(photoCookie, 1, "2028-02-01");
        long orderId = createOrderOtw(custCookie, 1, slotId, "otw-lc-" + run);

        // confirm (no body)
        postNoBody(photoCookie, "/api/orders/" + orderId + "/confirm", "otw-lc-conf-" + run);
        // pay (requires amount + paymentReference body)
        postWithKey(custCookie, "/api/orders/" + orderId + "/pay",
                "otw-lc-pay-" + run, Map.of("amount", 150, "paymentReference", "REF-" + run));
        // check-in (no body)
        postNoBody(photoCookie, "/api/orders/" + orderId + "/check-in", "otw-lc-ci-" + run);
        // check-out (no body)
        postNoBody(photoCookie, "/api/orders/" + orderId + "/check-out", "otw-lc-co-" + run);
        // complete (no body)
        ResponseEntity<Map> completeR = postNoBody(photoCookie,
                "/api/orders/" + orderId + "/complete", "otw-lc-done-" + run);

        assertEquals("COMPLETED", completeR.getBody().get("status"),
                "Completed order must have COMPLETED status");
    }

    @Test
    @SuppressWarnings("unchecked")
    void refundOrder_adminSession_returns200AndRefundedStatus() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        String adminCookie = loginAdmin();
        String photoCookie = loginPhoto1();
        String custCookie = loginCust1();

        long slotId = createSlotOtw(photoCookie, 1, "2028-02-05");
        long orderId = createOrderOtw(custCookie, 1, slotId, "otw-refund-" + run);

        postNoBody(photoCookie, "/api/orders/" + orderId + "/confirm", "otw-rf-conf-" + run);
        postWithKey(custCookie, "/api/orders/" + orderId + "/pay",
                "otw-rf-pay-" + run, Map.of("amount", 150, "paymentReference", "R-" + run));
        postNoBody(photoCookie, "/api/orders/" + orderId + "/check-in", "otw-rf-ci-" + run);
        postNoBody(photoCookie, "/api/orders/" + orderId + "/check-out", "otw-rf-co-" + run);
        postNoBody(photoCookie, "/api/orders/" + orderId + "/complete", "otw-rf-done-" + run);

        // Admin refunds the completed order (requires amount + reason body)
        ResponseEntity<Map> refundR = postWithKey(adminCookie,
                "/api/orders/" + orderId + "/refund", "otw-rf-refund-" + run,
                Map.of("amount", 150.00, "reason", "OTW test refund"));

        assertEquals("REFUNDED", refundR.getBody().get("status"),
                "Refunded order must have REFUNDED status");
    }

    @Test
    @SuppressWarnings("unchecked")
    void rescheduleOrder_updatesToNewSlot() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        String photoCookie = loginPhoto1();
        String custCookie = loginCust1();

        long slotA = createSlotOtw(photoCookie, 1, "2028-03-01");
        long slotB = createSlotOtw(photoCookie, 1, "2028-03-02");
        long orderId = createOrderOtw(custCookie, 1, slotA, "otw-resched-" + run);

        HttpHeaders h = withCookieAndJson(custCookie);
        h.set("Idempotency-Key", "otw-resched-act-" + run + "-" + UUID.randomUUID());
        ResponseEntity<Map> reschedR = restTemplate.postForEntity(
                baseUrl + "/api/orders/" + orderId + "/reschedule",
                new HttpEntity<>(Map.of("newTimeSlotId", slotB), h),
                Map.class);

        assertEquals(HttpStatus.OK, reschedR.getStatusCode(),
                "Reschedule must return 200; got " + reschedR.getStatusCode());
        assertEquals(((Number) reschedR.getBody().get("timeSlotId")).longValue(), slotB,
                "After reschedule, order must reference the new slot");
    }

    // ─── CSRF protection ──────────────────────────────────────────────────────

    @Test
    void stateChangingRequest_withoutOriginHeader_isRejected() {
        String cookie = loginCust1();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.COOKIE, cookie);
        headers.set("Idempotency-Key", "otw-csrf-" + UUID.randomUUID());
        // No Origin header!

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/api/orders",
                new HttpEntity<>(Map.of("listingId", 1, "timeSlotId", 6), headers),
                String.class);

        assertNotEquals(HttpStatus.OK, response.getStatusCode(),
                "POST without Origin header must be rejected by CSRF guard");
    }

    // ─── Points ───────────────────────────────────────────────────────────────

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
        String cookie = loginCust1();

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

    @Test
    void pointsHistory_withSession_returns200AndArray() {
        String cookie = loginCust1();

        ResponseEntity<List> response = restTemplate.exchange(
                baseUrl + "/api/points/history",
                HttpMethod.GET,
                new HttpEntity<>(withCookie(cookie)),
                List.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "GET /api/points/history must return 200");
        assertNotNull(response.getBody(), "Points history must not be null");
    }

    @Test
    @SuppressWarnings("unchecked")
    void pointsLeaderboard_withSession_returns200AndDescendingOrder() {
        String cookie = loginCust1();

        ResponseEntity<List> response = restTemplate.exchange(
                baseUrl + "/api/points/leaderboard",
                HttpMethod.GET,
                new HttpEntity<>(withCookie(cookie)),
                List.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "GET /api/points/leaderboard must return 200");
        assertNotNull(response.getBody(), "Leaderboard must not be null");

        List<Map<String, Object>> entries = (List<Map<String, Object>>) response.getBody();
        int prev = Integer.MAX_VALUE;
        for (Map<String, Object> e : entries) {
            int pts = ((Number) e.get("points")).intValue();
            assertTrue(pts <= prev, "Leaderboard must be in descending order; found " + pts + " after " + prev);
            prev = pts;
        }
    }

    @Test
    void pointsRules_adminSession_returns200AndAtLeastTwoRules() {
        String cookie = loginAdmin();

        ResponseEntity<List> response = restTemplate.exchange(
                baseUrl + "/api/points/rules",
                HttpMethod.GET,
                new HttpEntity<>(withCookie(cookie)),
                List.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "GET /api/points/rules must return 200 for admin");
        assertTrue(response.getBody().size() >= 2,
                "Must have at least 2 seeded points rules; got " + response.getBody().size());
    }

    @Test
    void pointsRules_customerSession_returns403() {
        String cookie = loginCust1();

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/api/points/rules",
                HttpMethod.GET,
                new HttpEntity<>(withCookie(cookie)),
                String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(),
                "Points rules must be admin-only");
    }

    @Test
    @SuppressWarnings("unchecked")
    void createPointsRule_adminSession_returns200AndRule() {
        String cookie = loginAdmin();
        HttpHeaders h = withCookieAndJson(cookie);
        Map<String, Object> body = Map.of(
                "name", "OTW_TEST_RULE_" + UUID.randomUUID().toString().substring(0, 6),
                "description", "OTW test rule",
                "points", 7,
                "scope", "INDIVIDUAL",
                "triggerEvent", "OTW_TEST_EVENT");

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl + "/api/points/rules",
                new HttpEntity<>(body, h),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "POST /api/points/rules must return 200");
        assertNotNull(response.getBody().get("id"), "Created rule must include 'id'");
        assertEquals(7, ((Number) response.getBody().get("points")).intValue(),
                "Created rule must have correct points value");
    }

    @Test
    @SuppressWarnings("unchecked")
    void updatePointsRule_adminSession_returns200() {
        // First create a rule to update
        String cookie = loginAdmin();
        HttpHeaders h = withCookieAndJson(cookie);
        Map<String, Object> createBody = Map.of(
                "name", "OTW_UPDATE_RULE_" + UUID.randomUUID().toString().substring(0, 6),
                "description", "Before update",
                "points", 3,
                "scope", "INDIVIDUAL",
                "triggerEvent", "OTW_UPDATE_EVENT");
        ResponseEntity<Map> createR = restTemplate.postForEntity(
                baseUrl + "/api/points/rules",
                new HttpEntity<>(createBody, h),
                Map.class);
        assertEquals(HttpStatus.OK, createR.getStatusCode());
        long ruleId = ((Number) createR.getBody().get("id")).longValue();

        // Now update it
        Map<String, Object> updateBody = Map.of(
                "name", (String) createBody.get("name"),
                "description", "After update",
                "points", 8,
                "scope", "INDIVIDUAL",
                "triggerEvent", "OTW_UPDATE_EVENT");
        ResponseEntity<Map> updateR = restTemplate.exchange(
                baseUrl + "/api/points/rules/" + ruleId,
                HttpMethod.PUT,
                new HttpEntity<>(updateBody, withCookieAndJson(cookie)),
                Map.class);

        assertEquals(HttpStatus.OK, updateR.getStatusCode(),
                "PUT /api/points/rules/{id} must return 200");
        assertEquals(8, ((Number) updateR.getBody().get("points")).intValue(),
                "Updated rule must reflect new points value");
    }

    @Test
    @SuppressWarnings("unchecked")
    void adjustPoints_adminSession_returns200AndBalance() {
        String cookie = loginAdmin();
        HttpHeaders h = withCookieAndJson(cookie);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl + "/api/points/adjust",
                new HttpEntity<>(Map.of("userId", 4, "points", 15, "reason", "OTW bonus test"), h),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "POST /api/points/adjust must return 200");
        assertNotNull(response.getBody().get("balanceAfter"),
                "Response must include 'balanceAfter'");
        assertEquals("OTW bonus test", response.getBody().get("reason"),
                "Response must echo the adjustment reason");
    }

    @Test
    void getPointsAdjustments_adminSession_returns200AndArray() {
        String cookie = loginAdmin();

        ResponseEntity<List> response = restTemplate.exchange(
                baseUrl + "/api/points/adjustments",
                HttpMethod.GET,
                new HttpEntity<>(withCookie(cookie)),
                List.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "GET /api/points/adjustments must return 200");
        assertNotNull(response.getBody(), "Adjustments list must not be null");
    }

    @Test
    @SuppressWarnings("unchecked")
    void awardPoints_adminSession_returns200AndResponseBody() {
        String cookie = loginAdmin();
        HttpHeaders h = withCookieAndJson(cookie);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl + "/api/points/award",
                new HttpEntity<>(Map.of("userId", 4, "points", 25, "description", "OTW award test"), h),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "POST /api/points/award must return 200");
        assertNotNull(response.getBody(), "Award response body must not be null");
        // Award response includes at least userId and total points
        assertFalse(response.getBody().isEmpty(), "Award response must not be an empty object");
    }

    // ─── Users ────────────────────────────────────────────────────────────────

    @Test
    void adminEndpoint_withCustomerSession_returns403() {
        String cookie = loginCust1();

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/api/users",
                HttpMethod.GET,
                new HttpEntity<>(withCookie(cookie)),
                String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(),
                "Admin-only endpoint must return 403 for customer session");
    }

    @Test
    void adminEndpoint_withAdminSession_returns200AndNonEmptyList() {
        String cookie = loginAdmin();

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

    @Test
    void listPhotographers_withSession_returns200AndArray() {
        String cookie = loginCust1();

        ResponseEntity<List> response = restTemplate.exchange(
                baseUrl + "/api/users/photographers",
                HttpMethod.GET,
                new HttpEntity<>(withCookie(cookie)),
                List.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "GET /api/users/photographers must return 200");
        assertNotNull(response.getBody(), "Photographers list must not be null");
        assertFalse(response.getBody().isEmpty(), "Photographers list must include seeded photographers");
    }

    @Test
    void listProviders_withSession_returns200AndArray() {
        String cookie = loginCust1();

        ResponseEntity<List> response = restTemplate.exchange(
                baseUrl + "/api/users/providers",
                HttpMethod.GET,
                new HttpEntity<>(withCookie(cookie)),
                List.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "GET /api/users/providers must return 200");
        assertNotNull(response.getBody(), "Providers list must not be null");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getUserById_adminSession_returns200AndUsername() {
        String cookie = loginAdmin();

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/api/users/4",
                HttpMethod.GET,
                new HttpEntity<>(withCookie(cookie)),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "GET /api/users/4 must return 200");
        assertEquals("cust1", response.getBody().get("username"),
                "User 4 must be cust1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void patchUser_adminSession_returns200() {
        String cookie = loginAdmin();
        HttpHeaders h = withCookieAndJson(cookie);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/api/users/4",
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("fullName", "OTW Updated Name"), h),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "PATCH /api/users/4 must return 200");
        assertNotNull(response.getBody().get("message"),
                "PATCH response must include 'message'");
    }

    @Test
    @SuppressWarnings("unchecked")
    void putUser_adminSession_returns200() {
        String cookie = loginAdmin();
        HttpHeaders h = withCookieAndJson(cookie);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/api/users/4",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("fullName", "OTW PUT Name", "email", "cust1@example.com"), h),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "PUT /api/users/4 must return 200");
        assertNotNull(response.getBody().get("message"),
                "PUT response must include 'message'");
    }

    @Test
    @SuppressWarnings("unchecked")
    void toggleUserEnabled_adminSession_returns200() {
        String cookie = loginAdmin();
        HttpHeaders h = withCookieAndJson(cookie);

        // Disable cust2
        ResponseEntity<Map> disableR = restTemplate.exchange(
                baseUrl + "/api/users/5/enabled",
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("enabled", false), h),
                Map.class);
        assertEquals(HttpStatus.OK, disableR.getStatusCode(),
                "PATCH /api/users/5/enabled (disable) must return 200");

        // Re-enable cust2
        ResponseEntity<Map> enableR = restTemplate.exchange(
                baseUrl + "/api/users/5/enabled",
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("enabled", true), withCookieAndJson(cookie)),
                Map.class);
        assertEquals(HttpStatus.OK, enableR.getStatusCode(),
                "PATCH /api/users/5/enabled (enable) must return 200");
        assertNotNull(enableR.getBody().get("message"),
                "Response must include 'message'");
    }

    // ─── Actuator ─────────────────────────────────────────────────────────────

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

    // ─── Notifications ────────────────────────────────────────────────────────

    @Test
    void getNotifications_withSession_returns200AndArray() {
        String cookie = loginCust1();

        ResponseEntity<List> response = restTemplate.exchange(
                baseUrl + "/api/notifications",
                HttpMethod.GET,
                new HttpEntity<>(withCookie(cookie)),
                List.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "GET /api/notifications must return 200");
        assertNotNull(response.getBody(), "Notifications list must not be null");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getNotificationPreferences_withSession_returns200AndComplianceField() {
        String cookie = loginCust1();

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/api/notifications/preferences",
                HttpMethod.GET,
                new HttpEntity<>(withCookie(cookie)),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "GET /api/notifications/preferences must return 200");
        assertNotNull(response.getBody().get("compliance"),
                "Preferences must include 'compliance' field");
        assertNotNull(response.getBody().get("orderUpdates"),
                "Preferences must include 'orderUpdates' field");
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateNotificationPreferences_withSession_returns200AndForcedCompliance() {
        String cookie = loginCust1();
        HttpHeaders h = withCookieAndJson(cookie);
        Map<String, Object> prefs = Map.of(
                "orderUpdates", true, "holds", true, "reminders", true,
                "approvals", true, "compliance", false, "muteNonCritical", false);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/api/notifications/preferences",
                HttpMethod.PUT,
                new HttpEntity<>(prefs, h),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "PUT /api/notifications/preferences must return 200");
        // Compliance cannot be disabled — server forces it back to true
        assertTrue((Boolean) response.getBody().get("compliance"),
                "Compliance notification must be forced to true even when requested false");
    }

    @Test
    @SuppressWarnings("unchecked")
    void markNotificationReadAndArchive_returns200() {
        String photoCookie = loginPhoto1();
        String custCookie = loginCust1();

        // Unmute cust1 so notifications are delivered
        HttpHeaders prefH = withCookieAndJson(custCookie);
        restTemplate.exchange(baseUrl + "/api/notifications/preferences", HttpMethod.PUT,
                new HttpEntity<>(Map.of("orderUpdates", true, "holds", true, "reminders", true,
                        "approvals", true, "compliance", true, "muteNonCritical", false), prefH),
                Map.class);

        // Create an order to trigger a notification for cust1
        long slotId = createSlotOtw(photoCookie, 1, "2028-04-01");
        createOrderOtw(custCookie, 1, slotId, "otw-notif-read");

        // Retrieve notifications
        ResponseEntity<List> listR = restTemplate.exchange(
                baseUrl + "/api/notifications",
                HttpMethod.GET,
                new HttpEntity<>(withCookie(custCookie)),
                List.class);
        assertEquals(HttpStatus.OK, listR.getStatusCode());
        assertFalse(listR.getBody().isEmpty(), "Should have at least one notification after order creation");

        long notifId = ((Number) ((Map<?, ?>) listR.getBody().get(0)).get("id")).longValue();

        // Mark as read
        HttpHeaders h = withCookieAndJson(custCookie);
        ResponseEntity<Map> readR = restTemplate.postForEntity(
                baseUrl + "/api/notifications/" + notifId + "/read",
                new HttpEntity<>(Map.of(), h),
                Map.class);
        assertEquals(HttpStatus.OK, readR.getStatusCode(),
                "POST /api/notifications/{id}/read must return 200");

        // Archive
        ResponseEntity<Map> archiveR = restTemplate.postForEntity(
                baseUrl + "/api/notifications/" + notifId + "/archive",
                new HttpEntity<>(Map.of(), withCookieAndJson(custCookie)),
                Map.class);
        assertEquals(HttpStatus.OK, archiveR.getStatusCode(),
                "POST /api/notifications/{id}/archive must return 200");
    }

    @Test
    void exportNotifications_adminSession_returns200() {
        String cookie = loginAdmin();

        ResponseEntity<List> response = restTemplate.exchange(
                baseUrl + "/api/notifications/export",
                HttpMethod.GET,
                new HttpEntity<>(withCookie(cookie)),
                List.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "GET /api/notifications/export must return 200 for admin");
        assertNotNull(response.getBody(), "Export list must not be null");
    }

    @Test
    @SuppressWarnings("unchecked")
    void postExportNotifications_adminSession_returns200AndExportCount() {
        // Create a notification by placing an order for cust1 first
        String photoCookie = loginPhoto1();
        String custCookie = loginCust1();
        long slotId = createSlotOtw(photoCookie, 1, "2028-06-15");
        createOrderOtw(custCookie, 1, slotId, "otw-export-notif");

        String cookie = loginAdmin();
        HttpHeaders h = withCookieAndJson(cookie);

        // GET to find notifications ready for export
        ResponseEntity<List> getR = restTemplate.exchange(
                baseUrl + "/api/notifications/export",
                HttpMethod.GET,
                new HttpEntity<>(withCookie(cookie)),
                List.class);
        assertEquals(HttpStatus.OK, getR.getStatusCode(),
                "GET /api/notifications/export must return 200");

        // POST with available IDs; skip POST assertion if nothing is pending export
        List<Object> items = getR.getBody();
        if (items != null && !items.isEmpty()) {
            List<Integer> ids = items.stream()
                    .map(item -> ((Number) ((Map<?, ?>) item).get("id")).intValue())
                    .collect(java.util.stream.Collectors.toList());
            ResponseEntity<Map> postR = restTemplate.postForEntity(
                    baseUrl + "/api/notifications/export",
                    new HttpEntity<>(Map.of("ids", ids), h),
                    Map.class);
            assertEquals(HttpStatus.OK, postR.getStatusCode(),
                    "POST /api/notifications/export must return 200");
            assertNotNull(postR.getBody().get("exported"),
                    "Export response must include 'exported' count");
        }
        // If no notifications pending export, GET coverage is sufficient
    }

    // ─── SSE stream ───────────────────────────────────────────────────────────

    @Test
    void sseStream_withoutSession_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/api/messages/stream",
                String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
                "SSE stream endpoint must require authentication; got: " + response.getStatusCode());
    }

    /**
     * Authenticated SSE test using Java 11 HttpClient with BodyHandlers.ofLines().
     *
     * ofLines() returns a CompletableFuture that completes as soon as the response
     * headers arrive — before the infinite event stream body is consumed — which
     * lets us assert status + Content-Type without blocking forever.
     */
    @Test
    void sseStream_withSession_returns200AndEventStreamContentType() throws Exception {
        String cookie = loginCust1();

        java.net.http.HttpClient sseClient = java.net.http.HttpClient.newBuilder()
                .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
                .build();

        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(baseUrl + "/api/messages/stream"))
                .header("Cookie", cookie)
                .header("Origin", CSRF_ORIGIN)
                .GET()
                .build();

        java.net.http.HttpResponse<java.util.stream.Stream<String>> response =
                sseClient.sendAsync(req, java.net.http.HttpResponse.BodyHandlers.ofLines())
                        .get(5, java.util.concurrent.TimeUnit.SECONDS);

        // Close the stream so the connection is released immediately
        response.body().close();

        assertEquals(200, response.statusCode(),
                "SSE stream must return 200 for authenticated user; got: " + response.statusCode());

        String contentType = response.headers().firstValue("content-type").orElse("");
        assertTrue(contentType.contains("text/event-stream"),
                "SSE Content-Type must be text/event-stream; got: " + contentType);
    }

    // ─── Messages / Chat ──────────────────────────────────────────────────────

    @Test
    void listConversations_withSession_returns200AndArray() {
        String cookie = loginCust1();

        ResponseEntity<List> response = restTemplate.exchange(
                baseUrl + "/api/messages/conversations",
                HttpMethod.GET,
                new HttpEntity<>(withCookie(cookie)),
                List.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "GET /api/messages/conversations must return 200");
        assertNotNull(response.getBody(), "Conversations list must not be null");
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendMessage_andGetConversation_returns200AndConversationId() {
        String photoCookie = loginPhoto1();
        String custCookie = loginCust1();

        long slotId = createSlotOtw(photoCookie, 1, "2028-05-01");
        long orderId = createOrderOtw(custCookie, 1, slotId, "otw-chat");

        // Send message (cust1 → photo1)
        HttpHeaders h = withCookieAndJson(custCookie);
        ResponseEntity<Map> sendR = restTemplate.postForEntity(
                baseUrl + "/api/messages/send",
                new HttpEntity<>(Map.of("recipientId", 2, "content", "Hi from OTW test", "orderId", orderId), h),
                Map.class);

        assertEquals(HttpStatus.OK, sendR.getStatusCode(),
                "POST /api/messages/send must return 200");
        assertNotNull(sendR.getBody().get("conversationId"),
                "Send response must include 'conversationId'");
        long convId = ((Number) sendR.getBody().get("conversationId")).longValue();

        // Get conversation messages
        ResponseEntity<List> convR = restTemplate.exchange(
                baseUrl + "/api/messages/conversations/" + convId,
                HttpMethod.GET,
                new HttpEntity<>(withCookie(custCookie)),
                List.class);

        assertEquals(HttpStatus.OK, convR.getStatusCode(),
                "GET /api/messages/conversations/{id} must return 200");
        assertFalse(convR.getBody().isEmpty(), "Conversation must have at least the sent message");
    }

    @Test
    @SuppressWarnings("unchecked")
    void replyToConversation_returns200() {
        String photoCookie = loginPhoto1();
        String custCookie = loginCust1();

        long slotId = createSlotOtw(photoCookie, 1, "2028-05-02");
        long orderId = createOrderOtw(custCookie, 1, slotId, "otw-reply");

        // Send initial message to create conversation
        HttpHeaders h = withCookieAndJson(custCookie);
        ResponseEntity<Map> sendR = restTemplate.postForEntity(
                baseUrl + "/api/messages/send",
                new HttpEntity<>(Map.of("recipientId", 2, "content", "First message", "orderId", orderId), h),
                Map.class);
        assertEquals(HttpStatus.OK, sendR.getStatusCode());
        long convId = ((Number) sendR.getBody().get("conversationId")).longValue();

        // photo1 replies
        ResponseEntity<Map> replyR = restTemplate.postForEntity(
                baseUrl + "/api/messages/conversations/" + convId + "/reply",
                new HttpEntity<>(Map.of("content", "Reply from OTW test"), withCookieAndJson(photoCookie)),
                Map.class);

        assertEquals(HttpStatus.OK, replyR.getStatusCode(),
                "POST /api/messages/conversations/{id}/reply must return 200");
        assertNotNull(replyR.getBody().get("id"), "Reply response must include message 'id'");
    }

    @Test
    @SuppressWarnings("unchecked")
    void uploadImageToConversation_andDownloadAsParticipant_returns200() {
        String photoCookie = loginPhoto1();
        String custCookie = loginCust1();

        long slotId = createSlotOtw(photoCookie, 1, "2028-05-03");
        long orderId = createOrderOtw(custCookie, 1, slotId, "otw-img");

        // Send message to create conversation
        HttpHeaders h = withCookieAndJson(custCookie);
        ResponseEntity<Map> sendR = restTemplate.postForEntity(
                baseUrl + "/api/messages/send",
                new HttpEntity<>(Map.of("recipientId", 2, "content", "Image incoming", "orderId", orderId), h),
                Map.class);
        assertEquals(HttpStatus.OK, sendR.getStatusCode());
        long convId = ((Number) sendR.getBody().get("conversationId")).longValue();

        // Upload image as cust1
        HttpHeaders uploadHeaders = new HttpHeaders();
        uploadHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
        uploadHeaders.set(HttpHeaders.COOKIE, custCookie);
        uploadHeaders.set("Origin", CSRF_ORIGIN);

        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.IMAGE_JPEG);
        ByteArrayResource fileResource = new ByteArrayResource(
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}) {
            @Override public String getFilename() { return "test.jpg"; }
        };
        MultiValueMap<String, Object> formBody = new LinkedMultiValueMap<>();
        formBody.add("file", new HttpEntity<>(fileResource, fileHeaders));

        ResponseEntity<Map> imgR = restTemplate.postForEntity(
                baseUrl + "/api/messages/conversations/" + convId + "/image",
                new HttpEntity<>(formBody, uploadHeaders),
                Map.class);

        assertEquals(HttpStatus.OK, imgR.getStatusCode(),
                "POST /api/messages/conversations/{id}/image must return 200");
        assertNotNull(imgR.getBody().get("attachment"),
                "Image upload response must include 'attachment'");
        Map<String, Object> att = (Map<String, Object>) imgR.getBody().get("attachment");
        long attId = ((Number) att.get("id")).longValue();

        // cust1 (participant) can download the attachment
        ResponseEntity<byte[]> dlR = restTemplate.exchange(
                baseUrl + "/api/messages/attachments/" + attId + "/download",
                HttpMethod.GET,
                new HttpEntity<>(withCookie(custCookie)),
                byte[].class);

        assertEquals(HttpStatus.OK, dlR.getStatusCode(),
                "Participant must be able to download the attachment");
        assertNotNull(dlR.getBody(), "Attachment download must return content");
        assertTrue(dlR.getBody().length > 0, "Attachment content must not be empty");
    }

    // ─── Blacklist ────────────────────────────────────────────────────────────

    @Test
    void listBlacklist_adminSession_returns200AndArray() {
        String cookie = loginAdmin();

        ResponseEntity<List> response = restTemplate.exchange(
                baseUrl + "/api/blacklist",
                HttpMethod.GET,
                new HttpEntity<>(withCookie(cookie)),
                List.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "GET /api/blacklist must return 200 for admin");
        assertNotNull(response.getBody(), "Blacklist must not be null");
    }

    @Test
    void listBlacklist_customerSession_returns403() {
        String cookie = loginCust1();

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/api/blacklist",
                HttpMethod.GET,
                new HttpEntity<>(withCookie(cookie)),
                String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(),
                "GET /api/blacklist must be admin-only");
    }

    @Test
    @SuppressWarnings("unchecked")
    void createAndLiftBlacklistEntry_adminSession_returns200() {
        String cookie = loginAdmin();
        HttpHeaders h = withCookieAndJson(cookie);

        // Create blacklist entry for cust2 (userId=5)
        ResponseEntity<Map> createR = restTemplate.postForEntity(
                baseUrl + "/api/blacklist",
                new HttpEntity<>(Map.of("userId", 5, "reason", "OTW test block", "durationDays", 1), h),
                Map.class);
        assertEquals(HttpStatus.OK, createR.getStatusCode(),
                "POST /api/blacklist must return 200");
        assertNotNull(createR.getBody().get("id"),
                "Blacklist entry must include 'id'");
        long blId = ((Number) createR.getBody().get("id")).longValue();

        // Re-enable the user (blacklisting disables them)
        restTemplate.exchange(
                baseUrl + "/api/users/5/enabled",
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("enabled", true), withCookieAndJson(cookie)),
                Map.class);

        // Get blacklist entry by ID
        ResponseEntity<Map> getR = restTemplate.exchange(
                baseUrl + "/api/blacklist/" + blId,
                HttpMethod.GET,
                new HttpEntity<>(withCookie(cookie)),
                Map.class);
        assertEquals(HttpStatus.OK, getR.getStatusCode(),
                "GET /api/blacklist/{id} must return 200");
        assertTrue((Boolean) getR.getBody().get("active"),
                "New blacklist entry must be active");

        // Get blacklist entries for user (endpoint returns an object, not a raw array)
        ResponseEntity<String> userBlR = restTemplate.exchange(
                baseUrl + "/api/blacklist/user/5",
                HttpMethod.GET,
                new HttpEntity<>(withCookie(cookie)),
                String.class);
        assertEquals(HttpStatus.OK, userBlR.getStatusCode(),
                "GET /api/blacklist/user/{userId} must return 200");
        assertNotNull(userBlR.getBody(), "Blacklist-by-user response must not be null");

        // Lift the entry
        ResponseEntity<Map> liftR = restTemplate.postForEntity(
                baseUrl + "/api/blacklist/" + blId + "/lift",
                new HttpEntity<>(Map.of("reason", "OTW test reinstated"), withCookieAndJson(cookie)),
                Map.class);
        assertEquals(HttpStatus.OK, liftR.getStatusCode(),
                "POST /api/blacklist/{id}/lift must return 200");
        assertFalse((Boolean) liftR.getBody().get("active"),
                "Lifted blacklist entry must have active=false");
    }

    // ─── Addresses ────────────────────────────────────────────────────────────

    @Test
    void listAddresses_withSession_returns200AndArray() {
        String cookie = loginCust1();

        ResponseEntity<List> response = restTemplate.exchange(
                baseUrl + "/api/addresses",
                HttpMethod.GET,
                new HttpEntity<>(withCookie(cookie)),
                List.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "GET /api/addresses must return 200");
        assertNotNull(response.getBody(), "Address list must not be null");
        assertFalse(response.getBody().isEmpty(), "cust1 should have seeded addresses");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAddressById_withSession_returns200AndLabelField() {
        // Create a fresh address and retrieve it by the dynamically assigned ID
        // so the test does not depend on any specific seeded row.
        String cookie = loginCust1();
        HttpHeaders h = withCookieAndJson(cookie);
        Map<String, Object> createBody = Map.of(
                "label", "OTW GetById",
                "street", "42 Dynamic Dr",
                "city", "Chicago",
                "state", "IL",
                "postalCode", "60601",
                "country", "US",
                "isDefault", false);

        ResponseEntity<Map> createResp = restTemplate.postForEntity(
                baseUrl + "/api/addresses",
                new HttpEntity<>(createBody, h),
                Map.class);
        assertEquals(HttpStatus.OK, createResp.getStatusCode(), "Address creation must succeed");
        long addrId = ((Number) createResp.getBody().get("id")).longValue();

        // Now GET by the dynamic ID
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/api/addresses/" + addrId,
                HttpMethod.GET,
                new HttpEntity<>(withCookie(cookie)),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "GET /api/addresses/{id} must return 200 for the owning user");
        assertEquals("OTW GetById", response.getBody().get("label"),
                "Returned address must have the label we set");
        assertNotNull(response.getBody().get("street"), "Address response must include 'street'");
    }

    @Test
    @SuppressWarnings("unchecked")
    void createAddress_withSession_returns200AndId() {
        String cookie = loginCust1();
        HttpHeaders h = withCookieAndJson(cookie);
        Map<String, Object> body = Map.of(
                "label", "OTW Studio",
                "street", "999 OTW Ave",
                "city", "Los Angeles",
                "state", "CA",
                "postalCode", "90210",
                "country", "US",
                "isDefault", false);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl + "/api/addresses",
                new HttpEntity<>(body, h),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "POST /api/addresses must return 200");
        assertNotNull(response.getBody().get("id"), "Created address must include 'id'");
        assertEquals("OTW Studio", response.getBody().get("label"),
                "Created address must have the given label");
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateAndDeleteAddress_withSession_returns200() {
        String cookie = loginCust1();
        HttpHeaders h = withCookieAndJson(cookie);

        // Create an address to update and delete
        Map<String, Object> createBody = Map.of(
                "label", "OTW Temp",
                "street", "1 Temp St",
                "city", "Chicago",
                "state", "IL",
                "postalCode", "60601",
                "country", "US",
                "isDefault", false);
        ResponseEntity<Map> createR = restTemplate.postForEntity(
                baseUrl + "/api/addresses",
                new HttpEntity<>(createBody, h),
                Map.class);
        assertEquals(HttpStatus.OK, createR.getStatusCode());
        long addrId = ((Number) createR.getBody().get("id")).longValue();

        // Update it
        Map<String, Object> updateBody = Map.of(
                "label", "OTW Updated",
                "street", "2 Updated St",
                "city", "Chicago",
                "state", "IL",
                "postalCode", "60601",
                "country", "US",
                "isDefault", false);
        ResponseEntity<Map> updateR = restTemplate.exchange(
                baseUrl + "/api/addresses/" + addrId,
                HttpMethod.PUT,
                new HttpEntity<>(updateBody, withCookieAndJson(cookie)),
                Map.class);
        assertEquals(HttpStatus.OK, updateR.getStatusCode(),
                "PUT /api/addresses/{id} must return 200");
        assertEquals("OTW Updated", updateR.getBody().get("label"),
                "Updated address must reflect new label");

        // Delete it
        ResponseEntity<Map> deleteR = restTemplate.exchange(
                baseUrl + "/api/addresses/" + addrId,
                HttpMethod.DELETE,
                new HttpEntity<>(withCookieAndJson(cookie)),
                Map.class);
        assertEquals(HttpStatus.OK, deleteR.getStatusCode(),
                "DELETE /api/addresses/{id} must return 200");
    }
}
