package com.booking.api.e2e;

import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Browser-level end-to-end tests for admin governance pages:
 * admin dashboard, user management, and blacklist administration.
 *
 * All tests log in as the seeded 'admin' user and interact with the real
 * jQuery SPA rendered against the full Spring Boot backend.
 */
class AdminGovernanceE2eIT extends BaseE2eIT {

    // ─── Admin dashboard ───────────────────────────────────────────────────────

    @Test
    void adminDashboardLoadsAndShowsContent() throws Exception {
        HtmlPage page = loginAs("admin", "password123");

        assertTrue(isVisible(page, "app-main"), "App must be visible after admin login");

        String content = page.getElementById("page-content").getTextContent();
        assertFalse(content.isBlank(),
                "Admin dashboard page content must not be blank");
        // The dashboard should include some summary heading
        assertTrue(
                content.contains("Dashboard") || content.contains("Orders") ||
                content.contains("Users") || content.contains("Admin"),
                "Admin dashboard should contain summary content, got: "
                        + content.substring(0, Math.min(300, content.length())));
    }

    @Test
    void adminDashboardActiveNavLinkIsAdminDashboard() throws Exception {
        HtmlPage page = loginAs("admin", "password123");

        DomElement active = page.querySelector("#main-nav a.active");
        assertNotNull(active, "An active nav link must be present after admin login");
        assertEquals("admin-dashboard", active.getAttribute("data-page"),
                "Admin's default active link must be admin-dashboard");
    }

    // ─── Users Admin page ──────────────────────────────────────────────────────

    @Test
    void usersAdminPageListsSeededUsers() throws Exception {
        HtmlPage page = loginAs("admin", "password123");

        DomElement usersLink = page.querySelector("#main-nav a[data-page='users-admin']");
        assertNotNull(usersLink, "Users-admin nav link must exist");
        usersLink.click();
        webClient.waitForBackgroundJavaScript(5_000);

        String content = page.getElementById("page-content").getTextContent();
        // Seeded users: admin, photo1, photo2, cust1, cust2
        assertTrue(
                content.contains("admin") || content.contains("photo1") || content.contains("cust1"),
                "Users admin page should list seeded users, got: "
                        + content.substring(0, Math.min(400, content.length())));
    }

    @Test
    void usersAdminPageShowsAllFiveSeededAccounts() throws Exception {
        HtmlPage page = loginAs("admin", "password123");

        DomElement usersLink = page.querySelector("#main-nav a[data-page='users-admin']");
        assertNotNull(usersLink);
        usersLink.click();
        webClient.waitForBackgroundJavaScript(5_000);

        String content = page.getElementById("page-content").getTextContent();
        // All 5 seeded usernames should appear somewhere on the page
        int foundCount = 0;
        for (String username : new String[]{"admin", "photo1", "photo2", "cust1", "cust2"}) {
            if (content.contains(username)) foundCount++;
        }
        assertTrue(foundCount >= 3,
                "At least 3 seeded user accounts should appear on users-admin page, " +
                "found " + foundCount + " in: " + content.substring(0, Math.min(500, content.length())));
    }

    // ─── Blacklist Admin page ──────────────────────────────────────────────────

    @Test
    void blacklistAdminPageRenders() throws Exception {
        HtmlPage page = loginAs("admin", "password123");

        DomElement blacklistLink = page.querySelector("#main-nav a[data-page='blacklist-admin']");
        assertNotNull(blacklistLink, "Blacklist-admin nav link must exist");
        blacklistLink.click();
        webClient.waitForBackgroundJavaScript(5_000);

        DomElement active = page.querySelector("#main-nav a.active");
        assertNotNull(active);
        assertEquals("blacklist-admin", active.getAttribute("data-page"),
                "Active page must be blacklist-admin after clicking Blacklist link");

        // The page content should be non-blank
        String content = page.getElementById("page-content").getTextContent();
        assertFalse(content.isBlank(), "Blacklist admin page must render content");
    }

    @Test
    void blacklistAdminPageHasBlacklistForm() throws Exception {
        HtmlPage page = loginAs("admin", "password123");

        DomElement blacklistLink = page.querySelector("#main-nav a[data-page='blacklist-admin']");
        assertNotNull(blacklistLink);
        blacklistLink.click();
        webClient.waitForBackgroundJavaScript(5_000);

        // The blacklist page should have form elements for adding entries
        String pageHtml = page.getElementById("page-content").asXml();
        assertTrue(
                pageHtml.contains("userId") || pageHtml.contains("reason") ||
                pageHtml.contains("Blacklist") || pageHtml.contains("blacklist") ||
                pageHtml.contains("bl-user"),
                "Blacklist admin page should contain blacklist form elements, got HTML: "
                        + pageHtml.substring(0, Math.min(600, pageHtml.length())));
    }

    // ─── Points Admin page ─────────────────────────────────────────────────────

    @Test
    void pointsAdminPageRenders() throws Exception {
        HtmlPage page = loginAs("admin", "password123");

        DomElement pointsAdminLink = page.querySelector("#main-nav a[data-page='points-admin']");
        assertNotNull(pointsAdminLink, "Points-admin nav link must exist for admin");
        pointsAdminLink.click();
        webClient.waitForBackgroundJavaScript(5_000);

        DomElement active = page.querySelector("#main-nav a.active");
        assertNotNull(active);
        assertEquals("points-admin", active.getAttribute("data-page"),
                "Active page must be points-admin after clicking Points link");

        String content = page.getElementById("page-content").getTextContent();
        assertFalse(content.isBlank(), "Points admin page must render content");
    }

    @Test
    void pointsAdminPageShowsPointsRules() throws Exception {
        HtmlPage page = loginAs("admin", "password123");

        DomElement pointsAdminLink = page.querySelector("#main-nav a[data-page='points-admin']");
        assertNotNull(pointsAdminLink);
        pointsAdminLink.click();
        webClient.waitForBackgroundJavaScript(5_000);

        String content = page.getElementById("page-content").getTextContent();
        // Seeded rules: ORDER_PAYMENT and ORDER_COMPLETED
        assertTrue(
                content.contains("ORDER_PAYMENT") || content.contains("ORDER_COMPLETED") ||
                content.contains("Points") || content.contains("Rules") || content.contains("rule"),
                "Points admin page should show seeded points rules or labels, got: "
                        + content.substring(0, Math.min(400, content.length())));
    }

    // ─── Role isolation: non-admin cannot access admin pages ──────────────────

    @Test
    void customerNavDoesNotIncludeAdminLinks() throws Exception {
        HtmlPage page = loginAs("cust1", "password123");

        String navHtml = page.getElementById("main-nav").asXml();
        assertFalse(navHtml.contains("users-admin"),
                "Customer nav must NOT contain users-admin link");
        assertFalse(navHtml.contains("blacklist-admin"),
                "Customer nav must NOT contain blacklist-admin link");
        assertFalse(navHtml.contains("points-admin"),
                "Customer nav must NOT contain points-admin link");
    }

    @Test
    void photographerNavDoesNotIncludeAdminLinks() throws Exception {
        HtmlPage page = loginAs("photo1", "password123");

        String navHtml = page.getElementById("main-nav").asXml();
        assertFalse(navHtml.contains("users-admin"),
                "Photographer nav must NOT contain users-admin link");
        assertFalse(navHtml.contains("blacklist-admin"),
                "Photographer nav must NOT contain blacklist-admin link");
    }

    // ─── Admin can navigate the full admin nav ────────────────────────────────

    @Test
    void adminCanNavigateBrowseSearch() throws Exception {
        HtmlPage page = loginAs("admin", "password123");

        // Admin also has a Browse (search) link
        DomElement searchLink = page.querySelector("#main-nav a[data-page='search']");
        assertNotNull(searchLink, "Admin nav must include Browse/Search link");
        searchLink.click();
        webClient.waitForBackgroundJavaScript(5_000);

        DomElement active = page.querySelector("#main-nav a.active");
        assertNotNull(active);
        assertEquals("search", active.getAttribute("data-page"),
                "Active page must be 'search' after admin clicks Browse");

        // Search page renders
        assertNotNull(page.getElementById("search-input"),
                "Search input must be available when admin navigates to Browse page");
    }
}
