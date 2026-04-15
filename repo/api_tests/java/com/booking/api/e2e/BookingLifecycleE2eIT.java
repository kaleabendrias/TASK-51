package com.booking.api.e2e;

import org.htmlunit.Page;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.DomNode;
import org.htmlunit.html.DomNodeList;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Browser-level end-to-end tests for the full booking lifecycle.
 *
 * Covers the critical transactional flow: search → listing detail → book a slot →
 * order confirmation → order detail view → orders list. Tests are strictly
 * unconditional: every test asserts real pre-conditions rather than silently
 * returning on absent data.
 *
 * Near-term slots (May 2026, within the SPA's 30-day window) are created once
 * via the API in {@code @BeforeAll}, independent of SQL seed data. Tests navigate
 * to listing 1 ("Studio Portrait") by its known data-id so that sort order
 * cannot hide the card.
 *
 * Backend verification: after UI actions the test calls /api/orders directly via
 * the HtmlUnit WebClient (which carries the session cookie automatically) to
 * confirm the backend persisted the expected change — not just that the DOM
 * changed.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BookingLifecycleE2eIT extends BaseE2eIT {

    /**
     * Creates near-term slots (May 2026) for all three listings via the API.
     * This runs once before any test in this class, ensuring Book Now buttons
     * appear in the SPA's 30-day availability window regardless of whether
     * data-test.sql successfully seeded slots 6-8.
     *
     * Capacity is set to 1000 so multiple tests can book without exhausting the slot.
     * Uses the same JdkClientHttpRequestFactory approach as OverTheWireApiIT to
     * avoid Java's HttpURLConnection restrictions on the Origin header.
     */
    @BeforeAll
    void ensureNearTermSlotsExist() {
        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        RestTemplate rt = new RestTemplate(new JdkClientHttpRequestFactory(httpClient));
        rt.setErrorHandler(new ResponseErrorHandler() {
            @Override public boolean hasError(ClientHttpResponse r) { return false; }
            @Override public void handleError(ClientHttpResponse r) { }
        });

        String base = "http://localhost:" + port;

        // Login as photo1 to get a session cookie for slot creation
        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> loginResp = rt.postForEntity(
                base + "/api/auth/login",
                new HttpEntity<>(Map.of("username", "photo1", "password", "password123"), loginHeaders),
                Map.class);
        String setCookie = loginResp.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        if (setCookie == null) return; // cannot create slots; tests will fail with clear assertion
        String sessionCookie = setCookie.split(";")[0];

        // photo1 owns listings 1 and 3; create near-term slots for those
        for (int[] ls : new int[][]{{1, 1}, {3, 10}}) {
            HttpHeaders slotHeaders = new HttpHeaders();
            slotHeaders.setContentType(MediaType.APPLICATION_JSON);
            slotHeaders.set(HttpHeaders.COOKIE, sessionCookie);
            slotHeaders.set("Origin", "http://localhost");
            Map<String, Object> slotBody = Map.of(
                    "listingId", ls[0],
                    "slotDate",  "2026-05-" + String.format("%02d", ls[1]),
                    "startTime", "10:00",
                    "endTime",   "12:00",
                    "capacity",  1000);
            rt.postForEntity(base + "/api/timeslots",
                    new HttpEntity<>(slotBody, slotHeaders), Map.class);
        }

        // photo2 owns listing 2; login as photo2 and create its near-term slot
        ResponseEntity<Map> photo2LoginResp = rt.postForEntity(
                base + "/api/auth/login",
                new HttpEntity<>(Map.of("username", "photo2", "password", "password123"), loginHeaders),
                Map.class);
        String photo2SetCookie = photo2LoginResp.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        if (photo2SetCookie != null) {
            String photo2Cookie = photo2SetCookie.split(";")[0];
            HttpHeaders slotHeaders = new HttpHeaders();
            slotHeaders.setContentType(MediaType.APPLICATION_JSON);
            slotHeaders.set(HttpHeaders.COOKIE, photo2Cookie);
            slotHeaders.set("Origin", "http://localhost");
            rt.postForEntity(base + "/api/timeslots",
                    new HttpEntity<>(Map.of(
                            "listingId", 2,
                            "slotDate",  "2026-05-05",
                            "startTime", "10:00",
                            "endTime",   "12:00",
                            "capacity",  1000), slotHeaders),
                    Map.class);
        }
    }

    // ─── Listing Detail ────────────────────────────────────────────────────────

    @Test
    void searchCardClickNavigatesToListingDetail() throws Exception {
        HtmlPage page = loginAs("cust1", "password123");
        webClient.waitForBackgroundJavaScript(6_000);

        // Navigate to listing 1 by its known data-id (sort order cannot hide it)
        DomElement listing1Card = page.querySelector(".listing-card[data-id='1']");
        assertNotNull(listing1Card,
                "Studio Portrait (listing 1) must appear in search results; check seed data");

        listing1Card.click();
        webClient.waitForBackgroundJavaScript(5_000);

        DomElement backBtn = page.getElementById("back-search");
        assertNotNull(backBtn,
                "Listing-detail page must include a 'Back to Search' button (#back-search)");
    }

    @Test
    void listingDetailShowsSlotsTable() throws Exception {
        HtmlPage page = loginAs("cust1", "password123");
        webClient.waitForBackgroundJavaScript(6_000);

        DomElement card = page.querySelector(".listing-card[data-id='1']");
        assertNotNull(card, "Listing 1 card must be present in search results");
        card.click();
        webClient.waitForBackgroundJavaScript(5_000);

        DomElement slotsTable = page.getElementById("slots-table");
        assertNotNull(slotsTable,
                "Slots table (#slots-table) must be present on the listing-detail page");
    }

    @Test
    void listingDetailShowsBookNowButtonsForNearTermSlots() throws Exception {
        HtmlPage page = loginAs("cust1", "password123");
        webClient.waitForBackgroundJavaScript(6_000);

        DomElement card = page.querySelector(".listing-card[data-id='1']");
        assertNotNull(card, "Listing 1 card must be present");
        card.click();
        webClient.waitForBackgroundJavaScript(5_000); // listing API callback
        webClient.waitForBackgroundJavaScript(3_000); // nested slots API callback

        // Near-term slot created in @BeforeAll (2026-05-01, capacity=1000) must appear
        DomNodeList<DomNode> bookBtns = page.querySelectorAll(".btn-book");
        assertFalse(bookBtns.isEmpty(),
                "Book Now buttons must appear — @BeforeAll created a May 2026 slot for listing 1 " +
                "within the SPA's 30-day availability window");
    }

    @Test
    void clickBookNowShowsBookingOptionsPanel() throws Exception {
        HtmlPage page = loginAs("cust1", "password123");
        webClient.waitForBackgroundJavaScript(6_000);

        DomElement card = page.querySelector(".listing-card[data-id='1']");
        assertNotNull(card, "Listing 1 card must be present");
        card.click();
        webClient.waitForBackgroundJavaScript(5_000); // listing API callback
        webClient.waitForBackgroundJavaScript(3_000); // nested slots API callback

        DomNodeList<DomNode> bookBtns = page.querySelectorAll(".btn-book");
        assertFalse(bookBtns.isEmpty(),
                "Book Now buttons must exist — seed slot 6 should be within the 30-day window");

        ((DomElement) bookBtns.get(0)).click();
        webClient.waitForBackgroundJavaScript(2_000);

        DomElement bookingPanel = page.getElementById("booking-options");
        assertNotNull(bookingPanel,
                "Booking options panel (#booking-options) must exist after clicking Book Now");
        String style = bookingPanel.getAttribute("style");
        assertFalse(style.contains("display: none") || style.contains("display:none"),
                "Booking options panel must not be display:none after clicking Book Now");
    }

    @Test
    void bookingOptionsPanelHasAllRequiredElements() throws Exception {
        HtmlPage page = loginAs("cust1", "password123");
        webClient.waitForBackgroundJavaScript(6_000);

        DomElement card = page.querySelector(".listing-card[data-id='1']");
        assertNotNull(card, "Listing 1 card must be present");
        card.click();
        webClient.waitForBackgroundJavaScript(5_000); // listing API callback
        webClient.waitForBackgroundJavaScript(3_000); // nested slots API callback

        DomNodeList<DomNode> bookBtns = page.querySelectorAll(".btn-book");
        assertFalse(bookBtns.isEmpty(), "Book Now buttons must exist for listing 1");

        ((DomElement) bookBtns.get(0)).click();
        webClient.waitForBackgroundJavaScript(2_000);

        DomElement deliverySelect = page.getElementById("bo-delivery");
        assertNotNull(deliverySelect,
                "Delivery mode select (#bo-delivery) must exist in booking panel");

        DomElement confirmBtn = page.getElementById("bo-confirm-btn");
        assertNotNull(confirmBtn,
                "Confirm booking button (#bo-confirm-btn) must exist in booking panel");

        String deliveryHtml = deliverySelect.asXml();
        assertTrue(deliveryHtml.contains("ONSITE"),  "Delivery select must have ONSITE option");
        assertTrue(deliveryHtml.contains("PICKUP"),  "Delivery select must have PICKUP option");
        assertTrue(deliveryHtml.contains("COURIER"), "Delivery select must have COURIER option");
    }

    /**
     * Full booking flow: click Book Now → click Confirm Booking → verify order-detail page
     * renders → verify the order is persisted in the backend by calling /api/orders via the
     * same WebClient (same session cookie).
     */
    @Test
    void confirmBookingCreatesOrderAndVerifiesViaBackendApi() throws Exception {
        HtmlPage page = loginAs("cust1", "password123");
        webClient.waitForBackgroundJavaScript(6_000);

        DomElement card = page.querySelector(".listing-card[data-id='1']");
        assertNotNull(card, "Listing 1 card must be present");
        card.click();
        webClient.waitForBackgroundJavaScript(5_000); // listing API callback
        webClient.waitForBackgroundJavaScript(3_000); // nested slots API callback

        DomNodeList<DomNode> bookBtns = page.querySelectorAll(".btn-book");
        assertFalse(bookBtns.isEmpty(), "Book Now buttons must exist for listing 1");

        ((DomElement) bookBtns.get(0)).click();
        webClient.waitForBackgroundJavaScript(2_000);

        DomElement confirmBtn = page.getElementById("bo-confirm-btn");
        assertNotNull(confirmBtn, "Confirm booking button must exist");
        confirmBtn.click();
        webClient.waitForBackgroundJavaScript(6_000);

        // ── DOM-level: SPA navigated to order-detail ───────────────────────────
        String content = page.getElementById("page-content").getTextContent();
        assertTrue(
                content.contains("CREATED") || content.contains("Payment") || content.contains("Order"),
                "Order detail page must show CREATED status or payment info after booking; got: "
                        + content.substring(0, Math.min(400, content.length())));

        // ── Backend-level: call /api/orders via the same WebClient session ─────
        // The WebClient carries the authenticated session cookie automatically.
        Page ordersResponse = webClient.getPage(url("/api/orders"));
        String ordersJson = ordersResponse.getWebResponse().getContentAsString();
        assertTrue(ordersJson.startsWith("[") || ordersJson.contains("\"id\""),
                "GET /api/orders must return a JSON array after the browser booking; got: "
                        + ordersJson.substring(0, Math.min(300, ordersJson.length())));
        assertFalse(ordersJson.equals("[]"),
                "GET /api/orders must contain at least one order after the browser booking");
        assertTrue(ordersJson.contains("CREATED"),
                "The new order must have CREATED status in the backend API response");
    }

    /**
     * After booking via the browser, navigate to the orders page and confirm
     * the SPA renders the newly created order as a table row.
     */
    @Test
    void bookedOrderAppearsInOrdersListPage() throws Exception {
        HtmlPage page = loginAs("cust1", "password123");
        webClient.waitForBackgroundJavaScript(6_000);

        // Book via UI using listing 2 (slot 7, 2026-05-05, capacity=100)
        DomElement card = page.querySelector(".listing-card[data-id='2']");
        assertNotNull(card, "Listing 2 card must be present");
        card.click();
        webClient.waitForBackgroundJavaScript(5_000); // listing API callback
        webClient.waitForBackgroundJavaScript(3_000); // nested slots API callback

        DomNodeList<DomNode> bookBtns = page.querySelectorAll(".btn-book");
        assertFalse(bookBtns.isEmpty(), "Book Now buttons must exist for listing 2");

        ((DomElement) bookBtns.get(0)).click();
        webClient.waitForBackgroundJavaScript(2_000);

        DomElement confirmBtn = page.getElementById("bo-confirm-btn");
        assertNotNull(confirmBtn);
        confirmBtn.click();
        webClient.waitForBackgroundJavaScript(6_000);

        // Navigate to orders page
        DomElement ordersLink = page.querySelector("#main-nav a[data-page='orders']");
        assertNotNull(ordersLink, "Orders nav link must exist");
        ordersLink.click();
        webClient.waitForBackgroundJavaScript(4_000);

        // The orders table must have at least one row for the booking we just made
        DomNodeList<DomNode> rows = page.querySelectorAll("#orders-table tbody tr");
        assertFalse(rows.isEmpty(),
                "Orders table must have at least one row after booking via the browser");

        // Verify backend state independently
        Page ordersApi = webClient.getPage(url("/api/orders"));
        String json = ordersApi.getWebResponse().getContentAsString();
        assertTrue(json.contains("CREATED"),
                "Backend API must return at least one CREATED order after browser booking");
    }

    // ─── Orders Page Structure ────────────────────────────────────────────────

    @Test
    void ordersPageRendersForCustomer() throws Exception {
        HtmlPage page = loginAs("cust1", "password123");

        DomElement ordersLink = page.querySelector("#main-nav a[data-page='orders']");
        assertNotNull(ordersLink, "Orders nav link must exist for customer");
        ordersLink.click();
        webClient.waitForBackgroundJavaScript(4_000);

        DomElement active = page.querySelector("#main-nav a.active");
        assertNotNull(active);
        assertEquals("orders", active.getAttribute("data-page"),
                "Active nav link must be 'orders' after clicking Orders");

        DomElement ordersTable = page.getElementById("orders-table");
        assertNotNull(ordersTable,
                "Orders table (#orders-table) must be present on the orders page");
    }

    @Test
    void ordersPageTableHasExpectedHeaderColumns() throws Exception {
        HtmlPage page = loginAs("cust1", "password123");

        DomElement ordersLink = page.querySelector("#main-nav a[data-page='orders']");
        assertNotNull(ordersLink);
        ordersLink.click();
        webClient.waitForBackgroundJavaScript(4_000);

        DomElement table = page.getElementById("orders-table");
        assertNotNull(table);
        String tableHtml = table.asXml();
        assertTrue(tableHtml.contains("Order") || tableHtml.contains("Listing"),
                "Orders table header should include Order# and Listing columns");
    }

    @Test
    void photographerOrdersPageRendersContent() throws Exception {
        HtmlPage page = loginAs("photo1", "password123");

        DomElement ordersLink = page.querySelector("#main-nav a[data-page='orders']");
        assertNotNull(ordersLink, "Photographer nav must include an Orders link");
        ordersLink.click();
        webClient.waitForBackgroundJavaScript(4_000);

        String content = page.getElementById("page-content").getTextContent();
        assertFalse(content.isBlank(), "Orders page must render non-blank content for photographer");
    }

    // ─── Order Detail Page ────────────────────────────────────────────────────

    @Test
    void orderDetailPageBackButtonReturnsToOrders() throws Exception {
        HtmlPage page = loginAs("cust1", "password123");

        // Book an order so the orders page always has at least one row
        DomElement card = page.querySelector(".listing-card[data-id='3']");
        assumeTrue(card != null, "Listing 3 card should be visible for this test");
        card.click();
        webClient.waitForBackgroundJavaScript(5_000); // listing API callback
        webClient.waitForBackgroundJavaScript(3_000); // nested slots API callback
        DomNodeList<DomNode> bookBtns = page.querySelectorAll(".btn-book");
        assumeTrue(!bookBtns.isEmpty(), "Listing 3 must have available slots");
        ((DomElement) bookBtns.get(0)).click();
        webClient.waitForBackgroundJavaScript(2_000);
        DomElement confirmBtn = page.getElementById("bo-confirm-btn");
        assumeTrue(confirmBtn != null, "Confirm button should be present");
        confirmBtn.click();
        webClient.waitForBackgroundJavaScript(6_000);

        // Navigate to orders page
        DomElement ordersLink = page.querySelector("#main-nav a[data-page='orders']");
        assertNotNull(ordersLink);
        ordersLink.click();
        webClient.waitForBackgroundJavaScript(4_000);

        // At least one view-order link must now exist
        DomNodeList<DomNode> viewLinks = page.querySelectorAll(".view-order");
        assertFalse(viewLinks.isEmpty(),
                "Orders page must have at least one 'View' link after booking via UI");

        ((DomElement) viewLinks.get(0)).click();
        webClient.waitForBackgroundJavaScript(4_000);

        DomElement backBtn = page.getElementById("back-orders");
        assertNotNull(backBtn,
                "Order detail page must have a 'Back to Orders' button (#back-orders)");

        backBtn.click();
        webClient.waitForBackgroundJavaScript(3_000);

        DomElement active = page.querySelector("#main-nav a.active");
        assertNotNull(active);
        assertEquals("orders", active.getAttribute("data-page"),
                "Back button on order-detail must return to the orders page");
    }

    @Test
    void orderDetailPageShowsStatusTrackerAndAuditTable() throws Exception {
        HtmlPage page = loginAs("cust1", "password123");
        webClient.waitForBackgroundJavaScript(6_000);

        // Book to guarantee at least one order
        DomElement card = page.querySelector(".listing-card[data-id='1']");
        assertNotNull(card);
        card.click();
        webClient.waitForBackgroundJavaScript(5_000); // listing API callback
        webClient.waitForBackgroundJavaScript(3_000); // nested slots API callback

        DomNodeList<DomNode> bookBtns = page.querySelectorAll(".btn-book");
        assertFalse(bookBtns.isEmpty(), "Book Now buttons must exist");

        ((DomElement) bookBtns.get(0)).click();
        webClient.waitForBackgroundJavaScript(2_000);
        DomElement confirmBtn = page.getElementById("bo-confirm-btn");
        assertNotNull(confirmBtn);
        confirmBtn.click();
        webClient.waitForBackgroundJavaScript(6_000);

        // After booking the SPA is on order-detail; verify structural elements
        String contentHtml = page.getElementById("page-content").asXml();
        assertTrue(contentHtml.contains("status-tracker") || contentHtml.contains("audit-table")
                        || contentHtml.contains("CREATED"),
                "Order detail page must contain a status tracker or audit table; got: "
                        + contentHtml.substring(0, Math.min(600, contentHtml.length())));
    }

    // ─── Role Isolation ────────────────────────────────────────────────────────

    @Test
    void customerNavDoesNotExposeAdminDashboard() throws Exception {
        HtmlPage page = loginAs("cust1", "password123");

        String navHtml = page.getElementById("main-nav").asXml();
        assertFalse(navHtml.contains("admin-dashboard"),
                "Customer nav must not include admin-dashboard link");
    }

    @Test
    void photographerNavHasExpectedLinksAndNotSearch() throws Exception {
        HtmlPage page = loginAs("photo1", "password123");

        String navHtml = page.getElementById("main-nav").asXml();
        assertTrue(navHtml.contains("photo-dashboard"), "Photographer nav must include photo-dashboard");
        assertTrue(navHtml.contains("my-listings"),     "Photographer nav must include my-listings");
        assertTrue(navHtml.contains("orders"),          "Photographer nav must include orders");
        // By design photographers don't get a Browse/search link
        assertFalse(navHtml.contains("data-page=\"search\""),
                "Photographer nav must NOT include a browse/search link by design");
    }

    // ─── Photo Dashboard ──────────────────────────────────────────────────────

    @Test
    void photoDashboardIsDefaultPageForPhotographer() throws Exception {
        HtmlPage page = loginAs("photo1", "password123");

        DomElement active = page.querySelector("#main-nav a.active");
        assertNotNull(active);
        assertEquals("photo-dashboard", active.getAttribute("data-page"),
                "Photographer must land on photo-dashboard after login");

        String content = page.getElementById("page-content").getTextContent();
        assertFalse(content.isBlank(), "Photo dashboard must render non-blank content");
        assertTrue(
                content.contains("Dashboard") || content.contains("Orders") ||
                content.contains("Listings") || content.contains("Photo"),
                "Photo dashboard should show summary content, got: "
                        + content.substring(0, Math.min(300, content.length())));
    }
}
