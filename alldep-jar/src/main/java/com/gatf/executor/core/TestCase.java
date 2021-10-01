/*
    Copyright 2013-2019, Sumeet Chhetri
    
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
package com.gatf.executor.core;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import org.junit.Assert;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamOmitField;


/**
 * @author Sumeet Chhetri
 * Represents the test case details, url, name, request and expected response assertions
 * Also defines json/xml level node validation assertion steps
 * Defines the workflow parameter mappings and repeat scenario parameters/provider
 */
@XStreamAlias("TestCase")
@JsonAutoDetect(getterVisibility=Visibility.NONE, fieldVisibility=Visibility.ANY, isGetterVisibility=Visibility.NONE)
@JsonInclude(value = Include.NON_NULL)
public class TestCase implements Serializable {

	private static final long serialVersionUID = 1L;
	
	private static final Logger logger = Logger.getLogger(TestCase.class);
	
	private static final String PRE = UUID.randomUUID().toString() + "__";
	
	private static final Pattern quotedString = Pattern.compile("((?<![\\\\])['\"])((?:.(?!(?<![\\\\])\\1))*.?)\\1");
	
	public static final String[] CDATA_NODES = new String[]{"content","expectedResContent"};
	
	private String baseUrl;
	
	private String url;
	
	private String name;
	
	@XStreamAsAttribute
	private String method;
	
	private String description;
	
	private String content;
	
	private String contentFile;
	
	private Map<String, String> headers = new HashMap<String, String>();
	
	@XStreamAsAttribute
	private String exQueryPart;
	
	@XStreamAsAttribute
	private int expectedResCode;
	
	@XStreamAsAttribute
	private String expectedResContentType;
	
	private String expectedResContent;
	
	@XStreamAsAttribute
	private boolean skipTest;
	
	@XStreamAsAttribute
	private boolean detailedLog;
	
	@XStreamAsAttribute
	private boolean secure;
	
	private List<String> expectedNodes = new ArrayList<String>();
	
	@XStreamAsAttribute
	private boolean soapBase;
	
	private String wsdlKey;
	
	private String operationName;
	
	private Map<String, String> soapParameterValues = new HashMap<String, String>();
	
	private Map<String, String> workflowContextParameterMap = new HashMap<String, String>();
	
	@XStreamAsAttribute
	private int sequence = 0;
	
	private List<String> multipartContent = new ArrayList<String>();
	
	private String outFileName;
	
	private List<Map<String, String>> repeatScenarios = new ArrayList<Map<String,String>>();
	
	@XStreamOmitField
	@JsonIgnore
	private List<Map<String, String>> repeatScenariosOrig = new ArrayList<Map<String,String>>();
	
	private String repeatScenarioProviderName;
	
	@XStreamOmitField
	@JsonIgnore
	private String sourcefileName;
	
	@XStreamOmitField
	@JsonIgnore
	private String aurl;
	
	@XStreamOmitField
	@JsonIgnore
	private String aexQueryPart;
	
	@XStreamOmitField
	@JsonIgnore
	private String acontent;
	
	@XStreamOmitField
	@JsonIgnore
	private List<String> aexpectedNodes = new ArrayList<String>();
	
	@XStreamAsAttribute
	private Integer numberOfExecutions = 1;
	
	@XStreamAsAttribute
	private boolean repeatScenariosConcurrentExecution;
	
	@XStreamAsAttribute	
	private boolean stopOnFirstFailureForPerfTest;

	@XStreamOmitField
	@JsonIgnore
	private volatile boolean failed;
	
	@XStreamOmitField
	private Integer simulationNumber;
	
	@XStreamAsAttribute
	private Long preWaitMs;
	
	@XStreamAsAttribute
	private Long postWaitMs;
	
	@XStreamAsAttribute
	private Boolean reportResponseContent = true;
	
	private String preExecutionDataSourceHookName;
	
	private String postExecutionDataSourceHookName;
	
	@XStreamAsAttribute
	private boolean abortOnInvalidStatusCode;
	
	@XStreamAsAttribute
	private boolean abortOnInvalidContentType;
	
	private String relatedTestName;
	
	@XStreamOmitField
	@JsonIgnore
	//Parameters carried over from related testcases
	private Map<String, String> carriedOverVariables;
	
	@XStreamOmitField
	@JsonIgnore
	private String identifierPrefix =  "Run";
	
	private String executeOnCondition;
	
	private List<String> logicalValidations;
	
	@XStreamAsAttribute
	private boolean disablePreHooks = false;
	
	@XStreamAsAttribute
	private boolean disablePostHooks = false;
	
	@XStreamOmitField
	@JsonIgnore
	private Map<String, String> currentScenarioVariables;
	
	@XStreamOmitField
	@JsonIgnore
	private boolean isServerApiAuth = false;
	
	@XStreamOmitField
	@JsonIgnore
	private boolean isServerApiTarget = false;
	
	@XStreamOmitField
	@JsonIgnore
	private boolean isExternalApi = false;
	
	private PerformanceConfig perfConfig;
	
	public String getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getMethod() {
		return method;
	}

	public void setMethod(String method) {
		this.method = method;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public String getContentFile() {
		return contentFile;
	}

	public void setContentFile(String contentFile) {
		this.contentFile = contentFile;
	}

	public Map<String, String> getHeaders() {
		return headers;
	}

	public void setHeaders(Map<String, String> headers) {
		this.headers = headers;
	}

	public int getExpectedResCode() {
		return expectedResCode;
	}

	public void setExpectedResCode(int expectedResCode) {
		this.expectedResCode = expectedResCode;
	}

	public String getExpectedResContent() {
		return expectedResContent;
	}

	public void setExpectedResContent(String expectedResContent) {
		this.expectedResContent = expectedResContent;
	}

	public boolean isSkipTest() {
		return skipTest;
	}

	public void setSkipTest(boolean skipTest) {
		this.skipTest = skipTest;
	}

	public List<String> getExpectedNodes() {
		return expectedNodes;
	}

	public void setExpectedNodes(List<String> expectedNodes) {
		this.expectedNodes = expectedNodes;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getExpectedResContentType() {
		return expectedResContentType;
	}

	public void setExpectedResContentType(String expectedResContentType) {
		this.expectedResContentType = expectedResContentType;
	}

	public String getExQueryPart() {
		return exQueryPart;
	}

	public void setExQueryPart(String exQueryPart) {
		this.exQueryPart = exQueryPart;
	}

	public boolean isDetailedLog() {
		return detailedLog;
	}

	public void setDetailedLog(boolean detailedLog) {
		this.detailedLog = detailedLog;
	}

	public boolean isSecure() {
		return secure;
	}

	public void setSecure(boolean secure) {
		this.secure = secure;
	}

	public Map<String, String> getSoapParameterValues() {
		return soapParameterValues;
	}

	public void setSoapParameterValues(Map<String, String> soapParameterValues) {
		this.soapParameterValues = soapParameterValues;
	}
	
	public boolean isSoapBase() {
		return soapBase;
	}

	public void setSoapBase(boolean soapBase) {
		this.soapBase = soapBase;
	}
	
	public String getWsdlKey() {
		return wsdlKey;
	}

	public void setWsdlKey(String wsdlKey) {
		this.wsdlKey = wsdlKey;
	}

	public String getOperationName() {
		return operationName;
	}

	public void setOperationName(String operationName) {
		this.operationName = operationName;
	}

	public List<String> getMultipartContent() {
		return multipartContent;
	}

	public void setMultipartContent(List<String> multipartContent) {
		this.multipartContent = multipartContent;
	}
	
	public Map<String, String> getWorkflowContextParameterMap() {
		return workflowContextParameterMap;
	}

	public void setWorkflowContextParameterMap(
			Map<String, String> workflowContextParameterMap) {
		this.workflowContextParameterMap = workflowContextParameterMap;
	}

	public int getSequence() {
		return sequence;
	}

	public void setSequence(int sequence) {
		this.sequence = sequence;
	}

	public String getOutFileName() {
		return outFileName;
	}

	public void setOutFileName(String outFileName) {
		this.outFileName = outFileName;
	}

	public List<Map<String, String>> getRepeatScenarios() {
		return repeatScenarios;
	}

	public void setRepeatScenarios(List<Map<String, String>> repeatScenarios) {
		this.repeatScenarios = repeatScenarios;
	}

	public List<Map<String, String>> getRepeatScenariosOrig() {
		return repeatScenariosOrig;
	}

	public void setRepeatScenariosOrig(List<Map<String, String>> repeatScenariosOrig) {
		this.repeatScenariosOrig = repeatScenariosOrig;
	}

	public void setSourcefileName(String sourcefileName) {
		this.sourcefileName = sourcefileName;
	}

	public String getAurl() {
		return aurl;
	}

	public void setAurl(String aurl) {
		this.aurl = aurl;
	}

	public String getAexQueryPart() {
		return aexQueryPart;
	}

	public void setAexQueryPart(String aexQueryPart) {
		this.aexQueryPart = aexQueryPart;
	}

	public String getAcontent() {
		return acontent;
	}

	public void setAcontent(String acontent) {
		this.acontent = acontent;
	}

	public List<String> getAexpectedNodes() {
		return aexpectedNodes;
	}

	public void setAexpectedNodes(List<String> aexpectedNodes) {
		this.aexpectedNodes = aexpectedNodes;
	}

	public Integer getNumberOfExecutions() {
		return numberOfExecutions;
	}

	public void setNumberOfExecutions(Integer numberOfExecutions) {
		this.numberOfExecutions = numberOfExecutions;
	}
	
	public boolean isRepeatScenariosConcurrentExecution() {
		return repeatScenariosConcurrentExecution;
	}

	public void setRepeatScenariosConcurrentExecution(
			boolean repeatScenariosConcurrentExecution) {
		this.repeatScenariosConcurrentExecution = repeatScenariosConcurrentExecution;
	}

	public String getRepeatScenarioProviderName() {
		return repeatScenarioProviderName;
	}

	public void setRepeatScenarioProviderName(String repeatScenarioProviderName) {
		this.repeatScenarioProviderName = repeatScenarioProviderName;
	}

	public boolean isStopOnFirstFailureForPerfTest() {
		return stopOnFirstFailureForPerfTest;
	}

	public void setStopOnFirstFailureForPerfTest(
			boolean stopOnFirstFailureForPerfTest) {
		this.stopOnFirstFailureForPerfTest = stopOnFirstFailureForPerfTest;
	}

	public Integer getSimulationNumber() {
		return simulationNumber;
	}

	public void setSimulationNumber(Integer simulationNumber) {
		this.simulationNumber = simulationNumber;
	}

	public Long getPreWaitMs() {
		return preWaitMs;
	}

	public void setPreWaitMs(Long preWaitMs) {
		this.preWaitMs = preWaitMs;
	}

	public Long getPostWaitMs() {
		return postWaitMs;
	}

	public void setPostWaitMs(Long postWaitMs) {
		this.postWaitMs = postWaitMs;
	}

	public Boolean getReportResponseContent() {
		return reportResponseContent;
	}

	public void setReportResponseContent(Boolean reportResponseContent) {
		this.reportResponseContent = reportResponseContent;
	}

	public String getPreExecutionDataSourceHookName() {
		return preExecutionDataSourceHookName;
	}

	public void setPreExecutionDataSourceHookName(
			String preExecutionDataSourceHookName) {
		this.preExecutionDataSourceHookName = preExecutionDataSourceHookName;
	}

	public String getPostExecutionDataSourceHookName() {
		return postExecutionDataSourceHookName;
	}

	public void setPostExecutionDataSourceHookName(
			String postExecutionDataSourceHookName) {
		this.postExecutionDataSourceHookName = postExecutionDataSourceHookName;
	}

	public boolean isAbortOnInvalidStatusCode() {
		return abortOnInvalidStatusCode;
	}

	public void setAbortOnInvalidStatusCode(boolean abortOnInvalidStatusCode) {
		this.abortOnInvalidStatusCode = abortOnInvalidStatusCode;
	}

	public boolean isAbortOnInvalidContentType() {
		return abortOnInvalidContentType;
	}

	public void setAbortOnInvalidContentType(boolean abortOnInvalidContentType) {
		this.abortOnInvalidContentType = abortOnInvalidContentType;
	}

	public String getIdentifierPrefix() {
		if(identifierPrefix==null) {
			identifierPrefix = "Run";
		}
		return identifierPrefix;
	}

	public void setIdentifierPrefix(String identifierPrefix) {
		this.identifierPrefix = identifierPrefix;
	}

	public boolean isFailed() {
		return failed;
	}

	public void setFailed(boolean failed) {
		this.failed = failed;
	}

	public String getIdentifier() {
		if(getSimulationNumber()==null || getSimulationNumber()==0) {
			return sourcefileName;
		} else {
			return getIdentifierPrefix() + "-" + getSimulationNumber();
		}
	}
	
	public String getSourcefileName() {
		return sourcefileName;
	}
	
	public String getRelatedTestName() {
		return relatedTestName;
	}

	public void setRelatedTestName(String relatedTestName) {
		this.relatedTestName = relatedTestName;
	}

	public Map<String, String> getCarriedOverVariables() {
		return carriedOverVariables;
	}

	public void setCarriedOverVariables(Map<String, String> carriedOverVariables) {
		this.carriedOverVariables = carriedOverVariables;
	}

	public String getExecuteOnCondition() {
		return executeOnCondition;
	}

	public void setExecuteOnCondition(String executeOnCondition) {
		this.executeOnCondition = executeOnCondition;
	}

	public List<String> getLogicalValidations() {
		return logicalValidations;
	}

	public void setLogicalValidations(List<String> logicalValidations) {
		this.logicalValidations = logicalValidations;
	}

	public Map<String, String> getCurrentScenarioVariables() {
		return currentScenarioVariables;
	}

	public void setCurrentScenarioVariables(
			Map<String, String> currentScenarioVariables) {
		this.currentScenarioVariables = currentScenarioVariables;
	}

	public boolean isServerApiAuth() {
		return isServerApiAuth;
	}

	public void setServerApiAuth(boolean isServerApiAuth) {
		this.isServerApiAuth = isServerApiAuth;
	}

	public boolean isServerApiTarget() {
		return isServerApiTarget;
	}

	public void setServerApiTarget(boolean isServerApiTarget) {
		this.isServerApiTarget = isServerApiTarget;
	}

	public boolean isDisablePreHooks() {
		return disablePreHooks;
	}

	public void setDisablePreHooks(boolean disablePreHooks) {
		this.disablePreHooks = disablePreHooks;
	}

	public boolean isDisablePostHooks() {
		return disablePostHooks;
	}

	public void setDisablePostHooks(boolean disablePostHooks) {
		this.disablePostHooks = disablePostHooks;
	}

	public boolean isExternalApi() {
		return isExternalApi;
	}

	public void setExternalApi(boolean isExternalApi) {
		this.isExternalApi = isExternalApi;
	}

	public PerformanceConfig getPerfConfig() {
		return perfConfig;
	}

	public void setPerfConfig(PerformanceConfig perfConfig) {
		this.perfConfig = perfConfig;
	}

	@Override
	public String toString() {
		final int maxLen = 10;
		StringBuilder builder = new StringBuilder();
		builder.append("TestCase = [\n");
		builder.append("baseUrl=");
		builder.append(baseUrl);
		builder.append("\nurl=");
		builder.append(url);
		builder.append("\nname=");
		builder.append(name);
		builder.append("\nmethod=");
		builder.append(method);
		builder.append("\ndescription=");
		builder.append(description);
		builder.append("\ncontent=");
		builder.append(content);
		builder.append("\nheaders=");
		builder.append(headers != null ? toString(headers.entrySet(), maxLen)
				: null);
		builder.append("\nexQueryPart=");
		builder.append(exQueryPart);
		builder.append("\nexpectedResCode=");
		builder.append(expectedResCode);
		builder.append("\nexpectedResContentType=");
		builder.append(expectedResContentType);
		builder.append("\nexpectedResContent=");
		builder.append(expectedResContent);
		builder.append("\nskipTest=");
		builder.append(skipTest);
		builder.append("\ndetailedLog=");
		builder.append(detailedLog);
		builder.append("\nsecure=");
		builder.append(secure);
		builder.append("\nexpectedNodes=");
		builder.append(expectedNodes != null ? toString(expectedNodes, maxLen)
				: null);
		builder.append("\nsoapBase=");
		builder.append(soapBase);
		builder.append("\nwsdlKey=");
		builder.append(wsdlKey);
		builder.append("\noperationName=");
		builder.append(operationName);
		builder.append("\nsoapParameterValues=");
		builder.append(soapParameterValues != null ? toString(
				soapParameterValues.entrySet(), maxLen) : null);
		builder.append("\nworkflowContextParameterMap=");
		builder.append(workflowContextParameterMap != null ? toString(
				workflowContextParameterMap.entrySet(), maxLen) : null);
		builder.append("\nsequence=");
		builder.append(sequence);
		builder.append("\nfilesToUpload=");
		builder.append(multipartContent != null ? toString(multipartContent, maxLen)
				: null);
		builder.append("\noutFileName=");
		builder.append(outFileName);
		builder.append("\nrepeatScenarios=");
		builder.append(repeatScenarios != null ? toString(repeatScenarios,
				maxLen) : null);
		builder.append("\nrepeatScenarioProviderName=");
		builder.append(repeatScenarioProviderName);
		builder.append("\nsourcefileName=");
		builder.append(sourcefileName);
		builder.append("\naurl=");
		builder.append(aurl);
		builder.append("\naexQueryPart=");
		builder.append(aexQueryPart);
		builder.append("\nacontent=");
		builder.append(acontent);
		builder.append("\naexpectedNodes=");
		builder.append(aexpectedNodes != null ? toString(aexpectedNodes, maxLen)
				: null);
		builder.append("\nnumberOfExecutions=");
		builder.append(numberOfExecutions);
		builder.append("\nrepeatScenariosConcurrentExecution=");
		builder.append(repeatScenariosConcurrentExecution);
		builder.append("\nstopOnFirstFailureForPerfTest=");
		builder.append(stopOnFirstFailureForPerfTest);
		builder.append("\nfailed=");
		builder.append(failed);
		builder.append("\nsimulationNumber=");
		builder.append(simulationNumber);
		builder.append("\npreWaitMs=");
		builder.append(preWaitMs);
		builder.append("\npostWaitMs=");
		builder.append(postWaitMs);
		builder.append("\nreportResponseContent=");
		builder.append(reportResponseContent);
		builder.append("\npreExecutionDataSourceHookName=");
		builder.append(preExecutionDataSourceHookName);
		builder.append("\npostExecutionDataSourceHookName=");
		builder.append(postExecutionDataSourceHookName);
		builder.append("\nabortOnInvalidContentType=");
		builder.append(abortOnInvalidContentType);
		builder.append("\nabortOnInvalidStatusCode=");
		builder.append(abortOnInvalidStatusCode);
		builder.append("\nrelatedTestName=");
		builder.append(relatedTestName);
		builder.append("\nexecuteOnCondition=");
		builder.append(executeOnCondition);
		builder.append("\nlogicalValidations=");
		builder.append(logicalValidations != null ? toString(logicalValidations, maxLen)
				: null);
		builder.append("]\n");
		return builder.toString();
	}

	private String toString(Collection<?> collection, int maxLen) {
		StringBuilder builder = new StringBuilder();
		builder.append("[");
		int i = 0;
		for (Iterator<?> iterator = collection.iterator(); iterator.hasNext()
				&& i < maxLen; i++) {
			if (i > 0)
				builder.append(", ");
			builder.append(iterator.next());
		}
		builder.append("]");
		return builder.toString();
	}

	public TestCase() {
	}
	
	public TestCase(String[] csvP) {
		boolean valid = false;
		if(csvP!=null) {
			List<String> csvParts = Arrays.asList(csvP);
			for (int i = 0; i < csvParts.size(); i++) {
				setProperties(csvParts, i);
			}
			valid = csvParts.size()>4;
		} else {
			valid = false;
		}
		if(!valid)
		{
			throw new RuntimeException("Invalid CSV data provided for creating TestCase");
		}
	}
	
	public TestCase(String[] csvP, Map<Integer, Integer> mappings) {
		boolean valid = false;
		if(csvP!=null) {
			List<String> csvParts = Arrays.asList(csvP);
			List<String> csvPartsN = new ArrayList<String>(40);
			for (int i = 0; i < 40; i++) {
				csvPartsN.add(null);
			}
			for (int i = 0; i < csvParts.size(); i++) {
				if(mappings.get(i)!=null)
				{
					csvPartsN.set(mappings.get(i), csvParts.get(i));
				}
			}
			for (int i = 0; i < csvPartsN.size(); i++) {
				setProperties(csvPartsN, i);
			}
			valid = csvPartsN.size()>4;
		} else {
			valid = false;
		}
		if(!valid)
		{
			throw new RuntimeException("Invalid CSV data provided for creating TestCase");
		}
	}
	
	public String toCSV() {
		StringBuilder build = new StringBuilder();
		build.append(getCsvValue(getUrl()));
		build.append(",");
		build.append(getCsvValue(getName()));
		build.append(",");
		build.append(getCsvValue(getMethod()));
		build.append(",");
		build.append(getCsvValue(getDescription()));
		build.append(",");
		build.append(getCsvValue(getContent()));
		build.append(",");
		if(getHeaders()!=null)
		{
			for (Map.Entry<String, String> entry : getHeaders().entrySet()) {
				build.append(getInternalCsvValue(entry.getKey()));
				build.append(":");
				build.append(getInternalCsvValue(entry.getValue()));
				build.append("|");
			}
		}
		build.append(",");
		build.append(getCsvValue(getExQueryPart()));
		build.append(",");
		build.append(getExpectedResCode());
		build.append(",");
		build.append(getCsvValue(getExpectedResContentType()));
		build.append(",");
		build.append(getCsvValue(getExpectedResContent()));
		build.append(",");
		if(getExpectedNodes()!=null)
		{
			for (String node : getExpectedNodes()) {
				build.append(getInternalCsvValue(node));
				build.append("|");
			}
		}
		build.append(",");
		build.append(isSkipTest());
		build.append(",");
		build.append(isDetailedLog());
		build.append(",");
		build.append(isSecure());
		build.append(",");
		if(getMultipartContent()!=null)
		{
			for (String node : getMultipartContent()) {
				build.append(getInternalCsvValue(node));
				build.append("|");
			}
		}
		build.append(",");
		build.append(isSoapBase());
		build.append(",");
		if(getSoapParameterValues()!=null)
		{
			for (Map.Entry<String, String> entry : getSoapParameterValues().entrySet()) {
				build.append(getInternalCsvValue(entry.getKey()));
				build.append(":");
				build.append(getInternalCsvValue(entry.getValue()));
				build.append("|");
			}
		}
		build.append(",");
		build.append(getCsvValue(getWsdlKey()));
		build.append(",");
		build.append(getCsvValue(getOperationName()));
		build.append(",");
		build.append(getSequence());
		build.append(",");
		if(getWorkflowContextParameterMap()!=null)
		{
			for (Map.Entry<String, String> entry : getWorkflowContextParameterMap().entrySet()) {
				build.append(getInternalCsvValue(entry.getKey()));
				build.append(":");
				build.append(getInternalCsvValue(entry.getValue()));
				build.append("|");
			}
		}
		build.append(",");
		build.append(getCsvValue(getOutFileName()));
		build.append(",");
		build.append(getCsvValue(getRepeatScenarioProviderName()));
		build.append(",");
		build.append(getNumberOfExecutions());
		build.append(",");
		build.append(isRepeatScenariosConcurrentExecution());
		build.append(",");
		build.append(isStopOnFirstFailureForPerfTest());
		build.append(",");
		build.append(getPreWaitMs());
		build.append(",");
		build.append(getPostWaitMs());
		build.append(",");
		build.append(getReportResponseContent());
		build.append(",");
		build.append(getPreExecutionDataSourceHookName());
		build.append(",");
		build.append(getPostExecutionDataSourceHookName());
		build.append(",");
		build.append(isAbortOnInvalidContentType());
		build.append(",");
		build.append(isAbortOnInvalidStatusCode());
		build.append(",");
		build.append(getRelatedTestName());
		build.append(",");
		build.append(getExecuteOnCondition());
		build.append(",");
		if(getLogicalValidations()!=null)
		{
			for (String node : getLogicalValidations()) {
				build.append(getInternalCsvValue(node));
				build.append(":");
			}
		}
		return build.toString();
	}
	
	private String getCsvValue(String value)
	{
		if(value==null) {
			return "";
		} else {
			return "\"" + value + "\"";
		}
	}
	
	private String getInternalCsvValue(String value)
	{
		if(value==null) {
			return "";
		} else {
			return value.replaceAll(",", "").replaceAll("\\|", "").replaceAll("\n", "");
		}
	}
	
	public void validate(Map<String, String> httpHeaders, String baseUrl) {
		
		if(StringUtils.isBlank(getName()))
			throw new RuntimeException("Blank Name specified for testcase");
		
		if(!isSoapBase())
		{
			if(StringUtils.isBlank(baseUrl) && StringUtils.isBlank(getBaseUrl()))
				throw new RuntimeException("No Base URL specified");
				
			if(StringUtils.isNotBlank(getBaseUrl()))
				Assert.assertTrue("Base URL is not valid", AcceptanceTestContext.URL_VALIDATOR.isValid(getBaseUrl()));
			
			String fullUrl = StringUtils.isBlank(getBaseUrl())?baseUrl:getBaseUrl();
			fullUrl = fullUrl.trim() + "/" + getUrl().trim();
			String parturl = fullUrl.substring(fullUrl.indexOf("://")+3);
			fullUrl = fullUrl.substring(0, fullUrl.indexOf("://")+3) + parturl.replace("//", "/");
			
			if(StringUtils.isBlank(getUrl()))
				throw new RuntimeException("Blank URL sepcified for testcase");
			
			if(StringUtils.isBlank(getMethod()))
				throw new RuntimeException("Blank HTTP Method specified for testcase");
			
			if(!getMethod().toUpperCase().equals(HttpMethod.GET) && !getMethod().toUpperCase().equals(HttpMethod.PUT)
					&& !getMethod().toUpperCase().equals(HttpMethod.POST) && !getMethod().toUpperCase().equals(HttpMethod.DELETE))
				throw new RuntimeException("Invalid HTTP Method specified for testcase");
			
			//if(("POST".equalsIgnoreCase(getMethod()) || "PUT".equalsIgnoreCase(getMethod())) && getHeaders().isEmpty())
			//	throw new RuntimeException("No Content-Type Header provided");
		}
		
		Map<String, String> nHeaders = new HashMap<String, String>();
		if(getHeaders()!=null)
		{
			for (Map.Entry<String, String> entry : getHeaders().entrySet()) {
				if(httpHeaders.containsKey(entry.getKey().toLowerCase())) {
					nHeaders.put(httpHeaders.get(entry.getKey().toLowerCase()), entry.getValue());
				} else {
					logger.info("Custom HTTP Header " + entry.getKey() + " specified for testcase");
					nHeaders.put(entry.getKey(), entry.getValue());
				}
			}
			setHeaders(nHeaders);
		}
		
		if(!isSoapBase() && (StringUtils.isNotBlank(getContent()) || StringUtils.isNotBlank(getContentFile())) && 
				(getHeaders()==null || !getHeaders().containsKey(HttpHeaders.CONTENT_TYPE)))
			throw new RuntimeException("No Content-Type Header provided");
		
		/*try {
			MediaType.valueOf(getHeaders().get(HttpHeaders.CONTENT_TYPE));
		} catch (Exception e) {
			throw new RuntimeException("Invalid Content-Type Header specified for testcase");
		}*/
		
		try {
			Status.fromStatusCode(getExpectedResCode());
		} catch (Exception e) {
			throw new RuntimeException("Invalid expected Status code specified for testcase");
		}
		
		if(StringUtils.isNotBlank(getExpectedResContentType()))
		{
			try {
				MediaType.valueOf(getExpectedResContentType());
				getHeaders().put(HttpHeaders.ACCEPT, getExpectedResContentType());
			} catch (Exception e) {
				//throw new RuntimeException("Invalid expected response content type specified for testcase");
			}
		}
		
		setExQueryPart(getExQueryPart()==null?"":getExQueryPart());
		
		if(!isSoapBase())
		{
			if(getUrl().indexOf("?")==-1 && !getExQueryPart().trim().isEmpty()) {
				setUrl(getUrl() + "?" + getExQueryPart());
			} else if(getUrl().indexOf("?")!=-1 && !getExQueryPart().trim().isEmpty()) {
				setUrl(getUrl() + "&" + getExQueryPart());
			}
		}
		
		setDescription(getDescription()==null?"":getDescription());
		
		if(isSoapBase()) {
			if(getWsdlKey()==null || getOperationName()==null) {
				throw new RuntimeException("Invalid wsdlkey/operationName specified for testcase");
			}
		}
		
		if(getWorkflowContextParameterMap()!=null)
		{
			for (Map.Entry<String, String> entry : getWorkflowContextParameterMap().entrySet()) {
				if(!entry.getKey().trim().matches("(^[a-zA-Z][a-zA-Z0-9_]*)|(^[_][a-zA-Z0-9_]+)")) {
					throw new RuntimeException("Invalid workflow variable name " + entry.getKey() + " specified for testcase...skipping...");
				}
			}
		}
		
		if(getRepeatScenarios()!=null && !getRepeatScenarios().isEmpty()) {
			Assert.assertNull("Only one of repeatScenarios or repeatScenarioProviderName allowed", getRepeatScenarioProviderName());
		}
	}

	public TestCase(TestCase other) {
		super();
		this.url = other.url;
		this.name = other.name;
		this.method = other.method;
		this.description = other.description;
		this.content = other.content;
		if(other.headers!=null)
		{
			this.headers = new HashMap<String, String>(other.headers);
		}
		this.exQueryPart = other.exQueryPart;
		this.expectedResCode = other.expectedResCode;
		this.expectedResContentType = other.expectedResContentType;
		this.expectedResContent = other.expectedResContent;
		this.skipTest = other.skipTest;
		this.detailedLog = other.detailedLog;
		this.secure = other.secure;
		if(other.expectedNodes!=null)
		{
			this.expectedNodes = new ArrayList<String>(other.expectedNodes);
		}
		this.soapBase = other.soapBase;
		this.wsdlKey = other.wsdlKey;
		this.operationName = other.operationName;
		if(other.soapParameterValues!=null)
		{
			this.soapParameterValues = new HashMap<String, String>(other.soapParameterValues);
		}
		if(other.workflowContextParameterMap!=null)
		{
			this.workflowContextParameterMap = new HashMap<String, String>(other.workflowContextParameterMap);
		}
		this.sequence = other.sequence;
		this.multipartContent = other.multipartContent;
		this.outFileName = other.outFileName;
		if(other.repeatScenarios!=null)
		{
			this.repeatScenarios = new ArrayList<Map<String,String>>(other.repeatScenarios);
		}
		if(other.repeatScenariosOrig!=null)
		{
			this.repeatScenariosOrig = new ArrayList<Map<String,String>>(other.repeatScenariosOrig);
		}
		this.sourcefileName = other.sourcefileName;
		this.aurl = other.aurl;
		this.aexQueryPart = other.aexQueryPart;
		this.acontent = other.acontent;
		this.aexpectedNodes = new ArrayList<String>();
		this.numberOfExecutions = other.numberOfExecutions;
		this.repeatScenariosConcurrentExecution = other.repeatScenariosConcurrentExecution;
		this.stopOnFirstFailureForPerfTest = other.stopOnFirstFailureForPerfTest;
		this.failed = other.failed;
		this.simulationNumber = other.simulationNumber;
		this.baseUrl = other.baseUrl;
		this.preWaitMs = other.preWaitMs;
		this.postWaitMs = other.postWaitMs;
		this.reportResponseContent = other.reportResponseContent;
		this.repeatScenarioProviderName = other.repeatScenarioProviderName;
		this.preExecutionDataSourceHookName = other.preExecutionDataSourceHookName;
		this.postExecutionDataSourceHookName = other.postExecutionDataSourceHookName;
		this.abortOnInvalidContentType = other.abortOnInvalidContentType;
		this.abortOnInvalidStatusCode = other.abortOnInvalidStatusCode;
		this.identifierPrefix = other.identifierPrefix;
		this.relatedTestName = other.relatedTestName;
		this.carriedOverVariables = other.carriedOverVariables;
		this.executeOnCondition = other.executeOnCondition;
		this.logicalValidations = other.logicalValidations;
		this.currentScenarioVariables = other.currentScenarioVariables;
		this.isServerApiAuth = other.isServerApiAuth;
		this.isServerApiTarget = other.isServerApiTarget;
		this.isExternalApi = other.isExternalApi;
		this.perfConfig = other.perfConfig;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (abortOnInvalidContentType ? 1231 : 1237);
		result = prime * result + (abortOnInvalidStatusCode ? 1231 : 1237);
		result = prime * result
				+ ((acontent == null) ? 0 : acontent.hashCode());
		result = prime * result
				+ ((aexQueryPart == null) ? 0 : aexQueryPart.hashCode());
		result = prime * result
				+ ((aexpectedNodes == null) ? 0 : aexpectedNodes.hashCode());
		result = prime * result + ((aurl == null) ? 0 : aurl.hashCode());
		result = prime * result + ((baseUrl == null) ? 0 : baseUrl.hashCode());
		result = prime
				* result
				+ ((carriedOverVariables == null) ? 0 : carriedOverVariables
						.hashCode());
		result = prime * result + ((content == null) ? 0 : content.hashCode());
		result = prime * result
				+ ((description == null) ? 0 : description.hashCode());
		result = prime * result + (detailedLog ? 1231 : 1237);
		result = prime * result
				+ ((exQueryPart == null) ? 0 : exQueryPart.hashCode());
		result = prime
				* result
				+ ((executeOnCondition == null) ? 0 : executeOnCondition
						.hashCode());
		result = prime * result
				+ ((expectedNodes == null) ? 0 : expectedNodes.hashCode());
		result = prime * result + expectedResCode;
		result = prime
				* result
				+ ((expectedResContent == null) ? 0 : expectedResContent
						.hashCode());
		result = prime
				* result
				+ ((expectedResContentType == null) ? 0
						: expectedResContentType.hashCode());
		result = prime * result
				+ ((multipartContent == null) ? 0 : multipartContent.hashCode());
		result = prime * result + ((headers == null) ? 0 : headers.hashCode());
		result = prime
				* result
				+ ((identifierPrefix == null) ? 0 : identifierPrefix.hashCode());
		result = prime
				* result
				+ ((logicalValidations == null) ? 0 : logicalValidations
						.hashCode());
		result = prime * result + ((method == null) ? 0 : method.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime
				* result
				+ ((numberOfExecutions == null) ? 0 : numberOfExecutions
						.hashCode());
		result = prime * result
				+ ((operationName == null) ? 0 : operationName.hashCode());
		result = prime * result
				+ ((outFileName == null) ? 0 : outFileName.hashCode());
		result = prime
				* result
				+ ((postExecutionDataSourceHookName == null) ? 0
						: postExecutionDataSourceHookName.hashCode());
		result = prime * result
				+ ((postWaitMs == null) ? 0 : postWaitMs.hashCode());
		result = prime
				* result
				+ ((preExecutionDataSourceHookName == null) ? 0
						: preExecutionDataSourceHookName.hashCode());
		result = prime * result
				+ ((preWaitMs == null) ? 0 : preWaitMs.hashCode());
		result = prime * result
				+ ((relatedTestName == null) ? 0 : relatedTestName.hashCode());
		result = prime
				* result
				+ ((repeatScenarioProviderName == null) ? 0
						: repeatScenarioProviderName.hashCode());
		result = prime * result
				+ ((repeatScenarios == null) ? 0 : repeatScenarios.hashCode());
		result = prime * result
				+ (repeatScenariosConcurrentExecution ? 1231 : 1237);
		result = prime
				* result
				+ ((repeatScenariosOrig == null) ? 0 : repeatScenariosOrig
						.hashCode());
		result = prime
				* result
				+ ((reportResponseContent == null) ? 0 : reportResponseContent
						.hashCode());
		result = prime * result + (secure ? 1231 : 1237);
		result = prime * result + sequence;
		result = prime
				* result
				+ ((simulationNumber == null) ? 0 : simulationNumber.hashCode());
		result = prime * result + (skipTest ? 1231 : 1237);
		result = prime * result + (soapBase ? 1231 : 1237);
		result = prime
				* result
				+ ((soapParameterValues == null) ? 0 : soapParameterValues
						.hashCode());
		result = prime * result
				+ ((sourcefileName == null) ? 0 : sourcefileName.hashCode());
		result = prime * result + (stopOnFirstFailureForPerfTest ? 1231 : 1237);
		result = prime * result + ((url == null) ? 0 : url.hashCode());
		result = prime
				* result
				+ ((workflowContextParameterMap == null) ? 0
						: workflowContextParameterMap.hashCode());
		result = prime * result + ((wsdlKey == null) ? 0 : wsdlKey.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TestCase other = (TestCase) obj;
		if (abortOnInvalidContentType != other.abortOnInvalidContentType)
			return false;
		if (abortOnInvalidStatusCode != other.abortOnInvalidStatusCode)
			return false;
		if (acontent == null) {
			if (other.acontent != null)
				return false;
		} else if (!acontent.equals(other.acontent))
			return false;
		if (aexQueryPart == null) {
			if (other.aexQueryPart != null)
				return false;
		} else if (!aexQueryPart.equals(other.aexQueryPart))
			return false;
		if (aexpectedNodes == null) {
			if (other.aexpectedNodes != null)
				return false;
		} else if (!aexpectedNodes.equals(other.aexpectedNodes))
			return false;
		if (aurl == null) {
			if (other.aurl != null)
				return false;
		} else if (!aurl.equals(other.aurl))
			return false;
		if (baseUrl == null) {
			if (other.baseUrl != null)
				return false;
		} else if (!baseUrl.equals(other.baseUrl))
			return false;
		if (carriedOverVariables == null) {
			if (other.carriedOverVariables != null)
				return false;
		} else if (!carriedOverVariables.equals(other.carriedOverVariables))
			return false;
		if (content == null) {
			if (other.content != null)
				return false;
		} else if (!content.equals(other.content))
			return false;
		if (description == null) {
			if (other.description != null)
				return false;
		} else if (!description.equals(other.description))
			return false;
		if (detailedLog != other.detailedLog)
			return false;
		if (exQueryPart == null) {
			if (other.exQueryPart != null)
				return false;
		} else if (!exQueryPart.equals(other.exQueryPart))
			return false;
		if (executeOnCondition == null) {
			if (other.executeOnCondition != null)
				return false;
		} else if (!executeOnCondition.equals(other.executeOnCondition))
			return false;
		if (expectedNodes == null) {
			if (other.expectedNodes != null)
				return false;
		} else if (!expectedNodes.equals(other.expectedNodes))
			return false;
		if (expectedResCode != other.expectedResCode)
			return false;
		if (expectedResContent == null) {
			if (other.expectedResContent != null)
				return false;
		} else if (!expectedResContent.equals(other.expectedResContent))
			return false;
		if (expectedResContentType == null) {
			if (other.expectedResContentType != null)
				return false;
		} else if (!expectedResContentType.equals(other.expectedResContentType))
			return false;
		if (multipartContent == null) {
			if (other.multipartContent != null)
				return false;
		} else if (!multipartContent.equals(other.multipartContent))
			return false;
		if (headers == null) {
			if (other.headers != null)
				return false;
		} else if (!headers.equals(other.headers))
			return false;
		if (identifierPrefix == null) {
			if (other.identifierPrefix != null)
				return false;
		} else if (!identifierPrefix.equals(other.identifierPrefix))
			return false;
		if (logicalValidations == null) {
			if (other.logicalValidations != null)
				return false;
		} else if (!logicalValidations.equals(other.logicalValidations))
			return false;
		if (method == null) {
			if (other.method != null)
				return false;
		} else if (!method.equals(other.method))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (numberOfExecutions == null) {
			if (other.numberOfExecutions != null)
				return false;
		} else if (!numberOfExecutions.equals(other.numberOfExecutions))
			return false;
		if (operationName == null) {
			if (other.operationName != null)
				return false;
		} else if (!operationName.equals(other.operationName))
			return false;
		if (outFileName == null) {
			if (other.outFileName != null)
				return false;
		} else if (!outFileName.equals(other.outFileName))
			return false;
		if (postExecutionDataSourceHookName == null) {
			if (other.postExecutionDataSourceHookName != null)
				return false;
		} else if (!postExecutionDataSourceHookName
				.equals(other.postExecutionDataSourceHookName))
			return false;
		if (postWaitMs == null) {
			if (other.postWaitMs != null)
				return false;
		} else if (!postWaitMs.equals(other.postWaitMs))
			return false;
		if (preExecutionDataSourceHookName == null) {
			if (other.preExecutionDataSourceHookName != null)
				return false;
		} else if (!preExecutionDataSourceHookName
				.equals(other.preExecutionDataSourceHookName))
			return false;
		if (preWaitMs == null) {
			if (other.preWaitMs != null)
				return false;
		} else if (!preWaitMs.equals(other.preWaitMs))
			return false;
		if (relatedTestName == null) {
			if (other.relatedTestName != null)
				return false;
		} else if (!relatedTestName.equals(other.relatedTestName))
			return false;
		if (repeatScenarioProviderName == null) {
			if (other.repeatScenarioProviderName != null)
				return false;
		} else if (!repeatScenarioProviderName
				.equals(other.repeatScenarioProviderName))
			return false;
		if (repeatScenarios == null) {
			if (other.repeatScenarios != null)
				return false;
		} else if (!repeatScenarios.equals(other.repeatScenarios))
			return false;
		if (repeatScenariosConcurrentExecution != other.repeatScenariosConcurrentExecution)
			return false;
		if (repeatScenariosOrig == null) {
			if (other.repeatScenariosOrig != null)
				return false;
		} else if (!repeatScenariosOrig.equals(other.repeatScenariosOrig))
			return false;
		if (reportResponseContent == null) {
			if (other.reportResponseContent != null)
				return false;
		} else if (!reportResponseContent.equals(other.reportResponseContent))
			return false;
		if (secure != other.secure)
			return false;
		if (sequence != other.sequence)
			return false;
		if (simulationNumber == null) {
			if (other.simulationNumber != null)
				return false;
		} else if (!simulationNumber.equals(other.simulationNumber))
			return false;
		if (skipTest != other.skipTest)
			return false;
		if (soapBase != other.soapBase)
			return false;
		if (soapParameterValues == null) {
			if (other.soapParameterValues != null)
				return false;
		} else if (!soapParameterValues.equals(other.soapParameterValues))
			return false;
		if (sourcefileName == null) {
			if (other.sourcefileName != null)
				return false;
		} else if (!sourcefileName.equals(other.sourcefileName))
			return false;
		if (stopOnFirstFailureForPerfTest != other.stopOnFirstFailureForPerfTest)
			return false;
		if (url == null) {
			if (other.url != null)
				return false;
		} else if (!url.equals(other.url))
			return false;
		if (workflowContextParameterMap == null) {
			if (other.workflowContextParameterMap != null)
				return false;
		} else if (!workflowContextParameterMap
				.equals(other.workflowContextParameterMap))
			return false;
		if (wsdlKey == null) {
			if (other.wsdlKey != null)
				return false;
		} else if (!wsdlKey.equals(other.wsdlKey))
			return false;
		return true;
	}
	
	protected static List<String> getAndSanitizeParts(String csvLine) {
		
		Matcher match = quotedString.matcher(csvLine);
		int i = 0;
		List<String> csvParts = new ArrayList<String>();
		Map<String, String> params = new HashMap<String, String>();
		while(match.find())
		{
			csvLine = csvLine.replace(match.group(0), PRE+i);
			params.put(PRE+i, match.group(2));
			i++;
		}
		String[] parts = csvLine.split(",");
		for (i=0;i<parts.length;i++) {
			String part = parts[i];
			if(params.containsKey(part)) {
				part = params.get(part);
			}
			csvParts.add(part);
		}
		return csvParts;
	}
	
	private void setProperties(List<String> csvParts, int index)
	{
		if(index==0 && csvParts.size()>0 && csvParts.get(0)!=null)
			setUrl(csvParts.get(0));
		if(index==1 && csvParts.size()>1 && csvParts.get(1)!=null)
			setName(csvParts.get(1));
		if(index==2 && csvParts.size()>2 && csvParts.get(2)!=null)
			setMethod(csvParts.get(2));
		if(index==3 && csvParts.size()>3 && csvParts.get(3)!=null)
			setDescription(csvParts.get(3));
		if(index==4 && csvParts.size()>4 && StringUtils.isNotBlank(csvParts.get(4)))
			setContent(StringEscapeUtils.unescapeJava(csvParts.get(4)));
		else if(index==4 && csvParts.size()>4)
			setContent(null);
		if(index==5 && csvParts.size()>5 && csvParts.get(5)!=null)
		{	
			setHeaders(new HashMap<String, String>());
			String[] headers = csvParts.get(5).split("\\|");
			for (String header : headers) {
				String[] kv = header.split(":");
				if(kv.length!=2) {
					logger.error("Invalid Header specified for testcase - " + header);
				}
				if(!kv[0].isEmpty() && !kv[1].isEmpty())
				{
					getHeaders().put(kv[0], kv[1]);
				}
			}
		}
		if(index==6 && csvParts.size()>6 && csvParts.get(6)!=null)
			setExQueryPart(csvParts.get(6));
		if(index==7 && csvParts.size()>7 && csvParts.get(7)!=null)
			setExpectedResCode(Integer.parseInt(csvParts.get(7)));
		if(index==8 && csvParts.size()>8 && csvParts.get(8)!=null)
			setExpectedResContentType(csvParts.get(8));
		if(index==9 && csvParts.size()>9 && StringUtils.isNotBlank(csvParts.get(9)))
			setExpectedResContent(StringEscapeUtils.unescapeJava(csvParts.get(9)));
		else if(index==9 && csvParts.size()>9)
			setExpectedResContent(null);
		if(index==10 && csvParts.size()>10 && csvParts.get(10)!=null)
		{
			setExpectedNodes(new ArrayList<String>());
			String[] nodes = csvParts.get(10).split("\\|");
			for (String node : nodes) {
				if(!node.isEmpty())
				{
					getExpectedNodes().add(node);
				}
			}
		}
		if(index==11 && csvParts.size()>11 && csvParts.get(11)!=null)
			setSkipTest(getBooleanValue(csvParts.get(11)));
		if(index==12 && csvParts.size()>12 && csvParts.get(12)!=null)
			setDetailedLog(getBooleanValue(csvParts.get(12)));
		if(index==13 && csvParts.size()>13 && csvParts.get(13)!=null)
			setSecure(getBooleanValue(csvParts.get(13)));
		if(index==14 && csvParts.size()>14 && csvParts.get(14)!=null)
		{
			setMultipartContent(new ArrayList<String>());
			String[] files = csvParts.get(14).split("\\|");
			for (String multfile : files) {
				if(!multfile.isEmpty())
				{
					String[] mulff = multfile.split(":");
					//controlname:type:filenameOrText:contentType
					if(mulff.length==4 || mulff.length==3) {
						getMultipartContent().add(multfile);
					}
				}
			}
		}
		if(index==15 && csvParts.size()>15 && csvParts.get(15)!=null)
			setSoapBase(getBooleanValue(csvParts.get(15)));
		if(index==16 && csvParts.size()>16 && csvParts.get(16)!=null)
		{
			setSoapParameterValues(new HashMap<String, String>());
			String[] soapVals = csvParts.get(16).split("\\|");
			for (String soapVal : soapVals) {
				String[] kv = soapVal.split(":");
				if(kv.length!=2) {
					logger.error("Invalid Soap Parameter key/value specified for testcase - " + soapVal);
				}
				if(!kv[0].isEmpty() && !kv[1].isEmpty())
				{
					getSoapParameterValues().put(kv[0], kv[1]);
				}
			}
		}
		if(index==17 && csvParts.size()>17 && csvParts.get(17)!=null)
			setWsdlKey(csvParts.get(17));
		if(index==18 && csvParts.size()>18 && csvParts.get(18)!=null)
			setOperationName(csvParts.get(18));
		if(index==19 && csvParts.size()>19 && csvParts.get(19)!=null)
			setSequence(Integer.parseInt(csvParts.get(19)));
		if(index==20 && csvParts.size()>20 && csvParts.get(20)!=null)
		{	
			setWorkflowContextParameterMap(new HashMap<String, String>());
			String[] workflowParams = csvParts.get(20).split("\\|");
			for (String workflowParam : workflowParams) {
				String[] kv = workflowParam.split(":");
				if(kv.length!=2) {
					logger.error("Invalid Workflow Parameter key/value specified for testcase - " + workflowParam);
				}
				if(!kv[0].isEmpty() && !kv[1].isEmpty())
				{
					getWorkflowContextParameterMap().put(kv[0], kv[1]);
				}
			}
		}
		if(index==21 && csvParts.size()>21 && csvParts.get(21)!=null)
			setOutFileName(csvParts.get(21));
		if(index==22 && csvParts.size()>22 && csvParts.get(22)!=null)
			setRepeatScenarioProviderName(csvParts.get(22));
		if(index==23 && csvParts.size()>23 && csvParts.get(23)!=null)
			setNumberOfExecutions(Integer.parseInt(csvParts.get(23)));
		if(index==24 && csvParts.size()>24 && csvParts.get(24)!=null)
			setRepeatScenariosConcurrentExecution(getBooleanValue(csvParts.get(24)));
		if(index==25 && csvParts.size()>25 && csvParts.get(25)!=null)
			setStopOnFirstFailureForPerfTest(getBooleanValue(csvParts.get(25)));
		if(index==26 && csvParts.size()>26 && csvParts.get(26)!=null)
			setPreWaitMs(Long.parseLong(csvParts.get(26)));
		if(index==27 && csvParts.size()>27 && csvParts.get(27)!=null)
			setPostWaitMs(Long.parseLong(csvParts.get(27)));
		if(index==28 && csvParts.size()>28 && csvParts.get(28)!=null)
			setReportResponseContent(getBooleanValue(csvParts.get(28)));
		if(index==29 && csvParts.size()>29 && csvParts.get(29)!=null)
			setPreExecutionDataSourceHookName(csvParts.get(29));
		if(index==30 && csvParts.size()>30 && csvParts.get(30)!=null)
			setPostExecutionDataSourceHookName(csvParts.get(30));
		if(index==31 && csvParts.size()>31 && csvParts.get(31)!=null)
			setAbortOnInvalidContentType(getBooleanValue(csvParts.get(31)));
		if(index==32 && csvParts.size()>32 && csvParts.get(32)!=null)
			setAbortOnInvalidStatusCode(getBooleanValue(csvParts.get(32)));
		if(index==33 && csvParts.size()>33 && csvParts.get(33)!=null)
			setRelatedTestName(csvParts.get(33));
		if(index==34 && csvParts.size()>34 && csvParts.get(34)!=null)
			setExecuteOnCondition(csvParts.get(34));
		if(index==35 && csvParts.size()>35 && csvParts.get(35)!=null)
		{
			setLogicalValidations(new ArrayList<String>());
			String[] nodes = csvParts.get(35).split(":");
			for (String node : nodes) {
				if(!node.isEmpty())
				{
					getLogicalValidations().add(node);
				}
			}
		}
		if(index==35)
		{	
			if(getNumberOfExecutions()==null)
				setNumberOfExecutions(1);
			if(getHeaders().size()==0)
				setHeaders(null);
			if(getExpectedNodes().size()==0)
				setExpectedNodes(null);
			if(getWorkflowContextParameterMap().size()==0)
				setWorkflowContextParameterMap(null);
			if(getSoapParameterValues().size()==0)
				setSoapParameterValues(null);
			if(getRepeatScenarios().size()==0)
				setRepeatScenarios(null);
			if(getMultipartContent().size()==0)
				setMultipartContent(null);
		}
	}
	
	private boolean getBooleanValue(String value)
	{
		if(value.trim().equalsIgnoreCase("y") || value.trim().equalsIgnoreCase("yes")
				|| value.trim().equalsIgnoreCase("1"))
			return true;
		else if(value.trim().equalsIgnoreCase("n") || value.trim().equalsIgnoreCase("no")
				|| value.trim().equalsIgnoreCase("0"))
			return false;
		return Boolean.valueOf(value);
	}
}
