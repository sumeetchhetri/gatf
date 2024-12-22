Generic Automated Test Framework (GATF)
=========

**GATF** is an automated test generator and acceptance testing framework. It provides multiple components namely the **Test Generator**, the **Test Executor** , the **Config Tool** and the **Robotic Process Automation Tool**

GATF Acceptance Test executor is data-type agnostic, which means that your testcases can be in any formats like XML, JSON or plain old CSV. Whereas the Test generator generates only XML files (so that even complex data structures can be supported within your testcases) 

> The primary goal of GATF is Automation with Simplicity.

Important features
==========
1. Robotic Process Automation
2. Comparative Env Testing
3. Concurrent User Testing
4. Performance Testing
5. Load Testing (API and UI both) [wrk, wrk2, vegeta, autocannon, embedded]
6. UI Testing with seleasy language + seleasy debugger
7. Reporting Engine
8. Remote Agent for Distributed Load Testing
9. Workflow Testing
10. Data Source Integrations
	- SQL
	- MongoDB
	- Files (XML/JSON/CSV/XLS/XLSX/ODT)

GATF Test Executor
================

The GATF Test Executor module provides a consolidated testing tool for,

  1. Single Session Test case execution
  2. Performance Test case execution
  3. Scenario/Workflow based Test case execution
  4. Concurrent User Simulation
  5. Comparative Test case study against multiple environments
  6. Load (Distributed) Testing

  It also provides the following,

  1. Pie charts for overall test status (Success/Failure reports)
  2. Line/Bar charts for overall performance results
  3. Detailed test case reports with comprehensive information about a test execution request/response
  4. Maven/Executable-Jar test case execution options

It uses a highly performant asynchronous http client library - async-http-client and hence achieves very good execution times.



<h3 style="margin-left:20px">GATF Test Data Providers</h3>
<p style="margin-left:20px">The GATF framework provides the option to integrate to multiple data sources for fetching test case data, which include the following,</p>

<li style="margin-left:40px">Any SQL compliant database</li>
<li style="margin-left:40px">MongoDB</li>
<li style="margin-left:40px">Files - CSV/JSON/XML</li>
<li style="margin-left:40px">Inline/Value based</li>
<li style="margin-left:40px">Custom Provider</li>
<li style="margin-left:40px">Random Value Provider</li>

<p style="margin-left:40px">The framework provides automatic built-in providers for easy integration to the above mentioned data sources. But we can also define custom providers if required.</p>

<h3 style="margin-left:20px">GATF Pre/Post Test Case Execution Hooks</h3>
<p style="margin-left:20px">The framework also provides the facility to plug in pre/post test case execution logic in order to control the test case execution flow, it provides 2 simple annotations,</p>

<li style="margin-left:40px">@PreTestCaseExecutionHook - Marks a method as a pre-test-case execution hook</li>
<li style="margin-left:40px">@PostTestCaseExecutionHook - Marks a method as a post-test-case execution hook</li>


<p style="margin-left:20px">The complete configuration for the GATF executor framework is listed below, We just need to define a file with the contents below and configure it in maven or provide the path to the file to the executor to execute your test cases.</p>

<details style="margin-left:20px">
<summary>Sample executor config.xml</summary>
<br>

```xml
<gatf-execute-config>
    <!-- Whether auto authentication support is enabled -->
    <authEnabled>true</authEnabled>
  
    <!-- Authentication token parameters - name(token), where to find it(json),
        name(token) and usage of token in subsequent secure calls(query parameter) -->
    <authExtractAuth>token,json,token,queryparam</authExtractAuth>
  
    <!-- The authentication URL -->
    <authUrl>api/rest/auth/loginh</authUrl>
  
    <!-- The username and password parameters and their point of presence in the request -->
    <authParamsDetails>username,header,password,header</authParamsDetails>
  
    <!-- The environment base URL, all requests(authentication and other testcases)
        having relative URL's will be using this URL as their base -->
    <baseUrl>http://localhost:8080/sampleApp</baseUrl>
  
    <!-- Whether the plugin is enabled -->
    <enabled>true</enabled>
  
    <!-- Where should the testcase execution reports be generated, default folder name is out -->
    <outFilesDir>out</outFilesDir>
  
    <!-- Where to find all the testcases(xml,json,csv files), default folder name is data -->
    <testCaseDir>data</testCaseDir>
  
    <!-- Whether HTTP compression is enabled, default true -->
    <httpCompressionEnabled>true</httpCompressionEnabled>
    <!-- The HTTP connection timeout, default 10000 (10sec), time in ms -->
    <httpConnectionTimeout>100000</httpConnectionTimeout>
     
    <!-- The HTTP request timeout, default 10000 (10sec), time in ms -->
    <httpRequestTimeout>10000</httpRequestTimeout>
  
    <!-- The plugin enables the use of pre/post testcase execution hooks,
        with the help of the @PreTestCaseExecutionHook and @PostTestCaseExecutionHook annotations,
        Custom logic can be written using these annotations for pre/post testcase execution control,
        this parameter defines the pacakage to scan to find the custom defined java classes defining
        the hooks using the annotation -->
    <testCaseHooksPaths>
        <testCaseHooksPath>com.*</testCaseHooksPath>
    </testCaseHooksPaths>
  
    <!-- The number of concurrent users to simulate, default 0 -->
    <concurrentUserSimulationNum>1</concurrentUserSimulationNum>
    <!-- The number of concurrent connections to the Base environment HTTP Server, default 1 -->
    <numConcurrentExecutions>1</numConcurrentExecutions>\
  
    <!-- The provider name that will be used to fetch user/password details for user credentials -->
    <simulationUsersProviderName>file-auth-provider</simulationUsersProviderName>
  
    <!-- Whether comparative analysis is enabled, if yes then the compareEnvBaseUrls property inside
          gatfTestDataConfig element needs to define the multiple environments where a comparison
          of test case executions is desired -->
    <compareEnabled>false</compareEnabled>
  
    <!-- Whether load testing is enabled -->
    <loadTestingEnabled>false</loadTestingEnabled>
  
    <!-- Load testing execution time, time in ms -->
    <loadTestingTime>40000</loadTestingTime>
 
    <!-- The concurrent user ramp up time during load testing, time in ms -->
    <concurrentUserRampUpTime>500</concurrentUserRampUpTime>
  
    <!-- The number of report samples to be generated during the load testing window -->
    <loadTestingReportSamples>6</loadTestingReportSamples>
  
    <!-- Whether debug logs are enabled or not -->
    <debugEnabled>true</debugEnabled>
  
    <!-- The WSDL Location file with the wsdl endpoints -->
    <wsdlLocFile>wsdl-locations.csv</wsdlLocFile>
  
    <!-- The test data configuration parameters including the global parameters, compare
        environment URL's and the list of all the test data providers you may define-->
    <gatfTestDataConfig>
         
        <!-- The list of compare environment URL's -->
        <compareEnvBaseUrls>
            <string>http://localhost:8081/sampleApp</string>
        </compareEnvBaseUrls>
  
        <!-- The global parameter list -->
        <globalVariables>
            <gvar1>gvalue</gvar1>
        </globalVariables>
  
        <!-- The list of test data providers --> 
        <providerTestDataList>
  
            <!-- The MongoDB test data provider -->
            <gatf-testdata-provider>
                <!-- Provider name -->
                <providerName>mongodb-auth-provider</providerName>
                <!-- Provider class -->
                <providerClass>com.gatf.executor.dataprovider.MongoDBTestDataProvider
                </providerClass>
                <args>
                    <!-- MongoDB Host -->
                    <arg>localhost</arg>
                    <!-- MongoDB Port -->
                    <arg>27017</arg>
                    <!-- MongoDB Database name -->
                    <arg>wellogic</arg>
                    <!-- MongoDB Collection -->
                    <arg>User</arg>
                    <!-- MongoDB Query -->
                    <arg>{"status": "ACTIVE"}</arg>
                    <!-- MongoDB Collection property names -->
                    <arg>user,password</arg>
                    <!-- Provider variable names -->
                    <arg>username,password</arg>
                </args>
            </gatf-testdata-provider>
  
            <!-- The Database test data provider -->
            <gatf-testdata-provider>
                <providerName>database-auth-provider</providerName>
                <providerClass>com.gatf.executor.dataprovider.DatabaseTestCaseDataProvider
                </providerClass>
                <args>
                    <!-- Database JDBC Url -->
                    <arg>jdbc:mysql://localhost/globaldb</arg>
                    <!-- Database driver class name -->
                    <arg>com.mysql.jdbc.Driver</arg>
                    <!-- Database username -->
                    <arg>root</arg>
                    <!-- Database Password -->
                    <arg></arg>
                    <!-- SQL Query -->
                    <arg>SELECT USER_NAME,PASSWORD FROM BASE_USER</arg>
                    <!-- Provider variable names -->
                    <arg>username,password</arg>
                </args>
            </gatf-testdata-provider>
  
            <!-- The File test data provider -->
            <gatf-testdata-provider>
                <enabled>true</enabled>
                <providerName>file-auth-provider</providerName>
                <providerClass>com.gatf.executor.dataprovider.FileTestCaseDataProvider
                </providerClass>
                <args>
                    <!-- File path, relative to 'testCaseDir' folder -->
                    <arg>authfile.csv</arg>
                    <!-- File Type, can be csv,json or xml -->
                    <arg>csv</arg>
                    <!-- Provider variable names -->
                    <arg>username,password</arg>
                </args>
            </gatf-testdata-provider>
  
            <!-- The inline test data provider -->
            <gatf-testdata-provider>
                <enabled>true</enabled>
                <providerName>inline-auth-provider</providerName>
                <providerClass>com.gatf.executor.dataprovider.InlineValueTestCaseDataProvider
                </providerClass>
                <args>
                    <!-- Provider variable names -->
                    <arg>username,password</arg>
                    <!-- Inline values follow -->
                    <arg>user1,password</arg>                  
                    <arg>user2,password</arg>
                </args>
            </gatf-testdata-provider>
  
            <!-- The Random values test data provider -->
            <gatf-testdata-provider>
                <enabled>true</enabled>
                <providerName>random-auth-provider</providerName>
                <providerClass>com.gatf.executor.dataprovider.RandomValueTestCaseDataProvider</providerClass>
                <args>
                    <!-- Provider variable names -->
                    <arg>username,password</arg>
                    <!-- Provider variable types, can be one of alpha, alphanum, number, boolean,
                         -number, +number and date(format) -->
                    <arg>alpha,alphanum</arg>
                </args>
            </gatf-testdata-provider>
  
            <!-- The Custom test data provider -->
            <gatf-testdata-provider>
                <enabled>true</enabled>
                <providerName>custom-auth-provider</providerName>
                <providerClass>com.CustomTestDataProvider</providerClass>
                <args>
                    <!-- Provider variable names -->
                    <arg>username,password</arg>
                </args>
            </gatf-testdata-provider>
        </providerTestDataList>
    </gatfTestDataConfig>
</gatf-execute-config>
```

</details>

<details style="margin-left:20px">
<summary>Sample maven configuration</summary>
<br>

```xml
<!-- Add jitpack artifact repositories for gatf dependencies -->
<pluginRepositories>
	<pluginRepository>
	    <id>jitpack.io</id>
	    <url>https://jitpack.io</url>
	</pluginRepository>
</pluginRepositories>

<repositories>
	<repository>
	    <id>jitpack.io</id>
	    <url>https://jitpack.io</url>
	</repository>
</repositories>

<!-- Add the generator plugin which will be invoked during the test phase -->
<build>
	<plugins>
		<plugin>
			<groupId>com.github.sumeetchhetri.gatf</groupId>
			<artifactId>gatf-plugin</artifactId>
			<version>1.1.0</version>
			<executions>
				<execution>
					<id>gatf-execution</id>
					<configuration>
						<configFile>gatf-config.xml</configFile>
					</configuration>
					<phase>test</phase>
					<goals>
						<goal>gatf-executor</goal>
					</goals>
				</execution>
			</executions>
		</plugin>
	</plugins>
</body>
```

</details>

<details style="margin-left:20px" open>
<summary>Execution</summary>
<br>

For direct execution, we just need to specify a simple config.xml(any name .xml) file with the contents as follows, on the command line,

```sh
java -jar gatf-alldep-3.0.3.jar -executor /workdir/path/to/gatf/config.xml
```

```sh
docker run -v /dev/shm:/dev/shm -v /local-folder:/workdir -e TZ=Asia/Kolkata -it sumeetchhetri/gatf-bin:latest -executor /workdir/path/to/gatf/config.xml
```

For direct execution for RPA (selenium), we just need to specify a simple config.xml(any name .xml) file with the contents as follows, on the command line,

```sh
java -jar gatf-alldep-3.0.3.jar -selenium /workdir/path/to/gatf/config.xml
```

```sh
docker run -v /dev/shm:/dev/shm -v /local-folder:/workdir -e TZ=Asia/Kolkata -it sumeetchhetri/gatf-bin:latest -selenium /workdir/path/to/gatf/config.xml
```

For validating test script for RPA (selenium), use the following command,

```sh
java -jar gatf-alldep-3.0.3.jar -validate-sel test-script-name-relative-to-workdir.sel /workdir/path/to/gatf/config.xml
```

```sh
docker run -v /dev/shm:/dev/shm -v /local-folder:/workdir -e TZ=Asia/Kolkata -it sumeetchhetri/gatf-bin:latest -validate-sel test-script-name-relative-to-workdir.sel /workdir/path/to/gatf/config.xml
```

For running gatf in distributed mode as a listener, use the following command,

```sh
java -jar gatf-alldep-3.0.3.jar -listener
```

```sh
docker run -v /dev/shm:/dev/shm -v /local-folder:/workdir -e TZ=Asia/Kolkata -it sumeetchhetri/gatf-bin:latest -listener
```

</details>

GATF Robotic Process Automation Tool
================

The GATF RPA tool provides an easy to use language to write process automation tests called **seleasy**, using seleasy it becomes super easy to write any simple or complex test cases also utilizing the full capabilities of the workflow and provider logic described in the configuration above, moreover UI load and Distributed load testing can now be very easily performed with gatf.

Moreover a javascript library is provided as an additional support system to automatically record and playback browser actions, this small script when injected into the (Inspect Element - Chrome), (Developer Tools - Firefox) etc, provides an initial bootstrap to generate most of the automation scripts.

Just to give a glimpse of how easy it is to write process automation test cases with seleasy(gatf) assume we have a login page hosted at http://example.com and after logging in we need to see the Name of the user in the Profile section under the xpath */html/body/div[4]/div[1]/div/div[2]/div[1]/div/p[1]*, an example gatf script to execute the test in 4 different browsers would look like,

```
open chrome
open chrome-hdl
open chrome-dkr
open chrome-rec
open firefox
open ie
open opera
goto http://example.com
??10 id@username type "user"
??10 id@password type "password"
??10 class@loginBtn click
??10 xpath@"/html/body/div[4]/div[1]/div/div[2]/div[1]/div/p[1]" click
```
Simplicity lies at the core of the seleasy language which was invented just to ensure that we focus on the problem at hand instead of writing lengthy selenium scripts in java.

To start recording browser actions just type into the Inspect Element -> Console (Chrome)

```
Fg.startRecording()
```

To stop recording

```
Fg.stopRecording()
```

Seleasy Syntax Reference
---------------------

```
Mobile shake
	shake


Data Source Query definition
	#dsq {dsn} {query}
Examples :-
	#dsq sqldb "update some_table set cola = 'value' where colb = 1"


If block
	? {find-expr} & {find-expr}?
	{
		code
	}
	? eval {template-expr} & {template-expr}?
	{
		code
	}
	? browser-scope {browser-name}?
	{
		code
	}
	? session-scope {browser-name}?
	{
		code
	}
Examples :-
	? xpath@"ddd"
	{
		exec @print("if")
	}
	? eval "a"=="a" & eval ${bvar}!="b"
	{
		exec @print("if")
	}
	? browser-scope "chrome"
	{
		exec @print("if")
	}
	? s-scope "one"
	{
		exec @print("if")
	}


Send keys using Robot
	robot keydown|keyup|keypress {key-code1} ... keydown|keyup|keypress|keyrelease|key {key-codeN}
	scrollup
	scrolldown
	scrollpageup
	scrollpagedown
Examples :-
	robot keydown 1
	robot keyup 1
	robot keypress 1
	robot keypress 1 keydown 2 keyup 
	scrollup
	scrolldown
	scrollpageup
	scrollpagedown


Open URL in window
	goto {url}
Examples :-
	goto http://abc.com/testpage.html


Submit element
	submit {find-expr}
Examples :-
	submit id@'ele1'


Define test mode
	mode {normal|integration} {true|false}
Examples :-
	mode normal
	mode integration


Java control command, block statements
	#j{if|try|catch|finally|else|else if|while|for|continue|break|\{|\}|synchronized} {java statement}
Examples :-
	#jif(1==1) {} else {}
	#jfor(int i=0;i<10;i++){}


Select value from dropdown element
	select {text|index|value|first|last}@{value} {find-expr}
Examples :-
	select text@'first' id@'abc'
	select index@2 id@'abc'
	select value@'second' id@'abc'
	select first id@'abc'
	select last id@'abc'


Mobile Tap
	tap ({find-expr}|{find-expr} {duration}|{x-co-ordinate} {y-co-ordinate}|{x-co-ordinate} {y-co-ordinate} {duration}|{find-expr} {x-co-ordinate} {y-co-ordinate}|{find-expr} {x-co-ordinate} {y-co-ordinate} {duration})
Examples :-
	tap id@'ele'
	tap id@'ele' 2000
	tap id@'ele' 123 234
	tap id@'ele' 123 234 2000
	tap 123 234
	tap 123 234 2000


Save URL passed to window.open
	netapix on {api-method} {api-url}
	netapix off {status|header|json} {header-name|json-path}?
Examples :-
	netapix on GET http://abc.com/api/person
	netapix off status --> creates context parameter by name ${apiStatus}=200
	netapix off header token --> creates context parameter by name ${apiHeader}=some-token
	netapix off json $.store.book[0].title --> creates context parameter by name ${apiJson}=some-value


Loop block
	## {find-expr}
	{
		code
	}
Examples :-
	## class@"ddd"
	{
		exec @print(@index)
		click xpath@"ddd-@index"
	}


File Variable definition
	filevar {var1,var2,..} {regex} {var1-pattern,var2-pattern..}
Examples :-
	filevar v1,@v2,@v3 "(.*)|(.*),(.*)" "$1,ABC_$2,BCD$3EFG"
	filevar v1 "([a-z]+)" "$1"


Mobile hide keypad
	hidekeypad


Maximize window
	maximize


Scroll [Javascript based]
	scroll {up|down|pageup|pagedown|top|bottom|left|right|find-expr}
Examples :-
	scroll up
	scroll id@dsdsd


Check whether an http request was received from a given sender, for a given receiver and matching the given content with timeout period
	http {name}  {from} {to} {content} {timeout}?
Examples :-
	http 'http request message for sms' 'from' 'to' 'How are you doing today'
	http 'http request message for sms' 'from' 'to' 'How are you doing today (.*)'
	http 'http request message for sms' 'from' 'to' 'How are you doing today (.*)' 50000


Save element image to file
	imgsave {element-selector} {optional image-file-path-to-save-screenshot-to}
Examples :-
	imgsave id@'eleid'
	imgsave id@'eleid' '/path/to/image/file/file.png'


Print pdf of the page to file
	printpdf {filepath} {text?} {col-separator}?
Examples :-
	printpdf '/path/to/file.txt'
	printpdf '/path/to/file.txt' text
	printpdf '/path/to/file.txt' text |


Block level comment
	/*
	 Some multi line comment
	 Some multi line comment 2
	*/


Double Click element
	doubleclick {find-expr}
	dblclick {find-expr}
Examples :-
	doubleclick id@'ele1'


Click element
	click(fo|nf) {find-expr}
Examples :-
	click id@'ele1'
	clickfo id@'ele1'
	clicknf id@'ele1'


Define Screen No-activity layers
	layer {find-expr}
Examples :-
	layer id@"loader-icon"
	layer id@"overlay-div"


Fail test/sub-test and continue
	warn {error string}
Examples :-
	warn "Test failed"
	warn "Sub-Test failed"


Take screenshot
	screenshot {image-file-path-to-save-screenshot-to}
Examples :-
	screenshot
	screenshot "/path/to/image/file/file.png"


Subtest definition
	subtest "name" session-name|@session-id (args)
	{
		code
	}
where
	session-name - the browser session name for which to run this sub test
	session-id - the browser session id prefixed with @ for which to run this sub test
	args - the arguments passed to the subtest, space separated names for args
Examples :-
	subtest "sb1" "bs1"
	{
		select index@4 id@"Location"
	}
	subtest "sb1" @1 (arg1 arg2)
	{
		select index@$v{arg1} id@"$v{arg2}"
	}


Function definition
	func "name" (args)
	{
		code
	}
where
	args - the arguments passed to the func
Examples :-
	func "cmd1"
	{
		select index@4 id@"Location"
	}
	func "cmd2" @1 (arg1 arg2)
	{
		select index@$v{arg1} id@"$v{arg2}"
	}




Mobile rotate
	rotate


Transient Variable definition
	#transient-variable {variable-name} {find-expr} {sub-selector}
Examples :-
	#transient-variable var1 id@'abc' text
	#transient-variable var1 id@'abc' html
	#transient-variable var1 id@'abc' attr@data-prop
	#tv var1 id@'abc' text
	#tv var1 id@'abc' html
	#tv var1 id@'abc' attr@data-prop


Else block, needs to be superseded by an If or Else-If block
	:
	{
		code
	}
Examples :-
	:
	{
		exec @print("else")
	}


Mobile Zoom
	zoom ({x-co-ordinate} {y-co-ordinate}|{find-expr})
Examples :-
	zoom 123 234
	zoom id@'ele'


Include other seleasy scripts inline
	include {script-path}
Examples :-
	include a/b/c/t1.sel
	include t2.sel


Pass test/sub-test
	pass {some string}?
Examples :-
	pass
	pass "Test passed"
	pass "Sub-Test passed"


Navigate Forward/Next
	forward


Transient Provider definition
	#transient-provider {provider-name} {variableName1,...,variableNameN} {find-expr} {sub-selector1,...,sub-selectorN} {lazy}?
Examples :-
	#transient-provider prov1 var1,var2 id@'abc' text,attr@abc
	#tp prov1 var1,var2 id@'abc' text,attr@abc


Mobile Touch
	touch ({press|moveto|tap {find-expr}|{find-expr} {x-co-ordinate} {y-co-ordinate}|{x-co-ordinate} {y-co-ordinate}}|longpress|{longpress {find-expr} {x-co-ordinate} {y-co-ordinate} {duration}|{find-expr} {x-co-ordinate} {y-co-ordinate}|{find-expr} {duration}|{x-co-ordinate} {y-co-ordinate} {duration}|{x-co-ordinate} {y-co-ordinate}}|{wait {duration}}|release) ... ({press|moveto|tap {find-ex...
Examples :-
	touch moveto id@'ele' longpress moveto id@'ele2' wait 1000 release
	touch moveto id@'ele' longpress id@'ele1' moveto id@'ele2' wait 1000 release


Show alert with message
	alert {value}
Examples :-
	alert
	alert 'Hello'


Import dynamic (code vars) properties file, dynamic variables provide support for configurable code
	dynprops {file-path}
Examples :-
	dynprops a/b/c/t1.props
	dynprops t2.props


Type Value in input/textarea elements
	(type|sendkeys|chord|randomize)(bl|ch|bk|cl|fo) {text} {find-expr}
	where 
		bl->Trigger Blur
		ch->Trigger Change
		bk->Trigger Backspace
		cl->Trigger Click
		fo->Trigger Focus
Examples :-
	type 'abc' id@'ele1'
	sendkeys 'abc' id@'ele1'
	typebl 'abc' id@'ele1'
	sendkeyscl 'abc' id@'ele1'


Handle Confirm Dialog
	confirm ok|cancel|yes|no {optional button-text-to-check}
Examples :-
	confirm ok
	confirm yes
	confirm cancel
	confirm no
	confirm yes 'Confirm'


Type UTF-8 or normal ASCII characters in input/textarea elements
	chord(bl|ch|bk|cl|fo) {utf-8 character1}{utf-8 character2}...{utf-8 characterN} {find-expr}
Examples :-
	chord \u0048\u0065\u006c\u006c\u006f\u0020\u0057\u006f\u0072\u006c\u0064 id@'abc'


Hover over an element
	hover {find-expr}
Examples :-
	hover id@'abc'


Find Expression
	{eval|browser-scope|session-scope|relative}? {id|name|class|xpath|tag|cssselector|css|text|partialLinkText|linkText|jq|$|jquery|active|this|current}(@selector) (title|currentUrl|pageSource|width|height|xpos|ypos|alerttext) {matching-value|matching-value-in-list}


Execute java code
	exec {java statement}
Available variables in context -
	1. @driver - WebDriver instance
	2. @window - WebDriver instance
	3. @element - Currently selected WebElement instance
	4. @sc - Currently selected SearchContext instance
	5. @printProvJson - Print Provider data as json
	6. @printProv - Print Provider data
	7. @print - System.out.println
	8. @index - Current provider index under iteration
Examples :-
	exec @driver.refresh()
	exec @window.back()
	exec @element.click()
	exec @sc.findElement(org.openqa.selenium.By.id("id")
	exec @printProvJson("provider-name")
	exec @printProv("provider-name")
	exec @print("something-to-console")
	exec @print(@index)
	exec System.out.println("Hello World!")


Refresh window
	refresh


Specify Java Imports
	require fqcn|[{fqcn1},..{fqcnN}]
Examples :-
	require java.util.Date
	require [java.util.List, java.math.BigDecimal]


Type random values in input/textarea elements
	randomize(bl|ch|bk|cl|fo) {find-expr} alphanumeric|alpha|alphanumericlc|alphalc|alphanumericuc|alphauc|numeric|value|range|prefixed|prefixed_|fuzzyn|fuzzya|fuzzyauc|fuzzyalc|fuzzyan|fuzzyanuc|fuzzyanlc {optional character count|range start} {count of space separated random words(for eg, name of person)|range end}
Examples :-
	randomize id@'ele1' alphanumeric 12
	randomize id@'ele1' alpha 8 3 (first-name middle-name last-name)
	randomize id@'ele1' numeric 5
	randomize id@'ele1' range 9999 99999
	randomize id@'ele1' value 'abcd'
	randomize id@'ele1' fuzzyn _@3:4:5:6
	randomize id@'ele1' fuzzya _@3:4:5:6
	randomize id@'ele1' fuzzyan _@3:4:5:6


Value
	{primtive-value}
Examples :-
	'abc'
	123
	true


Provider Loop block
	#provider {find-expr}
	{
		code
	}
	#p {find-expr}
	{
		code
	}
	#counter {start-index} {end-index}
	{
		code
	}
	#counter {end-index}
	{
		code
	}
	#c {start-index} {end-index}
	{
		code
	}
	#c {end-index}
	{
		code
	}
	#sql {dsn} {query} {var1,var2,..}
	{
		code
	}
	#mongo {dsn} {collection-name} {query} {col1,col2,..} {var1,var2,..}
	{
		code
	}
	#file {filepath} {var1,var2,..}
	{
		code
	}
Examples :-
	#provider "provider-name"
	{
		exec @print(@index)
		click xpath@'$provider-variable-1'
	}
	#provider "provider-name" 0 3
	{
		exec @print(@index)
		click xpath@'$provider-variable-1'
	}
	#provider "provider-name" 2
	{
		exec @print(@index)
		click xpath@'$provider-variable-1'
	}
	#p "provider-name"
	{
		exec @print(@index)
		click xpath@'$provider-variable-1'
	}
	#p "provider-name" 0 3
	{
		exec @print(@index)
		click xpath@'$provider-variable-1'
	}
	#p "provider-name" 2
	{
		exec @print(@index)
		click xpath@'$provider-variable-1'
	}
	#counter 0 5
	{
		exec @print(@index)
	}
	#counter 5
	{
		exec @print(@index)
	}
	#c 0 5
	{
		exec @print(@index)
	}
	#c 5
	{
		exec @print(@index)
	}
	#sql sqldsn "select col1,col2 from some_table" "var1,var2"
	{
		exec @print(@index)
		exec @print(@var1)
		click xpath@'$var2'
	}
	#file "/tmp/path/file.txt" "var1,var2"
	{
		exec @print(@index)
		exec @print(@var1)
		click xpath@'$var2'
	}


Check whether email was received from a given sender, for a given receiver, with a given subject and matching the given content with timeout period
	mail {name}  {from} {to} {subject} {content}? {timeout}?
Examples :-
	mail 'mail for appointment' 'abc@a.com' 'abc@b.com' 'Hello There'
	mail 'mail for appointment' 'abc@a.com' 'abc@b.com' 'Hello There' 'How are you doing today'
	mail 'mail for appointment' 'abc@a.com' 'abc@b.com' 'Hello There' 'How are you doing today (.*)'
	mail 'mail for appointment' 'abc@a.com' 'abc@b.com' 'Hello There' 'How are you doing today (.*)' 50000


Else-If block, needs to be superseded by an If block
	:? {find-expr}
	{
		code
	}
Examples :-
	:? xpath@"ddd"
	{
		exec @print("else-if")
	}


Continue in loop
	continue


Select frame or use a scoped frame block
	frame main|parent|1..N|{some-name}
Examples :-
	frame main
	frame parent
	frame 2
	frame "my-frame"
	frame
	{...
	}


Open Browser
	open {chrome|chrome-dkr|chrome-rec|chrome-hdl|firefox|firefox-dkr|firefox-rec|firefox-hdl|opera|opera-dkr|opera-rec|opera-hdl|ie|edge|safari|appium-android|appium-ios..} {optional session-name}
		*-hdl -- Headless Docker based WebDriver
		*-dkr -- VNC Docker based WebDriver
		*-rec -- VNC Docker based WebDriver With Recording
Examples :-
	open chrome
	open firefox "my-ff-sess"


Save URL passed to window.open
	wopensave {on|off} {num-window-opens|filepath}? {text}? {openPos}?
Examples :-
	wopensave on
	wopensave on 2
	wopensave off '/path/to/file.txt' text
	wopensave off '/path/to/file.txt' text 2
	wopensave off '/path/to/file.txt' text 2 |


Fail test/sub-test
	fail {error string}
Examples :-
	fail "Test failed"
	fail "Sub-Test failed"


Import only subtests/functions from other seleasy scripts
	import {script-path}
Examples :-
	import a/b/c/t1.sel
	import t2.sel


Navigate Back/Previous
	back


Import config properties file
	config {file-path}
Examples :-
	config a/b/c/t1.props
	config t2.props


Add Settings
	setting {width|height|posx|posy|clk_focus} {value} {value}?
Examples :-
	setting width 100
	setting height 100
	setting posx 100
	setting posy 100
	setting clk_focus true


Execute embedded code in java/js/ruby/groovy/python
	<<<(java|js|ruby|groovy|python) a,b,c
	{code}
	>>>
Available variables in context -
	1. @driver - WebDriver instance (Java only)
	2. @window - WebDriver instance (Java only)
	3. @element - Currently selected WebElement instance (Java only)
	4. @sc - Currently selected SearchContext instance (Java only)
	5. @printProvJson - Print Provider data as json (Java only)
	6. @printProv - Print Provider data (Java only)
	7. @print - System.out.println (Java only)
	8. @index - Current provider index under iteration (Java only)
	9. @cntxtParam - Add string variable to current context
	9. @cntxtVar - Add variable to current context
Examples :-
	<<<(java) a,b,c
	System.out.println(a);
	>>>
	<<<(js) a,b,c
	console.log(a);
	>>>
	<<<(groovy) a,b,c
	println a
	>>>
	<<<(ruby) a,b,c
	puts a
	>>>
	<<<(python) a,b,c
	print(a)
	>>>


Value List
	[{primtive-value},...,{primtive-value}]
Examples :-
	['abc', 'sss']
	[123, 234]
	[true, false]


Subtest/Function execution
	@call "name" session-name|@session-id (args)
where
	session-name - the browser session name for which to run this sub test
	session-id - the browser session id prefixed with @ for which to run this sub test
	args - the arguments passed to the subtest, space separated pairs of colon separated name/value
Examples :-
	@call "func1" @1 (arg1:1 arg2:abc)
	{
		select index@$v{arg1} id@"$v{arg2}"
	}




Close window
	close {tab-index|others}?
Examples :-
	close
	close 1
	close others


Select a tab or use a scoped tab block
	tab main|0..N|{some-name}
Examples :-
	tab main
	tab 0
	tab 2
	tab "my-frame"
	tab
	{...
	}


Break from loop
	break


Read text from an element image using OCR (Captcha)
	readimgtext {element-selector} {varname} {optional image-file-path-to-save-screenshot-to}
	ocr {element-selector} {varname} {optional image-file-path-to-save-screenshot-to}
Examples :-
	readimgtext id@'eleid' somevar
	ocr id@'eleid' somevar
	readimgtext id@'eleid' captchatext '/path/to/image/file/file.png'


Js Variable definition
	jsvar {javascript statement returning value}
Examples :-
	jsvar var1 'return "123"'
	jsvar var1 'return $("#elid").val()'


Subtest/Function execution
	@call "name" session-name|@session-id (args)
where
	session-name - the browser session name for which to run this sub test
	session-id - the browser session id prefixed with @ for which to run this sub test
	args - the arguments passed to the subtest, space separated pairs of colon separated name/value
Examples :-
	@call "func1" @1 (arg1:1 arg2:abc)
	{
		select index@$v{arg1} id@"$v{arg2}"
	}




Read text from a QR code or a Bar code
	readqrbar {element-selector} {varname} {optional image-file-path-to-save-screenshot-to}
	qrcode {element-selector} {varname}  {optional image-file-path-to-save-screenshot-to}
	barcode {element-selector} {varname} {optional image-file-path-to-save-screenshot-to}
Examples :-
	readqrbar id@'eleid' somevar
	qrcode id@'eleid' somevar
	barcode id@'eleid' somevar
	readqrbar id@'eleid' captchatext '/path/to/image/file/file.png'


Clear element value
	clear {find-expr}
Examples :-
	clear id@'ele1'


Wait Till Browser/Dowument is ready, called after application level logouts or reloads
	waitready


Single line comment
	//...


Select/Open Window
	window {optional main|0}
Examples :-
	window
	window 0
	window main


Variable definition
	var name @{another-variable-name}|plugin ...|{primitive-value}
Examples :-
	var var0 "Some text"
	var var1 @var0
	var var1 plugin jsonpath $v{myvar} out.x.y.z
	var var1 123455
	var var1 123.455
	var var1 true
	var var1 new java.util.Date()


Wait for element to be visible/invisible
	??(+|-) {find-expr}
	'+' - wait till element is visible
	'-' - wait till element is not visible
Examples :-
	??+ id@'eleid'
	??- id@'eleid'


Mobile Swipe
	swipe {start-x-co-ordinate} {start-y-co-ordinate} {end-x-co-ordinate} {end-y-co-ordinate}
Examples :-
	swipe 123 234 200 300


Upload file
	upload {filepath} {find-expr}
Examples :-
	upload '/path/to/file.txt' id@'ele1'


Hover over an element and click some other element
	hoverclick {find-expr} {find-expr}
Examples :-
	hoverclick id@'hoverele' id@'clickele'


Execute javascript code from file in the browser
	execjsfile {javascript file path}
Examples :-
	execjsfile 'file.js'


Define Global timeout
	timeout {timeout-secs} {if-timeout-secs}} {sleep-granularity-millis}
Examples :-
	timeout 60 1 1000


Multiple Chained Actions
	actions movetoelement|moveto {find-expr} ({click|clickandhold|release|dblclick|doubleclick|contextclick|clickhold|rightclick} {find-expr}?|{keydown|keyup|sendkeys|type {value}}|{movetoelement|moveto {find-expr}}|{draganddrop|dragdrop|draganddrop1|dragdrop1 {find-expr} {find-expr}}|randomize {alphanumeric|alpha|alphanumericlc|alphalc|alphanumericuc|alphauc|numeric|value|range|prefixed|prefixed_|fuzzyn|fuzzya|fuzzyauc|fuzzyalc|fuzzyan|fuzzyanuc|fuzzyanlc} {arg1} {arg2} {arg3}?}|{movebyoffset|moveby {x-offset} {y-offset}}) ... movetoelement|moveto {find-expr} ... ({click|clickan...
Examples :-
	actions movetoelement id@'ele' click moveto id@'ele2' clickandhold moveto id@'ele3' release type '123'
	actions movetoelement id@'ele' sendkeys 'abc'


Draw a circle on a canvas element
	canvas {find-expr}
Examples :-
	canvas xpath@"asds"


Read File line by line
	#readfile {file-path} {binary}?
	{
		code
	}
Examples :-
	#readfile "/path/to/file.txt"
	{
		exec @print(@index)
		exec @print(@line)
		click xpath@'$provider-variable-1'
	}
	#readfile "/path/to/file.bin" "binary"
	{
		exec @print(@index)
		exec @print(@line)
		click xpath@'$provider-variable-1'
	}


Sleep for milliseconds
	sleep {time-in-ms}
Examples :-
	sleep 10000


Mobile Pinch
	pinch ({x-co-ordinate} {y-co-ordinate}|{find-expr})
Examples :-
	pinch 123 234
	pinch id@'ele'


Execute javascript code in the browser
	execjs {javascript statement}
Examples :-
	execjs 'console.log("Hello");'
	execjs '$("#elid").click();'


Take element screenshot
	ele-screenshot {element-selector} {optional image-file-path-to-save-screenshot-to}
Examples :-
	ele-screenshot id@'eleid'
	ele-screenshot id@'eleid' '/path/to/image/file/file.png'




XML Plugin
	xmlread {xml-text}
	xmlwrite {optional-path-to-file} {xml-object-or-map-list-set}
	xmlpath {xml-text} {xpath-string}
Examples :-
	xmlread '<o><a>abc</a><b>1</b></o>'
	xmlwrite '/path/to/file.xml' $xmlObjectVar
	xmlpath '<o><a>abc</a><b>1</b></o>' '/o/a'


Curl Plugin
	curl get|put|post|delete {url}
	{
		[
			header=value
		]
		content-body
	}
Examples :-
	curl get http://abc.com
	curl post http://abc.com
	{
		[
			Content-Type=application/xml
		]
		<abc>abc</abc>
	}


JSON Plugin
	jsonread {json-text}
	jsonwrite {optional-path-to-file} {jackson-annotated-json-object-or-map-list-set}
	jsonpath {json-text} {json-path-string}
Examples :-
	jsonread '{"a": "abc", "b": 1}'
	jsonwrite '/path/to/file.json' $jsonObjectVar
	jsonpath '{"a": "abc", "b": 1}' '$.a'


API Plugin
	api {test-name}@{optional test-case-file-name}
Examples :-
	api api-name
	api api-name@test-case-file-path
```


GATF Config Tool
================

Gatf also provides with a User Interface for managing and executing test cases online with the help of an embedded Http server which provides for an easy to to use configuration tool to control gatf, It provides real time statistics on test runs in a single or multi node load scenario.

<details style="margin-left:20px" open>
<summary>Execution</summary>
<br>

```sh
java -jar gatf-alldep-3.0.3.jar -configtool 9080 0.0.0.0 .
```

```sh
docker run -v /dev/shm:/dev/shm -v /local-folder:/workdir -p 9080:9080 -e TZ=Asia/Kolkata -it sumeetchhetri/gatf-bin:latest
```

```
0.0.0.0 - ip address for embedded http server
9080 - port for embedded http server
. - the current directory where the config file gatf-config.xml and other resource directories and files can be found
```

</details>

Test HTML Generator Plugin
==========
You can also refer the [test-html-generator-plugin](https://github.com/sumeetchhetri/test-html-generator-plugin) home page if you want to generate HTML pages for all you REST endpoints automatically.


GATF Test Generator
==============
The Test Generator plugin is responsible for generating test cases automatically by just looking at either,

  1. All your REST-full service classes annotated with JAX RS annotations (@Path...) or spring based Controller annotations (@RequestMapping..)
  2. All your soap based WSDL locations

<p style="margin-left:20px">The default format of the testcases is XML, but this can be overridden in the plugin configuration to either JSON or CSV. Moreover the generator is also able to generate POSTMAN collections while generating test cases with the help of a simple plugin configuration parameter. It also generates input request objects based on the API request parameters and also provides default object sets with random values.</p>

<details style="margin-left:20px">
<summary>Sample generator config.xml</summary>
<br>

```xml
<configuration>
    <!--The package(s)/classes(s) to be scanned for JAX-RS annotations for generating testcases-->
    <testPaths>
        <testPath>com.sample.services.*</testPath>
    </testPaths>
    <!-- Whether a soap client/http client is used to execute SOAP tests
     the useSoapClient, soapWsdlKeyPairs properties in the configuration are only
     required if we want to generate testcases for SOAP endpoints as well -->
    <useSoapClient>true</useSoapClient>
    <!--The WSDL Key/Location pair, the WSDL location will be looked up to generate the possible soap testcases-->
    <soapWsdlKeyPairs>
        <soapWsdlKeyPair>AuthService,http://localhost:8080/soap/auth?wsdl</soapWsdlKeyPair>
        <soapWsdlKeyPair>UserService,http://localhost:8080/soap/users?wsdl</soapWsdlKeyPair>
    </soapWsdlKeyPairs>
    <!--The REST API service URL suffix-->   
    <urlSuffix>_param=value</urlSuffix>
    <!--The REST API service URL prefix-->
    <urlPrefix>rest</urlPrefix>
    <!--The request data type, when generating request entities-->
    <requestDataType>json</requestDataType>
    <!--The expected response data type-->
    <responseDataType>json</responseDataType>
    <!--The resource path where the testcases and wsdl-locations.csv will be generated-->
    <resourcepath>src/test/resources/generated</resourcepath>
    <!-- Generate postman collections for a given Postman Testcase version-->
    <postmanCollectionVersion>2</postmanCollectionVersion>
    <!-- The generated testcase format, can be either xml,json or csv -->
    <testCaseFormat>xml</testCaseFormat>
    <!--Whether this plugin is enabled-->
    <enabled>true</enabled>
</configuration>
```

</details>

<details style="margin-left:20px">
<summary>Sample maven configuration</summary>
<br>

```xml
<!-- Add jitpack artifact repositories for gatf dependencies -->
<pluginRepositories>
	<pluginRepository>
	    <id>jitpack.io</id>
	    <url>https://jitpack.io</url>
	</pluginRepository>
</pluginRepositories>

<repositories>
	<repository>
	    <id>jitpack.io</id>
	    <url>https://jitpack.io</url>
	</repository>
</repositories>

<!-- Add the generator plugin which will be invoked during the test phase -->
<build>
	<plugins>
		<plugin>
			<groupId>com.github.sumeetchhetri.gatf</groupId>
			<artifactId>gatf-plugin</artifactId>
			<version>1.1.0</version>
			<executions>
				<execution>
					<id>gatf-rest-json-xml</id>
					<configuration>
						<testPaths>
							<!-- The package name where all API's will be found -->
							<testPath>com.pkg.services.*</testPath>
						</testPaths>
						<!-- If there is a URL prefix that gatf should know -->
						<urlPrefix>api/rest</urlPrefix>
						<!-- The type of input request formats (json/xml) -->
						<requestDataType>json</requestDataType>
						<!-- The type of ouput request formats (json/xml) -->
						<responseDataType>json</responseDataType>
						<!-- Are all API endpoints proected -->
						<overrideSecure>true</overrideSecure>
						<!-- Where do we generate the test cases -->
						<resourcepath>${project.reporting.outputDirectory}/generated-rest-json-xml</resourcepath>
						<!-- Do we also generate postman collections for the endpoints -->
						<postmanCollectionVersion>2</postmanCollectionVersion>
						<enabled>true</enabled>
					</configuration>
					<goals>
						<goal>gatf-generator</goal>
					</goals>
				</execution>
			</executions>
		</plugin>
	</plugins>
</body>
```

</details>

<p style="margin-left:20px">The generated test cases are outlined below assuming an imaginary Get user API, every API endpoint class will have a corresponding test case file (either json, xml or csv)</p>

<details style="margin-left:20px">
<summary>Testcase (XML) - Best format for specifying all types of test cases</summary>
<br>

```xml
<TestCase url="jsonapi/users/1" name="GetUser" method="GET" expectedResCode="200" expectedResContentType="application/json" skipTest="false" detailedLog="false" secure="false" soapBase="false">
    <description>Get a user with id = 1</description>
    <headers>
		<entry>
			<string>someheader</string>
			<string>value</string>
		</entry>
		<entry>
			<string>anotherheader</string>
			<string>value</string>
		</entry>
	</headers>
    <expectedNodes>
		<string>id</string>
	</expectedNodes>
</TestCase>
```

</details>


<details style="margin-left:20px">
<summary>Testcase (JSON) - Better than CSV at representing data but difficult to represent complex request/response content</summary>
<br>

```json
 {
    "url": "\/jsonapi\/users/1",
    "name": "GetUser",
    "method": "GET",
    "description": "Get a user with id = 1",
    "headers": {
      "Content-Type": "application\/json"
    },
    "expectedResCode": 200,
    "expectedResContentType": "application\/json",
    "skipTest": false,
    "expectedNodes": [
      "id"
    ]
  }
```

</details>


<details style="margin-left:20px">
<summary>Testcase (CSV) - Simplest data format but cannot be used or complex request/response content</summary>
<br>

```csv
#URL,NAME,HTTPMETHOD,DESCRIPTION,CONTENT,HEADERS,EXQUERYPART,EXPECTEDSTATUSCODE,EXPECTEDCONTTYPE,EXPECTEDCONT,EXPECTEDNODES,SKIPTEST,LOG,SECURE,SOAPBASE,SOAPPARAMVALUES,WSDLKEY,OPERATIONNM
jsonapi/users/1,GetUser,GET,Get a user with id = 1,,someheader:val|anotherheader:val,,200,application/json,,id,false,false,true
```

</details>

<details style="margin-left:20px;margin-top:20px;" open>
<summary>Execution</summary>
<br>

```sh
java -jar gatf-alldep-3.0.3.jar -generator /workdir/path/to/gatf/config.xml
```

```sh
docker run -v /dev/shm:/dev/shm -v /local-folder:/workdir -e TZ=Asia/Kolkata -it sumeetchhetri/gatf-bin:latest -generator /workdir/path/to/gatf/config.xml
```

</details>

<br/>

Limitations
-----
- The generator assumes that the entities in the application are JACKSON/JAXB compliant (having proper jackson/jaxb annotations), hence it is tightly coupled with these libraries for generating request content


License
------
Apache License Version 2.0


