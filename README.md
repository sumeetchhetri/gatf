Generic Automated Test Framework (GATF)
=========

**GATF** is a automated test generator and acceptance testing framework(laid on top of TestNG, Soap-ws and Rest-assured). Provides 2 components namely the **Test Generator** and the **Accpetance Test Executor** class

  - Generates testcases (xml) for all your API's
  - Both Rest-ful and SOAP based API's are supported
  - JAX-RS annotations are looked up to generate testcases for rest-ful api's
  - WSDL file is referred to genrate soap based test cases
  - Acceptance test class provided to execute all generated testcases (in most cases you would only need this class in your testng.xml for all your API tests)


GATF Acceptance Test executor is data-type agnostic, which means that your testcases can be in any formats like XML, JSON or plain old CSV. Whereas the Test generator generates only XML files (so that even complex data structures can be suuported within your testcases) 

> The primary goal of GATF is Automation with Simplicity.


Lets go over the primary entity within GATF, Testcase  

Testcase
-----------

A testcase is nothing but an entity that defines any method call over HTTP, to simplify things, its just a plain object that holds information about your API call with properties like the HTTP Method, the URI, the parameters, the content, the Headers passed, the expected output and son on. Lets walk over the properties of a Testcase in details below,

* Url - the Service URL, mandatory (ex, /api/users)
* Name - the test case name, mandatory (ex, GetUsers)
* Description - the test case description, optional (ex, Get all the users)
* Method - the HTTP method, mandatory (ex, GET)
* ExQueryPart - extra query parameters, optional (ex, limit=10, this will be appended to the Url, so the Url now becomes /api/users?limit=10)
* Content - the body/content to be passed in the request, optional
* Headers - the HTTP headers to be passed in the request, optional (ex, Content-Type: application/json)
* Secure - whether this request requires a prior authentication API call, optional (ex, true/false)
* SoapBase - whether this request is a SOAP method call with runtime request generation+transformation, optional (ex, true/false)
* WsdlKey - the wsdl key name sepcified while passing the wsdl value in the wsdl-location.csv file or the pom.xml configuration for the maven plugin, mandatory when SoapBase=true (ex, UserSoapService), Refer to the [SOAP 1] section below
* OperationName - the soap method operationName as specified in the wsdl file for that particular service, mandatory when SoapBase=true (ex, getUsers), Refer to the [SOAP] [1] section below
* SoapParameterValues - the transformation key/value pairs used while tranforming the runtime generated soap message, Refer to the [SOAP] [1] section below
* ExpectedResCode - the expected HTTP status code, mandatory (ex, 200)
* ExpectedResContentType - the expected content type, optional (ex, application/json)
* ExpectedNodes - the expected nodes in the response body in dotted notation, optional (ex, User.id)
* ExpectedResContent - the exact expected response content to be matched with the actual result, optional
* SkipTest - whether to skip this test, mandatory (ex, true/false)
* DetailedLog - whether or not to log the detailed execution logs, mandatory (ex, true/false)
* FilesToUpload - the file content or multipart data to upload, optional (ex, file:file:wsdl-locations.csv:), specifees a colon separated list of file details to be uploaded, the list defines controlname:type(file/text):fileOrText(filename/text content):contentType(only for text content)

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


Enter TestNG
-----
To start writing acceptance testcases supporting application level authentication, we need to first discuss the testng.xml file that would help you acheive the desired results, TestNG testng.xml file specifies the testsuite and the testcases for your tests.


```xml
<!DOCTYPE suite SYSTEM "http://testng.org/testng-1.0.dtd" >
<suite name="MTelTestSuite" parallel="false" verbose="2" >
    <!-- The Base URL or the URL prefix, which will be prepended to all your testcase URL's-->
    <parameter name="baseUrl" value="http://localhost:8080/"/>
	
    <!--The default folder for maven projects is {project}/src/test/resources, if this value is not specified is then the new value from this parameter is used instead of the default base path  -->
    <parameter name="testCasesBasePath" value="somebasefolder"/>
    
    <!--The folder/path withing the testCasesBasePath folder, so if above parameter is not specified the testcases will be searched in the {project}/src/test/resources/data folder or if it is specified then the {testCasesBasePath}/data folder -->
    <parameter name="testCasesPath" value="data"/>
	
    <!-- Whether authentication is required or not -->
	<parameter name="authEnabled" value="true"/>
    
    <!-- In case if authentication is enabled, then the authentication/login URL-->
	<parameter name="authUrl" value="rest/auth/loginh"/>
    
    <!-- A comma separated list of values specifying the name of the authentication token, how to extract it from the response, what name to use while sending it in the request and how to sent it in the request, the value below says, that fetch the token property from the response json body and use it as an authentication token in the query parameter for all subsequent requests -->
	<parameter name="authExtractAuth" value="token,json,token,queryparam"/>
	
    <!-- The file location inside the testCasesBasePath which specifies wsdlkey,wsldlocation values -->
	<parameter name="wsdlLocFile" value="wsdl-locations.csv"/>
    
    <!-- Whether soap endpoints also require authentication -->
	<parameter name="soapAuthEnabled" value="true"/>
    
    <!-- The Authentication Service WsdlKey to use for authentication -->
	<parameter name="soapAuthWsdlKey" value="AuthService"/>
    
    <!-- The SOAP operation to use to authenticate which can be found in the wsdl location specified with the key above in the wsdl-locations.csv file-->
	<parameter name="soapAuthOperation" value="loginbyHeaders"/>
    
    <!-- Ditto as authExtractAuth, apart from the fact that the place to fetch the token is always the response body xml-->
	<parameter name="soapAuthExtractAuth" value="token,token,queryparam"/>
 		
 	<test name="API Tests">
 		<classes>
         <!-- This is the generic Accpetance Test class defined by the framework-->
	     <class name="com.gatf.test.ApiAcceptanceTest"/>
	     </classes>
 	</test>
</suite>

```
In the example above the WSDL for AuthService is not defined, assuming the loginbyHeaders is a soap service defined in the assumed wsdl file :-)


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
[2]:http://lit-savannah-1186.herokuapp.com/tests.html
[3]:http://lit-savannah-1186.herokuapp.com/    
