/*
    Copyright 2013-2026, Sumeet Chhetri

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
package com.gatf.selenium;

import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Stack;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.text.StringEscapeUtils;
import org.openqa.selenium.Keys;
import org.openqa.selenium.logging.LoggingPreferences;
import org.reflections.Reflections;
import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.core.WorkflowContextHandler;
import com.gatf.selenium.Command.ProviderLoopCommand.ProviderType;
import com.gatf.selenium.SeleniumTest.GatfRunTimeError;
import com.gatf.selenium.plugins.ApiPlugin;
import com.gatf.selenium.plugins.CurlPlugin;
import com.gatf.selenium.plugins.JsonPlugin;
import com.gatf.selenium.plugins.XmlPlugin;

import static com.gatf.selenium.Command.esc;
import static com.gatf.selenium.Command.throwError;
import static com.gatf.selenium.Command.withManagedArgs;

/**
 * G8 — seleasy `open <browser>` codegen: emits the per-browser driver-options + driver construction Java
 * (chrome*, firefox*, ie, safari, opera, edge, remote-*, appium-*) into the generated test. Moved verbatim
 * from Command.DriverCommand.javacode(); the helpers it uses (esc/throwError/withManagedArgs) are Command's
 * package-private statics, statically imported so the moved text is unchanged.
 */
@SuppressWarnings("unused")
final class DriverInitCodegen {

	private DriverInitCodegen() {}

	static void append(StringBuilder b, SeleniumDriverConfig config, String rUrl, Object[] fileLineDetails) {
            if(config.getName().startsWith("chrome")) {
                b.append("org.openqa.selenium.chrome.ChromeOptions ___dc___ = new org.openqa.selenium.chrome.ChromeOptions();\n");
                b.append("___dc___.setCapability(org.openqa.selenium.chrome.ChromeOptions.LOGGING_PREFS, ___lp___);\n");
                if(config.getCapabilities()!=null)
                {
                    for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
                    {
                        b.append("___dc___.setCapability(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                }
                b.append("___dc___.setCapability(CapabilityType.ACCEPT_INSECURE_CERTS, true);\n");
                
                // G8 fix: build the arguments LOCALLY. This used to mutate the shared driver config, so every
                // generation (every Run/Debug in the server, every script in a suite) appended another
                // --remote-allow-origins=* and could glue tokens together ("...errors--remote-allow-origins=*").
                String __cargs = withManagedArgs(config.getArguments(), "--ignore-certificate-errors", "--remote-allow-origins=*");
                if(!config.getName().equals("chrome")) {
                	__cargs = withManagedArgs(__cargs, "--disable-dev-shm-usage", "--no-sandbox");
                }
                b.append("___dc___.addArguments(\""+esc(__cargs)+"\".trim());\n");
                
                
                if(config.getProperties()!=null)
                {
                	b.append("Map<String, Object> __prefs = new java.util.HashMap<String, Object>();\n");
                	for (Map.Entry<String, String> e : config.getProperties().entrySet())
                    {
                        b.append("__prefs.put(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                	b.append("___dc___.setExperimentalOption(\"prefs\", __prefs);\n");
                }
                b.append("String __bkey__ = addWdm(\""+config.getName().toLowerCase().trim()+"\", ___dc___);\n");
            	if(!config.getName().equals("chrome")) {
            		b.append("set___d___(getDockerDriver(__bkey__));\n");
            	} else {
            		b.append("set___d___(new org.openqa.selenium.chrome.ChromeDriver(___dc___));\n");
            	}
            } else if(config.getName().startsWith("firefox")) {
            	b.append("org.openqa.selenium.firefox.FirefoxProfile ___dcprf___ = new org.openqa.selenium.firefox.FirefoxProfile();\n");
                b.append("org.openqa.selenium.firefox.FirefoxOptions ___dc___ = new org.openqa.selenium.firefox.FirefoxOptions();\n");
                //b.append("___dc___.setCapability(CapabilityType.LOGGING_PREFS, ___lp___);\n");
                if(config.getCapabilities()!=null)
                {
                    for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
                    {
                        b.append("___dc___.setCapability(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                }
                b.append("___dc___.getProfile().setAcceptUntrustedCertificates(true);\n");
                b.append("___dc___.getProfile().setAssumeUntrustedCertificateIssuer(true);\n");
                b.append("___dc___.setCapability(CapabilityType.ACCEPT_INSECURE_CERTS, true);\n");
                // G8 fix: local, de-duplicated (was: appended to the shared config on every generation; a null
                // arguments value became the literal token "null")
                String __fargs = config.getName().equals("firefox") ? (config.getArguments()==null ? "" : config.getArguments())
                		: withManagedArgs(config.getArguments(), "--disable-dev-shm-usage");
                if(StringUtils.isNotBlank(__fargs))
                {
                	b.append("___dc___.addArguments(\""+esc(__fargs)+"\".trim());\n");
                }
                if(config.getProperties()!=null)
                {
                	for (Map.Entry<String, String> e : config.getProperties().entrySet())
                    {
                        b.append("___dcprf___.setPreference(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                }
                b.append("___dc___.setProfile(___dcprf___);\n");
                b.append("String __bkey__ = addWdm(\""+config.getName().toLowerCase().trim()+"\", ___dc___);\n");
            	if(!config.getName().equals("firefox")) {
            		b.append("set___d___(getDockerDriver(__bkey__));\n");
            	} else {
            		b.append("set___d___(new org.openqa.selenium.firefox.FirefoxDriver(___dc___));\n");
            	}
            } else if(config.getName().equalsIgnoreCase("ie")) {
                b.append("org.openqa.selenium.ie.InternetExplorerOptions ___dc___ = new org.openqa.selenium.ie.InternetExplorerOptions();\n");
                //b.append("___dc___.setCapability(CapabilityType.LOGGING_PREFS, ___lp___);\n");
                if(config.getCapabilities()!=null)
                {
                    for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
                    {
                        b.append("___dc___.setCapability(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                }
                if(StringUtils.isNotBlank(config.getArguments()))
                {
                	b.append("___dc___.addCommandSwitches(\""+esc(config.getArguments())+"\".split(\"\\\\s+\"));\n");
                }
                b.append("___dc___.setCapability(CapabilityType.ACCEPT_INSECURE_CERTS, true);\n");
                b.append("String __bkey__ = addWdm(\""+config.getName().toLowerCase().trim()+"\", ___dc___);\n");
            	if(!config.getName().equals("ie")) {
            		b.append("set___d___(getDockerDriver(__bkey__));\n");
            	} else {
            		b.append("set___d___(new org.openqa.selenium.ie.InternetExplorerDriver(___dc___));\n");
            	}
            } else if(config.getName().equalsIgnoreCase("safari")) {
                b.append("org.openqa.selenium.safari.SafariOptions ___dc___ = new org.openqa.selenium.safari.SafariOptions();\n");
                //b.append("___dc___.setCapability(CapabilityType.LOGGING_PREFS, ___lp___);\n");
                if(config.getCapabilities()!=null)
                {
                    for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
                    {
                        b.append("___dc___.setCapability(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                }
                b.append("___dc___.setCapability(CapabilityType.ACCEPT_INSECURE_CERTS, true);\n");
                b.append("String __bkey__ = addWdm(\""+config.getName().toLowerCase().trim()+"\", ___dc___);\n");
            	if(!config.getName().equals("safari")) {
            		b.append("set___d___(getDockerDriver(__bkey__));\n");
            	} else {
            		b.append("set___d___(new org.openqa.selenium.safari.SafariDriver(___dc___));\n");
            	}
            } else if(config.getName().equalsIgnoreCase("opera")) {
                b.append("org.openqa.selenium.opera.OperaOptions ___dc___ = new org.openqa.selenium.opera.OperaOptions();\n");
                //b.append("___dc___.setCapability(CapabilityType.LOGGING_PREFS, ___lp___);\n");
                if(config.getCapabilities()!=null)
                {
                    for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
                    {
                        b.append("___dc___.setCapability(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                }
                if(StringUtils.isNotBlank(config.getArguments()))
                {
                	b.append("___dc___.addCommandSwitches(\""+esc(config.getArguments())+"\".split(\"\\\\s+\"));\n");
                }
                b.append("___dc___.setCapability(CapabilityType.ACCEPT_INSECURE_CERTS, true);\n");
                b.append("String __bkey__ = addWdm(\""+config.getName().toLowerCase().trim()+"\", ___dc___);\n");
            	if(!config.getName().equals("opera")) {
            		b.append("set___d___(getDockerDriver(__bkey__));\n");
            	} else {
            		b.append("set___d___(new org.openqa.selenium.opera.OperaDriver(___dc___));\n");
            	}
            } else if(config.getName().equalsIgnoreCase("edge")) {
            	b.append("String __bkey__ = addWdm(\""+config.getName().toLowerCase().trim()+"\");\n");
                b.append("org.openqa.selenium.edge.EdgeOptions ___dc___ = new org.openqa.selenium.edge.EdgeOptions();\n");
                b.append("___dc___.setCapability(org.openqa.selenium.edge.EdgeOptions.LOGGING_PREFS, ___lp___);\n");
                if(config.getCapabilities()!=null)
                {
                    for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
                    {
                        b.append("___dc___.setCapability(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                }
                b.append("___dc___.setCapability(CapabilityType.ACCEPT_INSECURE_CERTS, true);\n");
                b.append("String __bkey__ = addWdm(\""+config.getName().toLowerCase().trim()+"\", ___dc___);\n");
            	if(!config.getName().equals("edge")) {
            		b.append("set___d___(getDockerDriver(__bkey__));\n");
            	} else {
            		b.append("set___d___(new org.openqa.selenium.edge.EdgeDriver(___dc___));\n");
            	}
            } else if(config.getName().equalsIgnoreCase("remote-chrome")) {
                b.append("DesiredCapabilities ___dc___ = DesiredCapabilities.chrome();\n");
                b.append("___dc___.setCapability(org.openqa.selenium.chrome.ChromeOptions.LOGGING_PREFS, ___lp___);\n");
                if(config.getCapabilities()!=null)
                {
                    for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
                    {
                        b.append("___dc___.setCapability(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                }
                b.append("set___d___(new RemoteWebDriver(new java.net.URL(\""+rUrl+"\"), ___dc___));\n");
            }  else if(config.getName().equalsIgnoreCase("remote-firefox")) {
                b.append("DesiredCapabilities ___dc___ = DesiredCapabilities.firefox();\n");
                //b.append("___dc___.setCapability(CapabilityType.LOGGING_PREFS, ___lp___);\n");
                if(config.getCapabilities()!=null)
                {
                    for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
                    {
                        b.append("___dc___.setCapability(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                }
                b.append("set___d___(new RemoteWebDriver(new java.net.URL(\""+rUrl+"\"), ___dc___));\n");
            } else if(config.getName().equalsIgnoreCase("remote-ie")) {
                b.append("DesiredCapabilities ___dc___ = DesiredCapabilities.internetExplorer();\n");
                //b.append("___dc___.setCapability(CapabilityType.LOGGING_PREFS, ___lp___);\n");
                if(config.getCapabilities()!=null)
                {
                    for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
                    {
                        b.append("___dc___.setCapability(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                }
                b.append("set___d___(new RemoteWebDriver(new java.net.URL(\""+rUrl+"\"), ___dc___));\n");
            } else if(config.getName().equalsIgnoreCase("remote-edge")) {
                b.append("DesiredCapabilities ___dc___ = DesiredCapabilities.edge();\n");
                b.append("___dc___.setCapability(org.openqa.selenium.edge.EdgeOptions.LOGGING_PREFS, ___lp___);\n");
                if(config.getCapabilities()!=null)
                {
                    for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
                    {
                        b.append("___dc___.setCapability(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                }
                b.append("set___d___(new RemoteWebDriver(new java.net.URL(\""+rUrl+"\"), ___dc___));\n");
            } else if(config.getName().equalsIgnoreCase("remote-opera")) {
                b.append("DesiredCapabilities ___dc___ = DesiredCapabilities.operaBlink();\n");
                //b.append("___dc___.setCapability(CapabilityType.LOGGING_PREFS, ___lp___);\n");
                if(config.getCapabilities()!=null)
                {
                    for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
                    {
                        b.append("___dc___.setCapability(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                }
                b.append("set___d___(new RemoteWebDriver(new java.net.URL(\""+rUrl+"\"), ___dc___));\n");
            }  else if(config.getName().equalsIgnoreCase("remote-safari")) {
                b.append("DesiredCapabilities ___dc___ = DesiredCapabilities.safari();\n");
                //b.append("___dc___.setCapability(CapabilityType.LOGGING_PREFS, ___lp___);\n");
                if(config.getCapabilities()!=null)
                {
                    for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
                    {
                        b.append("___dc___.setCapability(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                }
                b.append("set___d___(new RemoteWebDriver(new java.net.URL(\""+rUrl+"\"), ___dc___));\n");
            } else if(config.getName().equalsIgnoreCase("appium-android")) {
                b.append("DesiredCapabilities ___dc___ = new DesiredCapabilities(org.openqa.selenium.remote.BrowserType.ANDROID, \""+config.getVersion()+"\", org.openqa.selenium.Platform.ANDROID);\n");
                //b.append("___dc___.setCapability(CapabilityType.LOGGING_PREFS, ___lp___);\n");
                if(config.getCapabilities()!=null)
                {
                    for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
                    {
                        b.append("___dc___.setCapability(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                }
                b.append("set___d___(new io.appium.java_client.android.AndroidDriver(new java.net.URL(\""+rUrl+"\"), ___dc___));\n");
            } else if(config.getName().equalsIgnoreCase("appium-ios")) {
                b.append("DesiredCapabilities ___dc___ = new DesiredCapabilities(\"ios\", \""+config.getVersion()+"\", org.openqa.selenium.Platform.MAC);\n");
                //b.append("___dc___.setCapability(CapabilityType.LOGGING_PREFS, ___lp___);\n");
                if(config.getCapabilities()!=null)
                {
                    for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
                    {
                        b.append("___dc___.setCapability(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                }
                b.append("set___d___(new io.appium.java_client.ios.IOSDriver(new java.net.URL(\""+rUrl+"\"), ___dc___));\n");
            } /*else if(config.getName().equalsIgnoreCase("selendroid")) {
                b.append("SelendroidCapabilities  ___dc___ = new SelendroidCapabilities(\"\");\n");
                b.append("___dc___.setCapability(CapabilityType.LOGGING_PREFS, ___lp___);\n");
                if(config.getCapabilities()!=null)
                {
                    for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
                    {
                        b.append("___dc___.setCapability(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                }
                b.append("set___d___(new io.selendroid.client.SelendroidDriver(___dc___));\n");
            }*/ //else if(config.getName().equalsIgnoreCase("ios-driver")) {
                //b.append("DesiredCapabilities ___dc___ = new DesiredCapabilities(org.openqa.selenium.remote.BrowserType.ANDROID, \""+version+"\", org.openqa.selenium.Platform.ANDROID);\n");
                //b.append("___dc___.setCapability(CapabilityType.LOGGING_PREFS, ___lp___);\n");
                //if(config.getCapabilities()!=null)
                //{
                //    for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
                //    {
                //        b.append("___dc___.setCapability(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                //    }
                //}
                //b.append("get___d___() = new org.openqa.selenium.remote.RemoteWebDriver(\""+rUrl+"\", ___dc___);\n");
            //}
            else {
                throwError(fileLineDetails, new RuntimeException("Invalid driver configuration specified, no browser found with name " + config.getName()));
            }
	}
}
