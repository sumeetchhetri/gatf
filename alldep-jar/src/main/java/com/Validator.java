package com;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.remote.CapabilityType;

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.selenium.Command;
import com.gatf.selenium.SeleniumCodeGeneratorAndUtil;
import com.gatf.selenium.SeleniumDriverConfig;
import com.gatf.selenium.SeleniumTest;
import com.gatf.selenium.SeleniumTestSession;

/*GATF_ST_CLASS_START_*/ @SuppressWarnings("serial")
public class Validator extends SeleniumTest
implements Serializable {
	public static void main(String[] args) throws Exception {
		GatfExecutorConfig config = Command.getConfig("/path/to/project/gatf-config.xml", "/path/to/project");
		config.setSeleniumLoggerPreferences("browser(OFF),client(OFF),driver(OFF),performance(OFF),profiler(OFF),server(OFF)");
		for (SeleniumDriverConfig selConf : config.getSeleniumDriverConfigs())
		{
			if(selConf.getDriverName()!=null) {
				System.setProperty(selConf.getDriverName(), selConf.getPath());
			}
		}

		AcceptanceTestContext c = new AcceptanceTestContext();
		c.setGatfExecutorConfig(config);
		c.validateAndInit(true);
		c.getWorkflowContextHandler().initializeSuiteContext(1);

		final LoggingPreferences lp = SeleniumCodeGeneratorAndUtil.getLp(config);
		Validator v = new Validator(c, 0);
		v.execute(lp);
	}

	public Validator(AcceptanceTestContext ___cxt___, int index) {
		/*GATF_ST_CLASS_INIT_*/ super("test.sel", ___cxt___, index);
	}

	public void close() {
		/*GATF_ST_CLASS_CLOSE_*/ if (get___d___() != null) get___d___().close();
	}

	public SeleniumTest copy(AcceptanceTestContext ctx, int index) {
		return new Validator(ctx, index);
	}

	public void setupDriverchrome(LoggingPreferences ___lp___) throws Exception {
		/*GATF_ST_DRIVER_INIT_CHROME*/ org.openqa.selenium.chrome.ChromeOptions ___dc___ =
				new org.openqa.selenium.chrome.ChromeOptions();
		___dc___.setCapability(org.openqa.selenium.chrome.ChromeOptions.LOGGING_PREFS, ___lp___);
		___dc___.setCapability("deviceName", "test");
		___dc___.setCapability(CapabilityType.ACCEPT_INSECURE_CERTS, true);
		___dc___.addArguments(
				"--whitelisted-ips --no-sandbox  --ignore-certificate-errors".split("\\s+"));
		Map<String, Object> __prefs = new java.util.HashMap<String, Object>();
		__prefs.put("download.default_directory", "/tmp/");
		___dc___.setExperimentalOption("prefs", __prefs);
		set___d___(new org.openqa.selenium.chrome.ChromeDriver(___dc___));
	}
	public List<SeleniumTestSession> execute(LoggingPreferences ___lp___) throws Exception {
		addTest(null, "chrome");
		setSession(null, 0, true);
		quit();
		setupDriverchrome(___lp___);
		_execute(___lp___);
		quitAll();
		return get__sessions__();
	}

	public int concurrentExecutionNum() {
		return 0;
	}

	@SuppressWarnings("unused")
	public void _execute(LoggingPreferences ___lp___) throws Exception {
		WebDriver ___ocw___ = null;
		try {
			SearchContext ___sc___1 = get___d___();
			WebDriver ___cw___ = get___d___();
			___ocw___ = ___cw___;
			List<WebElement> ___ce___ = null;
			___cw___.manage().window().maximize();

			try {
				___cw___.navigate().to(evaluate("https://example.com/url/"));
			} catch (org.openqa.selenium.TimeoutException ___e___3) {
				___cw___.navigate().refresh();
			}

			newProvider("prov1");
			List<String[]> ___w___2 = transientProviderData(
					___cw___,
					___sc___1,
					___ce___,
					0L,null,
					new String[] {evaluate("userNameTxt"),null},
					new String[] {"class",null},
					evaluate("attr@id,attr@name"),
					false,
					null,
					new String[] {},
					null,
					null,
					null,
					"Element not found by selector class@'userNameTxt' at line number 2 ",
					false,
					1000,
					false,
					""
					);

			for (int ___w___3 = 0; ___w___3 < ___w___2.size(); ___w___3++) {
				Map<String, String> __mp = new java.util.HashMap<String, String>();
				__mp.put("var1", ___w___2.get(___w___3)[0]);

				__mp.put("var2", ___w___2.get(___w___3)[1]);
				getProviderTestDataMap("prov1").add(__mp);
			}
			int ___w___4 = getProviderTestDataMap("prov1").size();
			set__provname__("prov1");

			Object ___w___5 = null;

			//if (___get_var_nex__("@index") != null) ___w___5 = (Integer) ___get_var_nex__("@index");

			for (int ___itr___1 = 0; ___itr___1 < ___w___4; ___itr___1++) {

		        ___add_var__("@index", ___itr___1);
		        set__provpos__("prov1", ___itr___1);
		        /*GATF_ST_LINE@/Users/sumeetc/Projects/GitHub/gatf/alldep-jar/sample/data/clinical.sel:4*/ System
		            .out.println(getProviderDataValue("var1", false));
		        /*GATF_ST_LINE@/Users/sumeetc/Projects/GitHub/gatf/alldep-jar/sample/data/clinical.sel:5*/
		        //exec @print(${var2})
		        /*GATF_ST_LINE@/Users/sumeetc/Projects/GitHub/gatf/alldep-jar/sample/data/clinical.sel:6*/ boolean
		            ___ifcnt___1 = false;
		        try {

		          Assert.assertTrue(
		              "Evaluation condition is invalid at line number 6 ",
		              doEvalIf("\"${var1}\"==\"usernameId\""));
		          /*GATF_ST_LINE@/Users/sumeetc/Projects/GitHub/gatf/alldep-jar/sample/data/clinical.sel:8*/ System
		              .out.println("variable 1-----if native");

		          ___ifcnt___1 = true;

		        } catch (AssertionError ___e___2) {
		          ___ifcnt___1 = false;
		        } catch (Exception ___e___1) {
		          System.out.println(___e___1.getMessage());
		        }

		        /*GATF_ST_LINE@/Users/sumeetc/Projects/GitHub/gatf/alldep-jar/sample/data/clinical.sel:10*/ if (getProviderDataValueO(
		                "var1", false)
		            .equals("usernameId")) {
		          /*GATF_ST_LINE@/Users/sumeetc/Projects/GitHub/gatf/alldep-jar/sample/data/clinical.sel:11*/ System
		              .out.println("variable 1-----if java");
		          /*GATF_ST_LINE@/Users/sumeetc/Projects/GitHub/gatf/alldep-jar/sample/data/clinical.sel:12*/ }
		      }
			___add_var__("@index", ___w___5);
			rem__provname__("prov1");

			List<Map<String, String>> prov1 = getProviderTestDataMap("prov1");

			//id
			System.out.println(getElements(___cw___, ___sc___1, "id@usernameId", null, null, null));

			//className
			System.out.println(getElements(___cw___, ___sc___1, "class@userNameTxt", null, null, null));
			System.out.println(getElements(___cw___, ___sc___1, "className@userNameTxt", null, null, null));

			//name
			System.out.println(getElements(___cw___, ___sc___1, "name@username", null, null, null));

			//tagname
			System.out.println(getElements(___cw___, ___sc___1, "tag@input", null, null, null));
			System.out.println(getElements(___cw___, ___sc___1, "tagname@input", null, null, null));

			//Xpath
			System.out.println(getElements(___cw___, ___sc___1, "xpath@\"//*[@id=\"usernameId\"]\"", null, null, null));
			System.out.println(getElements(___cw___, ___sc___1, "xpath@\"//*[text()='Login']\"", null, null, null));
			System.out.println(getElements(___cw___, ___sc___1, "xpath@\"//*[contains(text(), 'Login')]\"", null, null, null));
			System.out.println(getElements(___cw___, ___sc___1, "xpath@\"//input[contains(@class, 'form-control') and contains(@class, 'userNameTxt') and contains(@class, 'enterme')]\"", null, null, null));
			//Full Xpath
			System.out.println(getElements(___cw___, ___sc___1, "xpath@\"/html/body/div[3]/div/div[2]/div/div[1]/div/div/div/form[1]/div/div/div[1]/div[2]/div/div[1]/input\"", null, null, null));

			//cssselector
			//tag id
			System.out.println(getElements(___cw___, ___sc___1, "cssselector@\"input#usernameId\"", null, null, null));
			//tag class
			System.out.println(getElements(___cw___, ___sc___1, "cssselector@\"input.userNameTxt\"", null, null, null));
			//tag attribute
			System.out.println(getElements(___cw___, ___sc___1, "cssselector@\"input[name=username]\"", null, null, null));
			//tag class attribute
			System.out.println(getElements(___cw___, ___sc___1, "css@\"input.userNameTxt[name=username]\"", null, null, null));

			//text
			System.out.println(getElements(___cw___, ___sc___1, "text@\"User Name\"", null, null, null));

			//linkText
			System.out.println(getElements(___cw___, ___sc___1, "linkText@\"Forgot Password?\"", null, null, null));

			//partialLinkText
			System.out.println(getElements(___cw___, ___sc___1, "partialLinkText@\"Forgot\"", null));

			//jq
			System.out.println(getElements(___cw___, ___sc___1, "jq@input[name=username]", null));
			System.out.println(getElements(___cw___, ___sc___1, "jquery@.form-control.userNameTxt.enterme[name=username]", null));
			System.out.println(getElements(___cw___, ___sc___1, "$@body", null));

			//active
			System.out.println(getElements(___cw___, ___sc___1, "active", null));


			pushResult(new SeleniumTestResult(get___d___(), this, ___lp___));
		} catch (Throwable ___e___1) {
			try {
				___e___1.printStackTrace();
				java.lang.System.out.println("_main_exec.png");
				screenshotAsFile(get___d___(), getOutDir() + java.io.File.separator + evaluate("_main_exec.png"));
			} catch (java.io.IOException _ioe) {
			}
			pushResult(new SeleniumTestResult(get___d___(), this, ___e___1, "_main_exec.png", ___lp___, null));
		}
	}
}