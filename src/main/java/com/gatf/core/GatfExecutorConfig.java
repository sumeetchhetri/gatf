package com.gatf.core;

import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;
import org.junit.Assert;

import com.gatf.test.TestCase;
import com.thoughtworks.xstream.annotations.XStreamAlias;

@XStreamAlias("gatf-execute-config")
@JsonAutoDetect(getterVisibility=Visibility.NONE, fieldVisibility=Visibility.ANY, isGetterVisibility=Visibility.NONE)
@JsonSerialize(include=Inclusion.NON_NULL)
public class GatfExecutorConfig {

	private String baseUrl;
	
	private String testCasesBasePath;
	
	private String testCaseDir;
	
	private String outFilesBasePath;
	
	private String outFilesDir;
	
	private boolean authEnabled;
	
	private String authUrl;
	
	private String authExtractAuth;
	
	private String wsdlLocFile;
	
	private boolean soapAuthEnabled;
	
	private String soapAuthWsdlKey;
	
	private String soapAuthOperation;
	
	private String soapAuthExtractAuth;
	
	private boolean reportingEnabled;
	
	private Integer numConcurrentExecutions;
	
	private boolean httpCompressionEnabled;
	
	private Integer httpConnectionTimeout;
	
	private Integer httpRequestTimeout;

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

	public boolean isReportingEnabled() {
		return reportingEnabled;
	}

	public void setReportingEnabled(boolean reportingEnabled) {
		this.reportingEnabled = reportingEnabled;
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
	
	public String[] getSoapAuthExtractAuthParams() {
		if(soapAuthExtractAuth!=null) {
			return soapAuthExtractAuth.split(",");
		}
		return null;
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
	
	public void validate()
	{
		Assert.assertTrue("Testcase directory name is blank...", 
				getTestCaseDir()!=null && !getTestCaseDir().trim().isEmpty());
		
		if(isAuthEnabled()) {
			Assert.assertTrue(getAuthExtractAuthParams().length==4);
			Assert.assertTrue(!getAuthExtractAuthParams()[0].isEmpty());
			Assert.assertTrue(getAuthExtractAuthParams()[1].equalsIgnoreCase("json") ||
					getAuthExtractAuthParams()[1].equalsIgnoreCase("xml") ||
					getAuthExtractAuthParams()[1].equalsIgnoreCase("header") ||
					getAuthExtractAuthParams()[1].equalsIgnoreCase("plain") ||
					getAuthExtractAuthParams()[1].equalsIgnoreCase("cookie"));
			Assert.assertTrue(!getAuthExtractAuthParams()[2].isEmpty());
			Assert.assertTrue(getAuthExtractAuthParams()[3].equalsIgnoreCase("queryparam") ||
					getAuthExtractAuthParams()[3].equalsIgnoreCase("postparam") ||
					getAuthExtractAuthParams()[3].equalsIgnoreCase("header"));
			Assert.assertTrue(getAuthUrl()!=null && !getAuthUrl().isEmpty());
		}
		
		if(isSoapAuthEnabled()) {
			Assert.assertTrue(getSoapAuthWsdlKey()!=null && !getSoapAuthWsdlKey().isEmpty());
			Assert.assertTrue(getSoapAuthOperation()!=null && !getSoapAuthOperation().isEmpty());
			
			Assert.assertTrue(getSoapAuthExtractAuthParams().length==3);
			Assert.assertTrue(!getSoapAuthExtractAuthParams()[0].isEmpty());
			Assert.assertTrue(!getSoapAuthExtractAuthParams()[1].isEmpty());
			Assert.assertTrue(!getSoapAuthExtractAuthParams()[2].isEmpty());
			Assert.assertTrue(getSoapAuthExtractAuthParams()[2].equalsIgnoreCase("queryparam"));
		}
	}
}
