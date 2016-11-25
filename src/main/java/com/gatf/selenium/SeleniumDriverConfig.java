package com.gatf.selenium;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

import com.gatf.executor.core.MapKeyValueCustomXstreamConverter;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamConverter;

/**
 * @author Sumeet Chhetri
 * The Selenium Driver configuration properties
 */

@XStreamAlias("seleniumDriverConfig")
@JsonAutoDetect(getterVisibility=Visibility.NONE, fieldVisibility=Visibility.ANY, isGetterVisibility=Visibility.NONE)
@JsonSerialize(include=Inclusion.NON_NULL)
public class SeleniumDriverConfig implements Serializable {

    private String name;
    
    private String version;
    
    private String platform;
    
    private String driverName;

    private String path;

    @XStreamConverter(value=MapKeyValueCustomXstreamConverter.class)
    private Map<String, String> capabilities = new HashMap<String, String>();

    @XStreamConverter(value=MapKeyValueCustomXstreamConverter.class)
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
