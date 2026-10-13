// Exported from 10-unsupported.sel by GATF (gatf export selenium-java). Review every TODO(gatf-export) line.
// 6 of 28 statement(s) exported, 22 TODO, 3 note(s): see the export report.
// Needs selenium-java and junit-jupiter. SELENIUM_REMOTE_URL runs it on a Selenium Grid; -Dgatf.export.browser.binary and
// -Dgatf.export.browser.args (comma-separated, e.g. --headless=new) adjust the local browser.
import java.net.URI;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementNotInteractableException;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;

class Sel10UnsupportedTest {

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
    @DisplayName("10-unsupported")
    void test() throws Exception {
        // Δ161 export corpus (selenium-java, from Δ160): constructs with no mapping become TODO(gatf-export) lines; whole blocks stay commented.
        // gatf-export: 'open chrome' - the driver is created by newDriver() before the test
        driver.get("https://shop.test/");
        // TODO(gatf-export): #j if(1==1) {} else {}
        // TODO(gatf-export): ## class@"row"
        // TODO(gatf-export): {
        // TODO(gatf-export): click xpath@"//a[@index]"
        // TODO(gatf-export): }
        // TODO(gatf-export): #counter 0 5
        // TODO(gatf-export): {
        // TODO(gatf-export): exec @print(@index)
        // TODO(gatf-export): }
        // TODO(gatf-export): <<<(java) var0
        // TODO(gatf-export): System.out.println("}");
        // TODO(gatf-export): >>>
        // TODO(gatf-export): curl post http://abc.com
        // TODO(gatf-export): {
        // TODO(gatf-export): [
        // TODO(gatf-export): Content-Type: application/xml
        // TODO(gatf-export): ]
        // TODO(gatf-export): <abc>abc</abc>
        // TODO(gatf-export): }
        // TODO(gatf-export): ? eval "a"=="a"
        // TODO(gatf-export): {
        // TODO(gatf-export): click id@a
        // TODO(gatf-export): }
        // TODO(gatf-export): :
        // TODO(gatf-export): {
        // TODO(gatf-export): click id@b
        // TODO(gatf-export): }
        if (!all(by("id@x", By.id("x"))).isEmpty()) {
            actionable(by("id@c", By.id("c")), timeout, "click").click();
        } else if (all(by("id@y", By.id("y"))).isEmpty()) {
            actionable(by("id@d", By.id("d")), timeout, "click").click();
        }
        // TODO(gatf-export): frame "my-frame"
        // TODO(gatf-export): include lib/common.sel
        // TODO(gatf-export): randomize id@'ele1' alphanumeric 12
        // TODO(gatf-export): alert 'Hello'
        // TODO(gatf-export): ^mobileOnly click id@burger
        // TODO(gatf-export): click jq@'.menu:visible'
        // TODO(gatf-export): click relative tag@input rightof label@Email
        // TODO(gatf-export): open firefox
        // TODO(gatf-export): @call "nowhere"
        // TODO(gatf-export): a11y check
        // TODO(gatf-export): ?? text@'/*x/'
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

    /** Every element the find expression matches now (no wait). */
    private List<WebElement> all(Loc l) {
        return l.finder.find();
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

    /**
     * find(), then until the element is displayed (not for upload / submit) and enabled (not for hover / drag):
     * an approximation of seleasy's actionability probe.
     */
    private WebElement actionable(Loc l, Duration t, String mode) {
        long end = System.nanoTime() + t.toNanos();
        boolean visible = !mode.equals("upload") && !mode.equals("submit"), enabled = !mode.equals("hover") && !mode.equals("drag");
        WebElement e = find(l, t);
        while (true) {
            try {
                if ((!visible || e.isDisplayed()) && (!enabled || e.isEnabled())) return e;
            } catch (StaleElementReferenceException x) {
                e = find(l, Duration.ofNanos(Math.max(0L, end - System.nanoTime())));   // replaced while waiting: find it again
                continue;
            }
            if (System.nanoTime() > end) throw new ElementNotInteractableException(l.what + " is not " + (visible ? "displayed and " : "") + "enabled for " + mode + " (waited " + t.getSeconds() + "s)");
            pause(100L);
        }
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
