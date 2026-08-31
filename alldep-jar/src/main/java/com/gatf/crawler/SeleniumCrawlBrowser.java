/*
    Copyright 2013-2026, Sumeet Chhetri

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/
package com.gatf.crawler;

import java.net.URL;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;

import com.gatf.selenium.ChromeForTesting;

/**
 * {@link SpaCrawler.Browser} over Selenium: Chrome (headless by default). Same acquisition rules as test
 * runs — SELENIUM_REMOTE_URL → Grid; GATF_CHROME_BINARY / -Dgatf.chrome.binary → a binary path, or
 * {@code headless-shell} for the auto-provisioned Chrome-for-Testing shell + matching chromedriver;
 * otherwise Selenium Manager.
 */
public class SeleniumCrawlBrowser implements SpaCrawler.Browser {

    private final WebDriver d;

    public SeleniumCrawlBrowser(WebDriver d) {
        this.d = d;
    }

    public static SeleniumCrawlBrowser create(boolean headless) throws Exception {
        ChromeOptions o = new ChromeOptions();
        if (headless) o.addArguments("--headless=new");
        o.addArguments("--window-size=1366,900", "--disable-gpu");
        String grid = System.getenv("SELENIUM_REMOTE_URL");
        if (grid != null && !grid.trim().isEmpty()) return new SeleniumCrawlBrowser(new RemoteWebDriver(new URL(grid.trim()), o));
        String bin = System.getenv("GATF_CHROME_BINARY");
        if (bin == null || bin.trim().isEmpty()) bin = System.getProperty("gatf.chrome.binary");
        if (ChromeForTesting.isHeadlessShellRequest(bin)) {
            ChromeForTesting.Install cft = ChromeForTesting.headlessShell();
            o.setBinary(cft.browser.getAbsolutePath());
            return new SeleniumCrawlBrowser(new ChromeDriver(new ChromeDriverService.Builder().usingDriverExecutable(cft.driver).build(), o));
        }
        if (bin != null && !bin.trim().isEmpty()) o.setBinary(bin.trim());
        return new SeleniumCrawlBrowser(new ChromeDriver(o));
    }

    @Override
    public void get(String url) {
        d.get(url);
    }

    @Override
    public Object js(String script, Object... args) {
        return ((JavascriptExecutor) d).executeScript(script, args);
    }

    @Override
    public String currentUrl() {
        return d.getCurrentUrl();
    }

    @Override
    public void quit() {
        try {
            d.quit();
        } catch (Exception ignore) {
        }
    }
}
