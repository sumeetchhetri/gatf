package com.gatf.selenium;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import org.openqa.selenium.By;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;

import com.gatf.executor.core.AcceptanceTestContext;

import junit.framework.Assert;

public class Test extends SeleniumTest implements Serializable {
    public Test(AcceptanceTestContext ___cxt___, int index) {
        super("test1.sel", ___cxt___, index);
    }

    public void close() {
        if (get___d___() != null) get___d___().close();
      }

    public void quit() {
        if (get___d___() != null) get___d___().quit();
      }

      public SeleniumTest copy(AcceptanceTestContext ctx, int index) {
        return new Test(ctx, index);
      }

      public void setupDriverchrome(LoggingPreferences ___lp___) throws Exception {
        DesiredCapabilities ___dc___ = DesiredCapabilities.chrome();
        ___dc___.setCapability(CapabilityType.LOGGING_PREFS, ___lp___);
        set___d___(new org.openqa.selenium.chrome.ChromeDriver(___dc___));
        setBrowserName("chrome");
      }

      @SuppressWarnings("unchecked")
      public Map<String, SeleniumResult> execute(LoggingPreferences ___lp___) throws Exception {
        addTest("chrome");
        startTest();
        quit();
        setupDriverchrome(___lp___);
        _execute(___lp___);
        return get__result__();
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
              List<WebElement> ___w___1 = By.xpath(evaluate("sds")).findElements(___sc___1);
              Assert.assertTrue(
                  "Element not found by selector xpath@'sds' at line number 2 ",
                  ___w___1 != null && !___w___1.isEmpty());
              ((io.appium.java_client.TouchShortcuts) get___d___()).zoom(___w___1.get(0));

              ((io.appium.java_client.TouchShortcuts) get___d___()).zoom(1, 2);
              List<WebElement> ___w___2 = By.xpath(evaluate("sds")).findElements(___sc___1);
              Assert.assertTrue(
                  "Element not found by selector xpath@'sds' at line number 4 ",
                  ___w___2 != null && !___w___2.isEmpty());
              ((io.appium.java_client.TouchShortcuts) get___d___()).pinch(___w___2.get(0));

              ((io.appium.java_client.TouchShortcuts) get___d___()).pinch(1, 2);

              ((io.appium.java_client.TouchShortcuts) get___d___()).tap(1, 2, 3, 4);
              List<WebElement> ___w___3 = By.xpath(evaluate("sds")).findElements(___sc___1);
              Assert.assertTrue(
                  "Element not found by selector xpath@'sds' at line number 7 ",
                  ___w___3 != null && !___w___3.isEmpty());
              ((io.appium.java_client.TouchShortcuts) get___d___()).tap(1, ___w___3.get(0), 2);

              ((io.appium.java_client.TouchShortcuts) get___d___()).swipe(1, 2, 3, 4, 5);
              org.openqa.selenium.ScreenOrientation ___w___4 =
                  ((org.openqa.selenium.Rotatable) get___d___()).getOrientation();
              if (___w___4.value().equals(org.openqa.selenium.ScreenOrientation.LANDSCAPE.value())) {
                ((org.openqa.selenium.Rotatable) get___d___())
                    .rotate(org.openqa.selenium.ScreenOrientation.PORTRAIT);
              } else {
                ((org.openqa.selenium.Rotatable) get___d___())
                    .rotate(org.openqa.selenium.ScreenOrientation.LANDSCAPE);
              }
              ((io.appium.java_client.DeviceActionShortcuts) get___d___()).hideKeyboard();

              io.appium.java_client.TouchAction ___w___5 =
                  new io.appium.java_client.TouchAction((io.appium.java_client.MobileDriver) get___d___());
              List<WebElement> ___w___6 = By.xpath(evaluate("Sd")).findElements(___sc___1);
              Assert.assertTrue(
                  "Element not found by selector xpath@'Sd' at line number 11 ",
                  ___w___6 != null && !___w___6.isEmpty());
              ___w___5.press(___w___6.get(0), 1, 2);
              ___w___5.press(1, 2);
              List<WebElement> ___w___7 = By.xpath(evaluate("Sd")).findElements(___sc___1);
              Assert.assertTrue(
                  "Element not found by selector xpath@'Sd' at line number 11 ",
                  ___w___7 != null && !___w___7.isEmpty());
              ___w___5.press(___w___7.get(0));
              List<WebElement> ___w___8 = By.xpath(evaluate("Sd")).findElements(___sc___1);
              Assert.assertTrue(
                  "Element not found by selector xpath@'Sd' at line number 11 ",
                  ___w___8 != null && !___w___8.isEmpty());
              ___w___5.tap(___w___8.get(0), 1, 2);
              ___w___5.tap(1, 2);
              List<WebElement> ___w___9 = By.xpath(evaluate("Sd")).findElements(___sc___1);
              Assert.assertTrue(
                  "Element not found by selector xpath@'Sd' at line number 11 ",
                  ___w___9 != null && !___w___9.isEmpty());
              ___w___5.tap(___w___9.get(0));
              List<WebElement> ___w___10 = By.xpath(evaluate("Sd")).findElements(___sc___1);
              Assert.assertTrue(
                  "Element not found by selector xpath@'Sd' at line number 11 ",
                  ___w___10 != null && !___w___10.isEmpty());
              ___w___5.moveTo(___w___10.get(0), 1, 2);
              ___w___5.moveTo(1, 2);
              List<WebElement> ___w___11 = By.xpath(evaluate("Sd")).findElements(___sc___1);
              Assert.assertTrue(
                  "Element not found by selector xpath@'Sd' at line number 11 ",
                  ___w___11 != null && !___w___11.isEmpty());
              ___w___5.moveTo(___w___11.get(0));
              List<WebElement> ___w___12 = By.xpath(evaluate("Sd")).findElements(___sc___1);
              Assert.assertTrue(
                  "Element not found by selector xpath@'Sd' at line number 11 ",
                  ___w___12 != null && !___w___12.isEmpty());
              ___w___5.longPress(___w___12.get(0), 1, 2, 3);
              List<WebElement> ___w___13 = By.xpath(evaluate("Sd")).findElements(___sc___1);
              Assert.assertTrue(
                  "Element not found by selector xpath@'Sd' at line number 11 ",
                  ___w___13 != null && !___w___13.isEmpty());
              ___w___5.longPress(___w___13.get(0), 1, 2);
              ___w___5.longPress(1, 2, 3);
              ___w___5.wait();
              ___w___5.wait(1);
              ___w___5.release();
              ___w___5.perform();
              pushResult(new SeleniumTestResult(get___d___(), this, ___lp___));
            } catch (Throwable ___e___1) {
              pushResult(new SeleniumTestResult(get___d___(), this, ___e___1, ___lp___));
            }
          }
        }

