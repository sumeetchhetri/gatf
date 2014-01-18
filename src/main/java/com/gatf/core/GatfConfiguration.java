package com.gatf.core;

/*
	Copyright 2013-2014, Sumeet Chhetri
	
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

import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

import com.thoughtworks.xstream.annotations.XStreamAlias;

/*
 * <configuration>
		<testPaths>
			<docTestPath>com.mtel.services.*</docTestPath>
		</testPaths>
		<useSoapClient>true</useSoapClient>
		<soapWsdlKeyPairs>
			<soapWsdlKeyPair>AuthService,http://localhost:8081/soap/auth?wsdl</soapWsdlKeyPair>
			<soapWsdlKeyPair>ExampleService,http://localhost:8081/soap/example?wsdl</soapWsdlKeyPair>
			<soapWsdlKeyPair>MessageService,http://localhost:8081/soap/messages?wsdl</soapWsdlKeyPair>
			<soapWsdlKeyPair>UserService,http://localhost:8081/soap/user?wsdl</soapWsdlKeyPair>
		</soapWsdlKeyPairs>
		<urlPrefix>rest</urlPrefix>
		<requestDataType>json</requestDataType>
		<responseDataType>json</responseDataType>
		<overrideSecure>true</overrideSecure>
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
	
	private String responseDataType;
	
	private String resourcepath;
	
	private boolean enabled;
	
	private boolean overrideSecure;
	
	private boolean useSoapClient;
	
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

	public String getResponseDataType() {
		return responseDataType;
	}

	public void setResponseDataType(String responseDataType) {
		this.responseDataType = responseDataType;
	}

	public boolean isOverrideSecure() {
		return overrideSecure;
	}

	public void setOverrideSecure(boolean overrideSecure) {
		this.overrideSecure = overrideSecure;
	}

	public boolean isUseSoapClient() {
		return useSoapClient;
	}

	public void setUseSoapClient(boolean useSoapClient) {
		this.useSoapClient = useSoapClient;
	}
}
