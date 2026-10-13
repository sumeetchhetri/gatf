// Exported from 13-network.sel by GATF (gatf export selenium-java). Review every TODO(gatf-export) line.
// 5 of 27 statement(s) exported, 22 TODO, 2 note(s): see the export report.
// Needs selenium-java and junit-jupiter. SELENIUM_REMOTE_URL runs it on a Selenium Grid; -Dgatf.export.browser.binary and
// -Dgatf.export.browser.args (comma-separated, e.g. --headless=new) adjust the local browser.
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;

class Sel13NetworkTest {

    private WebDriver driver;
    /** The element timeout: seleasy's default, then each 'timeout' statement. */
    private Duration timeout = Duration.ofSeconds(60);

    @BeforeEach
    void openBrowser() throws Exception {
        driver = newDriver();
    }

    @AfterEach
    void closeBrowser() {
        try {
            if (driver != null) driver.quit();
        } finally {
            driver = null;
        }
    }

    @Test
    @DisplayName("13-network")
    void test() throws Exception {
        // Δ161 export corpus (selenium-java, from Δ160): routes, HAR replay, auth state, emulation, clock and screenshots.
        // gatf-export: 'open chrome' - the driver is created by newDriver() before the test
        // TODO(gatf-export): route '**/api/users' fulfill 200 users.json
        // TODO(gatf-export): route **/api/raw fulfill 204
        // TODO(gatf-export): route POST '**/api/orders' fulfill 201 '{"id": 7}' type application/json header 'X-Mock: yes' header 'X-Mock: again'
        // TODO(gatf-export): route '**/*.{png,jpg}' abort blockedbyclient
        // TODO(gatf-export): route '**/api/**' continue header 'X-Env: test'
        // TODO(gatf-export): route from har checkout.har url '**/api/**' notfound abort
        driver.get("https://shop.test/");
        // TODO(gatf-export): unroute '**/api/users'
        // TODO(gatf-export): unroute all
        // TODO(gatf-export): state save .auth/admin.json
        // TODO(gatf-export): state load .auth/admin.json
        driver.manage().window().setSize(new Dimension(1024, 768));
        // TODO(gatf-export): emulate geolocation 51.5072 -0.1276
        // TODO(gatf-export): emulate permissions geolocation,notifications
        // TODO(gatf-export): emulate colorscheme dark
        // TODO(gatf-export): emulate offline
        // TODO(gatf-export): emulate online
        // TODO(gatf-export): emulate locale de-DE
        // TODO(gatf-export): clock install 2026-01-01T09:00:00Z
        // TODO(gatf-export): clock fastforward 1h30m
        // TODO(gatf-export): clock set 2026-12-31T23:59:50Z
        // TODO(gatf-export): clock pause
        // TODO(gatf-export): clock resume
        screenshot((TakesScreenshot) driver, "target/gatf-export/13-network-line26.png");
        screenshot(find(by("id@'cart'", By.id("cart")), timeout), "cart.png");
        // TODO(gatf-export): screenshot compare "checkout step 2" 0.1 maxdiff 0.5% mask id@clock mask css@'.ad-banner'
    }

    // ---- gatf-export runtime: Selenium + JUnit 5 only ------------------------------------------------------------

    /** The script's browser (chrome): local, or on a Selenium Grid when SELENIUM_REMOTE_URL is set. */
    private static WebDriver newDriver() throws Exception {
        ChromeOptions options = new ChromeOptions();
        String binary = System.getProperty("gatf.export.browser.binary", "");
        if (!binary.isEmpty()) options.setBinary(binary);
        for (String a : System.getProperty("gatf.export.browser.args", "").split(",")) if (!a.trim().isEmpty()) options.addArguments(a.trim());
        String grid = System.getenv("SELENIUM_REMOTE_URL");
        if (grid != null && !grid.isEmpty()) return new RemoteWebDriver(URI.create(grid).toURL(), options);
        return new ChromeDriver(options);
    }

    /** Every element a find expression matches now. */
    @FunctionalInterface
    private interface Finder {
        List<WebElement> find();
    }

    /** A find expression: its seleasy text, how to find it, and whether it must match one element only. */
    private static final class Loc {
        final String what;
        final Finder finder;
        final boolean strict;

        Loc(String what, Finder finder, boolean strict) {
            this.what = what;
            this.finder = finder;
            this.strict = strict;
        }
    }

    private Loc by(String what, By by) {
        return new Loc(what, () -> driver.findElements(by), false);
    }

    /** The element seleasy acts on: the first match, waited for up to t (a strict locator must match one element). */
    private WebElement find(Loc l, Duration t) {
        long end = System.nanoTime() + t.toNanos();
        while (true) {
            try {
                List<WebElement> els = l.finder.find();
                if (els.size() > 1 && l.strict) throw new AssertionError("Strict locator " + l.what + " matched " + els.size() + " elements");
                if (!els.isEmpty()) return els.get(0);
            } catch (StaleElementReferenceException e) {
                // the page changed while searching: search again
            }
            if (System.nanoTime() > end) throw new NoSuchElementException("Element not found by selector " + l.what + " (waited " + t.getSeconds() + "s)");
            pause(100L);
        }
    }

    /** A page or element screenshot written to path (relative to the working directory). */
    private static void screenshot(TakesScreenshot source, String path) throws Exception {
        Path p = Paths.get(path).toAbsolutePath();
        Files.createDirectories(p.getParent());
        Files.write(p, source.getScreenshotAs(OutputType.BYTES));
    }

    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        }
    }
}
