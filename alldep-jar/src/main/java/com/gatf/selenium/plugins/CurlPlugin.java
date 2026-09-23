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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.WorkflowContextHandler;
import com.gatf.executor.executor.TestCaseExecutorUtil;

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

    private static final Logger LOG = LogManager.getLogger(CurlPlugin.class);

    /**
     * Q1: splits one header entry. Both {@code name=value} and {@code name: value} are accepted; the separator is
     * whichever of '=' or ':' comes FIRST, so values may themselves contain '=' or ':' (tokens, URLs).
     * Returns null for an entry with neither separator or an empty name.
     * (Before Q1 the logic was inverted: {@code Content-Type: application/json} threw StringIndexOutOfBounds, and
     * {@code Name: a=b} split at the '=' inside the value.)
     */
    static String[] splitHeader(String entry) {
        if (entry == null) return null;
        int eq = entry.indexOf('='), co = entry.indexOf(':');
        int at = eq < 0 ? co : (co < 0 ? eq : Math.min(eq, co));
        if (at <= 0) return null;
        String name = entry.substring(0, at).trim();
        return name.isEmpty() ? null : new String[] { name, entry.substring(at + 1).trim() };
    }

    /** Methods that may carry a request body. */
    static boolean hasBody(String method) {
        String m = method == null ? "" : method.trim().toUpperCase(java.util.Locale.ROOT);
        return m.equals("POST") || m.equals("PUT") || m.equals("PATCH") || m.equals("DELETE");
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
                        String[] nv = splitHeader(o.toString());
                        if(nv == null) {
                            LOG.warn("curl plugin: ignoring header entry without '=' or ':' separator: {}", o);
                            continue;
                        }
                        hdr(rbuilder, nv[0], nv[1]);
                    }
                }
            }

            String content = null;
            if(hasBody(method) && lst.size() > 1 && lst.get(1) != null && !lst.get(1).isEmpty() && lst.get(1).get(0) != null) {
                content = lst.get(1).get(0).toString();
                body = content.getBytes(StandardCharsets.UTF_8);
            }
        }

        if(body==null && (method.equalsIgnoreCase("post") || method.equalsIgnoreCase("put") || method.equalsIgnoreCase("patch"))) {
            body = new byte[0];   // was dead code: toUpperCase() compared against lower-case literals
        }
        // per-request timeout from the executor config (the shared client only has a connect timeout)
        Object ctx = args[args.length-1];
        if(ctx instanceof AcceptanceTestContext && ((AcceptanceTestContext)ctx).getGatfExecutorConfig()!=null) {
            Integer rt = ((AcceptanceTestContext)ctx).getGatfExecutorConfig().getHttpRequestTimeout();
            if(rt!=null && rt>0) {
                rbuilder.timeout(Duration.ofMillis(rt));
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
        String contentTypeHeader = response.headers().firstValue("Content-Type").orElse(null);
        if(contentTypeHeader!=null) {
            String bodyStr = new String(response.body(), StandardCharsets.UTF_8);
            if(contentTypeHeader.toLowerCase().indexOf("xml")!=-1) {
                resp.put("xml", XmlPlugin.read(new Object[]{bodyStr}));
            } else if(contentTypeHeader.toLowerCase().indexOf("json")!=-1) {
                resp.put("json", JsonPlugin.read(new Object[]{bodyStr}));
            } else {
            	resp.put("body", Base64.getEncoder().encodeToString(response.body()));
            }
        } else {
        	resp.put("body", Base64.getEncoder().encodeToString(response.body()));
        }
        resp.put("contentType", contentTypeHeader!=null ? contentTypeHeader : "");
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
    		"\tcurl 'http://abc.com/api' -H 'Content-Type: application/json' --data-raw '{\"a\":1}'    (raw cURL command line, single line)",
        };
    }
}
