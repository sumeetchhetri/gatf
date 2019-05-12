Generic Automated Test Framework (GATF)
=========

**GATF** is a automated test generator and acceptance testing framework. It provides multiple components namely the **Test Generator**, the **Test Executor** , the **Config Tool** and the **Selenium Executor**

GATF Acceptance Test executor is data-type agnostic, which means that your testcases can be in any formats like XML, JSON or plain old CSV. Whereas the Test generator generates only XML files (so that even complex data structures can be suuported within your testcases) 

> The primary goal of GATF is Automation with Simplicity.

Important features
==========
1. Comparative Env Testing
2. Concurrent User Testing
3. Performance Testing
4. Load Testing (API and UI both)
5. UI Testing
6. Reporting Engine
7. Remote Agent for Distributed Load Testing
8. Workflow Testing
9. Data Source Integrations
	- SQL
	- MongoDB
	- Files (XML/JSON/CSV/XLS/XLSX/ODT)

Test HTML Generator Plugin
==========
You can also refer the [test-html-generator-plugin][7] home page if you want to generate HTML pages for all you REST endpoints automatically.


GATF Test Generator
==============
The Test Generator plugin is responsible for generating test cases automatically by just looking at either,

1. All your REST-full service classes annotated with JAX RS annotations (@Path...) or spring based Controller annotations (@RequestMapping..)
2. All your soap based WSDL locations

The default format of the testcases is XML, but this can be overridden in the plugin configuration to either JSON or CSV. Moreover the generator is also able to generate POSTMAN collections while generating test cases with the help of a simple plugin configuration parameter. It also generates input request objects based on the API request parameters and also provides default object sets with random values.

Maven Configuration
--------------
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
			<version>2.0.0</version>
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

The generated test cases are outlined below assuming an imaginary Get user API, every API endpoint class will have a corresponding test case file (either json, xml or csv)

Testcase (XML)
--------------

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
Best format for specifying all types of test cases

Testcase (JSON)
--------------

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
Better than CSV at represnting data but difficult to represent complex request/response content 

Testcase (CSV)
--------------

```csv
#URL,NAME,HTTPMETHOD,DESCRIPTION,CONTENT,HEADERS,EXQUERYPART,EXPECTEDSTATUSCODE,EXPECTEDCONTTYPE,EXPECTEDCONT,EXPECTEDNODES,SKIPTEST,LOG,SECURE,SOAPBASE,SOAPPARAMVALUES,WSDLKEY,OPERATIONNM
jsonapi/users/1,GetUser,GET,Get a user with id = 1,,someheader:val|anotherheader:val,,200,application/json,,id,false,false,true
```
Simplest data format but cannot be used or complex request/response content


Direct Execution
--------------
For direct execution, we just need to specify a simple config.xml(any name .xml) file with the contents as follows,on the command line,
```sh
user@local> java -jar gatf-alldep-jar-2.0.0.jar -generator config.xml
```

Sample config.xml
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

Lets see what the GATF Test Executor is,

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


GATF Test Data Providers
---------------------------
The GATF framework provides the option to integrate to multiple data sources for fetching test case data, which include the following,

1. Any SQL compliant database
2. MongoDB
3. Files - CSV/JSON/XML
4. Inline/Value based
5. Custom Provider
6. Random Value Provider

The framework provides automatic built-in providers for easy integration to the above mentioned data sources. But we can also define custom providers if required.

GATF Pre/Post Test Case Execution Hooks
-------------------------
The framework also provides the facility to plug in pre/post test case execution logic in order to control the test case execution flow, it provides 2 simple annotations,

1. @PreTestCaseExecutionHook - Marks a method as a pre-test-case execution hook
2. @PostTestCaseExecutionHook - Marks a method as a post-test-case execution hook


GATF Executor Configuration File
--------------
The complete configuration for the GATF executor framework is listed below, We just need to define a file with the contents below and configure it in maven or provide the path to the file to the executor executable to execute your test cases.

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
Assuming the above configuration is present in a file named gatf-config.xml, the maven configuration required to execute the API testcases would be, 

Maven Configuration
--------------
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
			<version>2.0.0</version>
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

GATF Selenium Executor
================

The GATF selenium executor provides an easy to use language to write selenium tests called **seleasy**, using seleasy it becomes super easy to write any simple or complex test cases also utilizing the full capabilities of the workflow and provider logic described in the configuration above, moreover load and dsitributed load testing can now be very easily performed with gatf.

Just to give a glimpse of how easy it is to write selenium test cases with seleasy(gatf) assume we have a login page hosted at http://example.com and after logging in we need to see the Name of the user in the Profile section under the xpath */html/body/div[4]/div[1]/div/div[2]/div[1]/div/p[1]*, an example gatf script to execute the test in 4 different browsers would look like,

```
open chrome
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

GATF Executor Configuration File
--------------
```xml
<gatf-execute-config>
	<authEnabled>true</authEnabled>
	<baseUrl>http://localhost:8080/example</baseUrl>
	<enabled>true</enabled>
	<outFilesDir>out</outFilesDir>
	<testCaseDir>data</testCaseDir>
	<httpRequestTimeout>100000</httpRequestTimeout>
	<authDataProvider>file-auth-provider</authDataProvider>
	<gatfTestDataConfig>
		<providerTestDataList>
			<gatf-testdata-provider>
				<providerName>file-auth-provider</providerName>
				<providerClass>com.gatf.executor.dataprovider.FileTestDataProvider</providerClass>
				<providerProperties>username,password</providerProperties>
				<enabled>true</enabled>
				<args>
					<seleniumScript>users.csv</seleniumScript>
					<seleniumScript>csv</seleniumScript>
				</args>
			</gatf-testdata-provider>
		</providerTestDataList>
	</gatfTestDataConfig>
	<isSeleniumExecutor>true</isSeleniumExecutor>
	<javaHome>C:\\Path-to-java\\java-openjdk-1.8.0.191</javaHome>
	<gatfJarPath>C:\\Path-to-maven-repo\\.m2\\repository\\com\\github\\sumeetchhetri\\gatf\\gatf-alldep-jar\\2.0.0\\gatf-alldep-jar-2.0.0.jar</gatfJarPath>
	<seleniumDriverConfigs>
		<seleniumDriverConfig>
			<name>chrome</name>
			<driverName>webdriver.chrome.driver</driverName>
			<path>C:\\Path-to-selenium-drivers\\chromedriver.exe</path>
		</seleniumDriverConfig>
	</seleniumDriverConfigs>
	<seleniumScripts>
		<seleniumScript>path-relative-to-testCaseDir-above\\test.sel</seleniumScript>
	</seleniumScripts>
	<seleniumLoggerPreferences>browser(OFF),client(OFF),driver(OFF),performance(OFF),profiler(OFF),server(OFF)</seleniumLoggerPreferences>
</gatf-execute-config>
```

Maven Configuration
--------------
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

<!-- Add the executor plugin which will be invoked during the test phase -->
<build>
	<plugins>
		<plugin>
			<groupId>com.github.sumeetchhetri.gatf</groupId>
			<artifactId>gatf-plugin</artifactId>
			<version>2.0.0</version>
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

GATF Config Tool
================

Gatf also provides with a User Interface for managing and executing test cases online with the help of an embedded Http server which provides for an easy to to use configuration tool to control gatf, It provides real time statistics on test runs in a single or multi node load scenario.

Maven Configuration
--------------
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

<!-- Add the config plugin which will be invoked during the test phase -->
<build>
	<plugins>
		<plugin>
			<groupId>com.github.sumeetchhetri.gatf</groupId>
			<artifactId>gatf-plugin</artifactId>
			<version>2.0.0</version>
			<executions>
				<execution>
					<id>gatf-config</id>
					<phase>test</phase>
					<goals>
						<goal>gatf-config</goal>
					</goals>
				</execution>
			</executions>
		</plugin>
	</plugins>
</body>
```

Direct Execution
--------------
For direct execution,
```sh
user@local> java -jar gatf-alldep-jar-2.0.0.jar -configtool 9080 localhost .
```
**localhost:9080** - ip/port for embedded http server<br>
**.** - the current directory where the config file gatf-config.xml and other resource directories and files can be found

Limitations
-----
- The generator assumes that the entities in the application are JACKSON/JAXB compliant (having proper jackson/jaxb annotations), hence it is tightly coupled with these libraries for generating request content


License
------
Apache License Version 2.0

**Free Software, Hell Yeah!**
<br/>
**Happy Gatfying**

[1]:#soap-based-acceptance-testing
[2]:http://lit-savannah-1186.herokuapp.com/api-source/
[3]:http://lit-savannah-1186.herokuapp.com/gatf-artifacts/
[4]:http://lit-savannah-1186.herokuapp.com/gatf-artifacts/workflow-example/
[5]:#workflow
[6]:http://lit-savannah-1186.herokuapp.com/gatf-artifacts/workflow-example/data/sample-tests.xml
[7]:https://github.com/sumeetchhetri/test-html-generator-plugin
