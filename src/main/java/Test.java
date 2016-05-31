/*
    Copyright 2013-2016, Sumeet Chhetri
    
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

import java.io.File;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.logging.Logs;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.gatf.selenium.SeleniumException;
import com.google.common.base.Function;

public class Test {

    private WebDriver ___d___ = null;
    public void quit() {
    if(___d___!=null)___d___.quit();
    }
    public static void main(String[] args) throws Exception {
        new Test().main1(args);
    }
    public void main1(String[] args) throws Exception {
        System.setProperty("webdriver.chrome.driver", "F:\\Laptop_Backup\\Development\\selenium-drivers\\chromedriver.exe");
    //public Logs execute(AcceptanceTestContext ___cxt___, LoggingPreferences ___lp___) throws Exception {
    DesiredCapabilities ___dc___ = DesiredCapabilities.chrome();
    //___dc___.setCapability(CapabilityType.LOGGING_PREFS, ___lp___);
    ___d___ = new org.openqa.selenium.chrome.ChromeDriver(___dc___);
    SearchContext ___sc___1 = ___d___;
    WebDriver ___cw___ = ___d___;
    WebDriver ___ocw___ = ___cw___;
    try
    {
    ___cw___.navigate().to("https://portaldev.symplast.com");
    final WebDriver ___sc___2 = (WebDriver)___sc___1;
    (new WebDriverWait(___cw___, 30)).until(
    new Function<WebDriver, Boolean>(){
    public Boolean apply(WebDriver input) {
    List<WebElement>  ___w___1 = By.id("UserName").findElements(___sc___2);
    boolean ___c___1 = ___w___1!=null && !___w___1.isEmpty();
    return ___c___1;
    }
    public String toString() {
    return "";
    }
    });
    List<WebElement>  ___w___2 = By.id("UserName").findElements(___sc___1);
    boolean ___c___2 = ___w___2!=null && !___w___2.isEmpty();
    Assert.assertTrue(___c___2);
    ___w___2.get(0).sendKeys("rahulb");
    List<WebElement>  ___w___3 = By.id("Password").findElements(___sc___1);
    boolean ___c___3 = ___w___3!=null && !___w___3.isEmpty();
    Assert.assertTrue(___c___3);
    ___w___3.get(0).sendKeys("Ohum@123");
    List<WebElement>  ___w___4 = By.id("Location").findElements(___sc___1);
    boolean ___c___4 = ___w___4!=null && !___w___4.isEmpty();
    Assert.assertTrue(___c___4);
    Select ___w___5 = new Select(___w___4.get(0));
    ___w___5.selectByIndex(4);
    List<WebElement>  ___w___6 = By.cssSelector("input[type='submit']").findElements(___sc___1);
    boolean ___c___5 = ___w___6!=null && !___w___6.isEmpty();
    Assert.assertTrue(___c___5);
    ___w___6.get(0).click();
    final WebDriver ___sc___3 = (WebDriver)___sc___1;
    (new WebDriverWait(___cw___, 20)).until(
    new Function<WebDriver, Boolean>(){
    public Boolean apply(WebDriver input) {
    List<WebElement>  ___w___7 = By.className("welcome_text").findElements(___sc___3);
    boolean ___c___6 = ___w___7!=null && !___w___7.isEmpty();
    return ___c___6;
    }
    public String toString() {
    return "";
    }
    });
    List<WebElement>  ___w___8 = By.className("welcome_text").findElements(___sc___1);
    boolean ___c___7 = ___w___8!=null && !___w___8.isEmpty();
    Assert.assertTrue(___c___7);
    Actions ___w___9 = new Actions(___d___);
    ___w___9.moveToElement(___w___8.get(0)).perform();
    final WebDriver ___sc___4 = (WebDriver)___sc___1;
    (new WebDriverWait(___cw___, 10)).until(
    new Function<WebDriver, Boolean>(){
    public Boolean apply(WebDriver input) {
    List<WebElement>  ___w___10 = By.xpath("//*[contains(text(), 'Sign Out')]").findElements(___sc___4);
    boolean ___c___8 = ___w___10!=null && !___w___10.isEmpty();
    return ___c___8;
    }
    public String toString() {
    return "";
    }
    });
    ___cw___.navigate().refresh();
    File ___sr___1 = ((TakesScreenshot)___ocw___).getScreenshotAs(OutputType.FILE);
    FileUtils.copyFile(___sr___1, new File("test.png"));
    final WebDriver ___sc___5 = (WebDriver)___sc___1;
    (new WebDriverWait(___cw___, 20)).until(
    new Function<WebDriver, Boolean>(){
    public Boolean apply(WebDriver input) {
    List<WebElement>  ___w___11 = By.className("welcome_text").findElements(___sc___5);
    boolean ___c___9 = ___w___11!=null && !___w___11.isEmpty();
    return ___c___9;
    }
    public String toString() {
    return "";
    }
    });
    List<WebElement>  ___w___12 = By.className("welcome_text").findElements(___sc___1);
    boolean ___c___10 = ___w___12!=null && !___w___12.isEmpty();
    Assert.assertTrue(___c___10);
    Actions ___w___14 = new Actions(___d___);
    ___w___14.moveToElement(___w___12.get(0)).perform();
    
    List<WebElement>  ___w___13 = By.xpath("//*[contains(text(), 'Sign Out')]").findElements(___sc___1);
    boolean ___c___11 = ___w___13!=null && !___w___13.isEmpty();
    Assert.assertTrue(___c___11);
    //___w___13.get(0).click();
    Logs ___logs___ = ___d___.manage().logs();
    }
    catch(Throwable c)
    {
    throw new SeleniumException();
    }}
}
