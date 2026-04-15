package com.booking.api.e2e;

import org.htmlunit.html.DomElement;
import org.htmlunit.html.DomNode;
import org.htmlunit.html.DomNodeList;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Browser-level end-to-end tests for the search/filter page and navigation journeys.
 *
 * Verifies that after login the correct default page is displayed for each role,
 * that the search page renders seeded listings, and that navigation links
 * transition the SPA to the expected page content.
 */
class SearchAndNavigationE2eIT extends BaseE2eIT {

    // ─── Default page after login ──────────────────────────────────────────────

    @Test
    void customerDefaultPageIsSearch() throws Exception {
        HtmlPage page = loginAs("cust1", "password123");

        // After customer login the SPA navigates to the search page
        assertTrue(isVisible(page, "app-main"), "App must be visible after login");

        // The active nav link must be 'search'
        DomElement activeLink = page.querySelector("#main-nav a.active");
        assertNotNull(activeLink, "There must be an active nav link after login");
        assertEquals("search", activeLink.getAttribute("data-page"),
                "Customer default active page must be 'search'");

        // The search input must be present and ready
        assertNotNull(page.getElementById("search-input"),
                "search-input must be rendered on the default customer page");
    }

    @Test
    void photographerDefaultPageIsPhotoDashboard() throws Exception {
        HtmlPage page = loginAs("photo1", "password123");

        assertTrue(isVisible(page, "app-main"), "App must be visible after login");

        DomElement activeLink = page.querySelector("#main-nav a.active");
        assertNotNull(activeLink, "Active nav link must exist after photographer login");
        assertEquals("photo-dashboard", activeLink.getAttribute("data-page"),
                "Photographer default page must be 'photo-dashboard'");

        // Page content should mention Dashboard
        String content = page.getElementById("page-content").getTextContent();
        assertTrue(content.contains("Dashboard") || content.contains("Orders") || content.contains("Listings"),
                "Photo dashboard content should include dashboard summary, got: " + content.substring(0, Math.min(200, content.length())));
    }

    @Test
    void adminDefaultPageIsAdminDashboard() throws Exception {
        HtmlPage page = loginAs("admin", "password123");

        assertTrue(isVisible(page, "app-main"), "App must be visible after admin login");

        DomElement activeLink = page.querySelector("#main-nav a.active");
        assertNotNull(activeLink, "Active nav link must exist after admin login");
        assertEquals("admin-dashboard", activeLink.getAttribute("data-page"),
                "Admin default page must be 'admin-dashboard'");
    }

    // ─── Search page renders seeded listings ───────────────────────────────────

    @Test
    void searchPageShowsSeededListings() throws Exception {
        HtmlPage page = loginAs("cust1", "password123");

        assertTrue(isVisible(page, "app-main"), "App must be visible");

        // Wait additional time for search AJAX to complete
        webClient.waitForBackgroundJavaScript(6_000);

        // The listing grid must contain cards from the seeded data
        DomNodeList<DomNode> cards = page.querySelectorAll(".listing-card");
        assertFalse(cards.isEmpty(),
                "At least one listing card must appear on the search page after login");
    }

    @Test
    void searchPageShowsSeededListingTitles() throws Exception {
        HtmlPage page = loginAs("cust1", "password123");
        webClient.waitForBackgroundJavaScript(6_000);

        String gridText = page.getElementById("listing-results") != null
                ? page.getElementById("listing-results").getTextContent()
                : page.getElementById("page-content").getTextContent();

        // Seeded listings are: "Studio Portrait", "Outdoor Family", "Product Shots"
        assertTrue(
                gridText.contains("Studio Portrait") ||
                gridText.contains("Outdoor Family") ||
                gridText.contains("Product Shots"),
                "At least one seeded listing title should appear in search results, got: "
                        + gridText.substring(0, Math.min(500, gridText.length())));
    }

    @Test
    void searchInputIsInteractable() throws Exception {
        HtmlPage page = loginAs("cust1", "password123");
        webClient.waitForBackgroundJavaScript(3_000);

        DomElement searchInput = page.getElementById("search-input");
        assertNotNull(searchInput, "Search input must exist after customer login");
        assertEquals("text", searchInput.getAttribute("type"),
                "Search input must be type=text");

        // Search button must also be present
        DomElement searchBtn = page.getElementById("search-btn");
        assertNotNull(searchBtn, "Search button (#search-btn) must exist");
    }

    // ─── Navigation transitions ────────────────────────────────────────────────

    @Test
    void customerNavLinksNavigateCorrectly() throws Exception {
        HtmlPage page = loginAs("cust1", "password123");

        // Click the Orders nav link
        DomElement ordersLink = page.querySelector("#main-nav a[data-page='orders']");
        assertNotNull(ordersLink, "Orders nav link must exist for customer");
        ordersLink.click();
        webClient.waitForBackgroundJavaScript(4_000);

        DomElement activeLink = page.querySelector("#main-nav a.active");
        assertNotNull(activeLink, "An active nav link must exist after clicking Orders");
        assertEquals("orders", activeLink.getAttribute("data-page"),
                "Active page must be 'orders' after clicking the Orders nav link");

        // Page content must change to orders content
        String content = page.getElementById("page-content").getTextContent();
        // The orders page renders a table or a "no orders" message
        assertFalse(content.isBlank(), "Page content must not be blank after navigating to orders");
    }

    @Test
    void customerCanNavigateToAddressesPage() throws Exception {
        HtmlPage page = loginAs("cust1", "password123");

        DomElement addressesLink = page.querySelector("#main-nav a[data-page='addresses']");
        assertNotNull(addressesLink, "Addresses nav link must exist for customer");
        addressesLink.click();
        webClient.waitForBackgroundJavaScript(4_000);

        DomElement active = page.querySelector("#main-nav a.active");
        assertNotNull(active);
        assertEquals("addresses", active.getAttribute("data-page"),
                "Active page must be 'addresses' after clicking Addresses link");
    }

    @Test
    void photographerNavLinksIncludeMyListings() throws Exception {
        HtmlPage page = loginAs("photo1", "password123");

        DomElement myListingsLink = page.querySelector("#main-nav a[data-page='my-listings']");
        assertNotNull(myListingsLink, "My-listings nav link must exist for photographer");
        myListingsLink.click();
        webClient.waitForBackgroundJavaScript(4_000);

        DomElement active = page.querySelector("#main-nav a.active");
        assertNotNull(active);
        assertEquals("my-listings", active.getAttribute("data-page"),
                "Active page must be 'my-listings' after clicking Listings link");
    }

    @Test
    void adminNavIncludesUsersAndBlacklist() throws Exception {
        HtmlPage page = loginAs("admin", "password123");

        String navHtml = page.getElementById("main-nav").asXml();
        assertTrue(navHtml.contains("users-admin"),
                "Admin nav must include Users Admin link");
        assertTrue(navHtml.contains("blacklist-admin"),
                "Admin nav must include Blacklist Admin link");
    }

    @Test
    void adminCanNavigateToUsersAdminPage() throws Exception {
        HtmlPage page = loginAs("admin", "password123");

        DomElement usersLink = page.querySelector("#main-nav a[data-page='users-admin']");
        assertNotNull(usersLink, "Users-admin nav link must exist for admin");
        usersLink.click();
        webClient.waitForBackgroundJavaScript(4_000);

        DomElement active = page.querySelector("#main-nav a.active");
        assertNotNull(active);
        assertEquals("users-admin", active.getAttribute("data-page"),
                "Active page must be 'users-admin' after clicking Users link");

        // Users page should list seeded users
        String content = page.getElementById("page-content").getTextContent();
        assertTrue(content.contains("admin") || content.contains("photo") || content.contains("cust"),
                "Users admin page should display user data, got: "
                        + content.substring(0, Math.min(300, content.length())));
    }

    // ─── Filter panel ──────────────────────────────────────────────────────────

    @Test
    void toggleFiltersRevealsCategorySelect() throws Exception {
        HtmlPage page = loginAs("cust1", "password123");
        webClient.waitForBackgroundJavaScript(3_000);

        // The filter panel is collapsed by default; the toggle button opens it
        DomElement toggleBtn = page.getElementById("toggle-filters");
        assertNotNull(toggleBtn, "Toggle-filters button must exist on search page");

        toggleBtn.click();
        webClient.waitForBackgroundJavaScript(1_000);

        // After toggling, the category select should be present
        DomElement categorySelect = page.getElementById("f-category");
        assertNotNull(categorySelect, "Category select (#f-category) must exist in the filter panel");
    }
}
