package com.gatf.selenium.plugins;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.AsyncHttpClientConfig.Builder;
import com.ning.http.client.ListenableFuture;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.Response;
import com.ning.http.client.cookie.Cookie;

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
        
        Builder builder = new AsyncHttpClientConfig.Builder();
        AsyncHttpClient client = new AsyncHttpClient(builder.build());
        RequestBuilder rbuilder = new RequestBuilder(method.toUpperCase());
        rbuilder = rbuilder.setUrl(url);
        
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
                rbuilder.setBody(content);
            }
        }

        ListenableFuture<Response> responsef = client.executeRequest(rbuilder.build());
        Response response = responsef.get();
        
        Map<String, List<String>> headers = new HashMap<String, List<String>>();
        for (String name : response.getHeaders().keySet()) {
            headers.put(name, response.getHeaders().get(name));   
        }
        
        List<String> cookies = new ArrayList<String>();
        for (Cookie ck : response.getCookies()) {
            cookies.add(ck.toString());
        }
        
        Map<String, Object> resp = new HashMap<String, Object>();
        resp.put("statusCode", response.getStatusCode());
        resp.put("statusText", response.getStatusText());
        resp.put("body", response.getResponseBody());
        resp.put("headers", headers);
        resp.put("cookies", cookies);
        
        client.close();
        
        return new ObjectMapper().writeValueAsString(resp);
    }
}
