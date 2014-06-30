package com.gatf.executor.core;

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
import org.junit.Assert;

import com.gatf.GatfPluginConfig;
import com.gatf.executor.dataprovider.GatfTestDataConfig;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

/**
 * @author Sumeet Chhetri
 * The properties required to control the test suite execution
 */
@XStreamAlias("gatf-execute-config")
@JsonAutoDetect(getterVisibility=Visibility.NONE, fieldVisibility=Visibility.ANY, isGetterVisibility=Visibility.NONE)
@JsonSerialize(include=Inclusion.NON_NULL)
public class GatfExecutorConfig implements Serializable, GatfPluginConfig {

	private String baseUrl;
	
	private String testCasesBasePath;
	
	private String testCaseDir;
	
	private String outFilesBasePath;
	
	private String outFilesDir;
	
	private boolean authEnabled;
	
	private String authUrl;
	
	private String authExtractAuth;
	
	private String authParamsDetails;
	
	private String wsdlLocFile;
	
	private boolean soapAuthEnabled;
	
	private String soapAuthWsdlKey;
	
	private String soapAuthOperation;
	
	private String soapAuthExtractAuth;
	
	private Integer numConcurrentExecutions;
	
	private boolean httpCompressionEnabled;
	
	private Integer httpConnectionTimeout;
	
	private Integer httpRequestTimeout;
	
	private Integer concurrentUserSimulationNum;
	
	private String testDataConfigFile;
	
	private String authDataProvider;
	
	private boolean compareEnabled;
	
	private GatfTestDataConfig gatfTestDataConfig; 
	
	private Boolean enabled;
	
	private String[] testCaseHooksPaths;
	
	private boolean loadTestingEnabled;
	
	private Long loadTestingTime;
	
	private boolean distributedLoadTests;
	
	private String[] distributedNodes;
	
	@XStreamOmitField
	private Integer compareBaseUrlsNum;
	
	private Long concurrentUserRampUpTime;
	
	private Integer loadTestingReportSamples;
	
	private boolean debugEnabled;
	
	private String[] ignoreFiles;
	
	private String[] orderedFiles;
	
	private boolean isOrderByFileName;
	
	private boolean isServerLogsApiAuthEnabled;
	
	private String serverLogsApiFileName;
	
	private String serverLogsApiAuthExtractAuth;
	
	private boolean isFetchFailureLogs;
	
	public String getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public String getTestCasesBasePath() {
		return testCasesBasePath;
	}

	public void setTestCasesBasePath(String testCasesBasePath) {
		this.testCasesBasePath = testCasesBasePath;
	}

	public String getTestCaseDir() {
		return testCaseDir;
	}

	public void setTestCaseDir(String testCaseDir) {
		this.testCaseDir = testCaseDir;
	}

	public String getOutFilesBasePath() {
		return outFilesBasePath;
	}

	public void setOutFilesBasePath(String outFilesBasePath) {
		this.outFilesBasePath = outFilesBasePath;
	}

	public String getOutFilesDir() {
		return outFilesDir;
	}

	public void setOutFilesDir(String outFilesDir) {
		this.outFilesDir = outFilesDir;
	}

	public void setAuthEnabled(boolean authEnabled) {
		this.authEnabled = authEnabled;
	}

	public String getAuthUrl() {
		return authUrl;
	}

	public void setAuthUrl(String authUrl) {
		this.authUrl = authUrl;
	}

	public String getAuthExtractAuth() {
		return authExtractAuth;
	}

	public void setAuthExtractAuth(String authExtractAuth) {
		this.authExtractAuth = authExtractAuth;
	}

	public String getWsdlLocFile() {
		return wsdlLocFile;
	}

	public void setWsdlLocFile(String wsdlLocFile) {
		this.wsdlLocFile = wsdlLocFile;
	}

	public void setSoapAuthEnabled(boolean soapAuthEnabled) {
		this.soapAuthEnabled = soapAuthEnabled;
	}

	public String getSoapAuthWsdlKey() {
		return soapAuthWsdlKey;
	}

	public void setSoapAuthWsdlKey(String soapAuthWsdlKey) {
		this.soapAuthWsdlKey = soapAuthWsdlKey;
	}

	public String getSoapAuthOperation() {
		return soapAuthOperation;
	}

	public void setSoapAuthOperation(String soapAuthOperation) {
		this.soapAuthOperation = soapAuthOperation;
	}

	public String getSoapAuthExtractAuth() {
		return soapAuthExtractAuth;
	}

	public void setSoapAuthExtractAuth(String soapAuthExtractAuth) {
		this.soapAuthExtractAuth = soapAuthExtractAuth;
	}

	public Integer getNumConcurrentExecutions() {
		return numConcurrentExecutions;
	}

	public void setNumConcurrentExecutions(Integer numConcurrentExecutions) {
		this.numConcurrentExecutions = numConcurrentExecutions;
	}

	public String[] getAuthExtractAuthParams() {
		if(authExtractAuth!=null) {
			return authExtractAuth.split(",");
		}
		return null;
	}
	
	public String[] getServerApiAuthExtractAuthParams() {
		if(serverLogsApiAuthExtractAuth!=null) {
			return serverLogsApiAuthExtractAuth.split(",");
		}
		return null;
	}
	
	public String[] getAuthParamDetails() {
		if(authParamsDetails!=null) {
			return authParamsDetails.split(",");
		}
		return null;
	}
	
	public String[] getSoapAuthExtractAuthParams() {
		if(soapAuthExtractAuth!=null) {
			return soapAuthExtractAuth.split(",");
		}
		return null;
	}
	
	public Boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(Boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isAuthEnabled() {
		return authEnabled && getAuthExtractAuthParams()!=null;
	}
	
	public boolean isSoapAuthEnabled() {
		return soapAuthEnabled && getSoapAuthExtractAuthParams()!=null;
	}
	
	public boolean isSoapAuthTestCase(TestCase tcase) {
		return getSoapAuthWsdlKey()!=null && getSoapAuthOperation()!=null &&
				(getSoapAuthWsdlKey() + 
						getSoapAuthOperation()).equals(tcase.getWsdlKey()+tcase.getOperationName());
	}

	public boolean isHttpCompressionEnabled() {
		return httpCompressionEnabled;
	}

	public void setHttpCompressionEnabled(boolean httpCompressionEnabled) {
		this.httpCompressionEnabled = httpCompressionEnabled;
	}

	public Integer getHttpConnectionTimeout() {
		return httpConnectionTimeout;
	}

	public void setHttpConnectionTimeout(Integer httpConnectionTimeout) {
		this.httpConnectionTimeout = httpConnectionTimeout;
	}

	public Integer getHttpRequestTimeout() {
		return httpRequestTimeout;
	}

	public void setHttpRequestTimeout(Integer httpRequestTimeout) {
		this.httpRequestTimeout = httpRequestTimeout;
	}
	
	public Integer getConcurrentUserSimulationNum() {
		return concurrentUserSimulationNum;
	}

	public void setConcurrentUserSimulationNum(Integer concurrentUserSimulationNum) {
		this.concurrentUserSimulationNum = concurrentUserSimulationNum;
	}
	
	public GatfTestDataConfig getGatfTestDataConfig() {
		return gatfTestDataConfig;
	}

	public void setGatfTestDataConfig(GatfTestDataConfig gatfTestDataConfig) {
		this.gatfTestDataConfig = gatfTestDataConfig;
	}

	public String getTestDataConfigFile() {
		return testDataConfigFile;
	}

	public void setTestDataConfigFile(String testDataConfigFile) {
		this.testDataConfigFile = testDataConfigFile;
	}

	public String getAuthDataProvider() {
		return authDataProvider;
	}

	public void setAuthDataProvider(String authDataProvider) {
		this.authDataProvider = authDataProvider;
	}

	public Integer getCompareBaseUrlsNum() {
		return compareBaseUrlsNum;
	}

	public void setCompareBaseUrlsNum(Integer compareBaseUrlsNum) {
		this.compareBaseUrlsNum = compareBaseUrlsNum;
	}

	public boolean isCompareEnabled() {
		return compareEnabled;
	}

	public void setCompareEnabled(boolean compareEnabled) {
		this.compareEnabled = compareEnabled;
	}

	public String getAuthParamsDetails() {
		return authParamsDetails;
	}

	public void setAuthParamsDetails(String authParamsDetails) {
		this.authParamsDetails = authParamsDetails;
	}

	public String[] getTestCaseHooksPaths() {
		return testCaseHooksPaths;
	}

	public void setTestCaseHooksPaths(String[] testCaseHooksPaths) {
		this.testCaseHooksPaths = testCaseHooksPaths;
	}

	public boolean isLoadTestingEnabled() {
		return loadTestingEnabled;
	}

	public void setLoadTestingEnabled(boolean loadTestingEnabled) {
		this.loadTestingEnabled = loadTestingEnabled;
	}

	public boolean isFetchFailureLogs() {
		return isFetchFailureLogs;
	}

	public void setFetchFailureLogs(boolean isFetchFailureLogs) {
		this.isFetchFailureLogs = isFetchFailureLogs;
	}

	public Long getLoadTestingTime() {
		return loadTestingTime;
	}

	public void setLoadTestingTime(Long loadTestingTime) {
		this.loadTestingTime = loadTestingTime;
	}

	public boolean isDistributedLoadTests() {
		return distributedLoadTests;
	}

	public void setDistributedLoadTests(boolean distributedLoadTests) {
		this.distributedLoadTests = distributedLoadTests;
	}

	public String[] getDistributedNodes() {
		return distributedNodes;
	}

	public void setDistributedNodes(String[] distributedNodes) {
		this.distributedNodes = distributedNodes;
	}

	public Long getConcurrentUserRampUpTime() {
		return concurrentUserRampUpTime;
	}

	public void setConcurrentUserRampUpTime(Long concurrentUserRampUpTime) {
		this.concurrentUserRampUpTime = concurrentUserRampUpTime;
	}

	public Integer getLoadTestingReportSamples() {
		return loadTestingReportSamples;
	}

	public void setLoadTestingReportSamples(Integer loadTestingReportSamples) {
		this.loadTestingReportSamples = loadTestingReportSamples;
	}

	public boolean isDebugEnabled() {
		return debugEnabled;
	}

	public void setDebugEnabled(boolean debugEnabled) {
		this.debugEnabled = debugEnabled;
	}

	public String[] getIgnoreFiles() {
		return ignoreFiles;
	}

	public void setIgnoreFiles(String[] ignoreFiles) {
		this.ignoreFiles = ignoreFiles;
	}

	public String[] getOrderedFiles() {
		return orderedFiles;
	}

	public void setOrderedFiles(String[] orderedFiles) {
		this.orderedFiles = orderedFiles;
	}

	public boolean isOrderByFileName() {
		return isOrderByFileName;
	}

	public void setOrderByFileName(boolean isOrderByFileName) {
		this.isOrderByFileName = isOrderByFileName;
	}

	public boolean isServerLogsApiAuthEnabled() {
		return isServerLogsApiAuthEnabled;
	}

	public void setServerLogsApiAuthEnabled(boolean isServerLogsApiAuthEnabled) {
		this.isServerLogsApiAuthEnabled = isServerLogsApiAuthEnabled;
	}

	public String getServerLogsApiFileName() {
		return serverLogsApiFileName;
	}

	public void setServerLogsApiFileName(String serverLogsApiFileName) {
		this.serverLogsApiFileName = serverLogsApiFileName;
	}

	public String getServerLogsApiAuthExtractAuth() {
		return serverLogsApiAuthExtractAuth;
	}

	public void setServerLogsApiAuthExtractAuth(String serverLogsApiAuthExtractAuth) {
		this.serverLogsApiAuthExtractAuth = serverLogsApiAuthExtractAuth;
	}

	public void validate()
	{
		Assert.assertTrue("Testcase directory name is blank...", 
				getTestCaseDir()!=null && !getTestCaseDir().trim().isEmpty());
		
		if(isAuthEnabled()) {
			Assert.assertTrue("Invalid auth extract params", getAuthExtractAuthParams().length==4);
			Assert.assertTrue("Invalid auth extract token name", !getAuthExtractAuthParams()[0].isEmpty());
			Assert.assertTrue("Invalid auth extract mode specified, should be one of (xml,json,header,plain,cookie)", 
					getAuthExtractAuthParams()[1].equalsIgnoreCase("json") ||
					getAuthExtractAuthParams()[1].equalsIgnoreCase("xml") ||
					getAuthExtractAuthParams()[1].equalsIgnoreCase("header") ||
					getAuthExtractAuthParams()[1].equalsIgnoreCase("plain") ||
					getAuthExtractAuthParams()[1].equalsIgnoreCase("cookie"));
			Assert.assertTrue("Invalid auth name specified", !getAuthExtractAuthParams()[2].isEmpty());
			Assert.assertTrue("Invalid auth mode specified, should be one of (queryparam,header,cookie)", 
					getAuthExtractAuthParams()[3].equalsIgnoreCase("queryparam") ||
					getAuthExtractAuthParams()[3].equalsIgnoreCase("cookie") ||
					getAuthExtractAuthParams()[3].equalsIgnoreCase("header"));
			Assert.assertTrue("Invalid auth url", getAuthUrl()!=null && !getAuthUrl().isEmpty());
			
			Assert.assertNotNull("Invalid auth param details",getAuthParamsDetails());
			Assert.assertTrue("Invalid auth param details", getAuthParamDetails().length==4);
			Assert.assertTrue("Invalid auth user param name", !getAuthParamDetails()[0].isEmpty());
			Assert.assertTrue("Invalid auth user param name mode specified, should be one of (header,postparam,queryparam)", 
					getAuthParamDetails()[1].equalsIgnoreCase("header") ||
					getAuthParamDetails()[1].equalsIgnoreCase("postparam") ||
					getAuthParamDetails()[1].equalsIgnoreCase("queryparam"));
			Assert.assertTrue("Invalid auth password param name", !getAuthParamDetails()[2].isEmpty());
			Assert.assertTrue("Invalid auth password param name mode specified, should be one of (header,queryparam,header)", 
					getAuthParamDetails()[3].equalsIgnoreCase("queryparam") ||
					getAuthParamDetails()[3].equalsIgnoreCase("postparam") ||
					getAuthParamDetails()[3].equalsIgnoreCase("header"));
			
		}
		
		if(isSoapAuthEnabled()) {
			Assert.assertTrue("Invalid auth soap wsdl key", getSoapAuthWsdlKey()!=null && !getSoapAuthWsdlKey().isEmpty());
			Assert.assertTrue("Invalid auth soap wsdl operation", getSoapAuthOperation()!=null && !getSoapAuthOperation().isEmpty());
			
			Assert.assertTrue("Invalid auth soap extract params", getSoapAuthExtractAuthParams().length==3);
			Assert.assertTrue("Invalid auth soap extract token name", !getSoapAuthExtractAuthParams()[0].isEmpty());
			Assert.assertTrue("Invalid auth soap name specified", !getSoapAuthExtractAuthParams()[1].isEmpty());
			Assert.assertTrue("Invalid auth soap name", !getSoapAuthExtractAuthParams()[2].isEmpty());
			Assert.assertTrue("Invalid auth soap name", getSoapAuthExtractAuthParams()[2].equalsIgnoreCase("queryparam"));
		}
	}
}
