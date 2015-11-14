package com.gatf.generator.core;

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

import java.io.Serializable;

import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

import com.gatf.GatfPluginConfig;
import com.thoughtworks.xstream.annotations.XStreamAlias;

/*
 * <configuration>
		<testPaths>
			<testPath>com.sample.services.*</testPath>
		</testPaths>
		<useSoapClient>true</useSoapClient>
		<soapWsdlKeyPairs>
			<soapWsdlKeyPair>AuthService,http://localhost:8081/soap/auth?wsdl</soapWsdlKeyPair>
			<soapWsdlKeyPair>ExampleService,http://localhost:8081/soap/example?wsdl</soapWsdlKeyPair>
			<soapWsdlKeyPair>MessageService,http://localhost:8081/soap/messages?wsdl</soapWsdlKeyPair>
			<soapWsdlKeyPair>UserService,http://localhost:8081/soap/user?wsdl</soapWsdlKeyPair>
		</soapWsdlKeyPairs>
		<urlPrefix>rest</urlPrefix>
		<urlSuffix>_param=value</urlSuffix>
		<requestDataType>json</requestDataType>
		<responseDataType>json</responseDataType>
		<overrideSecure>true</overrideSecure>
		<resourcepath>src/test/resources/generated</resourcepath>
		<postmanCollectionVersion>2</postmanCollectionVersion>
		<testCaseFormat>xml</testCaseFormat>
		<enabled>true</enabled>
	</configuration>

 * @author Sumeet Chhetri
 * The test case generator configuration parameters
 */
@XStreamAlias("configuration")
@JsonAutoDetect(getterVisibility=Visibility.NONE, fieldVisibility=Visibility.ANY, isGetterVisibility=Visibility.NONE)
@JsonSerialize(include=Inclusion.NON_NULL)
public class GatfConfiguration implements Serializable, GatfPluginConfig {

	private static final long serialVersionUID = 1L;
	
	private String[] testPaths;
	
	private String[] soapWsdlKeyPairs;
	
	private String urlPrefix;
	
	private String requestDataType;
	
	private String responseDataType;
	
	private String resourcepath;
	
	private boolean enabled;
	
	private boolean overrideSecure;
	
	private boolean useSoapClient;
	
	private String urlSuffix;
	
	private int postmanCollectionVersion;
	
	private String testCaseFormat;
	
	private boolean debugEnabled;
	
	public String getTestCaseFormat() {
		return testCaseFormat;
	}

	public void setTestCaseFormat(String testCaseFormat) {
		this.testCaseFormat = testCaseFormat;
	}

	public int getPostmanCollectionVersion() {
		return postmanCollectionVersion;
	}

	public void setPostmanCollectionVersion(int postmanCollectionVersion) {
		this.postmanCollectionVersion = postmanCollectionVersion;
	}

    public String getUrlSuffix()
    {
        return urlSuffix;
    }

    public void setUrlSuffix(String urlSuffix)
    {
        this.urlSuffix = urlSuffix;
    }
	
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

	public boolean isDebugEnabled() {
		return debugEnabled;
	}

	public void setDebugEnabled(boolean debugEnabled) {
		this.debugEnabled = debugEnabled;
	}
}
