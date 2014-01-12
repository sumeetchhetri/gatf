package com.gatf.test;

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

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;

import org.apache.log4j.Logger;
import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

import com.jayway.restassured.internal.http.Method;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;


@XStreamAlias("TestCase")
@JsonAutoDetect(getterVisibility=Visibility.NONE, fieldVisibility=Visibility.ANY, isGetterVisibility=Visibility.NONE)
@JsonSerialize(include=Inclusion.NON_NULL)
public class TestCase {

	private static final Logger logger = Logger.getLogger(TestCase.class);
	
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

	@Override
	public String toString() {
		return "TestCase [name=" + name + ", url=" + url + ", description=" + description
				+ ", method=" + method + ", \ncontent=" + content
				+ ",\n headers=" + headers + ", exQueryPart=" + exQueryPart
				+ ", expectedResCode=" + expectedResCode
				+ ", expectedResContentType=" + expectedResContentType
				+ ", \nexpectedResContent=" + expectedResContent + ",\n skipTest=" + skipTest 
				+ ", detailedLog=" + detailedLog + ", expectedNodes=" + expectedNodes
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
			if(csvParts.length>=14) {
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
				if(csvParts.length==18) {
					setSoapBase(Boolean.valueOf(csvParts[14]));
					String[] soapVals = csvParts[15].split("\\|");
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
					setWsdlKey(csvParts[16]);
					setOperationName(csvParts[17]);
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
	
public void validate(Map<String, String> httpHeaders) {
		
		if(getName()==null || getName().trim().isEmpty())
			throw new RuntimeException("Blank Name specified for testcase");
		
		if(!isSoapBase())
		{
			if(getUrl()==null || getUrl().trim().isEmpty())
				throw new RuntimeException("Blank URL sepcified for testcase");
			
			if(getMethod()==null || getMethod().trim().isEmpty())
				throw new RuntimeException("Blank HTTP Method specified for testcase");
			try {
				setMethod(Method.valueOf(getMethod().toUpperCase()).name());
			} catch (Exception e) {
				throw new RuntimeException("Invalid HTTP Method specified for testcase");
			}
			
			if(getHeaders().isEmpty())
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
				throw new RuntimeException("Invalid expected response content type specified for testcase");
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
	}
}
