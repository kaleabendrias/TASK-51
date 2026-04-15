package com.booking.api.e2e;

import org.htmlunit.BrowserVersion;
import org.htmlunit.NicelyResynchronizingAjaxController;
import org.htmlunit.WebClient;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Base class for browser-level end-to-end journey tests using HtmlUnit.
 *
 * Uses a real embedded HTTP server (RANDOM_PORT) so that the full Spring Boot
 * application including static assets (index.html, app.js, jQuery) is served
 * over HTTP. HtmlUnit executes the jQuery SPA JavaScript, making real HTTP
 * calls against the backend — the same paths that production browsers use.
 *
 * A dedicated H2 in-memory database ("e2etestdb") is used so that E2E tests
 * are completely isolated from the shared "testdb" used by MockMvc API tests.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.datasource.url=jdbc:h2:mem:e2etestdb;" +
                "MODE=MySQL;DB_CLOSE_DELAY=-1;" +
                "DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE"
        }
)
@ActiveProfiles("test")
abstract class BaseE2eIT {

    /** JS wait budget in milliseconds for AJAX + DOM manipulation to settle. */
    private static final int JS_WAIT_MS = 6_000;

    @LocalServerPort
    protected int port;

    protected WebClient webClient;

    @BeforeEach
    void initBrowser() {
        webClient = new WebClient(BrowserVersion.CHROME);
        webClient.getOptions().setJavaScriptEnabled(true);
        webClient.getOptions().setCssEnabled(false);
        webClient.getOptions().setThrowExceptionOnScriptError(false);
        webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
        webClient.getOptions().setTimeout(15_000);
        webClient.setAjaxController(new NicelyResynchronizingAjaxController());
    }

    @AfterEach
    void closeBrowser() {
        webClient.close();
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    protected String url(String path) {
        return "http://localhost:" + port + path;
    }

    /**
     * Loads the root URL and waits for the initial auth-check AJAX call to
     * complete (login page becomes visible for unauthenticated clients).
     */
    protected HtmlPage loadRootAndWait() throws Exception {
        HtmlPage page = webClient.getPage(url("/"));
        webClient.waitForBackgroundJavaScript(JS_WAIT_MS);
        return page;
    }

    /**
     * Logs in via the HTML form on the given page and waits for the SPA to
     * transition to the main-app view. Returns the same page (SPA in-place).
     */
    protected HtmlPage loginViaForm(HtmlPage page, String username, String password) throws Exception {
        // Wait for login form to be active
        webClient.waitForBackgroundJavaScript(JS_WAIT_MS);

        HtmlInput userInput = (HtmlInput) page.getElementById("login-username");
        HtmlInput passInput = (HtmlInput) page.getElementById("login-password");
        assertNotNull(userInput, "Username field must be present on the login page");
        assertNotNull(passInput, "Password field must be present on the login page");

        userInput.type(username);
        passInput.type(password);

        // Submit the login form via the button click
        DomElement submitBtn = page.querySelector("#login-form button[type='submit']");
        assertNotNull(submitBtn, "Login submit button must be present");
        submitBtn.click();
        webClient.waitForBackgroundJavaScript(JS_WAIT_MS);

        return page;
    }

    /** Convenience: load root, wait for login page, login, wait for app. */
    protected HtmlPage loginAs(String username, String password) throws Exception {
        HtmlPage page = loadRootAndWait();
        return loginViaForm(page, username, password);
    }

    /** Returns true if the named element exists and does NOT have 'hidden' class. */
    protected boolean isVisible(HtmlPage page, String elementId) {
        DomElement el = page.getElementById(elementId);
        if (el == null) return false;
        return !el.getAttribute("class").contains("hidden");
    }

    /** Returns the text content of an element, or empty string if not found. */
    protected String textOf(HtmlPage page, String elementId) {
        DomElement el = page.getElementById(elementId);
        return el == null ? "" : el.getTextContent().trim();
    }
}
