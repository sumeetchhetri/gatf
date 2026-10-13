// Exported from 17-selenium.sel by GATF (gatf export selenium-java). Review every TODO(gatf-export) line.
// 37 of 39 statement(s) exported, 2 TODO, 11 note(s): see the export report.
// Needs selenium-java and junit-jupiter. SELENIUM_REMOTE_URL runs it on a Selenium Grid; -Dgatf.export.browser.binary and
// -Dgatf.export.browser.args (comma-separated, e.g. --headless=new) adjust the local browser.
import java.io.File;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
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
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.Select;

import static org.junit.jupiter.api.Assertions.fail;

class Sel17SeleniumTest {

    private WebDriver driver;
    /** The element timeout: seleasy's default, then each 'timeout' statement. */
    private Duration timeout = Duration.ofSeconds(60);
    private final Map<String, String> vars = new HashMap<>();
    private final List<String> softFailures = new ArrayList<>();

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
        if (!softFailures.isEmpty()) fail(softFailures.size() + " soft failure(s):\n" + String.join("\n", softFailures));
    }

    @Test
    @DisplayName("17-selenium")
    void test() throws Exception {
        // Δ161 export corpus (selenium-java only): what Selenium maps 1:1 that Playwright did not, and Java-specific escaping.
        // gatf-export: 'open chrome-hdl' - the driver is created by newDriver() before the test
        driver.get("https://shop.test/");
        driver.manage().window().maximize();
        type(actionable(by("id@q", By.id("q")), timeout, "type"), Keys.chord("ab\ue003c\ue007"), "");
        // TODO(gatf-export): chord 'bad \\u12' id@q
        type(actionable(by("id@q", By.id("q")), timeout, "type"), Keys.chord("it's"), "");
        type(actionable(by("id@q", By.id("q")), timeout, "type"), "\\u0022); System.exit(1); (\\u0022", "");
        // \\u000a System.exit(2);
        type(actionable(by("id@q", By.id("q")), timeout, "type"), "trailing \\", "");
        new Actions(driver).moveToElement(actionable(by("id@row", By.id("row")), timeout, "dblclick")).doubleClick().perform();
        new Actions(driver).moveToElement(actionable(by("css@'.menu'", By.cssSelector(".menu")), timeout, "hover")).perform();
        new Actions(driver).moveToElement(actionable(by("id@menu", By.id("menu")), timeout, "hover")).click(find(by("id@item", By.id("item")), timeout)).perform();
        actionable(by("id@form", By.id("form")), timeout, "submit").submit();
        actionable(by("id@q", By.id("q")), timeout, "clear").clear();
        actionable(by("id@file", By.id("file")), timeout, "upload").sendKeys(new File("files/a.txt").getAbsolutePath());
        new Select(actionable(by("id@size", By.id("size")), timeout, "select")).selectByValue("m");
        new Select(actionable(by("id@size", By.id("size")), timeout, "select")).selectByIndex(0);
        selectLast(new Select(actionable(by("id@size", By.id("size")), timeout, "select")));
        new Actions(driver).dragAndDrop(actionable(by("id@a", By.id("a")), timeout, "drag"), find(by("id@b", By.id("b")), timeout)).perform();
        actionable(by("linktext@'Next page'", By.linkText("Next page")), timeout, "click").click();
        actionable(by("partiallinktext@Next", By.partialLinkText("Next")), timeout, "click").click();
        actionable(by("text@'Order placed'", By.xpath("//*[contains(text(), 'Order placed')]")), timeout, "click").click();
        actionable(by("text@'it''s \"both\"'", By.xpath("//*[contains(text(), concat('it', \"'\", '', \"'\", 's \"both\"'))]")), timeout, "click").click();
        actionable(by("text@$v{who}", By.xpath("//*[contains(text(), " + xpathLiteral(v("who")) + ")]")), timeout, "click").click();
        actionable(active(), timeout, "click").click();
        js("window.scrollTo(0,0)");
        js("window.scrollTo(0,document.body.scrollHeight)");
        check("?? pagesource %'<html'", timeout, () -> driver.getPageSource(), a -> "<html".startsWith(a));
        check("?? xpos ='0'", timeout, () -> String.valueOf(driver.manage().window().getPosition().getX()), a -> "0".equals(a));
        check("?? id@x height >'10'", timeout, () -> String.valueOf(now(by("id@x", By.id("x"))).getSize().getHeight()), a -> "10".compareTo(a) > 0);
        check("?? alerttext ='Are you sure?'", timeout, () -> driver.switchTo().alert().getText(), a -> "Are you sure?".equals(a));
        check("?? id@x attr@data-state ='open'", timeout, () -> now(by("id@x", By.id("x"))).getAttribute("data-state"), a -> "open".equals(a));
        js("arguments[0]");
        softFailures.add("no banner");
        if (all(by("id@spinner", By.id("spinner"))).isEmpty()) {
            System.out.println("[gatf-export] pass: " + "pass");
        }
        // TODO(gatf-export): emulate geolocation 1 2
        st_evil____int_x_____name();
    }

    /** subtest evil *\/ int x; /* name (line 41). */
    private void st_evil____int_x_____name() throws Exception {
        actionable(by("id@e", By.id("e")), timeout, "click").click();
    }

    // ---- gatf-export runtime: Selenium + JUnit 5 only ------------------------------------------------------------

    /** The script's browser (chrome, headless): local, or on a Selenium Grid when SELENIUM_REMOTE_URL is set. */
    private static WebDriver newDriver() throws Exception {
        ChromeOptions options = new ChromeOptions();
        String binary = System.getProperty("gatf.export.browser.binary", "");
        if (!binary.isEmpty()) options.setBinary(binary);
        options.addArguments("--headless=new");
        for (String a : System.getProperty("gatf.export.browser.args", "").split(",")) if (!a.trim().isEmpty()) options.addArguments(a.trim());
        String grid = System.getenv("SELENIUM_REMOTE_URL");
        if (grid != null && !grid.isEmpty()) return new RemoteWebDriver(URI.create(grid).toURL(), options);
        return new ChromeDriver(options);
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

    /** seleasy's 'active': the focused element. */
    private Loc active() {
        return new Loc("active", () -> java.util.Collections.singletonList(driver.switchTo().activeElement()), false);
    }

    /** An XPath 1.0 string literal (concat(...) when it holds both quote kinds). */
    private static String xpathLiteral(String s) {
        if (s.indexOf('\'') < 0) return "'" + s + "'";
        if (s.indexOf('"') < 0) return "\"" + s + "\"";
        return "concat('" + s.replace("'", "', \"'\", '") + "')";
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
