package com.gatf.selenium.plugins;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import com.gatf.executor.executor.TestCaseExecutorUtil;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * @author Sumeet Chhetri<br/>
 *
 */
public class CurlPlugin {

    public static boolean isParameterizeFirstSetParam(String arg, int index) {
        if(index==1) {
            return false;
        }
        return true;
    }
    
    @SuppressWarnings("unchecked")
    public static Object execute(Object[] args) throws Exception {
        String method = args[0].toString();
        String url = args[1].toString();
        
        OkHttpClient client = TestCaseExecutorUtil.getClient();
        Request.Builder rbuilder = new Request.Builder();
        rbuilder.url(url);
        
        RequestBody body = null;
        
        List<List<Object>> lst = (List<List<Object>>)args[args.length-3];
        
        if(CollectionUtils.isNotEmpty(lst))
        {
            List<Object> iheaders = lst.get(0);
            if(iheaders.size()>0) {
                for (Object o : iheaders) {
                    if(o instanceof String) {
                    	String sep = "=";
                    	if(o.toString().indexOf("=")==-1) {
                    		if(o.toString().indexOf(":")==-1) {
                    			sep = ":";
                    		}
                    	}
                        String name = o.toString().substring(0, o.toString().indexOf(sep));
                        String value = o.toString().substring(o.toString().indexOf(sep)+1);
                        rbuilder.addHeader(name.trim(), value.trim());
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
        if(headers.get("Content-Type")!=null) {
            if(headers.get("Content-Type").get(0).toLowerCase().indexOf("xml")!=-1) {
                resp.put("xml", XmlPlugin.read(new Object[]{response.body().string()}));
            } else if(headers.get("Content-Type").get(0).toLowerCase().indexOf("json")!=-1) {
                resp.put("json", JsonPlugin.read(new Object[]{response.body().string()}));
            } else {
            	resp.put("body", Base64.getEncoder().encodeToString(response.body().bytes()));
            }
        } else {
        	resp.put("body", Base64.getEncoder().encodeToString(response.body().bytes()));
        }
        resp.put("contentType", response.body().contentType().toString());
        resp.put("headers", headers);
        resp.put("cookies", cookies);
        return resp;
    }
    
    public static String[] toSampleSelCmd() {
    	return new String[] {
    		"Curl Plugin",
    		"\tcurl get|put|post|delete {url}\n\t{\n\t\t[\n\t\t\theader=value\n\t\t]\n\t\tcontent-body\n\t}",
    		"Examples :-",
    		"\tcurl get http://abc.com",
    		"\tcurl post http://abc.com\n\t{\n\t\t[\n\t\t\tContent-Type=application/xml\n\t\t]\n\t\t<abc>abc</abc>\n\t}",
        };
    }
}
