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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.SessionBridge;
import com.gatf.executor.report.TestCaseReport;
import com.gatf.ui.GatfConfigToolUtil;
import com.gatf.ui.GatfReportsHandler;

/**
 * @author Sumeet Chhetri<br/>
 *
 */
public class ApiPlugin {

    public static Object api(Object[] args) throws Exception {
        String testname = args[0].toString();
        String testcaseFileName = "selenium-apis.xml";
        if(testname.indexOf("@")!=-1) {
            testcaseFileName = testname.substring(0, testname.indexOf("@"));
            testname = testname.substring(testname.indexOf("@")+1);
        }
        
        GatfConfigToolUtil mojo = new GatfConfigToolUtil();
        mojo.setContext((AcceptanceTestContext)args[args.length-1]);
        
        int index = (Integer)args[args.length-2];
        
        GatfReportsHandler handler = new GatfReportsHandler(mojo, null);
        mojo.setRootDir(mojo.getContext().getGatfExecutorConfig().getTestCasesBasePath());
        Object[] out = handler.executeTest(null, mojo.getContext().getGatfExecutorConfig(), null, "playTest", testcaseFileName, testname, false, 
        		false, index, true, 0, false, false, null);
        TestCaseReport report = (TestCaseReport)out[3];
        syncCookies(report);
        
        org.apache.logging.log4j.LogManager.getLogger(ApiPlugin.class).info("api plugin: executed {}@{} -> {}", testname, testcaseFileName, report);
        
        if(report!=null && !report.getStatus().equalsIgnoreCase("failed")) {
            if(report.getResponseContentType()!=null) {
                if(report.getResponseContentType().indexOf("xml")!=-1) {
                    return XmlPlugin.read(new Object[]{report.getResponseContent()});
                } else if(report.getResponseContentType().indexOf("json")!=-1) {
                    return JsonPlugin.read(new Object[]{report.getResponseContent()});
                }
            }
        } else {
        	if(report!=null) {
        		throw new RuntimeException(String.format("API test %s failed with statuscode %d and error (%s) Error details below\n%s", 
        				testname, report.getResponseStatusCode(), report.getError(), report.getErrorText()));
        	} else {
        		throw new RuntimeException("API test " + testname + " failed");
        	}
        }
        
        return null;
    }
    
    /** Q8: in sync mode, Set-Cookies from the API response go into the browser (the executor already sent its cookies). */
    static void syncCookies(TestCaseReport report) {
        SessionBridge.Bridge bridge = SessionBridge.current();
        if (bridge == null || report == null || report.getResHeaders() == null || report.getActualUrl() == null) return;
        List<String> setCookies = new ArrayList<String>();
        for (Map.Entry<String, List<String>> h : report.getResHeaders().entrySet()) {
            if (h.getKey() != null && h.getKey().equalsIgnoreCase("Set-Cookie") && h.getValue() != null) setCookies.addAll(h.getValue());
        }
        if (setCookies.isEmpty()) return;
        try {
            bridge.onSetCookies(URI.create(report.getActualUrl()), setCookies);
        } catch (IllegalArgumentException e) {
            // relative or malformed URL: nothing to scope the cookies to
        }
    }

    public static String[] toSampleSelCmd() {
    	return new String[] {
    		"API Plugin",
    		"\tapi {optional test-case-file-name}@{test-name}",
    		"Examples :-",
    		"\tapi api-name",
    		"\tapi test-case-file-path@api-name",
        };
    }
}
