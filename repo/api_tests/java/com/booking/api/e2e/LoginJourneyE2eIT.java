package com.booking.api.e2e;

import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Browser-level end-to-end tests for the login / logout / register user journeys.
 *
 * Each test loads the real index.html via HTTP, executes app.js through HtmlUnit's
 * JavaScript engine (Rhino), and asserts on DOM state after AJAX calls complete —
 * the same interactions a real browser user would perform.
 */
class LoginJourneyE2eIT extends BaseE2eIT {

    // ─── Login page rendering ──────────────────────────────────────────────────

    @Test
    void loginPageRendersOnFirstLoad() throws Exception {
        HtmlPage page = loadRootAndWait();

        // After the auth-check fails (no session), the login form should be visible
        assertTrue(isVisible(page, "login-page"),
                "Login page must be visible for unauthenticated user");

        assertNotNull(page.getElementById("login-username"), "Username field must exist");
        assertNotNull(page.getElementById("login-password"), "Password field must exist");
        assertNotNull(page.getElementById("login-form"),     "Login form must exist");

        // Main app must be hidden
        DomElement appMain = page.getElementById("app-main");
        assertNotNull(appMain);
        assertTrue(appMain.getAttribute("class").contains("hidden"),
                "Main app should be hidden before login");
    }

    @Test
    void loginFormHasCorrectInputTypes() throws Exception {
        HtmlPage page = loadRootAndWait();

        DomElement userField = page.getElementById("login-username");
        DomElement passField = page.getElementById("login-password");
        assertNotNull(userField);
        assertNotNull(passField);
        assertEquals("text",     userField.getAttribute("type"));
        assertEquals("password", passField.getAttribute("type"));
    }

    // ─── Successful login ──────────────────────────────────────────────────────

    @Test
    void loginWithValidCredentialsShowsMainApp() throws Exception {
        HtmlPage page = loginAs("cust1", "password123");

        assertTrue(isVisible(page, "app-main"),
                "Main app must be visible after successful login");
        assertFalse(isVisible(page, "login-page"),
                "Login page must be hidden after login");

        // user-display should show the full name of cust1 from data-test.sql
        String userDisplay = textOf(page, "user-display");
        assertFalse(userDisplay.isBlank(), "User display name should not be empty after login");
        assertTrue(userDisplay.contains("Customer"), // "Customer One" from seed
                "User display should contain the user's full name, got: " + userDisplay);
    }

    @Test
    void adminLoginShowsAdminDashboardByDefault() throws Exception {
        HtmlPage page = loginAs("admin", "password123");

        assertTrue(isVisible(page, "app-main"), "App main must be visible");

        // After login, admin's default page is 'admin-dashboard'
        // The active nav link should be 'admin-dashboard'
        DomElement activeLink = page.querySelector("#main-nav a.active");
        if (activeLink != null) {
            assertEquals("admin-dashboard", activeLink.getAttribute("data-page"));
        }
        // Or the page-content contains Dashboard heading
        String pageContent = page.getElementById("page-content").getTextContent();
        assertTrue(pageContent.contains("Dashboard") || pageContent.contains("Orders"),
                "Admin default page should be the dashboard");
    }

    @Test
    void photographerLoginShowsPhotographerNav() throws Exception {
        HtmlPage page = loginAs("photo1", "password123");

        assertTrue(isVisible(page, "app-main"), "App main must be visible");

        String navHtml = page.getElementById("main-nav").asXml();
        assertTrue(navHtml.contains("photo-dashboard"),
                "Photographer nav should include Dashboard link");
        assertTrue(navHtml.contains("my-listings"),
                "Photographer nav should include My Listings link");
    }

    @Test
    void customerLoginShowsCustomerNav() throws Exception {
        HtmlPage page = loginAs("cust1", "password123");

        assertTrue(isVisible(page, "app-main"), "App main must be visible");

        String navHtml = page.getElementById("main-nav").asXml();
        assertTrue(navHtml.contains("search"),    "Customer nav must have Browse/Search");
        assertTrue(navHtml.contains("orders"),    "Customer nav must have Orders");
        assertTrue(navHtml.contains("chat"),      "Customer nav must have Chat");
        assertTrue(navHtml.contains("addresses"), "Customer nav must have Addresses");
        // Admin links must NOT appear for regular customer
        assertFalse(navHtml.contains("users-admin"),     "Customer should not see admin user mgmt");
        assertFalse(navHtml.contains("blacklist-admin"), "Customer should not see blacklist admin");
    }

    // ─── Failed login ──────────────────────────────────────────────────────────

    @Test
    void loginWithWrongPasswordShowsErrorMessage() throws Exception {
        HtmlPage page = loadRootAndWait();
        loginViaForm(page, "admin", "wrongpassword");

        // Login error div should be visible with an error message
        DomElement errorDiv = page.getElementById("login-error");
        assertNotNull(errorDiv, "Login error div must exist");
        assertFalse(errorDiv.getAttribute("class").contains("hidden"),
                "Error message div should be visible after failed login");
        assertFalse(errorDiv.getTextContent().trim().isEmpty(),
                "Error message must not be empty after failed login");
    }

    @Test
    void loginWithNonexistentUserShowsError() throws Exception {
        HtmlPage page = loadRootAndWait();
        loginViaForm(page, "nosuchuser", "password123");

        DomElement errorDiv = page.getElementById("login-error");
        assertNotNull(errorDiv);
        assertFalse(errorDiv.getAttribute("class").contains("hidden"),
                "Error div should be visible for non-existent user");
        assertFalse(errorDiv.getTextContent().trim().isEmpty());
    }

    @Test
    void loginPageRemainsActiveAfterFailedLogin() throws Exception {
        HtmlPage page = loadRootAndWait();
        loginViaForm(page, "admin", "badpass");

        // Login page must still be visible
        assertTrue(isVisible(page, "login-page"),
                "Login page must remain visible after failed login");
        // Main app must still be hidden
        DomElement appMain = page.getElementById("app-main");
        assertNotNull(appMain);
        assertTrue(appMain.getAttribute("class").contains("hidden"),
                "Main app must not appear after failed login");
    }

    // ─── Logout ───────────────────────────────────────────────────────────────

    @Test
    void logoutReturnsToLoginPage() throws Exception {
        HtmlPage page = loginAs("cust1", "password123");
        assertTrue(isVisible(page, "app-main"), "Should be logged in");

        // Click the Logout button (rendered with text "Logout")
        DomElement logoutBtn = page.querySelector("button.btn-outline");
        if (logoutBtn != null) {
            logoutBtn.click();
            webClient.waitForBackgroundJavaScript(5_000);
        }

        // After logout, login page should be shown again
        assertTrue(isVisible(page, "login-page"),
                "Login page must be visible after logout");
        assertFalse(isVisible(page, "app-main"),
                "Main app must be hidden after logout");
    }

    // ─── Register section ─────────────────────────────────────────────────────

    @Test
    void registerLinkShowsRegisterForm() throws Exception {
        HtmlPage page = loadRootAndWait();

        DomElement showRegister = page.getElementById("show-register");
        assertNotNull(showRegister, "Show-register link must exist");
        showRegister.click();
        webClient.waitForBackgroundJavaScript(1_000);

        // Register section should be visible, login section hidden
        DomElement registerSection = page.getElementById("register-section");
        DomElement loginSection    = page.getElementById("login-section");
        assertNotNull(registerSection);
        assertNotNull(loginSection);
        assertFalse(registerSection.getAttribute("class").contains("hidden"),
                "Register section should be visible after clicking show-register");
        assertTrue(loginSection.getAttribute("class").contains("hidden"),
                "Login section should be hidden after switching to register");
    }

    @Test
    void allSeedUsersCanAuthenticateViaUI() throws Exception {
        // Verify every seeded test account can log in through the browser UI
        String[] users = {"admin", "photo1", "photo2", "cust1", "cust2"};
        for (String u : users) {
            // Each iteration opens a fresh WebClient context (fresh cookies)
            webClient.getCookieManager().clearCookies();
            HtmlPage page = loginAs(u, "password123");
            assertTrue(isVisible(page, "app-main"),
                    "User '" + u + "' should be able to log in via the browser UI");
        }
    }
}
