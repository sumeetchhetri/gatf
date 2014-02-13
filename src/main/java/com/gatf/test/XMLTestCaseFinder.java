package com.gatf.test;

import java.io.File;
import java.io.FilenameFilter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.gatf.report.TestCaseReport;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.core.util.QuickWriter;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.xml.PrettyPrintWriter;
import com.thoughtworks.xstream.io.xml.XppDriver;

public class XMLTestCaseFinder implements TestCaseFinder {

	@SuppressWarnings("unchecked")
	public List<TestCase> findTestCases(File dir, AcceptanceTestContext context)
	{
		XStream xstream = new XStream(
			new XppDriver() {
				public HierarchicalStreamWriter createWriter(Writer out) {
					return new PrettyPrintWriter(out) {
						boolean cdata = false;
						@SuppressWarnings("rawtypes")
						public void startNode(String name, Class clazz){
							super.startNode(name, clazz);
							cdata = (name.equals("content") || name.equals("expectedResContent"));
						}
						protected void writeText(QuickWriter writer, String text) {
							if(cdata) {
								writer.write("<![CDATA[");
								writer.write(text);
								writer.write("]]>");
							} else {
								writer.write(text);
							}
						}
					};
				}
			}
		);
		xstream.processAnnotations(new Class[]{TestCase.class});
		xstream.alias("TestCases", List.class);
		
		List<TestCase> testcases = new ArrayList<TestCase>();
		if (dir.isDirectory()) {
			File[] xmlFiles = dir.listFiles(new FilenameFilter() {
				public boolean accept(File folder, String name) {
					return name.toLowerCase().endsWith(".xml");
				}
			});

			for (File file : xmlFiles) {
				try {
					List<TestCase> xmlTestCases = (List<TestCase>)xstream.fromXML(file);
					if(xmlTestCases!=null && !xmlTestCases.isEmpty())
					{
						for (TestCase testCase : xmlTestCases) {
							testCase.setSourcefileName(file.getName());
							testcases.add(testCase);
						}
						context.getFinalTestResults().put(file.getName(), new ConcurrentLinkedQueue<TestCaseReport>());
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		return testcases;
	}
}
