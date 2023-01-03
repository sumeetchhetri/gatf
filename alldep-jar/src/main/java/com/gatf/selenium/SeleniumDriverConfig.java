package com.gatf.selenium;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * @author Sumeet Chhetri
 * The Selenium Driver configuration properties
 */

@JacksonXmlRootElement(localName = "seleniumDriverConfig")
@JsonAutoDetect(getterVisibility=Visibility.NONE, fieldVisibility=Visibility.ANY, isGetterVisibility=Visibility.NONE)
@JsonInclude(value = Include.NON_NULL)
public class SeleniumDriverConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;
    
    private String version;
    
    private String platform;
    
    private String driverName;

    private String path;

    private String arguments;

    private String url;

    private Map<String, String> capabilities = new HashMap<String, String>();

    private Map<String, String> properties = new HashMap<String, String>();  

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getVersion()
    {
        return version;
    }

    public void setVersion(String version)
    {
        this.version = version;
    }

    public String getPlatform()
    {
        return platform;
    }

    public void setPlatform(String platform)
    {
        this.platform = platform;
    }

    public String getDriverName()
    {
        return driverName;
    }

    public void setDriverName(String driverName)
    {
        this.driverName = driverName;
    }

    public String getPath()
    {
        return path;
    }

    public void setPath(String path)
    {
        this.path = path;
    }

    public String getArguments() {
		return arguments;
	}

	public void setArguments(String arguments) {
		this.arguments = arguments;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public Map<String, String> getCapabilities()
    {
        return capabilities;
    }

    public void setCapabilities(Map<String, String> capabilities)
    {
        this.capabilities = capabilities;
    }

    public Map<String,String> getProperties()
    {
        return properties;
    }

    public void setProperties(Map<String,String> properties)
    {
        this.properties = properties;
    }
}
