package com.gatf.executor.core;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@JacksonXmlRootElement(localName = "mailSimulator")
@JsonAutoDetect(getterVisibility=Visibility.NONE, fieldVisibility=Visibility.ANY, isGetterVisibility=Visibility.NONE)
@JsonInclude(value = Include.NON_NULL)
public class MailSimulatorConfig {
	
	private String login;
	
	private String password;
	
	private boolean isSecure;
	
	private int smtpPort;
	
	private int imapPort;

	public String getLogin() {
		return login;
	}

	public void setLogin(String login) {
		this.login = login;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public boolean isSecure() {
		return isSecure;
	}

	public void setSecure(boolean isSecure) {
		this.isSecure = isSecure;
	}

	public int getSmtpPort() {
		return smtpPort;
	}

	public void setSmtpPort(int smtpPort) {
		this.smtpPort = smtpPort;
	}

	public int getImapPort() {
		return imapPort;
	}

	public void setImapPort(int imapPort) {
		this.imapPort = imapPort;
	}
}
