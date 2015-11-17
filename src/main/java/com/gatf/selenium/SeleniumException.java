package com.gatf.selenium;

import org.openqa.selenium.WebDriver;

public class SeleniumException extends RuntimeException {
	
	private WebDriver d;
	
	public WebDriver getD() {
		return d;
	}

	public SeleniumException(WebDriver d, Throwable cause) {
		super(cause);
		this.d = d;
	}
}
