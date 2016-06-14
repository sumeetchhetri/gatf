import java.io.Serializable;
import com.gatf.selenium.SeleniumException;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.logging.Logs;
import java.util.List;
import java.util.Map;

import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.OutputType;
import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.selenium.SeleniumTest;
import com.gatf.selenium.SeleniumTest.SeleniumResult;
import com.gatf.selenium.SeleniumTest.SeleniumTestResult;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.Point;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.interactions.Actions;
import com.google.common.base.Function;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import org.apache.commons.io.FileUtils;
import java.io.File;
import org.junit.Assert;
import org.openqa.selenium.Keys;

public class Test extends SeleniumTest implements Serializable {
public Test(AcceptanceTestContext ___cxt___) {
super("test.sel", ___cxt___);
}
public void quit() {
if(get___d___()!=null)get___d___().quit();
}
public void setupDriverfirefox(LoggingPreferences ___lp___) throws Exception {
DesiredCapabilities ___dc___ = DesiredCapabilities.firefox();
___dc___.setCapability(CapabilityType.LOGGING_PREFS, ___lp___);
set___d___(new org.openqa.selenium.firefox.MarionetteDriver(___dc___));
setBrowserName("firefox");

}
public void setupDriverchrome(LoggingPreferences ___lp___) throws Exception {
DesiredCapabilities ___dc___ = DesiredCapabilities.chrome();
___dc___.setCapability(CapabilityType.LOGGING_PREFS, ___lp___);
set___d___(new org.openqa.selenium.chrome.ChromeDriver(___dc___));
setBrowserName("chrome");

}
@SuppressWarnings("unchecked")
public Map<String, SeleniumResult> execute(LoggingPreferences ___lp___) throws Exception {
quit();
setupDriverchrome(___lp___);
_execute(___lp___);
return null;
}
@SuppressWarnings("unchecked")
public void _execute(LoggingPreferences ___lp___) throws Exception {
try {
SearchContext ___sc___2 = get___d___();
WebDriver ___cw___ = get___d___();
WebDriver ___ocw___ = ___cw___;___cw___.navigate().to(evaluate("https://portaldev.symplast.com"));
List<WebElement>  ___w___1 = By.id(evaluate("Location")).findElements(___sc___2);Assert.assertTrue(___w___1!=null && !___w___1.isEmpty());

Select ___w___2 = new Select(___w___1.get(0));
___w___2.selectByIndex(4);
final Object[]  ___w___3 = new Object[1];
final WebDriver ___sc___3 = (WebDriver)___sc___2;
(new WebDriverWait(___cw___, 30)).until(
new Function<WebDriver, Boolean>(){
public Boolean apply(WebDriver input) {
List<WebElement>  ___w___4 = By.id(evaluate("UserName")).findElements(___sc___3);if(___w___4==null || ___w___4.isEmpty())return false;

___w___4.get(0).sendKeys(evaluate("rahulb"));
___w___3[0] = ___w___4;

return true;
}
public String toString() {
return "";
}
});
List<WebElement>  ___w___5 = By.id(evaluate("Password")).findElements(___sc___2);Assert.assertTrue(___w___5!=null && !___w___5.isEmpty());

___w___5.get(0).sendKeys(evaluate("Ohum@123"));
List<WebElement>  ___w___6 = By.cssSelector(evaluate("input[type='submit']")).findElements(___sc___2);Assert.assertTrue(___w___6!=null && !___w___6.isEmpty());

___w___6.get(0).click();
List<WebElement>  ___w___7 = By.xpath(evaluate("//*[@id=\"wrapper\"]/div[1]/nav/div[1]/div[1]/div[2]/ul/div/a/li")).findElements(___sc___2);Assert.assertTrue(___w___7!=null && !___w___7.isEmpty());

___w___7.get(0).click();
final Object[]  ___w___8 = new Object[1];
final WebDriver ___sc___4 = (WebDriver)___sc___2;
(new WebDriverWait(___cw___, 30)).until(
new Function<WebDriver, Boolean>(){
public Boolean apply(WebDriver input) {
List<WebElement>  ___w___9 = By.id(evaluate("ddlRefSource")).findElements(___sc___4);if(___w___9==null || ___w___9.isEmpty())return false;

___w___8[0] = ___w___9;

return true;
}
public String toString() {
return "";
}
});
List<WebElement>  ___w___10 = By.id(evaluate("ddlRefSource")).findElements(___sc___2);Assert.assertTrue(___w___10!=null && !___w___10.isEmpty());

Select ___w___11 = new Select(___w___10.get(0));
___w___11.selectByIndex(2);
List<WebElement>  ___w___12 = By.id(evaluate("ddlReferralByPatient")).findElements(___sc___2);Assert.assertTrue(___w___12!=null && !___w___12.isEmpty());

Select ___w___13 = new Select(___w___12.get(0));
___w___13.selectByIndex(7);
List<WebElement>  ___w___14 = By.id(evaluate("txtFName")).findElements(___sc___2);Assert.assertTrue(___w___14!=null && !___w___14.isEmpty());

___w___14.get(0).sendKeys(evaluate("Test"));
List<WebElement>  ___w___15 = By.id(evaluate("txtLName")).findElements(___sc___2);Assert.assertTrue(___w___15!=null && !___w___15.isEmpty());

___w___15.get(0).sendKeys(evaluate("Second"));
List<WebElement>  ___w___16 = By.id(evaluate("idPatientBasicDOB")).findElements(___sc___2);Assert.assertTrue(___w___16!=null && !___w___16.isEmpty());

___w___16.get(0).sendKeys(evaluate("06/06/2015"));
List<WebElement>  ___w___17 = By.id(evaluate("txtMName")).findElements(___sc___2);Assert.assertTrue(___w___17!=null && !___w___17.isEmpty());

___w___17.get(0).click();
List<WebElement>  ___w___18 = By.id(evaluate("ddlGender")).findElements(___sc___2);Assert.assertTrue(___w___18!=null && !___w___18.isEmpty());

Select ___w___19 = new Select(___w___18.get(0));
___w___19.selectByIndex(2);
List<WebElement>  ___w___20 = By.id(evaluate("txtCellPhoneNo")).findElements(___sc___2);Assert.assertTrue(___w___20!=null && !___w___20.isEmpty());

___w___20.get(0).sendKeys(evaluate("34567"));
List<WebElement>  ___w___21 = By.id(evaluate("btnSavePatientBasicInfo")).findElements(___sc___2);Assert.assertTrue(___w___21!=null && !___w___21.isEmpty());

___w___21.get(0).click();
List<WebElement>  ___w___22 = By.id(evaluate("popup_message")).findElements(___sc___2);Assert.assertTrue(___w___22!=null && !___w___22.isEmpty());

List<WebElement>  ___w___23 = By.id(evaluate("popup_ok")).findElements(___sc___2);Assert.assertTrue(___w___23!=null && !___w___23.isEmpty());

___w___23.get(0).click();
List<WebElement>  ___w___24 = By.id(evaluate("txtCellPhoneNo")).findElements(___sc___2);Assert.assertTrue(___w___24!=null && !___w___24.isEmpty());

___w___24.get(0).click();
List<WebElement>  ___w___25 = By.id(evaluate("txtCellPhoneNo")).findElements(___sc___2);Assert.assertTrue(___w___25!=null && !___w___25.isEmpty());

___w___25.get(0).sendKeys(evaluate("121211122"));
List<WebElement>  ___w___26 = By.id(evaluate("btnSavePatientBasicInfo")).findElements(___sc___2);Assert.assertTrue(___w___26!=null && !___w___26.isEmpty());

___w___26.get(0).click();
List<WebElement>  ___w___27 = By.id(evaluate("popup_ok")).findElements(___sc___2);Assert.assertTrue(___w___27!=null && !___w___27.isEmpty());

___w___27.get(0).click();
final Object[]  ___w___28 = new Object[1];
final WebDriver ___sc___5 = (WebDriver)___sc___2;
(new WebDriverWait(___cw___, 10)).until(
new Function<WebDriver, Boolean>(){
public Boolean apply(WebDriver input) {
List<WebElement>  ___w___29 = By.id(evaluate("signOut")).findElements(___sc___5);if(___w___29==null || ___w___29.isEmpty())return false;

___w___28[0] = ___w___29;

return true;
}
public String toString() {
return "";
}
});
File ___sr___1 = ((TakesScreenshot)___ocw___).getScreenshotAs(OutputType.FILE);
FileUtils.copyFile(___sr___1, new File("test.png"));
List<WebElement>  ___w___30 = By.className(evaluate("welcome_text")).findElements(___sc___2);Assert.assertTrue(___w___30!=null && !___w___30.isEmpty());
final Object[]  ___w___31 = new Object[1];
final WebDriver ___sc___6 = (WebDriver)___sc___2;
(new WebDriverWait(___cw___, 10000)).until(
new Function<WebDriver, Boolean>(){
public Boolean apply(WebDriver input) {
List<WebElement>  ___w___32 = By.id(evaluate("signOut")).findElements(___sc___6);if(___w___32==null || ___w___32.isEmpty())return false;

___w___31[0] = ___w___32;

return true;
}
public String toString() {
return "";
}
});
Actions ___w___33 = new Actions(get___d___());
___w___33.moveToElement(___w___30.get(0)).click(((List<WebElement>)___w___31[0]).get(0)).perform();
pushResult(new SeleniumTestResult(get___d___(), this, ___lp___));
}
catch(Throwable c)
{
    pushResult(new SeleniumTestResult(get___d___(), this, c, ___lp___));
}}

public static void main(String[] args) throws Exception {
    System.setProperty("webdriver.gecko.driver", "F:\\Laptop_Backup\\Development\\selenium-drivers\\geckodriver.exe");
    System.setProperty("webdriver.chrome.driver", "F:\\Laptop_Backup\\Development\\selenium-drivers\\chromedriver.exe");
    new Test(null).execute(new LoggingPreferences());
    new Test(null).quit();
}
}