package com.gatf.executor.report;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.commons.lang3.SystemUtils;

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
                    + SystemUtils.FILE_SEPARATOR + UUID.randomUUID().toString() + ".csv")));
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
