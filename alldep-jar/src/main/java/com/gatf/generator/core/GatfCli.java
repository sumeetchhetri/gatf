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
package com.gatf.generator.core;

import java.io.FileInputStream;
import java.io.InputStream;
import com.gatf.executor.core.GatfTestCaseExecutorUtil;
import com.gatf.executor.core.WorkflowContextHandler;
import com.gatf.executor.distributed.DistributedGatfListener;
import com.gatf.selenium.Command;
import com.gatf.ui.GatfConfigToolUtil;

/**
 * G8 — the CLI entry for every mode (-generator / -executor / -selenium / -configtool / -listener /
 * -validate-sel), extracted from GatfTestGeneratorUtil.main (still the jar's Main-Class; it delegates).
 * Fixes: a -generator config that is neither .xml nor .json showed an NPE instead of the help text, and
 * the process always exited 0 even on invalid usage or failure.
 */
public final class GatfCli {

	private GatfCli() {}

    public static void main(String[] args) throws Exception
    {
		// Install ByteBuddy agent programmatically
        //ByteBuddyAgent.install();

    	System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
    	boolean showHelp = false;
    	if(args.length>=1) {
    		if(args.length>1 && args[0].equals("-generator") && !args[1].trim().isEmpty())
    		{
    			GatfConfiguration config = null;
    			
    			if(args[1].trim().endsWith(".xml")) {
    				InputStream io = new FileInputStream(args[1]);
		    		config = (GatfConfiguration) WorkflowContextHandler.XOM.readValue(io, GatfConfiguration.class);
    			} else if(args[1].trim().endsWith(".json")) {
    				InputStream io = new FileInputStream(args[1]);
    				config = WorkflowContextHandler.OM.readValue(io, GatfConfiguration.class);
    			} else {
    				showHelp = true;
    			}
	    		
	    		if(config != null) {
	    		GatfTestGeneratorUtil testGenerator = new GatfTestGeneratorUtil();
	    		testGenerator.setDebugEnabled(config.isDebugEnabled());
	    		testGenerator.setEnabled(config.isEnabled());
	    		testGenerator.setRequestDataType(config.getRequestDataType());
	    		testGenerator.setTestPaths(config.getTestPaths());
	    		testGenerator.setSoapWsdlKeyPairs(config.getSoapWsdlKeyPairs());
	    		testGenerator.setUrlPrefix(config.getUrlPrefix());
	    		testGenerator.setResourcepath(config.getResourcepath());
	    		testGenerator.setOutDataType(config.getResponseDataType()); 
	    		testGenerator.setOverrideSecure(config.isOverrideSecure());
	    		testGenerator.setUrlSuffix(config.getUrlSuffix());
	    		testGenerator.setUseSoapClient(config.isUseSoapClient());
	    		testGenerator.setTestCaseFormat(config.getTestCaseFormat());
	    		testGenerator.setPostmanCollectionVersion(config.getPostmanCollectionVersion());
	    		testGenerator.setOverrideSecure(config.isOverrideSecure());
	    		testGenerator.execute();
	    		}
    		}
    		else if(args.length>1 && (args[0].equals("-executor") || args[0].equals("-selenium")) && !args[1].trim().isEmpty())
    		{
    			try {
    				GatfTestCaseExecutorUtil.main(args);
				} catch (Exception e) {
					e.printStackTrace();
					showHelp = true;
				}
    			
    		}
    		else if(args.length>3 && args[0].equals("-configtool") && !args[1].trim().isEmpty() 
    				&& !args[2].trim().isEmpty() && !args[3].trim().isEmpty())
    		{
    			GatfConfigToolUtil.main(args);
    		}
    		else if(args[0].equals("-listener"))
    		{
    			DistributedGatfListener.main(args);
    		}
    		else if(args[0].equals("-validate-sel"))
    		{
    			Command.validateSel(args, null, false);
    		}
    		else
    		{
    			showHelp = true;
    		}
    	} else {
    		showHelp = true;
    	}
    	
    	if(showHelp) {
    		System.out.println("Invalid invocation - valid invocation options are, \n" +
					"java -jar gatf-plugin-{version}.jar -generator {generator-config-file}.xml\n" +
					"java -jar gatf-plugin-{version}.jar -executor {executor-config-file}.xml\n" +
					"java -jar gatf-plugin-{version}.jar -selenium {selenium-config-file}.xml\n" +
					"java -jar gatf-plugin-{version}.jar -configtool {http_port} {ip_address} {project_folder}\n" + 
					"java -jar gatf-plugin-{version}.jar -validate-sel {file-name/relative/to/workdir}\n" + 
					"java -jar gatf-plugin-{version}.jar -listener\n");
    	}
    	
		System.exit(showHelp ? 1 : 0);   // G8: non-zero on invalid usage / executor failure (was always 0)
    }
}
