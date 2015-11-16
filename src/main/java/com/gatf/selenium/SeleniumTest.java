package com.gatf.selenium;

import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.logging.Logs;

import com.gatf.executor.core.AcceptanceTestContext;

public interface SeleniumTest {
	public void quit();
	public Logs execute(AcceptanceTestContext ___c___, LoggingPreferences ___lp___) throws Exception;
}
