package com.gatf.core;

import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

import com.thoughtworks.xstream.annotations.XStreamAlias;

/*
 * <configuration>
		<testPaths>
			<testPath>com.mtel.services.*</testPath>
		</testPaths>
		<soapWsdlKeyPairs>
			<soapWsdlKeyPair>AuthService,http://localhost:8081/soap/auth?wsdl</soapWsdlKeyPair>
			<soapWsdlKeyPair>ExampleService,http://localhost:8081/soap/example?wsdl</soapWsdlKeyPair>
			<soapWsdlKeyPair>MessageService,http://localhost:8081/soap/messages?wsdl</soapWsdlKeyPair>
			<soapWsdlKeyPair>UserService,http://localhost:8081/soap/user?wsdl</soapWsdlKeyPair>
		</soapWsdlKeyPairs>
		<urlPrefix>rest</urlPrefix>
		<requestDataType>json</requestDataType>
		<resourcepath>src/test/resources/generated</resourcepath>
		<enabled>true</enabled>
	</configuration>
 */

@XStreamAlias("configuration")
@JsonAutoDetect(getterVisibility=Visibility.NONE, fieldVisibility=Visibility.ANY, isGetterVisibility=Visibility.NONE)
@JsonSerialize(include=Inclusion.NON_NULL)
public class GatfConfiguration {

	private String[] testPaths;
	
	private String[] soapWsdlKeyPairs;
	
	private String urlPrefix;
	
	private String requestDataType;
	
	private String resourcepath;
	
	private boolean enabled;

	public String[] getTestPaths() {
		return testPaths;
	}

	public void setTestPaths(String[] testPaths) {
		this.testPaths = testPaths;
	}

	public String[] getSoapWsdlKeyPairs() {
		return soapWsdlKeyPairs;
	}

	public void setSoapWsdlKeyPairs(String[] soapWsdlKeyPairs) {
		this.soapWsdlKeyPairs = soapWsdlKeyPairs;
	}

	public String getUrlPrefix() {
		return urlPrefix;
	}

	public void setUrlPrefix(String urlPrefix) {
		this.urlPrefix = urlPrefix;
	}

	public String getRequestDataType() {
		return requestDataType;
	}

	public void setRequestDataType(String requestDataType) {
		this.requestDataType = requestDataType;
	}

	public String getResourcepath() {
		return resourcepath;
	}

	public void setResourcepath(String resourcepath) {
		this.resourcepath = resourcepath;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}
}
