package com.gatf.selenium;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.Assert;
import org.openqa.selenium.By;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.gatf.executor.core.AcceptanceTestContext;
import com.google.common.base.Function;

public class Test {
	
	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		
		String t = "browser(ALL),client(ALL),driver(ALL),performance(ALL),profiler(ALL),server(ALL)";
		if(t.toLowerCase().matches(".*browser\\(([a-z]+)\\).*")) {
			Matcher m = Pattern.compile(".*browser\\(([a-z]+)\\).*").matcher(t.toLowerCase());
			m.find();
			System.out.println(m.group(1));
		}
		
		Integer.parseInt("001");
		
		String a = RandomStringUtils.randomAlphabetic(10000);
		
		DigestUtils.sha1Hex(a);
		
		long st = System.nanoTime();
		String sha1hsh = DigestUtils.sha1Hex(a);
		System.out.println("SHA1 = " + (System.nanoTime() - st));
		
		st = System.nanoTime();
		long v = 0;
		for (int i=0;i<a.toCharArray().length;i++) {
			char c = a.toCharArray()[i];
			v += (int)c * (a.toCharArray().length - i);
		}
		System.out.println("MINE = " + (System.nanoTime() - st));
		
		System.out.println(v);
		
		/*String browser = commands.remove(0);
		String location = commands.remove(0);
		
		String tu = "seconds";
		if(commands.get(0).toLowerCase().startsWith("timeunit ")) {
			tu = commands.get(0).toLowerCase().substring(9);
		}
		
		StringBuilder build= new StringBuilder();
		if(browser!=null && location!=null) {
			build.append("public static class T_" + System.nanoTime() + "{\n");
			build.append("\tpublic static void main(String[] args) throws Exception {\n");
			build.append("\t\torg.openqa.selenium.WebDriver d = new org.openqa.selenium.chrome.ChromeDriver();\n");
			build.append("d.navigate().to(\""+location+"\");\n");
			for (String cmd : commands) {
				
			}
			build.append("\t}\n}");
		}*/
		Command cmd = Command.read("ui-auto.sel");
		//System.out.println(cmd.toCmd());
		System.out.println(cmd.javacode());
		
		// Create a new instance of the Firefox driver
        // Notice that the remainder of the code relies on the interface, 
        // not the implementation.
		//System.setProperty("webdriver.chrome.driver", "D:\\Development\\selenium-drivers\\chromedriver.exe");
		//WebDriver driver = new ChromeDriver();

        // And now use this to visit Google
        //driver.get("http://localhost:8080/");
        // Alternatively the same thing can be done like this
        // driver.navigate().to("http://www.google.com");

        // Google's search is rendered dynamically with JavaScript.
        // Wait for the page to load, timeout after 10 seconds
        //(new WebDriverWait(driver, 10)).until(ExpectedConditions.presenceOfElementLocated(By.id("Login_Form")));
        
        //WebElement un = driver.findElement(By.name("username"));
        //WebElement pw = driver.findElement(By.name("password"));
        //WebElement su = driver.findElement(By.cssSelector("input[onclick^=doLogin]"));
        
        //un.sendKeys("admin");
        //pw.sendKeys("admin");
        
        //su.click();
        
        // Google's search is rendered dynamically with JavaScript.
        // Wait for the page to load, timeout after 10 seconds
        //(new WebDriverWait(driver, 10)).until(ExpectedConditions.presenceOfElementLocated(By.id("System_Viewer")));

        //Close the browser
        //driver.quit();
	}
	
	public static void test(org.openqa.selenium.WebDriver t) throws Exception {
		
	}
	
	public static void test(org.openqa.selenium.WebDriver w, List<org.openqa.selenium.WebElement> el) throws Exception {
		
	}
	}
