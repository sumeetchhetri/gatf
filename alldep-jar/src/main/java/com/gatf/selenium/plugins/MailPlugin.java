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
package com.gatf.selenium.plugins;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.simplejavamail.api.mailer.Mailer;
import org.simplejavamail.config.ConfigLoader;
import org.simplejavamail.mailer.MailerBuilder;

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.WorkflowContextHandler;
import com.gatf.executor.executor.TestCaseExecutorUtil;

/**
 * @author Sumeet Chhetri<br/>
 *
 */
public class MailPlugin {
	
	private static Mailer mailer = null;
	
	public static void init(AcceptanceTestContext cntxt) {
		File mailConfFile = cntxt.getResourceFile("mail.properties");
        try {
            ConfigLoader.loadProperties(mailConfFile, true);
        } catch (Exception e) {
        }
	}

    // HttpRequest.Builder throws on restricted headers (Host/Content-Length/...) the JDK manages itself; skip those.
    private static void hdr(HttpRequest.Builder b, String name, String value) {
        try {
            b.header(name, value);
        } catch (IllegalArgumentException e) {
        }
    }

    @SuppressWarnings("unchecked")
    public static Object execute(Object[] args) throws Exception {
    	if(mailer==null) {
    		mailer = MailerBuilder.buildMailer();
    	}
    	
        String method = args[0].toString();
        String url = args[1].toString();

        HttpClient client = TestCaseExecutorUtil.getClient();
        HttpRequest.Builder rbuilder = HttpRequest.newBuilder();
        rbuilder.uri(URI.create(url));
        hdr(rbuilder, "Referer", WorkflowContextHandler.getBaseUrl(url));

        byte[] body = null;

        List<List<Object>> lst = (List<List<Object>>)args[args.length-3];

        if(CollectionUtils.isNotEmpty(lst))
        {
            List<Object> iheaders = lst.get(0);
            if(iheaders.size()>0) {
                for (Object o : iheaders) {
                    if(o instanceof String) {
                        String name = o.toString().substring(0, o.toString().indexOf("="));
                        String value = o.toString().substring(o.toString().indexOf("=")+1);
                        hdr(rbuilder, name, value);
                    }
                }
            }

            String content = null;
            if(method.equalsIgnoreCase("post") || method.equalsIgnoreCase("put")) {
                content = lst.get(1).get(0).toString();
                body = content.getBytes(StandardCharsets.UTF_8);
            }
        }

        if(method.toUpperCase().equals("post") || method.toUpperCase().equals("put")) {
			if(body==null) {
				body = new byte[0];
			}
		}
        rbuilder.method(method.toUpperCase(), body!=null ? HttpRequest.BodyPublishers.ofByteArray(body) : HttpRequest.BodyPublishers.noBody());
        HttpResponse<byte[]> response = client.send(rbuilder.build(), HttpResponse.BodyHandlers.ofByteArray());

        List<String> cookies = new ArrayList<String>();
        Map<String, List<String>> headers = new HashMap<String, List<String>>();
        for (Map.Entry<String, List<String>> he : response.headers().map().entrySet()) {
        	if(he.getKey().equalsIgnoreCase("Set-Cookie")) {
        		cookies.addAll(he.getValue());
        	} else {
        		headers.put(he.getKey(), he.getValue());
        	}
        }

        Map<String, Object> resp = new HashMap<String, Object>();
        resp.put("statusCode", response.statusCode());
        resp.put("statusText", "");
        resp.put("body", Base64.getEncoder().encodeToString(response.body()));
        resp.put("contentType", response.headers().firstValue("Content-Type").orElse(""));
        resp.put("headers", headers);
        resp.put("cookies", cookies);
        
        return WorkflowContextHandler.OM.writeValueAsString(resp);
    }
    
    public static String[] toSampleSelCmd() {
    	return new String[] {
    		"Mail Plugin",
    		"\tsendmail get|put|post|delete {url}\n\t{\n\t\t[\n\t\t\theader=value\n\t\t]\n\t\tcontent-body\n\t}",
    		"Examples :-",
    		"\tsendmail get http://abc.com",
    		"\tsendmail post http://abc.com\n\t{\n\t\t[\n\t\t\tContent-Type=application/xml\n\t\t]\n\t\t<abc>abc</abc>\n\t}",
        };
    }
}
