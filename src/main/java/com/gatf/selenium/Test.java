package com.gatf.selenium;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import org.openqa.selenium.By;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.selenium.SeleniumTest;
import com.google.common.base.Function;

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
          ___cw___.navigate().to(evaluate("https://abc.com/"));
          List<WebElement> ___w___1 = By.id(evaluate("Location")).findElements(___sc___1);
          Assert.assertTrue(
              "Element not found by selector id@'Location' at line number 3 ",
              ___w___1 != null && !___w___1.isEmpty());
          Select ___w___2 = new Select(___w___1.get(0));
          ___w___2.selectByIndex(Integer.parseInt(evaluate("4")));
          int ___w___3 = getProviderTestDataMap("auth").size();
          set__provname__("auth");

          for (int ___w___4 = 0; ___w___4 < ___w___3; ___w___4++) {
            set__provpos__("auth", ___w___4);
            __st__1(___sc___1, ___lp___);
          }
          rem__provname__("auth");

          List<WebElement> ___w___5 = By.id(evaluate("ddasda")).findElements(___sc___1);
          Assert.assertTrue(
              "Element not found by selector id@'ddasda' at line number 14 ",
              ___w___5 != null && !___w___5.isEmpty());
          if (true) {

            int ___itr___1 = 0;
            ___flp___1:
            for (final WebElement ___w___6 : ___w___5) {
              final SearchContext ___pc___1 = ___w___6;
              @SuppressWarnings("serial")
              List<WebElement> ___w___7 =
                  new java.util.ArrayList<WebElement>() {
                    {
                      add(___w___6);
                    }
                  };
              Integer ___i___1 =
                  new Functor<SearchContext, Integer>() {
                    @Override
                    public Integer f(SearchContext ___sc___1, SearchContext ___pc___1) {
                      try {
                        List<WebElement> ___w___8 = By.id(evaluate("dd")).findElements(___sc___1);
                        Assert.assertTrue(
                            "Element not found by selector id@'dd' at line number 16 ",
                            ___w___8 != null && !___w___8.isEmpty());

                        if (true) return 3;

                        return true ? 1 : 2;
                      } catch (AssertionError ___e___2) {
                      } catch (Exception ___e___1) {
                        System.out.println(___e___1.getMessage());
                      }

                      return 2;
                    }
                  }.f(___sc___1, ___pc___1);
              if (___i___1 != 2) {
                if (___i___1 == 3) break;
                if (___i___1 == 4) continue;
              }
              ___itr___1++;
            }
          }
          pushResult(new SeleniumTestResult(get___d___(), this, ___lp___));
        } catch (Throwable ___e___3) {
          pushResult(new SeleniumTestResult(get___d___(), this, ___e___3, ___lp___));
        }
      }

      void __st__1(SearchContext ___sc___1, LoggingPreferences ___lp___) {

        set__subtestname__("Subtest 1");
        try {
          final Object[] ___w___9 = new Object[1];
          final WebDriver ___sc___2 = (WebDriver) ___sc___1;
          try {
            (new WebDriverWait(___sc___2, 30))
                .until(
                    new Function<WebDriver, Boolean>() {
                      public Boolean apply(WebDriver input) {
                        List<WebElement> ___w___10 =
                            By.id(evaluate("UserName")).findElements(___sc___2);
                        if (___w___10 == null || ___w___10.isEmpty()) return false;
                        ___w___10.get(0).sendKeys(evaluate("$user"));
                        ___w___9[0] = ___w___10;

                        return true;
                      }

                      public String toString() {
                        return "";
                      }
                    });
          } catch (org.openqa.selenium.TimeoutException ___e___4) {
            throw new RuntimeException(
                "Element not found by selector id@'UserName' at line number 8 ", ___e___4);
          }
          List<WebElement> ___w___11 = By.id(evaluate("Password")).findElements(___sc___1);
          Assert.assertTrue(
              "Element not found by selector id@'Password' at line number 9 ",
              ___w___11 != null && !___w___11.isEmpty());
          ___w___11.get(0).sendKeys(evaluate("$pwd"));
          List<WebElement> ___w___12 =
              By.cssSelector(evaluate("input[type='submit']")).findElements(___sc___1);
          Assert.assertTrue(
              "Element not found by selector css@'input[type='submit']' at line number 10 ",
              ___w___12 != null && !___w___12.isEmpty());
          ___w___12.get(0).click();

          try {
            Thread.sleep(100);
          } catch (Exception ___e___5) {
          }

          pushResult(new SeleniumTestResult(get___d___(), this, ___lp___));
        } catch (Throwable ___e___6) {
          pushResult(new SeleniumTestResult(get___d___(), this, ___e___6, ___lp___));
        } finally {
          set__subtestname__(null);
        }
      }
    }

