# GATF configuration & test case reference

Everything here can also be edited in the web console (**Config** view and the test-case editor). The config file
can be XML (`gatf-config.xml`) or JSON (`gatf-config.json`, preferred when both exist).

- [Executor configuration](#executor-configuration)
- [API test cases](#api-test-cases)
- [Data sources, providers & hooks](#data-sources-providers--hooks)
- [Maven plugin](#maven-plugin)
- [Test generator](#test-generator)

## Executor configuration

The executor runs API suites (`-executor`) and seleasy suites (`-selenium`). A fully commented sample follows;
every element is optional unless noted, and the smallest useful config is just `baseUrl`, `testCaseDir` and
`outFilesDir` (plus `isSeleniumExecutor` + `seleniumScripts` for UI runs; or `seleniumScript`: a folder under the
test-case dir whose *.sel all run, a single .sel, or a list file with one script per line). The runnable
[`sample-project/`](../sample-project/) configs are a better starting point than this exhaustive list.

<details>
<summary>Sample executor config (XML, all options)</summary>

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
    <!-- The number of concurrent connections to the Base environment HTTP Server, default 1.
         UI suites: the number of scripts run in parallel (file-level workers, no fixed cap); `gatf test --workers` overrides it -->
    <numConcurrentExecutions>1</numConcurrentExecutions>
  
    <!-- The provider name that will be used to fetch user/password details for user credentials -->
    <authDataProvider>file-auth-provider</authDataProvider>
  
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

**Browser driver arguments** (`seleniumDriverConfigs[].arguments`, JSON configs): space-separated browser flags, each passed to the browser as its own argument, e.g. `"arguments": "--headless=new --no-sandbox"`. A flag's value cannot contain spaces (the string is split on whitespace). Earlier builds passed the whole string as a single argument, so Chrome ignored every flag in it.

### AI provider (opt-in)

`<ai>` turns on the AI features (the healer for now). Leave it out and nothing calls a model. The key is never in this file: it is read from the environment variable named by `apiKeyEnv` (default `ANTHROPIC_API_KEY` / `OPENAI_API_KEY`) or from the first line of `apiKeyFile`, and it never appears in reports, traces or logs.

```xml
<ai>
    <provider>anthropic</provider>            <!-- anthropic | openai | local (OpenAI-compatible) | a plugin provider -->
    <model>your-model-id</model>
    <!-- <endpoint>http://localhost:11434/v1/chat/completions</endpoint>   required for local -->
    <!-- <apiKeyEnv>ANTHROPIC_API_KEY</apiKeyEnv> or <apiKeyFile>/path/to/key</apiKeyFile> -->
    <timeoutMs>30000</timeoutMs>              <!-- per call -->
    <retries>2</retries>                      <!-- on timeouts, 429 and 5xx -->
    <maxCallsPerRun>10</maxCallsPerRun>
    <maxTokensPerRun>50000</maxTokensPerRun>
    <maxOutputTokens>1024</maxOutputTokens>
    <!-- <maxCostPerRun>1.0</maxCostPerRun> with <inputCostPerMTok>/<outputCostPerMTok> prices -->
</ai>
```

The `<ai>` block is used by the healer (Trace Viewer, editor, MCP `heal`) and the planner (`gatf plan`, MCP `plan`); see the [Tier 3 release notes](release-notes/tier-3.md).

## API test cases

A test file holds `<TestCases>` with one or more `<TestCase>` (XML, the most expressive format), a JSON array, or
CSV rows. GATF discovers every file under `testCaseDir` (ordered by file name when `isOrderByFileName` is true;
`sequence` orders cases within a file).

### A chained (workflow) example

```xml
<TestCases>
  <TestCase>
    <url>/auth/login</url>
    <name>Login</name>
    <method>POST</method>
    <headers><Content-Type><![CDATA[application/json]]></Content-Type></headers>
    <content>{"username":"${username}","password":"${password}"}</content>
    <expectedResCode>200</expectedResCode>
    <expectedResContentType>application/json</expectedResContentType>
    <expectedNodes><string>token</string></expectedNodes>
    <!-- capture response node `token` into ${authToken} for every later test -->
    <workflowContextParameterMap>
      <authToken><![CDATA[token]]></authToken>
    </workflowContextParameterMap>
    <sequence>1</sequence>
  </TestCase>
  <TestCase>
    <url>/users</url>
    <name>List users</name>
    <method>GET</method>
    <headers><Authorization><![CDATA[Bearer ${authToken}]]></Authorization></headers>
    <expectedResCode>200</expectedResCode>
    <expectedNodes><string>$[0].id</string></expectedNodes>   <!-- JSONPath / XPath node assertions -->
    <sequence>2</sequence>
  </TestCase>
</TestCases>
```

### Commonly used TestCase fields

| Field | Purpose |
|---|---|
| `baseUrl`, `url`, `method`, `headers`, `content` / `contentFile`, `exQueryPart` | The request (`${var}` placeholders resolve from global, workflow and provider variables) |
| `expectedResCode`, `expectedResContentType`, `expectedResContent`, `expectedNodes` | Response assertions; nodes are JSONPath (JSON) or XPath (XML/SOAP) |
| `logicalValidations` | Velocity boolean expressions evaluated against the response and variables |
| `workflowContextParameterMap` | Capture values for later tests: response nodes, `#responseHeader[..]`, `#responseCookie[..]`, `#responseMappedValue[..]`, `#responseMappedCount[..]` |
| `repeatScenarios` / `repeatScenarioProviderName` | Run the same case once per scenario row (inline or from a data provider) |
| `numberOfExecutions` | Performance mode: run N times and report timings |
| `perfConfig` | Hand the case to a load tool: `type` (`wperfk`, the default choice; `vegeta`, `autocannon`, `wrk`, `wrk2`, or a plugins/ engine), `threads`, `connections`, `durationSeconds`, `rate`, `timeout`, `filePath` |
| `relatedTestName` | Run another named case as a dependency |
| `executeOnCondition` | Velocity expression; when false the case is skipped (the condition is recorded as the reason) |
| `preWaitMs`, `postWaitMs` | Delays around the call |
| `preExecutionDataSourceHookName`, `postExecutionDataSourceHookName` | Run a data-source hook (e.g. SQL setup/cleanup) around the call |
| `abortOnInvalidStatusCode`, `abortOnInvalidContentType` | On a status-code (vs `expectedResCode`) / content-type (vs `expectedResContentType`) mismatch, fail this case at once and skip the rest of its response handling (body capture, response validation); the suite continues. Without `abortOnInvalidStatusCode` a status mismatch still fails the case, after the other validations have run |
| `secure` | Apply the configured auto-authentication token |
| `soapBase`, `wsdlKey`, `operationName` | SOAP test cases |
| `skipTest`, `detailedLog`, `sequence` | Control flags |

### Formats

<details>
<summary>XML</summary>

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

<details>
<summary>JSON</summary>

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

<details>
<summary>CSV (simplest; no complex bodies)</summary>

```csv
#URL,NAME,HTTPMETHOD,DESCRIPTION,CONTENT,HEADERS,EXQUERYPART,EXPECTEDSTATUSCODE,EXPECTEDCONTTYPE,EXPECTEDCONT,EXPECTEDNODES,SKIPTEST,LOG,SECURE,SOAPBASE,SOAPPARAMVALUES,WSDLKEY,OPERATIONNM
jsonapi/users/1,GetUser,GET,Get a user with id = 1,,someheader:val|anotherheader:val,,200,application/json,,id,false,false,true
```

</details>

## Data sources, providers & hooks

Configured under `gatfTestDataConfig` (see the executor sample):

- **Data sources** (`dataSourceList`): SQL (any JDBC driver), MongoDB, or a custom `dataSourceClass`; pooled.
- **Providers** (`providerTestDataList`): rows of variables from a data source query, files
  (CSV/JSON/XML/XLS/XLSX/ODT), inline values, random values, or a custom `providerClass`. `live` providers
  re-query on each use. Use them in API cases (`repeatScenarioProviderName`, `${column}` placeholders) and in
  seleasy (`#provider`, `#sql`, `#mongo`, `#file` loops).
- **Data-source hooks** (`dataSourceHooks`): query lists run on start, on shutdown, or around a test case.
- **Global variables** (`globalVariables`): project-wide `${name}` values.
- **Comparative runs** (`compareEnvBaseUrls` + `compareEnabled`): replay the suite against several environments
  and compare the responses.
- **Java hooks:** annotate a `public static void m(TestCase tc)` with `@PreTestCaseExecutionHook` or
  `@PostTestCaseExecutionHook` (optionally scoped to test names) in a package listed in `testCaseHooksPaths`.

## Maven plugin

Run a suite as part of a Maven build (goals: `gatf-executor`, `gatf-generator`, `gatf-config`):

```xml
<!-- jitpack hosts the GATF artifacts -->
<pluginRepositories>
    <pluginRepository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </pluginRepository>
</pluginRepositories>

<build>
    <plugins>
        <plugin>
            <groupId>com.github.sumeetchhetri.gatf</groupId>
            <artifactId>gatf-plugin</artifactId>
            <version>5.0.0</version>
            <executions>
                <execution>
                    <id>gatf-execution</id>
                    <phase>test</phase>
                    <goals>
                        <goal>gatf-executor</goal>
                    </goals>
                    <configuration>
                        <configFile>gatf-config.xml</configFile>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

## Test generator

Generates API test cases from JAX-RS (`@Path`) or Spring (`@RequestMapping`) controllers and from SOAP WSDLs,
with sample request bodies (random values), in XML, JSON or CSV, and optionally a Postman collection.
Run it with `gatf -generator <config>` or the `gatf-generator` Maven goal.

**Limitation:** request bodies are derived from Jackson/JAXB annotations, so entities must be Jackson/JAXB
compliant.

<details>
<summary>Generator config</summary>

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

<details>
<summary>Generator Maven configuration</summary>

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
			<version>5.0.0</version>
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
</build>
```

</details>
