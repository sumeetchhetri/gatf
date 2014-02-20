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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;

import org.apache.log4j.Logger;
import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;
import org.junit.Assert;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamOmitField;


@XStreamAlias("TestCase")
@JsonAutoDetect(getterVisibility=Visibility.NONE, fieldVisibility=Visibility.ANY, isGetterVisibility=Visibility.NONE)
@JsonSerialize(include=Inclusion.NON_NULL)
public class TestCase {

	private static final Logger logger = Logger.getLogger(TestCase.class);
	
	@XStreamAsAttribute
	private String baseUrl;
	
	@XStreamAsAttribute
	private String url;
	
	@XStreamAsAttribute
	private String name;
	
	@XStreamAsAttribute
	private String method;
	
	private String description;
	
	private String content;
	
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
	
	@XStreamAsAttribute
	private String wsdlKey;
	
	@XStreamAsAttribute
	private String operationName;
	
	private Map<String, String> soapParameterValues = new HashMap<String, String>();
	
	private Map<String, String> workflowContextParameterMap = new HashMap<String, String>();
	
	@XStreamAsAttribute
	private int sequence = 0;
	
	private List<String> filesToUpload = new ArrayList<String>();
	
	@XStreamAsAttribute
	private String outFileName;
	
	private List<Map<String, String>> repeatScenarios = new ArrayList<Map<String,String>>();
	
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
	private Integer numberOfExecutions = 0;
	
	@XStreamAsAttribute
	private boolean repeatScenariosConcurrentExecution;
	
	@XStreamAsAttribute	
	private boolean stopOnFirstFailureForPerfTest;

	@XStreamOmitField
	@JsonIgnore
	private volatile boolean failed;
	
	@XStreamOmitField
	private Integer simulationNumber;
	
	private String preExecuteHook;
	
	private String postExecuteHook;
	
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

	public List<String> getFilesToUpload() {
		return filesToUpload;
	}

	public void setFilesToUpload(List<String> filesToUpload) {
		this.filesToUpload = filesToUpload;
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

	public String getPreExecuteHook() {
		return preExecuteHook;
	}

	public void setPreExecuteHook(String preExecuteHook) {
		this.preExecuteHook = preExecuteHook;
	}

	public String getPostExecuteHook() {
		return postExecuteHook;
	}

	public void setPostExecuteHook(String postExecuteHook) {
		this.postExecuteHook = postExecuteHook;
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
			return "Run-" + getSimulationNumber();
		}
	}
	
	public String getSourcefileName() {
		return sourcefileName;
	}

	@Override
	public String toString() {
		return "TestCase [name=" + name + ", url=" + url + ", description=" + description
				+ ", method=" + method + ", \ncontent=" + content
				+ ",\n headers=" + headers + ", exQueryPart=" + exQueryPart
				+ ", expectedResCode=" + expectedResCode
				+ ", expectedResContentType=" + expectedResContentType
				+ ", \nexpectedResContent=" + expectedResContent + ",\n skipTest=" + skipTest 
				+ ", detailedLog=" + detailedLog + ", expectedNodes=" + expectedNodes
				+ ", filesToUpload=" + filesToUpload
				+ ", secure=" + secure + (!soapBase?"":(", soapBase = " +  soapBase
				+ ", soapParameterValues = " + soapParameterValues + ", wsdlKey = " +  wsdlKey
				+ ", operationName = " + operationName)) + "]";
	}
	
	public TestCase() {
	}
	
	public TestCase(String csvLine) {
		boolean valid = false;
		if(csvLine!=null) {
			String[] csvParts = csvLine.split(",");
			if(csvParts.length>=15) {
				setUrl(csvParts[0]);
				setName(csvParts[1]);
				setMethod(csvParts[2]);
				setDescription(csvParts[3]);
				setContent(csvParts[4]);
				String[] headers = csvParts[5].split("\\|");
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
				setExQueryPart(csvParts[6]);
				setExpectedResCode(Integer.parseInt(csvParts[7]));
				setExpectedResContentType(csvParts[8]);
				setExpectedResContent(csvParts[9]);
				String[] nodes = csvParts[10].split("\\|");
				for (String node : nodes) {
					if(!node.isEmpty())
					{
						getExpectedNodes().add(node);
					}
				}
				setSkipTest(Boolean.valueOf(csvParts[11]));
				setDetailedLog(Boolean.valueOf(csvParts[12]));
				setSecure(Boolean.valueOf(csvParts[13]));
				String[] files = csvParts[14].split("\\|");
				for (String multfile : files) {
					if(!multfile.isEmpty())
					{
						String[] mulff = multfile.split(":");
						//controlname,type,filenameOrText,contentType
						if(mulff.length==4 || mulff.length==3) {
							getFilesToUpload().add(multfile);
						}
					}
				}
				if(csvParts.length>=19) {
					setSoapBase(Boolean.valueOf(csvParts[15]));
					String[] soapVals = csvParts[16].split("\\|");
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
					setWsdlKey(csvParts[17]);
					setOperationName(csvParts[18]);
					setSequence(Integer.parseInt(csvParts[19]));
					String[] workflowParams = csvParts[20].split("\\|");
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
					setOutFileName(csvParts[21]);
				}
				valid = true;
			} else {
				valid = false;
			}
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
		for (Map.Entry<String, String> entry : getHeaders().entrySet()) {
			build.append(getCsvValue(entry.getKey()));
			build.append(":");
			build.append(getCsvValue(entry.getValue()));
			build.append("|");
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
		for (String node : getExpectedNodes()) {
			build.append(getCsvValue(node));
			build.append("|");
		}
		build.append(",");
		build.append(isSkipTest());
		build.append(",");
		build.append(isDetailedLog());
		build.append(",");
		build.append(isSecure());
		build.append(",");
		for (String node : getFilesToUpload()) {
			build.append(getCsvValue(node));
			build.append("|");
		}
		build.append(",");
		build.append(isSoapBase());
		build.append(",");
		for (Map.Entry<String, String> entry : getSoapParameterValues().entrySet()) {
			build.append(getCsvValue(entry.getKey()));
			build.append(":");
			build.append(getCsvValue(entry.getValue()));
			build.append("|");
		}
		build.append(",");
		build.append(getCsvValue(getWsdlKey()));
		build.append(",");
		build.append(getCsvValue(getOperationName()));
		build.append(",");
		build.append(getSequence());
		build.append(",");
		for (Map.Entry<String, String> entry : getWorkflowContextParameterMap().entrySet()) {
			build.append(getCsvValue(entry.getKey()));
			build.append(":");
			build.append(getCsvValue(entry.getValue()));
			build.append("|");
		}
		build.append(",");
		build.append(getCsvValue(getOutFileName()));
		return build.toString();
	}
	
	private String getCsvValue(String value)
	{
		if(value==null) {
			return "";
		} else {
			return value.replaceAll(",", "").replaceAll("\\|", "").replaceAll("\n", "");
		}
	}
	
	public void validate(Map<String, String> httpHeaders) {
		
		if(getName()==null || getName().trim().isEmpty())
			throw new RuntimeException("Blank Name specified for testcase");
		
		if(!isSoapBase())
		{
			if(getUrl()==null || getUrl().trim().isEmpty())
				throw new RuntimeException("Blank URL sepcified for testcase");
			
			if(getMethod()==null || getMethod().trim().isEmpty())
				throw new RuntimeException("Blank HTTP Method specified for testcase");
			
			if(!getMethod().toUpperCase().equals(HttpMethod.GET) && !getMethod().toUpperCase().equals(HttpMethod.PUT)
					&& !getMethod().toUpperCase().equals(HttpMethod.POST) && !getMethod().toUpperCase().equals(HttpMethod.DELETE))
				throw new RuntimeException("Invalid HTTP Method specified for testcase");
			
			if(getHeaders().isEmpty() && ("POST".equals(getMethod()) || "PUT".equals(getMethod())))
				throw new RuntimeException("No Content-Type Header provided");
		}
		
		Map<String, String> nHeaders = new HashMap<String, String>();
		for (Map.Entry<String, String> entry : getHeaders().entrySet()) {
			if(httpHeaders.containsKey(entry.getKey().toLowerCase())) {
				nHeaders.put(httpHeaders.get(entry.getKey().toLowerCase()), entry.getValue());
			} else {
				logger.info("Custom HTTP Header " + entry.getKey() + " specified for testcase");
				nHeaders.put(entry.getKey(), entry.getValue());
			}
		}
		setHeaders(nHeaders);
		
		/*if(!getHeaders().containsKey(HttpHeaders.CONTENT_TYPE))
			throw new RuntimeException("No Content-Type Header provided");
		
		try {
			MediaType.valueOf(getHeaders().get(HttpHeaders.CONTENT_TYPE));
		} catch (Exception e) {
			throw new RuntimeException("Invalid Content-Type Header specified for testcase");
		}*/
		
		try {
			Status.fromStatusCode(getExpectedResCode());
		} catch (Exception e) {
			throw new RuntimeException("Invalid expected Status code specified for testcase");
		}
		
		if(getExpectedResContentType()!=null)
		{
			try {
				MediaType.valueOf(getExpectedResContentType());
				getHeaders().put(HttpHeaders.ACCEPT, getExpectedResContentType());
			} catch (Exception e) {
				//throw new RuntimeException("Invalid expected response content type specified for testcase");
			}
		}
		
		setExQueryPart(getExQueryPart()==null?"":getExQueryPart());
		
		if(getUrl().indexOf("?")==-1 && !getExQueryPart().trim().isEmpty()) {
			setUrl(getUrl() + "?" + getExQueryPart());
		} else if(getUrl().indexOf("?")!=-1 && !getExQueryPart().trim().isEmpty()) {
			setUrl(getUrl() + "&" + getExQueryPart());
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
		this.filesToUpload = other.filesToUpload;
		this.outFileName = other.outFileName;
		if(other.repeatScenarios!=null)
		{
			this.repeatScenarios = new ArrayList<Map<String,String>>(other.repeatScenarios);
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
	}
	
	
}
