/*
    Copyright 2013-2016, Sumeet Chhetri
    
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
package com.gatf.executor.validator;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.junit.Assert;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import com.gatf.executor.core.WorkflowContextHandler;
import com.gatf.executor.core.WorkflowContextHandler.ResponseType;
import com.gatf.executor.report.TestCaseReport;

/**
 * @author Sumeet Chhetri
 * The validator that handles xml level node validations after test case execution
 */
public class XMLResponseValidator extends ResponseValidator {

	protected Object getInternalObject(TestCaseReport testCaseReport) throws Exception
	{
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = dbf.newDocumentBuilder();
		Document xmlDocument = db.parse(new ByteArrayInputStream(testCaseReport.getResponseContent().getBytes()));
		return xmlDocument;
	}

	protected String getNodeValue(Object intObj, String node) throws Exception {
	    NodeList xmlNodeList = null;
	    String xmlValue = null;
	    try
        {
	        xmlNodeList = getNodeByXpath(node, (Document)intObj);
	        xmlValue = getXMLNodeValue(xmlNodeList.item(0));
        }
        catch (Exception e)
        {
        }
	    //Assert.assertTrue("Expected Node " + node + " is null", xmlNodeList!=null && xmlNodeList.getLength()>0);
	    return xmlValue;
	}

	protected ResponseType getType() {
		return ResponseType.XML;
	}

	protected List<Map<String, String>> getResponseMappedValue(String expression, String propNames, Object initObj) throws Exception {
		expression = expression.replaceAll("\\.", "\\/");
		if(expression.equals("")) {
			expression = "/*";
		}
		
		if(expression.charAt(0)!='/')
			expression = "/" + expression;
		
		XPath xPath =  XPathFactory.newInstance().newXPath();
		NodeList xmlNodeList = (NodeList) xPath.compile(expression).evaluate((Document)initObj, XPathConstants.NODESET);
		Assert.assertTrue("Workflow xml variable " + expression +" is null",  
				xmlNodeList!=null && xmlNodeList.getLength()>0);
		
		List<Map<String, String>> xmlValues = WorkflowContextHandler.getNodeValueMapList(propNames, xmlNodeList);
		return xmlValues;
	}

	protected int getResponseMappedCount(String expression, Object initObj) throws Exception {
		expression = expression.replaceAll("\\.", "\\/");
		if(expression.charAt(0)!='/')
			expression = "/" + expression;
		
		XPath xPath =  XPathFactory.newInstance().newXPath();
		NodeList xmlNodeList = (NodeList) xPath.compile(expression).evaluate((Document)initObj, XPathConstants.NODESET);
		Assert.assertTrue("Workflow xml variable " + expression +" is null", 
				xmlNodeList!=null && xmlNodeList.getLength()>0);

		String xmlValue = XMLResponseValidator.getXMLNodeValue(xmlNodeList.item(0));
		Assert.assertNotNull("Workflow xml variable " + expression +" is null", xmlValue);
		
		int responseCount = -1;
		try {
			responseCount = Integer.valueOf(xmlValue);
		} catch (Exception e) {
			throw new AssertionError("Invalid responseMappedCount variable defined, " +
					"derived value should be number - "+expression);
		}
		return responseCount;
	}
}
