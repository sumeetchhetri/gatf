package com.gatf.selenium;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.imageio.ImageIO;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.Point;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.gatf.executor.core.AcceptanceTestContext;

public class Test extends SeleniumTest implements Serializable {
    public Test(AcceptanceTestContext ___cxt___, int index) {
        super("test.sel", ___cxt___, index);
    }
    public void close() {
        if(get___d___()!=null)get___d___().close();
    }
    public SeleniumTest copy(AcceptanceTestContext ctx, int index) {
        return new Test(ctx, index);}
    public void setupDriverappiumios(LoggingPreferences ___lp___) throws Exception {
        DesiredCapabilities ___dc___ = new DesiredCapabilities("ios", "null", org.openqa.selenium.Platform.MAC);
        ___dc___.setCapability(CapabilityType.LOGGING_PREFS, ___lp___);
        set___d___(new io.appium.java_client.ios.IOSDriver(new java.net.URL("http://127.0.0.1:4723/wd/hub"), ___dc___));
        //setBrowserName("appium-ios");

    }
    public void setupDriverchrome(LoggingPreferences ___lp___) throws Exception {
        org.openqa.selenium.chrome.ChromeOptions ___dc___ = new org.openqa.selenium.chrome.ChromeOptions();
        ___dc___.setCapability(CapabilityType.LOGGING_PREFS, ___lp___);
        set___d___(new org.openqa.selenium.chrome.ChromeDriver(___dc___));
        //setBrowserName("chrome");

    }
    public int concurrentExecutionNum() {
        return 0;
    }
    @SuppressWarnings("unchecked")
    public void _execute(LoggingPreferences ___lp___) throws Exception {
        try {
            SearchContext ___sc___1 = get___d___();
            WebDriver ___cw___ = get___d___();
            WebDriver ___ocw___ = ___cw___;
            List<WebElement> ___ce___ = null;
            try {
                ___cw___.navigate().to(evaluate("http://abc.com"));
            } catch (org.openqa.selenium.TimeoutException ___e___1) {
                ___cw___.navigate().refresh();
            }
            final Object[] ___w___1 = new Object[2];
            final WebDriver ___sc___2 = (WebDriver) ___sc___1;
            try {
                (new WebDriverWait(___sc___2, 10))
                .until(
                        new Function<WebDriver, Boolean>() {
                            public Boolean apply(WebDriver input) {

                                List<WebElement> ___ce___ = null;
                                List<WebElement> ___w___2 =
                                        By.id(evaluate("inputUsername")).findElements(___sc___2);
                                if (___w___2 == null || ___w___2.isEmpty()) return false;
                                ___w___1[0] = ___w___2;

                                ___w___1[1] = ___ce___;

                                return true;
                            }

                            public String toString() {
                                return "";
                            }
                        });
            } catch (org.openqa.selenium.TimeoutException ___e___2) {
                throw new RuntimeException(
                        "Element not found by selector id@'inputUsername' at line number 4 ", ___e___2);
            }
            ___ce___ = (List<WebElement>) ___w___1[1];

            List<WebElement> ___w___3 = By.id(evaluate("inputUsername")).findElements(___sc___1);
            Assert.assertTrue(
                    "Element not found by selector id@'inputUsername' at line number 5 ",
                    ___w___3 != null && !___w___3.isEmpty());
            ___ce___ = ___w___3;
            ___w___3.get(0).sendKeys(evaluate("admin"));
            List<WebElement> ___w___4 = By.id(evaluate("inputPassword")).findElements(___sc___1);
            Assert.assertTrue(
                    "Element not found by selector id@'inputPassword' at line number 6 ",
                    ___w___4 != null && !___w___4.isEmpty());
            ___ce___ = ___w___4;
            ___w___4.get(0).sendKeys(evaluate("admin"));
            List<WebElement> ___w___5 =
                    By.xpath("//*[contains(text(), 'Sign In')]").findElements(___sc___1);
            Assert.assertTrue(
                    "Element not found by selector text@'Sign In' at line number 7 ",
                    ___w___5 != null && !___w___5.isEmpty());
            ___ce___ = ___w___5;
            ___w___5.get(0).click();
            final Object[] ___w___6 = new Object[2];
            final WebDriver ___sc___3 = (WebDriver) ___sc___1;
            try {
                (new WebDriverWait(___sc___3, 10))
                .until(
                        new Function<WebDriver, Boolean>() {
                            public Boolean apply(WebDriver input) {

                                List<WebElement> ___ce___ = null;
                                List<WebElement> ___w___7 =
                                        By.className(evaluate("logoutLink")).findElements(___sc___3);
                                if (___w___7 == null || ___w___7.isEmpty()) return false;
                                ___w___6[0] = ___w___7;

                                ___w___6[1] = ___ce___;

                                return true;
                            }

                            public String toString() {
                                return "";
                            }
                        });
            } catch (org.openqa.selenium.TimeoutException ___e___3) {
                throw new RuntimeException(
                        "Element not found by selector class@'logoutLink' at line number 8 ", ___e___3);
            }
            ___ce___ = (List<WebElement>) ___w___6[1];

            List<WebElement> ___w___8 =
                    By.cssSelector(evaluate("div#left-panel>div#loginInfo")).findElements(___sc___1);
            Assert.assertTrue(
                    "Element not found by selector css@'div#left-panel>div#loginInfo' at line number 9 ",
                    ___w___8 != null && !___w___8.isEmpty());
            ___ce___ = ___w___8;
            List<WebElement> ___w___9 =
                    By.cssSelector(evaluate("div#left-panel>nav>div>ul>li")).findElements(___sc___1);
            Assert.assertTrue(
                    "Element not found by selector css@'div#left-panel>nav>div>ul>li' at line number 10 ",
                    ___w___9 != null && !___w___9.isEmpty());
            ___ce___ = ___w___9;
            boolean ___c___1 = ___w___9 != null && ___w___9.size() > 0 && true;
            if (!(___c___1 && 1 == ___w___9.size())) ___c___1 = false;
            else {
                final WebElement ___w___10 = ___w___9.get(0);
                ___c___1 &= evaluate("DashBoard").equals(___w___10.getText());
            }
            List<WebElement> ___w___11 =
                    By.cssSelector(evaluate("div#left-panel>nav>div>ul>li")).findElements(___sc___1);
            Assert.assertTrue(
                    "Element not found by selector css@'div#left-panel>nav>div>ul>li' at line number 14 ",
                    ___w___11 != null && !___w___11.isEmpty());
            ___ce___ = ___w___11;
            boolean ___c___2 = true;
            for (final WebElement ___w___12 : ___w___11) {
                ___c___2 &= evaluate("DashBoard").compareTo(___w___12.getText()) == 0;
            }
            List<WebElement> ___w___13 =
                    By.cssSelector(evaluate("div#left-panel>nav>div>ul>li")).findElements(___sc___1);
            Assert.assertTrue(
                    "Element not found by selector css@'div#left-panel>nav>div>ul>li' at line number 15 ",
                    ___w___13 != null && !___w___13.isEmpty());
            ___ce___ = ___w___13;
            boolean ___c___3 = true;
            for (final WebElement ___w___14 : ___w___13) {
                ___c___3 &= evaluate("li").compareTo(___w___14.getTagName()) == 0;
            }
            List<WebElement> ___w___15 =
                    By.cssSelector(evaluate("div#left-panel>nav>div>ul>li")).findElements(___sc___1);
            Assert.assertTrue(
                    "Element not found by selector css@'div#left-panel>nav>div>ul>li' at line number 16 ",
                    ___w___15 != null && !___w___15.isEmpty());
            ___ce___ = ___w___15;
            boolean ___c___4 = true;
            for (final WebElement ___w___16 : ___w___15) {
                ___c___4 &= evaluate("li").compareTo(___w___16.getAttribute("data-value")) == 0;
            }
            List<WebElement> ___w___17 =
                    By.cssSelector(evaluate("div#left-panel>nav>div>ul>li")).findElements(___sc___1);
            Assert.assertTrue(
                    "Element not found by selector css@'div#left-panel>nav>div>ul>li' at line number 17 ",
                    ___w___17 != null && !___w___17.isEmpty());
            ___ce___ = ___w___17;
            boolean ___c___5 = true;
            for (final WebElement ___w___18 : ___w___17) {
                ___c___5 &= evaluate("li").compareTo(___w___18.getCssValue("width")) == 0;
            }
            List<WebElement> ___w___19 =
                    By.cssSelector(evaluate("div#left-panel>nav>div>ul>li")).findElements(___sc___1);
            Assert.assertTrue(
                    "Element not found by selector css@'div#left-panel>nav>div>ul>li' at line number 18 ",
                    ___w___19 != null && !___w___19.isEmpty());
            ___ce___ = ___w___19;
            boolean ___c___6 = true;
            for (final WebElement ___w___20 : ___w___19) {
                ___c___6 &= ___w___20.isSelected();
            }
            List<WebElement> ___w___21 =
                    By.cssSelector(evaluate("div#left-panel>nav>div>ul>li")).findElements(___sc___1);
            Assert.assertTrue(
                    "Element not found by selector css@'div#left-panel>nav>div>ul>li' at line number 19 ",
                    ___w___21 != null && !___w___21.isEmpty());
            ___ce___ = ___w___21;
            boolean ___c___7 = true;
            for (final WebElement ___w___22 : ___w___21) {
                ___c___7 &= ___w___22.isEnabled();
            }
            List<WebElement> ___w___23 =
                    By.cssSelector(evaluate("div#left-panel>nav>div>ul>li")).findElements(___sc___1);
            Assert.assertTrue(
                    "Element not found by selector css@'div#left-panel>nav>div>ul>li' at line number 20 ",
                    ___w___23 != null && !___w___23.isEmpty());
            ___ce___ = ___w___23;
            boolean ___c___8 = true;
            for (final WebElement ___w___24 : ___w___23) {
                ___c___8 &= ___w___24.isDisplayed();
            }
            boolean ___ifcnt___1 = false;
            try {
                List<WebElement> ___w___25 =
                        By.cssSelector(evaluate("a[href=#myCustomers]")).findElements(___sc___1);
                Assert.assertTrue(
                        "Element not found by selector css@'a[href=#myCustomers]' at line number 21 ",
                        ___w___25 != null && !___w___25.isEmpty());
                ___ce___ = ___w___25;
                boolean ___ifcnt___2 = false;
                try {
                    List<WebElement> ___w___26 =
                            By.cssSelector(evaluate("a[href=#allCustomers]")).findElements(___sc___1);
                    Assert.assertTrue(
                            "Element not found by selector css@'a[href=#allCustomers]' at line number 22 ",
                            ___w___26 != null && !___w___26.isEmpty());
                    ___ce___ = ___w___26;
                    boolean ___ifcnt___3 = false;
                    try {
                        List<WebElement> ___w___27 =
                                By.cssSelector(evaluate("a[href=#myVendors]")).findElements(___sc___1);
                        Assert.assertTrue(
                                "Element not found by selector css@'a[href=#myVendors]' at line number 23 ",
                                ___w___27 != null && !___w___27.isEmpty());
                        ___ce___ = ___w___27;
                        boolean ___ifcnt___4 = false;
                        try {
                            List<WebElement> ___w___28 =
                                    By.cssSelector(evaluate("a[href=#allVendors]")).findElements(___sc___1);
                            Assert.assertTrue(
                                    "Element not found by selector css@'a[href=#allVendors]' at line number 24 ",
                                    ___w___28 != null && !___w___28.isEmpty());
                            ___ce___ = ___w___28;
                            boolean ___ifcnt___5 = false;
                            try {
                                List<WebElement> ___w___29 =
                                        By.cssSelector(evaluate("a[href=#customerConnection]")).findElements(___sc___1);
                                Assert.assertTrue(
                                        "Element not found by selector css@'a[href=#customerConnection]' at line number 25 ",
                                        ___w___29 != null && !___w___29.isEmpty());
                                ___ce___ = ___w___29;
                                boolean ___ifcnt___6 = false;
                                try {
                                    List<WebElement> ___w___30 =
                                            By.cssSelector(evaluate("a[href=#vendorConnection]")).findElements(___sc___1);
                                    Assert.assertTrue(
                                            "Element not found by selector css@'a[href=#vendorConnection]' at line number 26 ",
                                            ___w___30 != null && !___w___30.isEmpty());
                                    ___ce___ = ___w___30;
                                    boolean ___ifcnt___7 = false;
                                    try {
                                        List<WebElement> ___w___31 =
                                                By.name(evaluate("asdasd")).findElements(___sc___1);
                                        Assert.assertTrue(
                                                "Element not found by selector name@'asdasd' at line number 27 ",
                                                ___w___31 != null && !___w___31.isEmpty());
                                        ___ce___ = ___w___31;
                                        List<WebElement> ___w___32 = By.id(evaluate("1")).findElements(___sc___1);
                                        Assert.assertTrue(
                                                "Element not found by selector id@'1' at line number 29 ",
                                                ___w___32 != null && !___w___32.isEmpty());
                                        ___ce___ = ___w___32;
                                        ___w___32.get(0).click();
                                        final Object[] ___w___33 = new Object[2];
                                        final WebDriver ___sc___4 = (WebDriver) ___sc___1;
                                        try {
                                            (new WebDriverWait(___sc___4, 10))
                                            .until(
                                                    new Function<WebDriver, Boolean>() {
                                                        public Boolean apply(WebDriver input) {

                                                            List<WebElement> ___ce___ = null;
                                                            List<WebElement> ___w___34 =
                                                                    By.className(evaluate("22")).findElements(___sc___4);
                                                            if (___w___34 == null || ___w___34.isEmpty()) return false;
                                                            ___w___33[0] = ___w___34;

                                                            ___w___33[1] = ___ce___;

                                                            return true;
                                                        }

                                                        public String toString() {
                                                            return "";
                                                        }
                                                    });
                                        } catch (org.openqa.selenium.TimeoutException ___e___11) {
                                            throw new RuntimeException(
                                                    "Element not found by selector class@'22' at line number 30 ", ___e___11);
                                        }
                                        ___ce___ = (List<WebElement>) ___w___33[1];

                                        ___ifcnt___7 = true;

                                    } catch (AssertionError ___e___12) {
                                        ___ifcnt___7 = false;
                                    } catch (Exception ___e___10) {
                                        System.out.println(___e___10.getMessage());
                                    }
                                    if (!___ifcnt___7) {
                                        try {
                                            List<WebElement> ___w___35 =
                                                    By.name(evaluate("12345")).findElements(___sc___1);
                                            Assert.assertTrue(
                                                    "Element not found by selector name@'12345' at line number 32 ",
                                                    ___w___35 != null && !___w___35.isEmpty());
                                            ___ce___ = ___w___35;
                                            List<WebElement> ___w___36 = By.id(evaluate("2")).findElements(___sc___1);
                                            Assert.assertTrue(
                                                    "Element not found by selector id@'2' at line number 34 ",
                                                    ___w___36 != null && !___w___36.isEmpty());
                                            ___ce___ = ___w___36;
                                            ___w___36.get(0).click();
                                            final Object[] ___w___37 = new Object[2];
                                            final WebDriver ___sc___5 = (WebDriver) ___sc___1;
                                            try {
                                                (new WebDriverWait(___sc___5, 10))
                                                .until(
                                                        new Function<WebDriver, Boolean>() {
                                                            public Boolean apply(WebDriver input) {

                                                                List<WebElement> ___ce___ = null;
                                                                List<WebElement> ___w___38 =
                                                                        By.className(evaluate("33")).findElements(___sc___5);
                                                                if (___w___38 == null || ___w___38.isEmpty()) return false;
                                                                ___w___37[0] = ___w___38;

                                                                ___w___37[1] = ___ce___;

                                                                return true;
                                                            }

                                                            public String toString() {
                                                                return "";
                                                            }
                                                        });
                                            } catch (org.openqa.selenium.TimeoutException ___e___14) {
                                                throw new RuntimeException(
                                                        "Element not found by selector class@'33' at line number 35 ",
                                                        ___e___14);
                                            }
                                            ___ce___ = (List<WebElement>) ___w___37[1];

                                            ___ifcnt___7 = true;

                                        } catch (AssertionError ___e___15) {
                                            ___ifcnt___7 = false;
                                        } catch (Exception ___e___13) {
                                            System.out.println(___e___13.getMessage());
                                        }
                                    }
                                    if (!___ifcnt___7) {
                                        List<WebElement> ___w___39 = By.id(evaluate("3")).findElements(___sc___1);
                                        Assert.assertTrue(
                                                "Element not found by selector id@'3' at line number 39 ",
                                                ___w___39 != null && !___w___39.isEmpty());
                                        ___ce___ = ___w___39;
                                        ___w___39.get(0).click();
                                        final Object[] ___w___40 = new Object[2];
                                        final WebDriver ___sc___6 = (WebDriver) ___sc___1;
                                        try {
                                            (new WebDriverWait(___sc___6, 10))
                                            .until(
                                                    new Function<WebDriver, Boolean>() {
                                                        public Boolean apply(WebDriver input) {

                                                            List<WebElement> ___ce___ = null;
                                                            List<WebElement> ___w___41 =
                                                                    By.className(evaluate("44")).findElements(___sc___6);
                                                            if (___w___41 == null || ___w___41.isEmpty()) return false;
                                                            ___w___40[0] = ___w___41;

                                                            ___w___40[1] = ___ce___;

                                                            return true;
                                                        }

                                                        public String toString() {
                                                            return "";
                                                        }
                                                    });
                                        } catch (org.openqa.selenium.TimeoutException ___e___16) {
                                            throw new RuntimeException(
                                                    "Element not found by selector class@'44' at line number 40 ", ___e___16);
                                        }
                                        ___ce___ = (List<WebElement>) ___w___40[1];
                                    }
                                    List<WebElement> ___w___42 =
                                            By.cssSelector(evaluate("div[class=ssdfdsf]>nav>div>ul>li"))
                                            .findElements(___sc___1);
                                    Assert.assertTrue(
                                            "Element not found by selector css@'div[class=ssdfdsf]>nav>div>ul>li' at line number 42 ",
                                            ___w___42 != null && !___w___42.isEmpty());
                                    ___ce___ = ___w___42;
                                    if (true) {

                                        int ___itr___1 = 0;
                                        for (final WebElement ___w___43 : ___w___42) {
                                            final SearchContext ___pc___1 = ___w___43;
                                            @SuppressWarnings("serial")
                                            List<WebElement> ___w___44 =
                                            new java.util.ArrayList<WebElement>() {
                                                {
                                                    add(___w___43);
                                                }
                                            };
                                            ___w___44.get(0).click();
                                            final Object[] ___w___45 = new Object[2];
                                            final WebDriver ___sc___7 = (WebDriver) ___sc___1;
                                            try {
                                                (new WebDriverWait(___sc___7, 10))
                                                .until(
                                                        new Function<WebDriver, Boolean>() {
                                                            public Boolean apply(WebDriver input) {

                                                                List<WebElement> ___ce___ = null;
                                                                List<WebElement> ___w___46 =
                                                                        By.className(evaluate("55")).findElements(___sc___7);
                                                                if (___w___46 == null || ___w___46.isEmpty()) return false;
                                                                ___w___45[0] = ___w___46;

                                                                ___w___45[1] = ___ce___;

                                                                return true;
                                                            }

                                                            public String toString() {
                                                                return "";
                                                            }
                                                        });
                                            } catch (org.openqa.selenium.TimeoutException ___e___17) {
                                                throw new RuntimeException(
                                                        "Element not found by selector class@'55' at line number 45 ",
                                                        ___e___17);
                                            }
                                            ___ce___ = (List<WebElement>) ___w___45[1];

                                            ___itr___1++;
                                        }
                                    }
                                    List<WebElement> ___w___47 =
                                            By.className(evaluate("logoutLink")).findElements(___sc___1);
                                    Assert.assertTrue(
                                            "Element not found by selector class@'logoutLink' at line number 47 ",
                                            ___w___47 != null && !___w___47.isEmpty());
                                    ___ce___ = ___w___47;
                                    ___w___47.get(0).click();
                                    List<WebElement> ___w___48 =
                                            By.id(evaluate("adsdsd\"ds\"")).findElements(___sc___1);
                                    Assert.assertTrue(
                                            "Element not found by selector id@'adsdsd\"ds\"' at line number 48 ",
                                            ___w___48 != null && !___w___48.isEmpty());
                                    ___ce___ = ___w___48;
                                    ___w___48.get(0).sendKeys(evaluate("admin"));
                                    ___cw___.navigate().back();
                                    ___cw___.navigate().forward();
                                    ___cw___.navigate().refresh();
                                    ___cw___.manage().window().maximize();
                                    ___cw___.close();
                                    List<WebElement> ___w___49 =
                                            By.className(evaluate("logoutLink")).findElements(___sc___1);
                                    Assert.assertTrue(
                                            "Element not found by selector class@'logoutLink' at line number 54 ",
                                            ___w___49 != null && !___w___49.isEmpty());
                                    ___ce___ = ___w___49;
                                    ___w___49.get(0).clear();
                                    List<WebElement> ___w___50 =
                                            By.className(evaluate("logoutLink")).findElements(___sc___1);
                                    Assert.assertTrue(
                                            "Element not found by selector class@'logoutLink' at line number 55 ",
                                            ___w___50 != null && !___w___50.isEmpty());
                                    ___ce___ = ___w___50;
                                    ___w___50.get(0).submit();
                                    Dimension ___w___51 = ___cw___.manage().window().getSize();
                                    ___cw___.manage().window().setSize(new Dimension(100, ___w___51.getHeight()));
                                    Dimension ___w___52 = ___cw___.manage().window().getSize();
                                    ___cw___.manage().window().setSize(new Dimension(___w___52.getWidth(), 100));
                                    Point ___w___53 = ___cw___.manage().window().getPosition();
                                    ___cw___.manage().window().setPosition(new Point(100, ___w___53.getY()));
                                    Point ___w___54 = ___cw___.manage().window().getPosition();
                                    ___cw___.manage().window().setPosition(new Point(___w___54.getX(), 100));
                                    window(0);
                                    ___cw___ = get___d___();
                                    ___sc___1 = ___cw___;
                                    window(1);
                                    ___cw___ = get___d___();
                                    ___sc___1 = ___cw___;
                                    window(2);
                                    ___cw___ = get___d___();
                                    ___sc___1 = ___cw___;
                                    window(0);
                                    ___cw___ = get___d___();
                                    ___sc___1 = ___cw___;
                                    List<WebElement> ___w___55 =
                                            By.cssSelector(evaluate("div[class=ssdfdsf]>nav>div>ul>li"))
                                            .findElements(___sc___1);
                                    Assert.assertTrue(
                                            "Element not found by selector css@'div[class=ssdfdsf]>nav>div>ul>li' at line number 64 ",
                                            ___w___55 != null && !___w___55.isEmpty());
                                    ___ce___ = ___w___55;
                                    List<WebElement> t = ___w___55;
                                    int ___w___56 = getProviderTestDataMap("abc").size();
                                    set__provname__("abc");

                                    for (int ___w___57 = 0; ___w___57 < ___w___56; ___w___57++) {
                                        set__provpos__("abc", ___w___57);
                                        System.out.println("if");
                                    }
                                    rem__provname__("abc");

                                    Object u = null;
                                    if (___ocw___ instanceof JavascriptExecutor) {
                                        u = ((JavascriptExecutor) ___ocw___).executeScript(evaluate("return window;"));
                                    }
                                    if (___ocw___ instanceof JavascriptExecutor) {
                                        ((JavascriptExecutor) ___ocw___)
                                        .executeScript(evaluate("arguments[0].style.border='3px solid red'"));
                                    }
                                    if (get___d___() instanceof io.appium.java_client.AppiumDriver) {
                                        File ___sr___1 =
                                                ((TakesScreenshot)
                                                        new org.openqa.selenium.remote.Augmenter().augment(get___d___()))
                                                .getScreenshotAs(OutputType.FILE);
                                        FileUtils.copyFile(___sr___1, new File("test.png"));
                                    } else {
                                        File ___sr___1 = ((TakesScreenshot) ___ocw___).getScreenshotAs(OutputType.FILE);
                                        FileUtils.copyFile(___sr___1, new File("test.png"));
                                    }
                                    List<WebElement> ___w___58 =
                                            By.cssSelector(evaluate("div[class=ssdfdsf]>nav>div>ul>li"))
                                            .findElements(___sc___1);
                                    Assert.assertTrue(
                                            "Element not found by selector css@'div[class=ssdfdsf]>nav>div>ul>li' at line number 69 ",
                                            ___w___58 != null && !___w___58.isEmpty());
                                    ___ce___ = ___w___58;
                                    if (true) {
                                        WebElement ele = ___w___58.get(0);
                                        File sc = ((TakesScreenshot) ___ocw___).getScreenshotAs(OutputType.FILE);
                                        BufferedImage fi = ImageIO.read(sc);
                                        Point point = ele.getLocation();
                                        int ew = ele.getSize().getWidth();
                                        int eh = ele.getSize().getHeight();
                                        BufferedImage esc = fi.getSubimage(point.getX(), point.getY(), ew, eh);
                                        ImageIO.write(esc, "png", sc);
                                        FileUtils.copyFile(sc, new File("teste.png"));
                                    }
                                    final Object[] ___w___59 = new Object[2];
                                    final WebDriver ___sc___8 = (WebDriver) ___sc___1;
                                    try {
                                        (new WebDriverWait(___sc___8, 60))
                                        .until(
                                                new Function<WebDriver, Boolean>() {
                                                    public Boolean apply(WebDriver input) {

                                                        List<WebElement> ___ce___ = null;
                                                        List<WebElement> ___w___60 =
                                                                By.className(evaluate("welcome_text")).findElements(___sc___8);
                                                        if (___w___60 == null || ___w___60.isEmpty()) return false;
                                                        Actions ___w___61 = new Actions(get___d___());
                                                        ___w___61.moveToElement(___w___60.get(0)).perform();
                                                        ___w___59[0] = ___w___60;

                                                        ___w___59[1] = ___ce___;

                                                        return true;
                                                    }

                                                    public String toString() {
                                                        return "";
                                                    }
                                                });
                                    } catch (org.openqa.selenium.TimeoutException ___e___18) {
                                        throw new RuntimeException(
                                                "Element not found by selector class@'welcome_text' at line number 70 ",
                                                ___e___18);
                                    }
                                    ___ce___ = (List<WebElement>) ___w___59[1];

                                    List<WebElement> ___w___62 =
                                            By.className(evaluate("welcome_text")).findElements(___sc___1);
                                    Assert.assertTrue(
                                            "Element not found by selector class@'welcome_text' at line number 71 ",
                                            ___w___62 != null && !___w___62.isEmpty());
                                    ___ce___ = ___w___62;
                                    final Object[] ___w___63 = new Object[2];
                                    final WebDriver ___sc___9 = (WebDriver) ___sc___1;
                                    try {
                                        (new WebDriverWait(___sc___9, 10000))
                                        .until(
                                                new Function<WebDriver, Boolean>() {
                                                    public Boolean apply(WebDriver input) {

                                                        List<WebElement> ___ce___ = null;
                                                        List<WebElement> ___w___64 =
                                                                By.id(evaluate("signOut")).findElements(___sc___9);
                                                        if (___w___64 == null || ___w___64.isEmpty()) return false;
                                                        ___w___63[0] = ___w___64;

                                                        ___w___63[1] = ___ce___;

                                                        return true;
                                                    }

                                                    public String toString() {
                                                        return "";
                                                    }
                                                });
                                    } catch (org.openqa.selenium.TimeoutException ___e___19) {
                                        throw new RuntimeException(
                                                "Element not found by selector id@'signOut' at line number 71 ", ___e___19);
                                    }
                                    ___ce___ = (List<WebElement>) ___w___63[1];

                                    Actions ___w___65 = new Actions(get___d___());
                                    ___w___65
                                    .moveToElement(___w___62.get(0))
                                    .click(((List<WebElement>) ___w___63[0]).get(0))
                                    .perform();
                                    boolean ___ifcnt___8 = false;
                                    try {
                                        List<WebElement> ___w___66 =
                                                By.id(evaluate("Location1")).findElements(___sc___1);
                                        Assert.assertTrue(
                                                "Element not found by selector id@'Location1' at line number 72 ",
                                                ___w___66 != null && !___w___66.isEmpty());
                                        ___ce___ = ___w___66;
                                        System.out.println("if");

                                        ___ifcnt___8 = true;

                                    } catch (AssertionError ___e___21) {
                                        ___ifcnt___8 = false;
                                    } catch (Exception ___e___20) {
                                        System.out.println(___e___20.getMessage());
                                    }
                                    if (!___ifcnt___8) {
                                        try {
                                            List<WebElement> ___w___67 =
                                                    By.id(evaluate("Location2")).findElements(___sc___1);
                                            Assert.assertTrue(
                                                    "Element not found by selector id@'Location2' at line number 76 ",
                                                    ___w___67 != null && !___w___67.isEmpty());
                                            ___ce___ = ___w___67;
                                            System.out.println("else if");

                                            ___ifcnt___8 = true;

                                        } catch (AssertionError ___e___23) {
                                            ___ifcnt___8 = false;
                                        } catch (Exception ___e___22) {
                                            System.out.println(___e___22.getMessage());
                                        }
                                    }
                                    if (!___ifcnt___8) {
                                        System.out.println("else");
                                        List<WebElement> ___w___68 =
                                                By.id(evaluate("Location")).findElements(___sc___1);
                                        Assert.assertTrue(
                                                "Element not found by selector id@'Location' at line number 83 ",
                                                ___w___68 != null && !___w___68.isEmpty());
                                        ___ce___ = ___w___68;
                                        ___w___68.get(0).sendKeys(Keys.chord(Keys.CONTROL, "a"));
                                    }
                                    List<WebElement> ___w___69 = By.id(evaluate("Location")).findElements(___sc___1);
                                    Assert.assertTrue(
                                            "Element not found by selector id@'Location' at line number 85 ",
                                            ___w___69 != null && !___w___69.isEmpty());
                                    ___ce___ = ___w___69;
                                    if (true) {

                                        int ___itr___2 = 0;
                                        for (final WebElement ___w___70 : ___w___69) {
                                            final SearchContext ___pc___2 = ___w___70;
                                            @SuppressWarnings("serial")
                                            List<WebElement> ___w___71 =
                                            new java.util.ArrayList<WebElement>() {
                                                {
                                                    add(___w___70);
                                                }
                                            };
                                            System.out.println("loop");
                                            ___itr___2++;
                                        }
                                    }
                                    newProvider("prov1");
                                    List<WebElement> ___w___72 = By.id(evaluate("Location")).findElements(___sc___1);
                                    Assert.assertTrue(
                                            "Element not found by selector id@'Location' at line number 89 ",
                                            ___w___72 != null && !___w___72.isEmpty());
                                    ___ce___ = ___w___72;
                                    List<String[]> ___w___73 = new java.util.ArrayList<String[]>();
                                    for (final WebElement ___w___74 : ___w___72) {
                                        String[] __t = new String[2];
                                        __t[0] = ___w___74.getAttribute("abc");
                                        __t[1] = ___w___74.getCssValue("bcd");
                                        ___w___73.add(__t);
                                    }
                                    for (int ___w___75 = 0; ___w___75 < ___w___73.size(); ___w___75++) {
                                        Map<String, String> __mp = new java.util.HashMap<String, String>();
                                        __mp.put("temp", ___w___73.get(___w___75)[0]);

                                        __mp.put("temp1", ___w___73.get(___w___75)[1]);
                                        getProviderTestDataMap("prov1").add(__mp);
                                    }
                                    int ___w___76 = getProviderTestDataMap("prov1").size();
                                    set__provname__("prov1");

                                    for (int ___w___77 = 0; ___w___77 < ___w___76; ___w___77++) {
                                        set__provpos__("prov1", ___w___77);
                                        List<WebElement> ___w___78 =
                                                By.cssSelector(evaluate("input[type='submit']")).findElements(___sc___1);
                                        Assert.assertTrue(
                                                "Element not found by selector css@'input[type='submit']' at line number 92 ",
                                                ___w___78 != null && !___w___78.isEmpty());
                                        ___ce___ = ___w___78;
                                        ___w___78.get(0).click();
                                    }
                                    rem__provname__("prov1");

                                    set__provname__("prov1");
                                    set__provpos__("prov1", 1);
                                    {
                                        List<WebElement> ___w___79 =
                                                By.cssSelector(evaluate("input[type='submit']")).findElements(___sc___1);
                                        Assert.assertTrue(
                                                "Element not found by selector css@'input[type='submit']' at line number 96 ",
                                                ___w___79 != null && !___w___79.isEmpty());
                                        ___ce___ = ___w___79;
                                        ___w___79.get(0).click();
                                    }
                                    rem__provname__("prov1");

                                    List<WebElement> ___w___80 = By.xpath(evaluate("sds")).findElements(___sc___1);
                                    Assert.assertTrue(
                                            "Element not found by selector xpath@'sds' at line number 98 ",
                                            ___w___80 != null && !___w___80.isEmpty());
                                    ___ce___ = ___w___80;
                                    mzoompinch(___w___80.get(0), -1, -1, true);

                                    mzoompinch(null, 1, 2, true);
                                    List<WebElement> ___w___81 = By.xpath(evaluate("sds")).findElements(___sc___1);
                                    Assert.assertTrue(
                                            "Element not found by selector xpath@'sds' at line number 100 ",
                                            ___w___81 != null && !___w___81.isEmpty());
                                    ___ce___ = ___w___81;
                                    mzoompinch(___w___81.get(0), -1, -1, false);

                                    mzoompinch(null, 1, 2, false);

                                    new io.appium.java_client.TouchAction(
                                            (io.appium.java_client.MobileDriver) get___d___())
                                    .waitAction(java.time.Duration.ofMillis(3))
                                    .tap(1, 2)
                                    .perform();
                                    List<WebElement> ___w___82 = By.xpath(evaluate("sds")).findElements(___sc___1);
                                    Assert.assertTrue(
                                            "Element not found by selector xpath@'sds' at line number 103 ",
                                            ___w___82 != null && !___w___82.isEmpty());
                                    ___ce___ = ___w___82;
                                    new io.appium.java_client.TouchAction(
                                            (io.appium.java_client.MobileDriver) get___d___())
                                    .waitAction(java.time.Duration.ofMillis(2))
                                    .tap(___w___82.get(0))
                                    .perform();

                                    new io.appium.java_client.TouchAction(
                                            (io.appium.java_client.MobileDriver) get___d___())
                                    .press(1, 2)
                                    .waitAction(java.time.Duration.ofMillis(5))
                                    .moveTo(3, 4)
                                    .release()
                                    .perform();
                                    org.openqa.selenium.ScreenOrientation ___w___83 =
                                            ((org.openqa.selenium.Rotatable) get___d___()).getOrientation();
                                    if (___w___83
                                            .value()
                                            .equals(org.openqa.selenium.ScreenOrientation.LANDSCAPE.value())) {
                                        ((org.openqa.selenium.Rotatable) get___d___())
                                        .rotate(org.openqa.selenium.ScreenOrientation.PORTRAIT);
                                    } else {
                                        ((org.openqa.selenium.Rotatable) get___d___())
                                        .rotate(org.openqa.selenium.ScreenOrientation.LANDSCAPE);
                                    }
                                    try {
                                        Thread.sleep(200);
                                        ((io.appium.java_client.MobileDriver) get___d___()).hideKeyboard();
                                    } catch (Exception e) {
                                    }

                                    io.appium.java_client.TouchAction ___w___84 =
                                            new io.appium.java_client.TouchAction(
                                                    (io.appium.java_client.MobileDriver) get___d___());
                                    List<WebElement> ___w___85 = By.xpath(evaluate("Sd")).findElements(___sc___1);
                                    Assert.assertTrue(
                                            "Element not found by selector xpath@'Sd' at line number 107 ",
                                            ___w___85 != null && !___w___85.isEmpty());
                                    ___ce___ = ___w___85;
                                    ___w___84.press(___w___85.get(0), 1, 2);
                                    ___w___84.press(1, 2);
                                    List<WebElement> ___w___86 = By.xpath(evaluate("Sd")).findElements(___sc___1);
                                    Assert.assertTrue(
                                            "Element not found by selector xpath@'Sd' at line number 107 ",
                                            ___w___86 != null && !___w___86.isEmpty());
                                    ___ce___ = ___w___86;
                                    ___w___84.press(___w___86.get(0));
                                    List<WebElement> ___w___87 = By.xpath(evaluate("Sd")).findElements(___sc___1);
                                    Assert.assertTrue(
                                            "Element not found by selector xpath@'Sd' at line number 107 ",
                                            ___w___87 != null && !___w___87.isEmpty());
                                    ___ce___ = ___w___87;
                                    ___w___84.tap(___w___87.get(0), 1, 2);
                                    ___w___84.tap(1, 2);
                                    List<WebElement> ___w___88 = By.xpath(evaluate("Sd")).findElements(___sc___1);
                                    Assert.assertTrue(
                                            "Element not found by selector xpath@'Sd' at line number 107 ",
                                            ___w___88 != null && !___w___88.isEmpty());
                                    ___ce___ = ___w___88;
                                    ___w___84.tap(___w___88.get(0));
                                    List<WebElement> ___w___89 = By.xpath(evaluate("Sd")).findElements(___sc___1);
                                    Assert.assertTrue(
                                            "Element not found by selector xpath@'Sd' at line number 107 ",
                                            ___w___89 != null && !___w___89.isEmpty());
                                    ___ce___ = ___w___89;
                                    ___w___84.moveTo(___w___89.get(0), 1, 2);
                                    ___w___84.moveTo(1, 2);
                                    List<WebElement> ___w___90 = By.xpath(evaluate("Sd")).findElements(___sc___1);
                                    Assert.assertTrue(
                                            "Element not found by selector xpath@'Sd' at line number 107 ",
                                            ___w___90 != null && !___w___90.isEmpty());
                                    ___ce___ = ___w___90;
                                    ___w___84.moveTo(___w___90.get(0));
                                    List<WebElement> ___w___91 = By.xpath(evaluate("Sd")).findElements(___sc___1);
                                    Assert.assertTrue(
                                            "Element not found by selector xpath@'Sd' at line number 107 ",
                                            ___w___91 != null && !___w___91.isEmpty());
                                    ___ce___ = ___w___91;
                                    ___w___84.longPress(___w___91.get(0), 1, 2, java.time.Duration.ofMillis(3));
                                    List<WebElement> ___w___92 = By.xpath(evaluate("Sd")).findElements(___sc___1);
                                    Assert.assertTrue(
                                            "Element not found by selector xpath@'Sd' at line number 107 ",
                                            ___w___92 != null && !___w___92.isEmpty());
                                    ___ce___ = ___w___92;
                                    ___w___84.longPress(___w___92.get(0), 1, 2);
                                    ___w___84.longPress(1, 2, java.time.Duration.ofMillis(3));
                                    ___w___84.release();
                                    ___w___84.perform();
                                    if (get___d___() instanceof io.appium.java_client.ios.IOSDriver) {
                                        ((io.appium.java_client.ios.IOSDriver) get___d___()).shake();
                                    }
                                    System.out.println(getProviderDataValue("abt", true));
                                    int ___w___93 = getProviderTestDataMap("abc").size();
                                    set__provname__("abc");

                                    for (int ___w___94 = 0; ___w___94 < ___w___93; ___w___94++) {
                                        set__provpos__("abc", ___w___94);
                                        List<WebElement> ___w___95 = By.id(evaluate("$abt")).findElements(___sc___1);
                                        Assert.assertTrue(
                                                "Element not found by selector id@'$abt' at line number 112 ",
                                                ___w___95 != null && !___w___95.isEmpty());
                                        ___ce___ = ___w___95;
                                        ___w___95.get(0).click();
                                    }
                                    rem__provname__("abc");

                                    try {
                                        Thread.sleep(2000);
                                    } catch (Exception ___e___24) {
                                    }
                                    get___d___().switchTo().alert().accept();

                                    try {
                                        Thread.sleep(5000);
                                    } catch (Exception ___e___25) {
                                    }
                                    get___d___().switchTo().alert().accept();

                                    try {
                                        Thread.sleep(2000);
                                    } catch (Exception ___e___26) {
                                    }

                                    Object pat = null;
                                    List<String> ___a___1 = new java.util.ArrayList<String>();
                                    ___a___1.add("createPatient");
                                    List<List<String>> ___a___2 = new java.util.ArrayList<List<String>>();
                                    pat =
                                            pluginize(
                                                    "api", "com.gatf.selenium.plugins.ApiPlugin@api", ___a___1, ___a___2);

                                    Object mrno = null;
                                    List<String> ___a___3 = new java.util.ArrayList<String>();
                                    ___a___3.add("$v{pat}");
                                    ___a___3.add("mrno");
                                    List<List<String>> ___a___4 = new java.util.ArrayList<List<String>>();
                                    mrno =
                                            pluginize(
                                                    "jsonread",
                                                    "com.gatf.selenium.plugins.JsonPlugin@read",
                                                    ___a___3,
                                                    ___a___4);
                                    ___cxt___add_param__("mrno", mrno);

                                    Object o = null;
                                    List<String> ___a___5 = new java.util.ArrayList<String>();
                                    ___a___5.add("{\"a\": {\"b\": 1}}");
                                    List<List<String>> ___a___6 = new java.util.ArrayList<List<String>>();
                                    o =
                                            pluginize(
                                                    "jsonread",
                                                    "com.gatf.selenium.plugins.JsonPlugin@read",
                                                    ___a___5,
                                                    ___a___6);
                                    List<String> ___a___7 = new java.util.ArrayList<String>();
                                    ___a___7.add("$v{o}");
                                    ___a___7.add("file.json");
                                    List<List<String>> ___a___8 = new java.util.ArrayList<List<String>>();
                                    pluginize(
                                            "jsonwrite",
                                            "com.gatf.selenium.plugins.JsonPlugin@write",
                                            ___a___7,
                                            ___a___8);

                                    Object op = null;
                                    List<String> ___a___9 = new java.util.ArrayList<String>();
                                    ___a___9.add("$v{o}");
                                    ___a___9.add("a.b");
                                    List<List<String>> ___a___10 = new java.util.ArrayList<List<String>>();
                                    op =
                                            pluginize(
                                                    "jsonpath",
                                                    "com.gatf.selenium.plugins.JsonPlugin@path",
                                                    ___a___9,
                                                    ___a___10);

                                    Object ox = null;
                                    List<String> ___a___11 = new java.util.ArrayList<String>();
                                    ___a___11.add("<a><b>1</b></a>");
                                    List<List<String>> ___a___12 = new java.util.ArrayList<List<String>>();
                                    ox =
                                            pluginize(
                                                    "xmlread",
                                                    "com.gatf.selenium.plugins.XmlPlugin@read",
                                                    ___a___11,
                                                    ___a___12);
                                    List<String> ___a___13 = new java.util.ArrayList<String>();
                                    ___a___13.add("$v{ox}");
                                    ___a___13.add("file.xml");
                                    List<List<String>> ___a___14 = new java.util.ArrayList<List<String>>();
                                    pluginize(
                                            "xmlwrite",
                                            "com.gatf.selenium.plugins.XmlPlugin@write",
                                            ___a___13,
                                            ___a___14);

                                    Object oxp = null;
                                    List<String> ___a___15 = new java.util.ArrayList<String>();
                                    ___a___15.add("$v{ox}");
                                    ___a___15.add("a/b");
                                    List<List<String>> ___a___16 = new java.util.ArrayList<List<String>>();
                                    oxp =
                                            pluginize(
                                                    "xmlpath",
                                                    "com.gatf.selenium.plugins.XmlPlugin@path",
                                                    ___a___15,
                                                    ___a___16);

                                    Object myvar = null;
                                    List<String> ___a___17 = new java.util.ArrayList<String>();
                                    ___a___17.add("get");
                                    ___a___17.add("http://abc.com");
                                    List<List<String>> ___a___18 = new java.util.ArrayList<List<String>>();
                                    List<String> ___a___19 = new java.util.ArrayList<String>();
                                    ___a___19.add("header1=a");
                                    ___a___19.add("header2=k");
                                    ___a___19.add("header3=a");
                                    ___a___18.add(___a___19);
                                    List<String> ___a___20 = new java.util.ArrayList<String>();
                                    ___a___20.add("content");
                                    ___a___18.add(___a___20);
                                    myvar =
                                            pluginize(
                                                    "curl",
                                                    "com.gatf.selenium.plugins.CurlPlugin@execute",
                                                    ___a___17,
                                                    ___a___18);

                                    Object myvarvar = null;
                                    List<String> ___a___21 = new java.util.ArrayList<String>();
                                    ___a___21.add("$v{myvar}");
                                    ___a___21.add("out.x.y.z");
                                    List<List<String>> ___a___22 = new java.util.ArrayList<List<String>>();
                                    myvarvar =
                                            pluginize(
                                                    "jsonpath",
                                                    "com.gatf.selenium.plugins.JsonPlugin@path",
                                                    ___a___21,
                                                    ___a___22);
                                    List<String> ___a___23 = new java.util.ArrayList<String>();
                                    ___a___23.add("yourapitestcase");
                                    List<List<String>> ___a___24 = new java.util.ArrayList<List<String>>();
                                    pluginize("api", "com.gatf.selenium.plugins.ApiPlugin@api", ___a___23, ___a___24);

                                    boolean ___ifcnt___9 = false;
                                    try {
                                        List<WebElement> ___w___96 = By.xpath(evaluate("ddd")).findElements(___sc___1);
                                        Assert.assertTrue(
                                                "Element not found by selector xpath@'ddd' at line number 138 ",
                                                ___w___96 != null && !___w___96.isEmpty());
                                        ___ce___ = ___w___96;
                                        System.out.println("if");

                                        ___ifcnt___9 = true;

                                    } catch (AssertionError ___e___28) {
                                        ___ifcnt___9 = false;
                                    } catch (Exception ___e___27) {
                                        System.out.println(___e___27.getMessage());
                                    }
                                    if (!___ifcnt___9) {
                                        System.out.println("else");
                                    }
                                    int ___w___97 = getProviderTestDataMap("aa").size();
                                    set__provname__("aa");

                                    for (int ___w___98 = 0; ___w___98 < ___w___97; ___w___98++) {
                                        set__provpos__("aa", ___w___98);
                                        boolean ___ifcnt___10 = false;
                                        try {
                                            List<WebElement> ___w___99 =
                                                    By.xpath(evaluate("ddd")).findElements(___sc___1);
                                            Assert.assertTrue(
                                                    "Element not found by selector xpath@'ddd' at line number 148 ",
                                                    ___w___99 != null && !___w___99.isEmpty());
                                            ___ce___ = ___w___99;
                                            System.out.println("aaif");

                                            ___ifcnt___10 = true;

                                        } catch (AssertionError ___e___30) {
                                            ___ifcnt___10 = false;
                                        } catch (Exception ___e___29) {
                                            System.out.println(___e___29.getMessage());
                                        }
                                        if (!___ifcnt___10) {
                                            System.out.println("aaelse");
                                        }
                                    }
                                    rem__provname__("aa");

                                    List<WebElement> ___w___100 =
                                            By.cssSelector(evaluate("div#left-panel>nav>div>ul>li"))
                                            .findElements(___sc___1);
                                    Assert.assertTrue(
                                            "Element not found by selector css@'div#left-panel>nav>div>ul>li' at line number 157 ",
                                            ___w___100 != null && !___w___100.isEmpty());
                                    ___ce___ = ___w___100;
                                    boolean ___c___9 = ___w___100 != null && ___w___100.size() > 0 && true;
                                    if (!(___c___9 && 2 == ___w___100.size())) ___c___9 = false;
                                    else {
                                        final WebElement ___w___101 = ___w___100.get(0);
                                        ___c___9 &= evaluate("DashBoard").equals(___w___101.getText());
                                        final WebElement ___w___102 = ___w___100.get(1);
                                        ___c___9 &= evaluate("DashBoard1").equals(___w___102.getText());
                                    }
                                    List<WebElement> ___w___103 =
                                            By.cssSelector(evaluate("div#left-panel>nav>div>ul>li"))
                                            .findElements(___sc___1);
                                    Assert.assertTrue(
                                            "Element not found by selector css@'div#left-panel>nav>div>ul>li' at line number 162 ",
                                            ___w___103 != null && !___w___103.isEmpty());
                                    ___ce___ = ___w___103;
                                    ___w___103.get(0).sendKeys(evaluate("ghjg"));
                                    boolean ___ifcnt___11 = false;
                                    try {

                                        boolean ___c___10 = true;
                                        ___c___10 =
                                                evaluate("#set($__v__R1_O_P2__ = ${abc}==\"1\")\n${__v__R1_O_P2__}")
                                                .equalsIgnoreCase("true");
                                        System.out.println("if");

                                        String mydate =
                                                new java.text.SimpleDateFormat("MM/DD/YYYY").format(new java.util.Date());

                                        String mydate1 =
                                                new java.text.SimpleDateFormat("MM/DD/YYYY").format(new java.util.Date());

                                        ___cxt___add_param__("mydate1", mydate1);
                                        List<WebElement> ___w___105 =
                                                By.xpath("//*[contains(text(), '//sdsd')]").findElements(___sc___1);
                                        Assert.assertTrue(
                                                "Element not found by selector text@'//sdsd' at line number 171 ",
                                                ___w___105 != null && !___w___105.isEmpty());
                                        ___ce___ = ___w___105;
                                        boolean ___c___11 = true;
                                        for (final WebElement ___w___106 : ___w___105) {
                                            ___c___11 &= evaluate("${mydate1}").compareTo(___w___106.getText()) > 0;
                                        }
                                        for (final WebElement ___w___107 : ___w___105) {
                                            ___w___107.click();
                                        }
                                        List<WebElement> ___w___108 =
                                                By.xpath(evaluate("//sdsd")).findElements(___sc___1);
                                        Assert.assertTrue(
                                                "Element not found by selector xpath@'//sdsd' at line number 172 ",
                                                ___w___108 != null && !___w___108.isEmpty());
                                        ___ce___ = ___w___108;
                                        ___w___108.get(0).click();

                                        ___ifcnt___11 = true;

                                    } catch (AssertionError ___e___32) {
                                        ___ifcnt___11 = false;
                                    } catch (Exception ___e___31) {
                                        System.out.println(___e___31.getMessage());
                                    }

                                    List<WebElement> ___w___109 =
                                            By.className(evaluate("welcome_text")).findElements(___sc___1);
                                    Assert.assertTrue(
                                            "Element not found by selector class@'welcome_text' at line number 174 ",
                                            ___w___109 != null && !___w___109.isEmpty());
                                    ___ce___ = ___w___109;
                                    final Object[] ___w___110 = new Object[2];
                                    final WebDriver ___sc___10 = (WebDriver) ___sc___1;
                                    try {
                                        (new WebDriverWait(___sc___10, 10000))
                                        .until(
                                                new Function<WebDriver, Boolean>() {
                                                    public Boolean apply(WebDriver input) {

                                                        List<WebElement> ___ce___ = null;
                                                        List<WebElement> ___w___111 =
                                                                By.id(evaluate("signOut")).findElements(___sc___10);
                                                        if (___w___111 == null || ___w___111.isEmpty()) return false;
                                                        ___w___110[0] = ___w___111;

                                                        ___w___110[1] = ___ce___;

                                                        return true;
                                                    }

                                                    public String toString() {
                                                        return "";
                                                    }
                                                });
                                    } catch (org.openqa.selenium.TimeoutException ___e___33) {
                                        throw new RuntimeException(
                                                "Element not found by selector id@'signOut' at line number 174 ",
                                                ___e___33);
                                    }
                                    ___ce___ = (List<WebElement>) ___w___110[1];

                                    Actions ___w___112 = new Actions(get___d___());
                                    ___w___112
                                    .moveToElement(___w___109.get(0))
                                    .click(((List<WebElement>) ___w___110[0]).get(0))
                                    .perform();
                                    final Object[] ___w___113 = new Object[2];
                                    final WebDriver ___sc___11 = (WebDriver) ___sc___1;
                                    try {
                                        (new WebDriverWait(___sc___11, 30))
                                        .until(
                                                new Function<WebDriver, Boolean>() {
                                                    public Boolean apply(WebDriver input) {

                                                        List<WebElement> ___ce___ = null;
                                                        List<WebElement> ___w___114 =
                                                                By.id(evaluate("UserName")).findElements(___sc___11);
                                                        if (___w___114 == null || ___w___114.isEmpty()) return false;
                                                        Actions ___w___115 = new Actions(get___d___());
                                                        ___w___115.moveToElement(___w___114.get(0));
                                                        ___w___115.sendKeys(evaluate("test"));
                                                        List<WebElement> ___w___116 =
                                                                By.id(evaluate("sdsadsa")).findElements(___sc___11);
                                                        Assert.assertTrue(
                                                                "Element not found by selector id@'sdsadsa' at line number 175 ",
                                                                ___w___116 != null && !___w___116.isEmpty());
                                                        ___ce___ = ___w___116;
                                                        ___w___115.moveToElement(___w___116.get(0));
                                                        ___w___115.click();
                                                        ___w___115.clickAndHold();
                                                        ___w___115.release();
                                                        ___w___115.keyUp(Keys.getKeyFromUnicode('a'));
                                                        ___w___115.keyDown(Keys.getKeyFromUnicode('b'));
                                                        ___w___115.moveByOffset(1, 2);
                                                        ___w___115.build().perform();
                                                        ___w___113[0] = ___w___114;

                                                        ___w___113[1] = ___ce___;

                                                        return true;
                                                    }

                                                    public String toString() {
                                                        return "";
                                                    }
                                                });
                                    } catch (org.openqa.selenium.TimeoutException ___e___34) {
                                        throw new RuntimeException(
                                                "Element not found by selector id@'UserName' at line number 175 ",
                                                ___e___34);
                                    }
                                    ___ce___ = (List<WebElement>) ___w___113[1];

                                    List<WebElement> ___w___117 = By.id(evaluate("ptag")).findElements(___sc___1);
                                    Assert.assertTrue(
                                            "Element not found by selector id@'ptag' at line number 176 ",
                                            ___w___117 != null && !___w___117.isEmpty());
                                    ___ce___ = ___w___117;
                                    Actions ___w___118 = new Actions(get___d___());
                                    ___w___118.moveToElement(___w___117.get(0));
                                    ___w___118.doubleClick();
                                    ___w___118.build().perform();
                                    List<WebElement> ___w___119 = By.id(evaluate("demo")).findElements(___sc___1);
                                    Assert.assertTrue(
                                            "Element not found by selector id@'demo' at line number 177 ",
                                            ___w___119 != null && !___w___119.isEmpty());
                                    ___ce___ = ___w___119;
                                    boolean ___c___12 = true;
                                    for (final WebElement ___w___120 : ___w___119) {
                                        ___c___12 &= evaluate("Hello World").compareTo(___w___120.getText()) == 0;
                                    }
                                    List<WebElement> ___w___121 = By.id(evaluate("ptag")).findElements(___sc___1);
                                    Assert.assertTrue(
                                            "Element not found by selector id@'ptag' at line number 178 ",
                                            ___w___121 != null && !___w___121.isEmpty());
                                    ___ce___ = ___w___121;
                                    Actions ___w___122 = new Actions(get___d___());
                                    ___w___122.moveToElement(___w___121.get(0));
                                    ___w___122.click();
                                    ___w___122.build().perform();
                                    List<WebElement> ___w___123 = By.id(evaluate("ptag")).findElements(___sc___1);
                                    Assert.assertTrue(
                                            "Element not found by selector id@'ptag' at line number 179 ",
                                            ___w___123 != null && !___w___123.isEmpty());
                                    ___ce___ = ___w___123;
                                    Actions ___w___124 = new Actions(get___d___());
                                    for (final WebElement ___w___125 : ___w___123) {
                                        ___w___124.moveToElement(___w___125).doubleClick().perform();
                                    }
                                    List<WebElement> ___w___126 = By.id(evaluate("demo")).findElements(___sc___1);
                                    Assert.assertTrue(
                                            "Element not found by selector id@'demo' at line number 180 ",
                                            ___w___126 != null && !___w___126.isEmpty());
                                    ___ce___ = ___w___126;
                                    boolean ___c___13 = true;
                                    for (final WebElement ___w___127 : ___w___126) {
                                        ___c___13 &= evaluate("Hello World").compareTo(___w___127.getText()) == 0;
                                    }
                                    List<WebElement> ___w___128 = By.id(evaluate("ptag")).findElements(___sc___1);
                                    Assert.assertTrue(
                                            "Element not found by selector id@'ptag' at line number 181 ",
                                            ___w___128 != null && !___w___128.isEmpty());
                                    ___ce___ = ___w___128;
                                    Actions ___w___129 = new Actions(get___d___());
                                    for (final WebElement ___w___130 : ___w___128) {
                                        ___w___129.moveToElement(___w___130).doubleClick().perform();
                                    }
                                    List<WebElement> ___w___131 = By.id(evaluate("demo")).findElements(___sc___1);
                                    Assert.assertTrue(
                                            "Element not found by selector id@'demo' at line number 182 ",
                                            ___w___131 != null && !___w___131.isEmpty());
                                    ___ce___ = ___w___131;
                                    boolean ___c___14 = true;
                                    for (final WebElement ___w___132 : ___w___131) {
                                        ___c___14 &= evaluate("Hello World").compareTo(___w___132.getText()) == 0;
                                    }

                                    ___ifcnt___6 = true;

                                } catch (AssertionError ___e___35) {
                                    ___ifcnt___6 = false;
                                } catch (Exception ___e___9) {
                                    System.out.println(___e___9.getMessage());
                                }

                                ___ifcnt___5 = true;

                            } catch (AssertionError ___e___36) {
                                ___ifcnt___5 = false;
                            } catch (Exception ___e___8) {
                                System.out.println(___e___8.getMessage());
                            }

                            ___ifcnt___4 = true;

                        } catch (AssertionError ___e___37) {
                            ___ifcnt___4 = false;
                        } catch (Exception ___e___7) {
                            System.out.println(___e___7.getMessage());
                        }

                        ___ifcnt___3 = true;

                    } catch (AssertionError ___e___38) {
                        ___ifcnt___3 = false;
                    } catch (Exception ___e___6) {
                        System.out.println(___e___6.getMessage());
                    }

                    ___ifcnt___2 = true;

                } catch (AssertionError ___e___39) {
                    ___ifcnt___2 = false;
                } catch (Exception ___e___5) {
                    System.out.println(___e___5.getMessage());
                }

                ___ifcnt___1 = true;

            } catch (AssertionError ___e___40) {
                ___ifcnt___1 = false;
            } catch (Exception ___e___4) {
                System.out.println(___e___4.getMessage());
            }

            pushResult(new SeleniumTestResult(get___d___(), this, ___lp___));
        } catch (Throwable ___e___41) {
            pushResult(new SeleniumTestResult(get___d___(), this, ___e___41, ___lp___));
        }
    }
    @Override
    public List<SeleniumTestSession> execute(LoggingPreferences ___lp___) throws Exception {
        // TODO Auto-generated method stub
        return null;
    }
}
