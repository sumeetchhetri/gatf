package com.gatf.selenium.plugins;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.JsonNodeDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser;

/**
 * @author Sumeet Chhetri<br/>
 *
 */
public class XmlPlugin {
    
    private static XmlMapper xmlMapper = new XmlMapper();
    
    static {
        xmlMapper.registerModule(new SimpleModule().addDeserializer(JsonNode.class, new JsonNodeDeserializer() {
            @Override
            public JsonNode deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                String rootName = ((FromXmlParser)p).getStaxReader().getLocalName();
                return ctxt.getNodeFactory().objectNode().set(rootName, super.deserialize(p, ctxt));
            }
        }));
    }

    public static Object read(Object[] args) throws Exception {
        if(args.length>0 && args[0] instanceof String) {
            return xmlMapper.readTree(args[0].toString());
        }
        return null;
    }
    
    public static Object write(Object[] args) throws Exception {
        if(args.length==4) {
            if(args[0] instanceof List || args[0] instanceof Set || args[0] instanceof Map) {
                String ret = xmlMapper.writeValueAsString(args[0]).replaceFirst("<ObjectNode xmlns=\"\">", "");
                return ret.substring(0, ret.length()-13);
            } else {
                String ret = xmlMapper.writeValueAsString(args[0]).replaceFirst("<ObjectNode xmlns=\"\">", "");
                return ret.substring(0, ret.length()-13);
            }
        } else if(args.length==5 && args[1] instanceof String) {
            if(args[0] instanceof List || args[0] instanceof Set || args[0] instanceof Map) {
                xmlMapper.writeValue(new File(args[1].toString()), args[0]);
            } else {
                xmlMapper.writeValue(new File(args[1].toString()), args[0]);
            }
            return true;
        }
        return null;
    }
    
    public static Object path(Object[] args) throws Exception {
        if(args.length==5) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            if(args[1] instanceof JsonNode) {
                String ret = xmlMapper.writeValueAsString(args[1]).replaceFirst("<ObjectNode xmlns=\"\">", "");
                ret = ret.substring(0, ret.length()-13);
                Document doc = builder.parse(new ByteArrayInputStream(ret.getBytes()));
                return getNodeValue(doc, (String)args[0]);
            } else {
                Document doc = builder.parse(new ByteArrayInputStream(args[1].toString().getBytes()));
                return getNodeValue(doc, (String)args[0]);
            }
        }
        return null;
    }
    
    public static String[] toSampleSelCmd() {
    	return new String[] {
    		"XML Plugin",
    		"\txmlread {xml-text}",
    		"\txmlwrite {optional-path-to-file} {xml-object-or-map-list-set}",
    		"\txmlpath {xml-text} {xpath-string}",
    		"Examples :-",
    		"\txmlread '<o><a>abc</a><b>1</b></o>'",
    		"\txmlwrite '/path/to/file.xml' $xmlObjectVar",
    		"\txmlpath '<o><a>abc</a><b>1</b></o>' '/o/a'",
        };
    }
    
    protected static String getNodeValue(Object intObj, String node) throws Exception {
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
    
    public static String getXMLNodeValue(Node node)
    {
        String xmlValue = null;
        try
        {
            if(node.getNodeType()==Node.TEXT_NODE 
                    || node.getNodeType()==Node.CDATA_SECTION_NODE)
            {
                xmlValue = node.getNodeValue();
            }
            else if(node.getNodeType()==Node.ATTRIBUTE_NODE)
            {
                xmlValue = ((Attr)node).getValue();
            }
            else if(node.getNodeType()==Node.ELEMENT_NODE
                    && node.getChildNodes().getLength()>=1
                    && (node.getFirstChild().getNodeType()==Node.TEXT_NODE
                    || node.getFirstChild().getNodeType()==Node.CDATA_SECTION_NODE))
            {
                xmlValue = node.getFirstChild().getNodeValue();
            }
        }
        catch (Exception e)
        {
        }
        return xmlValue;
    }
    
    protected static NodeList getNodeByXpath(String xpathStr, Document xmlDocument) throws XPathExpressionException
    {
        if(xpathStr.charAt(0)!='/')
            xpathStr = "/" + xpathStr;
        XPath xPath =  XPathFactory.newInstance().newXPath();
        NodeList xmlNodeList = (NodeList) xPath.compile(xpathStr).evaluate(xmlDocument, XPathConstants.NODESET);
        return xmlNodeList;
    }
}
