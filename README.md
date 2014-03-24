Generic Automated Test Framework (GATF)
=========

**GATF** is a automated test generator and acceptance testing framework(laid on top of asyn-http-client and Soap-ws). Provides 2 components namely the **Test Generator** the **Test Executor**

GATF Acceptance Test executor is data-type agnostic, which means that your testcases can be in any formats like XML, JSON or plain old CSV. Whereas the Test generator generates only XML files (so that even complex data structures can be suuported within your testcases) 

> The primary goal of GATF is Automation with Simplicity.


GATF Test Generator
==============
The Test Generator is responsible for generating test cases automatically by just looking at either,

1. All your REST-full service classes annotated with @Path annotation
2. All your soap based WSDL locations

The default format of the testcases is XML, but this can be overridden in the plugin configuration to either JSON or CSV. Moreover the generator is also able to generate POSTMAN collections while generating test cases with the help of a simple plugin configuration parameter


Lets go over the primary entity within GATF, Testcase  

Testcase
-----------

A testcase is nothing but an entity that defines any method call over HTTP, to simplify things, its just a plain object that holds information about your API call with properties like the HTTP Method, the URI, the parameters, the content, the Headers passed, the expected output and son on. Lets walk over the properties of a Testcase in details below,
repe
* Url - the Service URL, mandatory (ex, /api/users)
* Name - the test case name, mandatory (ex, GetUsers)
* Description - the test case description, optional (ex, Get all the users)
* Method - the HTTP method, mandatory (ex, GET)
* Sequence - the work-flow sequence parameter, is an integer parameter execution in the work-flow chain starts from low-high sequence value
* WorkflowContextParameterMap - the workflow context global variable map, define key/value pairs of variable name and expected response node heirarchy(xpath values for xml, json path for json), Refer to the [Workflow][5] section below for more details
* ExQueryPart - extra query parameters, optional (ex, limit=10, this will be appended to the Url, so the Url now becomes /api/users?limit=10)
* Content - the body/content to be passed in the request, optional
* Headers - the HTTP headers to be passed in the request, optional (ex, Content-Type: application/json)
* Secure - whether this request requires a prior authentication API call, optional (ex, true/false)
* SoapBase - whether this request is a SOAP method call with runtime request generation+transformation, optional (ex, true/false)
* WsdlKey - the wsdl key name sepcified while passing the wsdl value in the wsdl-location.csv file or the pom.xml configuration for the maven plugin, mandatory when SoapBase=true (ex, UserSoapService), Refer to the [SOAP 1] section below
* OperationName - the soap method operationName as specified in the wsdl file for that particular service, mandatory when SoapBase=true (ex, getUsers), Refer to the [SOAP][1] section below
* SoapParameterValues - the transformation key/value pairs used while tranforming the runtime generated soap message, Refer to the [SOAP] [1] section below
* ExpectedResCode - the expected HTTP status code, mandatory (ex, 200)
* ExpectedResContentType - the expected content type, optional (ex, application/json)
* ExpectedNodes - the expected nodes in the response body in dotted notation, optional (ex, User.id)
* ExpectedResContent - the exact expected response content to be matched with the actual result, optional
* SkipTest - whether to skip this test, mandatory (ex, true/false)
* DetailedLog - whether or not to log the detailed execution logs, mandatory (ex, true/false)
* FilesToUpload - the file content or multipart data to upload, optional (ex, file:file:wsdl-locations.csv:), specifees a colon separated list of file details to be uploaded, the list defines controlname:type(file/text):fileOrText(filename/text content):contentType(only for text content)
* OutFileName - The name of the output file where the reponse of the API test will be stored
* RepeatScenarios - The list of variables (or scenarios) for which this test case will be repeated
* RepeatScenarioProviderName - The repeat scenario provider name, Refer [Provider][6]
* RepeatScenariosConcurrentExecution - Do we want to run the repeat sceanrios concurrently?
* NumberOfExecutions - Number of times to repeat execution of this test case
* StopOnFirstFailureForPerfTest - In case a test case is executed multiple times (due to NumberOfExecutions), do we stop on the first failure?
* PreWaitMs - Time to wait before execution of test case
* PostWaitMs - Time to wait after execution of test case
* ReportResponseContent - Do we want to log the test execution response data to the final test report?

Sample Application
=======
Have a look at the sample API application definition, test pages and the generated testcases [here][2] and [here][3]


REST-ful Acceptance Testing
----------

Assumptions - I
-----------
Lets provide a sample application scneario so that we can follow through the examples below, the sample application is laid out in the following manner.

1. The application provides 3 security API's for authentication
   - Login Using user/password headers = POST /api/loginbyHeaders
     <br/>*Headers*<br/>
```
username: user
password: passw
```
   - Login Using Form Parameters = POST /api/loginbyformparams
     <br/>*Headers*<br/>
```
Content-Type: application/x-www-form-urlencoded
```
     *Content*<br/>
```
username=admin&password=invalid
```
   - Login Using Basic authentication = POST /api/loginBasicAuth
     <br/>*Headers*<br/>
```
Authorization: Basic YWRtaW46YWRtaW4Abc=
```
2. User Service provides 3 API's, supporting JSON io like,
   - Get User = GET /jsonapi/users/{id}
     <br/>Response -
```
        {"id": 1, "name": "Test", "age": 27}
```
   - Get Users = GET /jsonapi/users
     <br/>Response -
```
[
    {"id": 1, "name": "Test", "age": 27}, 
    {"id": 2, "name": "Test", "age": 27}
]
``` 
   - Create User = POST /jsonapi/users
     <br/>Request -
```
{"id": 3, "name": "Test", "age": 27}
```
2. User Service also provides 3 API's, supporting XML io like,
   - Get User = GET /xmlapi/users/{id}
     <br/>Response -
```
<User>
    <id>1</id>
    <name>Test</name>
    <age>27</age>
</User>
```
   - Get Users = GET /xmlapi/users
     <br/>Response -
```
<Users>
    <User>
        <id>1</id>
        <name>Test</name>
        <age>27</age>
    </User>
    <User>
        <id>2</id>
        <name>Test</name>
        <age>27</age>
    </User>
</Users>
```
   - Create User = POST /xmlapi/users
     <br/>Request -
```
<User>
    <id>3</id>
    <name>Test</name>
    <age>27</age>
</User>
```    


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
<br/>


Usage - GATF Test Generator
======
The GATF utility/plugin can be either used as a maven plugin or directly as an executable jar file

Plugin Maven Configuration
---------
With the configuration below you can just execute the maven generate-test-resources goal in order to generate all your test cases and then on the command line, any other goal after the generate-test-resources goal can also be used like package or test.

**mvn generate-test-resources**
Sample pom.xml
```xml
<project ...>
    <!-- In case the maven gatf generator needs to be used to generate testcases -->
    <pluginRepositories>
            <pluginRepository>
                <id>gatf-repository</id>
                <name>Gatf Repository</name>
                <url>https://raw2.github.com/sumeetchhetri/gatf/master/maven/</url>
            </pluginRepository>
    </pluginRepositories>
 
    <!-- In case the maven gatf test class needs to be used to execute testcases -->
    <repositories>
        <repository>
            <id>gatf 1.1</id>
            <url>https://raw2.github.com/sumeetchhetri/gatf/master/maven/</url>
        </repository>
    </repositories>
 
    <plugins>
        <plugin>
                <groupId>com.test</groupId>
                <artifactId>gatf-plugin</artifactId>
                <version>1.1</version>
                <configuration>
                    <!--The comma separated package(s)/classes(s) to be scanned for JAX-RS annotations for generating testcases-->
                    <testPaths>
                        <testPath>com.sample.services.*</testPath>
                    </testPaths>
                    <!-- Whether a soap client/http client is used to execute SOAP tests
                         the useSoapClient, soapWsdlKeyPairs properties in the configuration are only
                         required if we want to generate testcases for SOAP endpoints as well-->
                    <useSoapClient>true</useSoapClient>
                    <!--The WSDL Key/Location pair, the WSDL location will be looked up to generate the possible soap testcases-->
                    <soapWsdlKeyPairs>
                        <soapWsdlKeyPair>AuthService,http://localhost:8080/soap/auth?wsdl</soapWsdlKeyPair>
                        <soapWsdlKeyPair>ExampleService,http://localhost:8080/soap/example?wsdl</soapWsdlKeyPair>
                        <soapWsdlKeyPair>MessageService,http://localhost:8080/soap/messages?wsdl</soapWsdlKeyPair>
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
                <executions>
                    <execution>
                        <goals>
                            <goal>gatf</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
            .
            .
            .
 
    </plugins>
    .
    .
    .
    .
</project>
```

Direct Execution
--------------
For direct execution, we just need to specify a simple config.xml(any name .xml) file with the contents as follows,on the command line,
user@local> **java -jar gatf-plugin-1.0.jar config.xml**

Sample config.xml
```xml
<configuration>
    <!--The comma separated package(s)/classes(s) to be scanned for JAX-RS annotations for generating testcases-->
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
        <soapWsdlKeyPair>ExampleService,http://localhost:8080/soap/example?wsdl</soapWsdlKeyPair>
        <soapWsdlKeyPair>MessageService,http://localhost:8080/soap/messages?wsdl</soapWsdlKeyPair>
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
6. Load Testing

It also provides the following,

1. Pie charts for overall test status (Success/Failure reports)
2. Line/Bar charts for overall performance results
3. Detailed test case reports with comprehensive information about a test execution request/response
4. Maven/Executable-Jar test case execution options

It uses a highly performant asynchronous http client library - async-http-client and hence achieves very good execution times.
The framework does not use the rest-assured/TestNG libraries any more starting from version 1.1 for performance reasons.


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
The framework also provides the facility to plugin pre/post test case execution logic in order to control the test case execution flow, provides 2 simple annotations,

1. @PreTestCaseExecutionHook - Marks a method as a pre-test-case execution hook
2. @PostTestCaseExecutionHook - Marks a method as a post-test-case execution hook


GATF Executor Configuration
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
  
    <!-- The provider name that will be used to fetch user/password details for user simulation -->
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






Workflow
======
The main focus of GATF is automation, and to acheive this goal is not simple and cannot be easily done just by generating testcases and having a test execution phase. What we actually need is a work-flow or orchestration logic to control the flow of API invocations in an orderly fashion receiving values from the response and using these values in further API requests. GATF provides this orchestration with the help of the Sequence/WorkflowContextParameterMap parameters in your GATF testcase file.

Example
-----
Lets take an example from the sample application present [here][4],

```xml
<TestCase url="api/rest/example?token={token}&amp;" name="com.sample.services.ExampleBeanServiceImpl.addBean" method="POST" expectedResCode="200" expectedResContentType="application/json" skipTest="false" detailedLog="false" secure="true" soapBase="false">
    <description>com.sample.services.ExampleBeanServiceImpl.addBean</description>
    <content><![CDATA[{"id":"DfCobuXzhe","name":"TDymsEDzCE","order":53,"valid":false,"bean":{"prop1":"WmGSHlwUYz","prop2":69,"prop3":40,"prop4":1391159300719,"prop5":true,"prop6":["tPrZqJQxml"],"prop7":{"DXITdnJkDz":"QqKLlRNSQd"}},"beans":[{"prop1":"TiUfFBzpec","prop2":30,"prop3":57,"prop4":1391159300719,"prop5":true,"prop6":["VRvfIDvqLb"],"prop7":{"WlfJTXeHPf":"gKxjxsLPke"}}],"mapofBeans":{"tygvEkxPJJ":{"prop1":"TziuNauqep","prop2":30,"prop3":39,"prop4":1391159300719,"prop5":true,"prop6":["SMWsDefXEv"],"prop7":{"xNUmOnqQig":"MmtnagDQhJ"}}}}]]></content>
    <headers>
      <entry>
        <string>Content-Type</string>
        <string>application/json</string>
      </entry>
    </headers>
    <workflowContextParameterMap>
    	<entry>
	        <string>beanId</string>
	        <string>id</string>
      </entry>
    </workflowContextParameterMap>
    <expectedNodes/>
    <soapParameterValues/>
    <filesToUpload/>
</TestCase>
<TestCase url="api/rest/example/$beanId?token={token}&amp;" name="com.sample.services.ExampleBeanServiceImpl.getBean" method="GET" expectedResCode="200" expectedResContentType="application/json" skipTest="false" detailedLog="false" secure="true" soapBase="false">
    <description>com.sample.services.ExampleBeanServiceImpl.getBean</description>
    <content><![CDATA[]]></content>
    <headers/>
    <expectedNodes>
    	<string>id</string>
    	<string>bean.prop1</string>
    </expectedNodes>
    <soapParameterValues/>
    <filesToUpload/>
</TestCase>
```

The example testcase above does not define a sequence value, if a work-flow sequence value is not defined for a testcase then the testcase workflow execution assumes the testcase definition order in a given testcase file as the workflow sequence. In this example [testcase-file][6] the sequence is automatically assumed to be the order of the testcase definitions. 

Talking about the workflowContextParameterMap, this is a key/value pair that defines parameter names and expected response nodes(jsonpath/json, xpath/xml) for a given API invocation. After the given API execution completes the GATF engine looks for the nodes defined in the workflowContextParameterMap and assigns the values found for these nodes as variable name definitions in a global map which can be used for the next testcase executions. In the example above after the API execution for api/rest/example?token={token}&amp; completes the engine reads the response node(json path) id from the response and assigns the value thus obtained to the beanId global variable. You may have already defined this variable in your testcase definitions coming later as $beanId(GATF uses Velocity templates). So after this API call completes the variable **$beanId** will have the value received from the API call. The second testcase gives an exmaple of the usage of variable names in GATF. GATF looks for the URL, content body and the query parameters(namely the ExQueryPart parameter) and replaces all computed variables at that instant before the execution of the next testcase, it actually transforms your testcase depending on all your variable definitions. All variable definitions are assumed to be string values.

This helps you stage your test execution steps in an orderly fashion or orchestrate the flow of tests getting executed. This is a very important feature present in GATF which helps it acheive its goal 'AUTOMATION'


SOAP based Acceptance Testing
----------

Assumptions - II
--------
Continuing from the Assumption section - I, Let us now provide a sample application scneario where the same User Service above is implemented as a SOAP service, the WSDL file is laid out as an example.
```xml
<?xml version="1.0"?>
<wsdl:definitions xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/" xmlns:tns="http://services.sample.com/" xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/" xmlns:ns2="http://schemas.xmlsoap.org/soap/http" xmlns:ns1="http://www.example.com" name="UserServiceServiceImplService" targetNamespace="http://services.sample.com/">
<wsdl:types>
    <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:tns="http://services.sample.com/" elementFormDefault="unqualified" targetNamespace="http://services.sample.com/" version="1.0">
    <xs:element name="UserService" type="tns:UserService"/>
    <xs:element name="addUser" type="tns:addUser"/>
    <xs:element name="addUserResponse" type="tns:addUserResponse"/>
    <xs:element name="deleteUser" type="tns:deleteUser"/>
    <xs:element name="deleteUserResponse" type="tns:deleteUserResponse"/>
    <xs:element name="getUser" type="tns:getUser"/>
    <xs:element name="getUserResponse" type="tns:getUserResponse"/>
    <xs:element name="getUsers" type="tns:getUsers"/>
    <xs:element name="getUsersResponse" type="tns:getUsersResponse"/>
    <xs:element name="updateUser" type="tns:updateUser"/>
    <xs:element name="updateUserResponse" type="tns:updateUserResponse"/>
    <xs:complexType name="getUser">
        <xs:sequence>
            <xs:element minOccurs="0" name="arg0" type="xs:string"/>
        </xs:sequence>
    </xs:complexType>
    <xs:complexType name="getUserResponse">
        <xs:sequence>
            <xs:element minOccurs="0" name="return" type="tns:user"/>
        </xs:sequence>
    </xs:complexType>
    <xs:complexType name="user">
        <xs:sequence>
            <xs:element minOccurs="0" name="id" type="xs:string"/>
            <xs:element minOccurs="0" name="name" type="xs:string"/>
            <xs:element minOccurs="0" name="age" type="xs:int"/>
        </xs:sequence>
    </xs:complexType>
    <xs:complexType name="errorDetail">
        <xs:sequence>
            <xs:element minOccurs="0" name="status" type="xs:int"/>
            <xs:element minOccurs="0" name="errorCode" type="xs:string"/>
            <xs:element minOccurs="0" name="message" type="xs:string"/>
        </xs:sequence>
    </xs:complexType>
    <xs:complexType name="addUser">
        <xs:sequence>
            <xs:element minOccurs="0" name="arg0" type="tns:user"/>
        </xs:sequence>
    </xs:complexType>
    <xs:complexType name="addUserResponse">
        <xs:sequence/>
    </xs:complexType>
    <xs:complexType name="getUsers">
        <xs:sequence/>
    </xs:complexType>
    <xs:complexType name="getUsersResponse">
        <xs:sequence>
            <xs:element maxOccurs="unbounded" minOccurs="0" name="return" type="tns:user"/>
        </xs:sequence>
    </xs:complexType>
    <xs:complexType name="deleteUser">
        <xs:sequence>
            <xs:element minOccurs="0" name="arg0" type="xs:string"/>
        </xs:sequence>
    </xs:complexType>
    <xs:complexType name="deleteUserResponse">
        <xs:sequence/>
    </xs:complexType>
    <xs:complexType name="updateUser">
        <xs:sequence>
            <xs:element minOccurs="0" name="arg0" type="xs:string"/>
            <xs:element minOccurs="0" name="arg1" type="tns:user"/>
        </xs:sequence>
    </xs:complexType>
    <xs:complexType name="updateUserResponse">
        <xs:sequence/>
    </xs:complexType>
</xs:schema>
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:ns1="http://services.sample.com/" targetNamespace="http://mtel.proconconsole.com" version="1.0">
<xs:import namespace="http://services.sample.com/"/>
<xs:element name="errorDetail" type="ns1:errorDetail"/>
</xs:schema>
<xsd:schema xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:tns="http://www.example.com" xmlns:ns0="http://services.sample.com/" attributeFormDefault="unqualified" elementFormDefault="unqualified" targetNamespace="http://www.example.com">
<xsd:element name="UserServiceException" nillable="true" type="ns0:errorDetail"/>
</xsd:schema>
</wsdl:types>
<wsdl:message name="deleteUserResponse">
    <wsdl:part element="tns:deleteUserResponse" name="parameters"/>
</wsdl:message>
<wsdl:message name="getUsers">
    <wsdl:part element="tns:getUsers" name="parameters"/>
</wsdl:message>
<wsdl:message name="getUserResponse">
    <wsdl:part element="tns:getUserResponse" name="parameters"/>
</wsdl:message>
<wsdl:message name="addUserResponse">
    <wsdl:part element="tns:addUserResponse" name="parameters"/>
</wsdl:message>
<wsdl:message name="getUsersResponse">
    <wsdl:part element="tns:getUsersResponse" name="parameters"/>
</wsdl:message>
<wsdl:message name="deleteUser">
    <wsdl:part element="tns:deleteUser" name="parameters"/>
</wsdl:message>
<wsdl:message name="UserServiceException">
    <wsdl:part element="ns1:UserServiceException" name="UserServiceException"/>
</wsdl:message>
<wsdl:message name="getUser">
    <wsdl:part element="tns:getUser" name="parameters"/>
</wsdl:message>
<wsdl:message name="updateUserResponse">
    <wsdl:part element="tns:updateUserResponse" name="parameters"/>
</wsdl:message>
<wsdl:message name="updateUser">
    <wsdl:part element="tns:updateUser" name="parameters"/>
</wsdl:message>
<wsdl:message name="addUser">
    <wsdl:part element="tns:addUser" name="parameters"/>
</wsdl:message>
<wsdl:portType name="UserServiceServiceImpl">
    <wsdl:operation name="getUser">
        <wsdl:input message="tns:getUser" name="getUser"/>
        <wsdl:output message="tns:getUserResponse" name="getUserResponse"/>
        <wsdl:fault message="tns:UserServiceException" name="UserServiceException"/>
    </wsdl:operation>
    <wsdl:operation name="addUser">
        <wsdl:input message="tns:addUser" name="addUser"/>
        <wsdl:output message="tns:addUserResponse" name="addUserResponse"/>
        <wsdl:fault message="tns:UserServiceException" name="UserServiceException"/>
    </wsdl:operation>
    <wsdl:operation name="getUsers">
        <wsdl:input message="tns:getUsers" name="getUsers"/>
        <wsdl:output message="tns:getUsersResponse" name="getUsersResponse"/>
        <wsdl:fault message="tns:UserServiceException" name="UserServiceException"/>
    </wsdl:operation>
    <wsdl:operation name="deleteUser">
        <wsdl:input message="tns:deleteUser" name="deleteUser"/>
        <wsdl:output message="tns:deleteUserResponse" name="deleteUserResponse"/>
        <wsdl:fault message="tns:UserServiceException" name="UserServiceException"/>
    </wsdl:operation>
    <wsdl:operation name="updateUser">
        <wsdl:input message="tns:updateUser" name="updateUser"/>
        <wsdl:output message="tns:updateUserResponse" name="updateUserResponse"/>
        <wsdl:fault message="tns:UserServiceException" name="UserServiceException"/>
    </wsdl:operation>
</wsdl:portType>
<wsdl:binding name="UserServiceServiceImplServiceSoapBinding" type="tns:UserServiceServiceImpl">
    <soap:binding style="document" transport="http://schemas.xmlsoap.org/soap/http"/>
    <wsdl:operation name="getUser">
        <soap:operation soapAction="" style="document"/>
        <wsdl:input name="getUser">
            <soap:body use="literal"/>
        </wsdl:input>
        <wsdl:output name="getUserResponse">
            <soap:body use="literal"/>
        </wsdl:output>
        <wsdl:fault name="UserServiceException">
            <soap:fault name="UserServiceException" use="literal"/>
        </wsdl:fault>
    </wsdl:operation>
    <wsdl:operation name="addUser">
        <soap:operation soapAction="" style="document"/>
        <wsdl:input name="addUser">
            <soap:body use="literal"/>
        </wsdl:input>
        <wsdl:output name="addUserResponse">
            <soap:body use="literal"/>
        </wsdl:output>
        <wsdl:fault name="UserServiceException">
            <soap:fault name="UserServiceException" use="literal"/>
        </wsdl:fault>
    </wsdl:operation>
    <wsdl:operation name="getUsers">
        <soap:operation soapAction="" style="document"/>
        <wsdl:input name="getUsers">
            <soap:body use="literal"/>
        </wsdl:input>
        <wsdl:output name="getUsersResponse">
            <soap:body use="literal"/>
        </wsdl:output>
        <wsdl:fault name="UserServiceException">
            <soap:fault name="UserServiceException" use="literal"/>
        </wsdl:fault>
    </wsdl:operation>
    <wsdl:operation name="deleteUser">
        <soap:operation soapAction="" style="document"/>
        <wsdl:input name="deleteUser">
            <soap:body use="literal"/>
        </wsdl:input>
        <wsdl:output name="deleteUserResponse">
            <soap:body use="literal"/>
        </wsdl:output>
        <wsdl:fault name="UserServiceException">
            <soap:fault name="UserServiceException" use="literal"/>
        </wsdl:fault>
    </wsdl:operation>
    <wsdl:operation name="updateUser">
        <soap:operation soapAction="" style="document"/>
        <wsdl:input name="updateUser">
            <soap:body use="literal"/>
        </wsdl:input>
        <wsdl:output name="updateUserResponse">
            <soap:body use="literal"/>
        </wsdl:output>
        <wsdl:fault name="UserServiceException">
            <soap:fault name="UserServiceException" use="literal"/>
        </wsdl:fault>
    </wsdl:operation>
</wsdl:binding>
<wsdl:service name="UserServiceServiceImplService">
    <wsdl:port binding="tns:UserServiceServiceImplServiceSoapBinding" name="UserServiceServiceImplPort">
        <soap:address location="http://localhost:8080/soap/users"/>
    </wsdl:port>
</wsdl:service>
</wsdl:definitions>

```

Testcase (SOAP - Auto)
-----
This is an auto-generated testcase example generated by the GATF Test Generator, this test case will be executed a normal HTTP client call and not a SOAP client
```xml
<TestCase url="http://localhost:8080/soap/users" name="CreateUser" method="POST" expectedResCode="200" expectedResContentType="application/xml" skipTest="false" detailedLog="false" secure="false" soapBase="false">    
    <description>Create a new User</description>    
	<content><![CDATA[<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:ser="http://services.sample.com/">
		   <soapenv:Header/>
		   <soapenv:Body>
			  <ser:createUser>
				 <arg0>
					<id>gero et</id>
					<name>sonoras imperio</name>
					<age>3</age>
				 </arg0>
			  </ser:createUser>
		   </soapenv:Body>
		</soapenv:Envelope>]]>
	</content>
	<headers>      
		<entry>        
			<string>SOAPAction</string>
			<string></string>
		</entry>    
	</headers>    
	<expectedNodes/>    
	<soapParameterValues/>  
</TestCase>
```

wsdl-locations.csv
----
```csv
LoginService,http://localhost:8080/soap/login?wsdl
UserService,http://localhost:8080/soap/users?wsdl
```

Testcase (SOAP - Manual)
-----
This is a manually sepcified test case example here, soapbased is set to true, this call will be invoked using a SOAP client derived from the WSDL file specified in the wsdl-locations.csv, we don't need to sepcify the URL in this case as it is automatically derived from the WSDL file by the Acceptance Testing framework
```xml
<TestCase name="CreateUser" method="POST" expectedResCode="200" expectedResContentType="application/xml" skipTest="false" detailedLog="false" secure="false" soapBase="true">    
    <description>Create a new User</description>
	<headers>      
		<entry>        
			<string>SOAPAction</string>
			<string></string>
		</entry>    
	</headers>    
	<expectedNodes>  
    	<string>id</string>
    </expectedNodes>
	<soapParameterValues>
        <entry>        
        	<string>arg0.id</string>
			<string>3</string>
		</entry>
        <entry>        
        	<string>arg0.name</string>
			<string>Test</string>
		</entry>
        <entry>        
        	<string>arg0.age</string>
			<string>30</string>
		</entry>
    </soap>
    <wsdlKey>UserService</wsdlKey>
    <operationName>addUser</operationName>
</TestCase>
```
Observe above that we did not specify the URL and the soap message content, we only mentioned that soapBased=true and the wsdlKey value mentioned in the wsdl-locations.csv and the operationName for the add user service from the WSDL above.
Also remember that in this case the soapParameterValues specified in the testcase above will be used to transform the soap request generated automatically during execution of the testcase.



Finally - Show me Authenticated Tests Example
-----
```xml
<TestCases>
    <TestCase name="Login" method="POST" expectedResCode="200" expectedResContentType="application/xml" skipTest="false" detailedLog="false" secure="false" soapBase="true">    
        <description>Login</description>
        <headers>      
        	<entry>        
    			<string>SOAPAction</string>
    			<string></string>
    		</entry>    
    	</headers>    
    	<expectedNodes>  
        	<string>token</string>
        </expectedNodes>
    	<soapParameterValues>
            <entry>        
            	<string>arg0.username</string>
    			<string>user</string>
    		</entry>
            <entry>        
            	<string>arg0.password</string>
    			<string>pass</string>
    		</entry>
        </soap>
        <wsdlKey>AuthService</wsdlKey>
        <operationName>loginbyHeaders</operationName>
    </TestCase>
    <TestCase name="CreateUser" method="POST" expectedResCode="200" expectedResContentType="application/xml" skipTest="false" detailedLog="false" secure="true" soapBase="true">    
        <description>Create a new User</description>
        <headers>      
    		<entry>        
    			<string>SOAPAction</string>
    			<string></string>
    		</entry>    
    	</headers>    
    	<expectedNodes>  
        	<string>id</string>
        </expectedNodes>
    	<soapParameterValues>
            <entry>        
            	<string>arg0.id</string>
    			<string>3</string>
    		</entry>
            <entry>        
            	<string>arg0.name</string>
    			<string>Test</string>
    		</entry>
            <entry>        
            	<string>arg0.age</string>
    			<string>30</string>
    		</entry>
        </soap>
        <wsdlKey>UserService</wsdlKey>
        <operationName>addUser</operationName>
    </TestCase>
</TestCases>
```
This example illustrates the soap method testcases, but you can just follow the same structure/property set to implement the testcases in eithe JSON/CSV.


Usage
-------
Maven - pom.xml
-------
```xml
<project ...>
	<!-- In case the maven gatf generator needs to be used to generate testcases -->
	<pluginRepositories>
	        <pluginRepository>
	            <id>testgen-repository</id>
	            <name>Testgen Repository</name>
	            <url>http://testgen.googlecode.com/svn/trunk/maven2/</url>
	        </pluginRepository>
	        <pluginRepository>
	        	<id>gatf-repository</id>
	            <name>Gatf Repository</name>
	            <url>https://raw2.github.com/sumeetchhetri/gatf/master/maven/</url>
	        </pluginRepository>
	</pluginRepositories>
	
	<!-- In case the maven gatf test class needs to be used to execute testcases -->
	<repositories>
		<repository>
			<id>gatf 1.0</id>
			<url>https://raw2.github.com/sumeetchhetri/gatf/master/maven/</url>
		</repository>
	</repositories>
	
	<plugins>
		<plugin>
				<groupId>com.test</groupId>
				<artifactId>gatf-plugin</artifactId>
				<version>1.0</version>
				<configuration>
					<!--The comma separated package(s)/classes(s) to be scanned for JAX-RS annotations for generating testcases-->
					<testPaths>
						<testPath>com.sample.services.*</testPath>
					</testPaths>
					<!--The WSDL Key/Location pair, the WSDL location will be looked up to generate the possible soap testcases-->
					<soapWsdlKeyPairs>
						<soapWsdlKeyPair>AuthService,http://localhost:8080/soap/auth?wsdl</soapWsdlKeyPair>
						<soapWsdlKeyPair>ExampleService,http://localhost:8080/soap/example?wsdl</soapWsdlKeyPair>
						<soapWsdlKeyPair>MessageService,http://localhost:8080/soap/messages?wsdl</soapWsdlKeyPair>
						<soapWsdlKeyPair>UserService,http://localhost:8080/soap/users?wsdl</soapWsdlKeyPair>
					</soapWsdlKeyPairs>
					<!--The REST API service URL prefix-->
					<urlPrefix>rest</urlPrefix>
					<!--The request data type, when generating request entities-->
					<requestDataType>json</requestDataType>
					<!--The resource path where the testcases and wsdl-locations.csv will be generated-->
					<resourcepath>src/test/resources/generated</resourcepath>
					<!--Whether this plugin is enabled-->
					<enabled>true</enabled>
				</configuration>
				<executions>
					<execution>
						<goals>
							<goal>gatf</goal>
						</goals>
					</execution>
				</executions>
			</plugin>
			.
			.
			.
			
	</plugins>
	.
	.
	.
	.
</project>
```

Direct jar execution - Command line
-----
config.xml
```xml
<configuration>
	<testPaths>
		<testPath>com.sample.services.*</testPath>
	</testPaths>
	<soapWsdlKeyPairs>
		<soapWsdlKeyPair>AuthService,http://localhost:8080/soap/auth?wsdl</soapWsdlKeyPair>
		<soapWsdlKeyPair>ExampleService,http://localhost:8080/soap/example?wsdl</soapWsdlKeyPair>
		<soapWsdlKeyPair>MessageService,http://localhost:8080/soap/messages?wsdl</soapWsdlKeyPair>
		<soapWsdlKeyPair>UserService,http://localhost:8080/soap/user?wsdl</soapWsdlKeyPair>
	</soapWsdlKeyPairs>
	<urlPrefix>rest</urlPrefix>
	<requestDataType>json</requestDataType>
	<resourcepath>.</resourcepath>
	<enabled>true</enabled>
</configuration>
```
Then on the command line using the file above
```
user@local> java -jar gatf-plugin-1.0.jar config.xml
```

File Upload example
-----
CSV example for file upload
```
/users/upload,File upload,POST,Testing File upload functionality reading from CSV file - Postive/REST,,Content-Type:application/octet-stream,,200,application/json,,,false,false,true,file:file:wsdl-locations.csv:,false,,,
```

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
