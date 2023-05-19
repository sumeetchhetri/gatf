package com.gatf.selenium.plugins;

import java.io.File;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.simplejavamail.api.mailer.Mailer;
import org.simplejavamail.config.ConfigLoader;
import org.simplejavamail.mailer.MailerBuilder;

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.WorkflowContextHandler;
import com.gatf.executor.executor.TestCaseExecutorUtil;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * @author Sumeet Chhetri<br/>
 *
 */
public class MailPlugin {
	
	private static Mailer mailer = null;
	
	public static void init(AcceptanceTestContext cntxt) {
		File mailConfFile = cntxt.getResourceFile("mail.properties");
		ConfigLoader.loadProperties(mailConfFile, true);
	}
    
    @SuppressWarnings("unchecked")
    public static Object execute(Object[] args) throws Exception {
    	if(mailer==null) {
    		mailer = MailerBuilder.buildMailer();
    	}
    	
        String method = args[0].toString();
        String url = args[1].toString();
        
        OkHttpClient client = TestCaseExecutorUtil.getClient();
        Request.Builder rbuilder = new Request.Builder();
        rbuilder.url(url);
        rbuilder.header("Referer", WorkflowContextHandler.getBaseUrl(url));
        
        RequestBody body = null;
        
        List<List<Object>> lst = (List<List<Object>>)args[args.length-3];
        
        if(CollectionUtils.isNotEmpty(lst))
        {
            List<Object> iheaders = lst.get(0);
            if(iheaders.size()>0) {
                for (Object o : iheaders) {
                    if(o instanceof String) {
                        String name = o.toString().substring(0, o.toString().indexOf("="));
                        String value = o.toString().substring(o.toString().indexOf("=")+1);
                        rbuilder.addHeader(name, value);
                    }
                }
            }
            
            String content = null;
            if(method.equalsIgnoreCase("post") || method.equalsIgnoreCase("put")) {
                content = lst.get(1).get(0).toString();
                body = RequestBody.create(content, null);
            }
        }

        if(method.toUpperCase().equals("post") || method.toUpperCase().equals("put")) {
			if(body==null) {
				body = RequestBody.create(StringUtils.EMPTY, null);
			}
		}
        Response response = client.newCall(rbuilder.method(method.toUpperCase(), body).build()).execute();
        
        List<String> cookies = new ArrayList<String>();
        Map<String, List<String>> headers = new HashMap<String, List<String>>();
        for (String name : response.headers().names()) {
        	if(name.equalsIgnoreCase("Set-Cookie")) {
        		cookies.addAll(response.headers().values(name));   
        	} else {
        		headers.put(name, response.headers().values(name));
        	}
        }
        
        Map<String, Object> resp = new HashMap<String, Object>();
        resp.put("statusCode", response.code());
        resp.put("statusText", response.message());
        resp.put("body", Base64.getEncoder().encodeToString(response.body().bytes()));
        resp.put("contentType", response.body().contentType().toString());
        resp.put("headers", headers);
        resp.put("cookies", cookies);
        
        return WorkflowContextHandler.OM.writeValueAsString(resp);
    }
    
    public static String[] toSampleSelCmd() {
    	return new String[] {
    		"Curl Plugin",
    		"\tmail get|put|post|delete {url}\n\t{\n\t\t[\n\t\t\theader=value\n\t\t]\n\t\tcontent-body\n\t}",
    		"Examples :-",
    		"\tmail get http://abc.com",
    		"\tmail post http://abc.com\n\t{\n\t\t[\n\t\t\tContent-Type=application/xml\n\t\t]\n\t\t<abc>abc</abc>\n\t}",
        };
    }
}
