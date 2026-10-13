// Exported from e2e-04-selenium.sel by GATF (gatf export selenium-java). Review every TODO(gatf-export) line.
// 27 of 27 statement(s) exported, 0 TODO, 5 note(s): see the export report.
// Environment variables (names only): ${baseUrl} -> GATF_BASEURL
// Needs selenium-java and junit-jupiter. SELENIUM_REMOTE_URL runs it on a Selenium Grid; -Dgatf.export.browser.binary and
// -Dgatf.export.browser.args (comma-separated, e.g. --headless=new) adjust the local browser.
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.ElementNotInteractableException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.Select;

class E2e04SeleniumTest {

    private WebDriver driver;
    /** The element timeout: seleasy's default, then each 'timeout' statement. */
    private Duration timeout = Duration.ofSeconds(60);
    private final Map<String, String> vars = new HashMap<>();

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
    @DisplayName("e2e-04-selenium")
    void test() throws Exception {
        // Δ161 export e2e (selenium-java only): Selenium-specific mappings against the fixture page; run by ExportSeleniumCompileTest.
        // gatf-export: 'open chrome' - the driver is created by newDriver() before the test
        driver.get(env("GATF_BASEURL") + "/index.html");
        driver.manage().window().setSize(new Dimension(900, 700));
        check("?? width ='900'", timeout, () -> String.valueOf(driver.manage().window().getSize().getWidth()), a -> "900".equals(a));
        type(actionable(by("id@q", By.id("q")), timeout, "type"), Keys.chord("ab\ue003c"), "");
        check("?? id@q attr@value ='ac'", timeout, () -> now(by("id@q", By.id("q"))).getAttribute("value"), a -> "ac".equals(a));
        type(actionable(by("id@email", By.id("email")), timeout, "type"), "x", "");
        actionable(by("id@email", By.id("email")), timeout, "clear").clear();
        type(actionable(by("id@email", By.id("email")), timeout, "type"), "z@y.x", "");
        check("?? id@email attr@value ='z@y.x'", timeout, () -> now(by("id@email", By.id("email"))).getAttribute("value"), a -> "z@y.x".equals(a));
        selectLast(new Select(actionable(by("id@size", By.id("size")), timeout, "select")));
        check("?? id@size attr@value ='l'", timeout, () -> now(by("id@size", By.id("size"))).getAttribute("value"), a -> "l".equals(a));
        vars.put("count", find(by("id@count", By.id("count")), timeout).getText());
        check("?? id@count text ='$v{count}'", timeout, () -> now(by("id@count", By.id("count"))).getText(), a -> v("count").equals(a));
        vars.put("title", String.valueOf(js("return document.title")));
        check("?? title ='$v{title}'", timeout, () -> driver.getTitle(), a -> v("title").equals(a));
        js("document.getElementById(\"greeting\").textContent = arguments.length + \" args\"");
        check("?? id@greeting text ='0 args'", timeout, () -> now(by("id@greeting", By.id("greeting"))).getText(), a -> "0 args".equals(a));
        check("?? text@'Order placed' tagname ='p'", timeout, () -> now(by("text@'Order placed'", By.xpath("//*[contains(text(), 'Order placed')]"))).getTagName(), a -> "p".equals(a));
        check("?? id@subscribe selected ='false'", timeout, () -> String.valueOf(now(by("id@subscribe", By.id("subscribe"))).isSelected()), a -> "false".equals(a));
        actionable(by("id@subscribe", By.id("subscribe")), timeout, "click").click();
        check("?? id@subscribe selected ='true'", timeout, () -> String.valueOf(now(by("id@subscribe", By.id("subscribe"))).isSelected()), a -> "true".equals(a));
        actionable(by("linktext@'Next page'", By.linkText("Next page")), timeout, "click").click();
        check("?? css@h1 text ='Second page'", timeout, () -> now(by("css@h1", By.cssSelector("h1"))).getText(), a -> "Second page".equals(a));
        driver.navigate().back();
        actionable(by("partiallinktext@Next", By.partialLinkText("Next")), timeout, "click").click();
        check("?? title ='Page two'", timeout, () -> driver.getTitle(), a -> "Page two".equals(a));
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

    /** A ${...} value: read from the environment, never from a GATF config. */
    private static String env(String name) {
        String value = System.getenv(name);
        if (value == null) throw new IllegalStateException("gatf-export: set the environment variable " + name);
        return value;
    }

    /** A $v{...} script variable. */
    private String v(String name) {
        String value = vars.get(name);
        if (value == null) throw new IllegalStateException("gatf-export: the script variable " + name + " is not set");
        return value;
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

    /** The first match right now (assertions re-read it on every check). */
    private WebElement now(Loc l) {
        return find(l, Duration.ZERO);
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

    /** Reads a value for an assertion. */
    @FunctionalInterface
    private interface Read {
        String get();
    }

    /** seleasy's comparison, expected.op(actual). */
    @FunctionalInterface
    private interface Expect {
        boolean test(String actual);
    }

    /** Re-reads the value until the comparison passes or t runs out (web-first). */
    private void check(String statement, Duration t, Read read, Expect expect) {
        long end = System.nanoTime() + t.toNanos();
        String last = null;
        RuntimeException error = null;
        while (true) {
            try {
                last = read.get();
                error = null;
                if (last != null && expect.test(last)) return;
            } catch (WebDriverException | IllegalStateException e) {
                error = e;
            }
            if (System.nanoTime() > end) break;
            pause(100L);
        }
        throw new AssertionError(statement + " failed after " + t.getSeconds() + "s; " + (error != null ? error.getMessage() : "actual: " + (last == null ? "null" : "'" + last + "'")), error);
    }

    /** sendKeys, then the type qualifier's event as seleasy fires it (bl, ch, fo, bk, cl). */
    private void type(WebElement e, CharSequence text, String qualifier) {
        e.sendKeys(text);
        switch (qualifier) {
            case "bl": js("arguments[0].blur();", e); break;
            case "ch": js("arguments[0].dispatchEvent(new Event('change', {bubbles: true}));", e); break;
            case "fo": e.click(); js("arguments[0].focus();", e); break;
            case "bk": e.sendKeys(Keys.chord(Keys.BACK_SPACE)); e.click(); break;
            case "cl": e.click(); break;
            default: break;
        }
    }

    private static void selectLast(Select s) {
        s.selectByIndex(s.getOptions().size() - 1);
    }

    /** executeScript: the script is a function body and sees 'arguments', as in seleasy. */
    private Object js(String script, Object... args) {
        return ((JavascriptExecutor) driver).executeScript(script, args);
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
