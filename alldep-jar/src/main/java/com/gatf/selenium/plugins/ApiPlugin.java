package com.gatf.selenium.plugins;

import com.gatf.executor.core.AcceptanceTestContext;
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
        Object[] out = handler.executeTest(mojo.getContext().getGatfExecutorConfig(), null, "playTest", testcaseFileName, testname, false, 
        		false, index, true, 0, false, false, null);
        TestCaseReport report = (TestCaseReport)out[3];
        
        System.out.println("Executed api " + testname + "@" + testcaseFileName);
        System.out.println(report);
        
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
    
    public static String[] toSampleSelCmd() {
    	return new String[] {
    		"API Plugin",
    		"\tapi {test-name}@{optional test-case-file-name}",
    		"Examples :-",
    		"\tapi api-name",
    		"\tapi api-name@test-case-file-path",
        };
    }
}
