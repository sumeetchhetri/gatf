/*
    Copyright 2013-2016, Sumeet Chhetri
    
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
package com.gatf.executor.report;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.gatf.executor.core.GatfExecutorConfig;

/**
 * @author Sumeet Chhetri
 *
 */
public class TestCaseExecutionLogGenerator implements Runnable {

	private static ConcurrentLinkedQueue<String> Q = new ConcurrentLinkedQueue<String>();
	
	GatfExecutorConfig config;
	
	private Writer writer;
	
	public TestCaseExecutionLogGenerator(GatfExecutorConfig config)
	{
		this.config = config;
	}
	
	public static void log(TestCaseReport report)
	{
		Q.add(report.toStatisticsCsv());
	}

	public void run() {
		try {
			File basePath = null;
        	if(config.getOutFilesBasePath()!=null)
        		basePath = new File(config.getOutFilesBasePath());
        	else
        	{
        		URL url = Thread.currentThread().getContextClassLoader().getResource(".");
        		basePath = new File(url.getPath());
        	}
        	File resource = new File(basePath, config.getOutFilesDir());
			writer = new BufferedWriter(new FileWriter(new File(resource.getAbsolutePath()
                    + File.separator + UUID.randomUUID().toString() + ".csv")));
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		try {
			while(true)
			{
				String logentry = null;
				while((logentry = Q.poll())==null)
				{
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						throw e;
					}
				}
				writer.write(logentry);
			}
		} catch (Exception e) {
			if(!(e instanceof InterruptedException))
			{
				e.printStackTrace();
			}
		} finally {
			if(writer!=null)
			{
				try {
					String logentry = null;
					while((logentry = Q.poll())!=null)
					{
						writer.write(logentry);
					}
					writer.flush();
					writer.close();
				} catch (Exception e) {
				}
			}
		}
	}
}
