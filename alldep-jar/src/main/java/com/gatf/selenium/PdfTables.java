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
package com.gatf.selenium;

import java.awt.AWTException;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.Security;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchWindowException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.Pdf;
import org.openqa.selenium.Point;
import org.openqa.selenium.PrintsPage;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.HasDevTools;
import org.openqa.selenium.devtools.v150.dom.DOM;
import org.openqa.selenium.devtools.v150.dom.model.Node;
import org.openqa.selenium.devtools.v150.dom.model.NodeId;
import org.openqa.selenium.devtools.v150.fetch.Fetch;
import org.openqa.selenium.devtools.v150.fetch.model.HeaderEntry;
import org.openqa.selenium.devtools.v150.fetch.model.RequestPattern;
import org.openqa.selenium.devtools.v150.fetch.model.RequestStage;
import org.openqa.selenium.devtools.v150.log.Log;
import org.openqa.selenium.devtools.v150.network.Network;
import org.openqa.selenium.devtools.v150.page.Page.PrintToPDFResponse;
import org.openqa.selenium.devtools.v150.target.Target;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.interactions.Pause;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.print.PrintOptions;
import org.openqa.selenium.remote.Augmenter;
import org.openqa.selenium.remote.LocalFileDetector;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.locators.RelativeLocator;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.core.WorkflowContextHandler;
import com.gatf.executor.dataprovider.FileTestDataProvider;
import com.gatf.executor.dataprovider.MongoDBTestDataSource;
import com.gatf.executor.dataprovider.SQLDatabaseTestDataSource;
import com.gatf.executor.dataprovider.TestDataSource;
import com.gatf.executor.executor.TestCaseExecutorUtil;
import com.gatf.selenium.Command.FindCommandImpl;
import com.gatf.selenium.Command.GatfSelCodeParseError;
import com.gatf.selenium.SeleniumTestSession.SeleniumResult;
import com.google.common.io.Resources;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.jayway.jsonpath.JsonPath;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.ios.IOSDriver;
import jakarta.xml.bind.DatatypeConverter;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract1;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import ru.yandex.qatools.ashot.AShot;
import ru.yandex.qatools.ashot.Screenshot;
import ru.yandex.qatools.ashot.shooting.ShootingStrategies;
import technology.tabula.ObjectExtractor;
import technology.tabula.Page;
import technology.tabula.PageIterator;
import technology.tabula.RectangularTextContainer;
import technology.tabula.Table;
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm;

/**
 * G8 — PDF text/table extraction (pdfbox) into the shared {@code _pdtTables} cache + a .json sidecar, moved
 * verbatim from SeleniumTest.extractTextFromPdf (which now delegates here). The cache stays owned by
 * SeleniumTest (also read there); this alias is the same Map instance.
 */
@SuppressWarnings({ "unused", "rawtypes" })
final class PdfTables {

	private PdfTables() {}

	private static final Map<String, List<List<Map<String, String>>>> _pdtTables = SeleniumTest._pdtTables;

	static void extractTextFromPdf(String filePath, String colSep, int headerMode, int skippages) {
		try {
			if(skippages<=0) skippages = 0;
			PDDocument doc = PDDocument.load(new File(filePath));
			System.out.println("Number pf pages in pdf: " + doc.getNumberOfPages());
			System.out.println("Is pdf encrypted: " + doc.isEncrypted());
			Writer wr = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePath+".txt")));
			
			/*PDFTextStripper stripper = new PDFTextStripper();
			stripper.setStartPage(1);
			new File(filePath+".txt").delete();
			stripper.writeText(doc, wr);
			*/
			
			String fk = DigestUtils.sha256Hex(filePath+".txt");
			_pdtTables.put(fk, new ArrayList<List<Map<String, String>>>());
			
			ObjectExtractor oedoc = new ObjectExtractor(doc);
			PageIterator pi = oedoc.extract();
			SpreadsheetExtractionAlgorithm sea = new SpreadsheetExtractionAlgorithm();
			int pn = 1;
			List<String> headers = null;
			List<Map<String, String>> lst = null;
			if(headerMode==2) {
				lst = new ArrayList<Map<String, String>>();
				_pdtTables.get(fk).add(lst);
			}
			while (pi.hasNext()) {
				Page page = pi.next();
				if(skippages-->0) continue;
				List<Table> table = sea.extract(page);
				if(table!=null && table.size()>0) {
					int tb = 1;
					//Set<String> headerSet = new HashSet<>();
					for(Table tables: table) {
						if(headerMode==0) {
							headers = null;
						}
						int nehdrs = 0;
						//Map<String, Double> colhts = new HashMap<>();
						//double highest = 0;
						wr.write("============== Page ["+pn+"], Table ["+tb+"] Starts ==============\n");
			            List<List<RectangularTextContainer>> rows = tables.getRows();
			            // iterate over the rows of the table
			            for (List<RectangularTextContainer> cells : rows) {
			            	List<String> cols = new ArrayList<>();
			            	int necols = 0;
			                // print all column-cells of the row plus line feed
			            	boolean txtfound = false;
			                for (RectangularTextContainer content : cells) {
			                    // Note: Cell.getText() uses \r to concat text chunks
			                    String text = content.getText().replace("\r", " ");
			                    wr.write(text + (StringUtils.isBlank(colSep)?"|":colSep));
			                    cols.add(text);
			                    txtfound |= !text.trim().isBlank();
			                    necols += !text.trim().isBlank()?1:0;
			                }
			                if(txtfound) {
			                	try {
					                if(headers==null && headerMode<2) {
					                	headers = cols;
					                	//headerSet.add(StringUtils.join(headers, "$$$$$%%%%&&&&))))((((----"));
					                	nehdrs = necols;
				                		lst = new ArrayList<Map<String, String>>();
										_pdtTables.get(fk).add(lst);
					                } else {
					                	/*if(nehdrs<necols) {
					                		headers = cols;
						                	nehdrs = necols;
					                	} else*/ if(nehdrs<=necols) {
					                		/*for (RectangularTextContainer content : cells) {
							                    // Note: Cell.getText() uses \r to concat text chunks
							                    String text = content.getText().replace("\r", " ");
							                    if(!text.trim().isBlank()) {
								                    for (int i=0;i<cols.size();i++) {
								                    	String colt = cols.get(i).trim();
									                	if(colt.equals(text.trim())) {
									                		colhts.put(headers.get(i).trim(), content.getHeight());
									                		if(highest<content.getHeight()) {
									                			highest = content.getHeight();
									                		}
									                	}
								                    }
							                    }
							                }*/
					                		Map<String, String> row = new HashMap<>();
					                		if(headerMode==2) {
								                for (int i=0;i<cols.size();i++) {
								                	String hdrlab = "_p"+(i+1);
													row.put(hdrlab, cols.get(i));
												}
					                		} else {
								                for (int i=0;i<headers.size();i++) {
								                	String hdrlab = headers.get(i).trim();
								                	if(StringUtils.isBlank(hdrlab)) {
								                		hdrlab = "_p"+(i+1);
								                	}
													row.put(hdrlab, cols.size()>i?cols.get(i):null);
												}
					                		}
							                lst.add(row);
					                	} else {
					                		if(lst.size()==0) {
					                			Map<String, String> row = new HashMap<>();
					                			if(headerMode==2) {
									                for (int i=0;i<cols.size();i++) {
									                	String hdrlab = "_p"+(i+1);
														row.put(hdrlab, cols.get(i));
													}
						                		} else {
									                for (int i=0;i<headers.size();i++) {
									                	String hdrlab = headers.get(i).trim();
									                	if(StringUtils.isBlank(hdrlab)) {
									                		hdrlab = "_p"+(i+1);
									                	}
														row.put(hdrlab, cols.size()>i?cols.get(i):null);
													}
						                		}
								                lst.add(row);
					                		} else {
						                		Map<String, String> row = lst.get(lst.size()-1);
						                		if(headerMode==2) {
									                for (int i=0;i<cols.size();i++) {
									                	String hdrlab = "_p"+(i+1);
									                	if(StringUtils.isNotBlank(cols.get(i))) {
									                		row.put(hdrlab, row.get(hdrlab)+"<<<<NEW_LINE>>>>"+cols.get(i));
									                	}
													}
						                		} else {
									                for (int i=0;i<headers.size();i++) {
									                	String hdrlab = headers.get(i).trim();
									                	if(StringUtils.isBlank(hdrlab)) {
									                		hdrlab = "_p"+(i+1);
									                	}
									                	if(StringUtils.isNotBlank(cols.get(i))) {
									                		row.put(hdrlab, row.get(hdrlab)+"<<<<NEW_LINE>>>>"+cols.get(i));
									                	}
													}
						                		}
					                		}
					                	}
					                }
								} catch (Exception e) {
									e.printStackTrace();
								}
			                }
			                wr.write("\n");
			            }
			            wr.write("============== Page ["+pn+"], Table ["+tb+"] Ends ==============\n\n\n");
			            tb++;
			        }
				}
				pn++;
			}
			
			wr.write("============== Full Page Content Starts ==============\n");
			FileUtils.writeStringToFile(new File(filePath+".json"), WorkflowContextHandler.OM.writeValueAsString(_pdtTables.get(fk)),"UTF-8");
			PDFTextStripper stripper = new PDFTextStripper();
			String content = stripper.getText(doc);
			wr.write(content);
			wr.write("============== Full Page Content Ends ==============\n");
			
			if (oedoc != null) {
				oedoc.close();
			}
			wr.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
