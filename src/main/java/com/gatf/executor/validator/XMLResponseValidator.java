package com.gatf.executor.validator;

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

import java.io.ByteArrayInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import com.gatf.executor.core.WorkflowContextHandler.ResponseType;
import com.ning.http.client.Response;

/**
 * @author Sumeet Chhetri
 * The validator that handles xml level node validations after test case execution
 */
public class XMLResponseValidator extends ResponseValidator {

	protected Object getInternalObject(Response response) throws Exception
	{
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = dbf.newDocumentBuilder();
		Document xmlDocument = db.parse(new ByteArrayInputStream(response.getResponseBody().getBytes()));
		return xmlDocument;
	}

	protected String getNodeValue(Object intObj, String node) throws Exception {
		NodeList xmlNodeList = getNodeByXpath(node, (Document)intObj, null);
		String xmlValue = getXMLNodeValue(xmlNodeList.item(0));
		return xmlValue;
	}

	protected ResponseType getType() {
		return ResponseType.XML;
	}
}
