package com.gatf.executor.dataprovider;

/*
Copyright 2013-2014, Sumeet Chhetri

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.Assert;

import com.gatf.executor.core.AcceptanceTestContext;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;
import com.mongodb.util.JSON;

/**
 * @author Sumeet Chhetri
 * The mongodb test case data provider implementation
 */
public class MongoDBTestDataSource extends TestDataSource {

	private Logger logger = Logger.getLogger(MongoDBTestDataSource.class.getSimpleName());
	
	List<ServerAddress> addresses = new ArrayList<ServerAddress>();
	
	public void init() {
		if(args==null || args.length==0) {
			throw new AssertionError("No arguments passed to the MongoDBTestDataProvider");
		}
		
		if(args.length<3) {
			throw new AssertionError("The arguments, namely mongodb-host, mongodb-port, mongodb-database are " +
					" mandatory for MongoDBTestDataProvider");
		}
		
		Assert.assertNotNull("mongodb-host cannot be empty", args[0]);
		Assert.assertNotNull("mongodb-port cannot be empty", args[1]);
		Assert.assertNotNull("mongodb-database cannot be empty", args[2]);
		
		String host = args[0].trim();
		String port = args[1].trim();
		String dbName = args[2].trim();
		
		Assert.assertFalse("mongodb-host cannot be empty", host.isEmpty());
		Assert.assertFalse("mongodb-port cannot be empty", port.isEmpty());
		Assert.assertFalse("mongodb-database cannot be empty", dbName.isEmpty());
		
		String username = null, password = "";
		if(args.length>3) {
			Assert.assertNotNull("mongodb-user cannot be empty", args[3]);
			Assert.assertFalse("mongodb-user cannot be empty", args[3].isEmpty());
			
			username = args[3].trim();
			if(args.length>4 && args[4]!=null)
				password = args[4].trim();
		}
		
		StringBuilder build = new StringBuilder();
		build.append("MongoDBTestDataSource configuration [\n");
		build.append(String.format("mongodb-host is %s\n", host));
		build.append(String.format("mongodb-port is %s\n", port));
		build.append(String.format("mongodb-database is %s\n", dbName));
		if(username!=null) {
			build.append(String.format("mongodb-user is %s\n", username));
			build.append(String.format("mongodb-password is %s\n", password));
		}
		logger.info(build.toString());
		
		try {
			String[] hosts = host.split(",");
			String[] ports = port.split(",");
			
			if(hosts.length>ports.length) {
				Assert.assertEquals(String.format("Port missing for host %s", hosts[ports.length-1]), hosts.length, ports.length);
			} else {
				Assert.assertEquals(String.format("Host missing for port %s", ports[hosts.length-1]), hosts.length, ports.length);
			}
			
			for (String portVal : ports) {
				try {
					Integer.valueOf(portVal);
				} catch (Exception e) {
					throw new AssertionError(String.format("Port value invalid - %s", portVal));
				}
			}
			
			for (int i=0;i<hosts.length;i++) {
				ServerAddress address = new ServerAddress(hosts[i], Integer.valueOf(ports[i]));
				addresses.add(address);
			}
			
			for (int i = 0; i < poolSize; i++) {
				MongoClient mongoClient = null;
				//Now try connecting to the Database
				try {
					mongoClient = new MongoClient(addresses);
				} catch (Exception e) {
					throw new AssertionError(String.format("Connection to MongoDB failed with the error %s", 
							ExceptionUtils.getStackTrace(e)));
				}
				
				DB db = null;
				try {
					db = mongoClient.getDB(dbName);
					if(username!=null && password!=null) {
						Assert.assertTrue(String.format("Authentication to the Mongo database %s failed with %s/%s", dbName, 
								username, password), db.authenticate(username, password.toCharArray()));
					}
				} catch (Exception e) {
					throw new AssertionError(String.format("Error during initialization of MongoDB connection %s", 
							ExceptionUtils.getStackTrace(e)));
				}
				
				addToPool(mongoClient, false);
			}
		} catch (Exception e) {
			throw new AssertionError(String.format("Error during initialization of MongoDB connection %s", 
					ExceptionUtils.getStackTrace(e)));
		} finally {
		}

	}
	
	public void destroy() {
		for (Resource res : pool) {
			MongoClient mongoClient = (MongoClient)res.object;
			mongoClient.close();
		}
		logger.info("Releasing connections....");
	}
	
	public List<Map<String, String>> provide(GatfTestDataProvider provider, AcceptanceTestContext context) {
		
		List<Map<String, String>> result = new ArrayList<Map<String,String>>();
		
		Assert.assertNotNull("provider cannot be null", provider);
		Assert.assertTrue("provider cannot be null", provider.getArgs()!=null && provider.getArgs().length>0);
		Assert.assertNotNull("mongodb-collection cannot be empty", provider.getArgs()[0]);
		Assert.assertNotNull("queryString cannot be empty", provider.getQueryStr());
		Assert.assertNotNull("variableNames cannot be empty", provider.getSourceProperties());
		Assert.assertNotNull("propertyNames cannot be empty", provider.getProviderProperties());
		
		String dbName = args[2].trim();
		String collName = provider.getArgs()[0].trim();
		String queryString = provider.getQueryStr().trim();
		String variableNames = provider.getProviderProperties();
		String propertyNames = provider.getSourceProperties();
		
		Assert.assertNotNull("mongodb-collection cannot be empty", collName.isEmpty());
		Assert.assertFalse("queryString cannot be empty", queryString.isEmpty());
		
		List<String> variableNamesArr = new ArrayList<String>();
		for (String varName : variableNames.split(",")) {
			if(!varName.trim().isEmpty()) {
				variableNamesArr.add(varName);
			}
		}
		Assert.assertTrue("need to define at-least a single variable name", 
				!variableNames.isEmpty() && variableNames.split(",").length>0 && variableNamesArr.size()>0);
		
		List<String> propertyNamesArr = new ArrayList<String>();
		for (String varName : propertyNames.split(",")) {
			if(!varName.trim().isEmpty()) {
				propertyNamesArr.add(varName);
			}
		}
		Assert.assertTrue("need to define at-least a single property name", 
				!propertyNames.isEmpty() && propertyNames.split(",").length>0 && propertyNamesArr.size()>0);
		
		Assert.assertTrue("property name and variable name sizes don't match", 
				propertyNamesArr.size()==variableNamesArr.size());
		
		StringBuilder build = new StringBuilder();
		build.append("Provider configuration [\n");
		build.append(String.format("dataSource name is %s\n", getDataSourceName()));
		build.append(String.format("mongodb-collection is %s\n", collName));
		build.append(String.format("queryString is %s\n", queryString));
		build.append(String.format("propertyNames is %s\n", propertyNames));
		build.append(String.format("variableNames is %s]", variableNames));
		logger.info(build.toString());
		
		Resource res = null;
		try {
			
			res = getResource();
			MongoClient mongoClient = (MongoClient)res.object;
			
			DB db = null;
			try {
				db = mongoClient.getDB(dbName);
				
				DBCollection coll = db.getCollection(collName);
				Assert.assertNotNull(String.format("Mongodb collection %s not found", collName), coll);
				
				DBObject queryObject = null;
				try {
					queryObject = (DBObject) JSON.parse(queryString);
				} catch (Exception e) {
					Assert.assertNotNull("queryString passed is invalid");
				}
				
				DBCursor cursor = null;
				try {
					cursor = coll.find(queryObject);
					while(cursor.hasNext()) {
						DBObject object = cursor.next();
						Map<String, String> row = new HashMap<String, String>();
						for (int i = 0; i < variableNamesArr.size(); i++) {
							Assert.assertTrue(String.format("Could not find %s field in the result document returned",
									propertyNamesArr.get(i)), object.containsField(propertyNamesArr.get(i)));
							row.put(variableNamesArr.get(i), object.get(propertyNamesArr.get(i)).toString());
						}
						result.add(row);
					}
				} catch (Exception e) {
					throw new AssertionError(e);
				} finally {
					if(cursor!=null)
						cursor.close();
				}
			} catch (Exception e) {
				throw new AssertionError(String.format("Fetching Test Data failed while executing query %s with the error %s", 
						queryString, ExceptionUtils.getStackTrace(e)));
			} finally {
				if(mongoClient!=null)mongoClient.close();
			}
		} catch (Exception e) {
			throw new AssertionError(String.format("Fetching Test Data failed while executing query %s with the error %s", 
					queryString, ExceptionUtils.getStackTrace(e)));
		} finally {
			if(res!=null)
				releaseToPool(res);
		}
		return result;
	}

	public boolean execute(String queryStr) {
		
		boolean result = false;
		
		Assert.assertNotNull("queryString cannot be empty", queryStr);
		
		String dbName = args[2].trim();
		String queryString = queryStr.trim();
		
		Assert.assertFalse("queryString cannot be empty", queryString.isEmpty());

		StringBuilder build = new StringBuilder();
		build.append("DataSourceHook configuration [\n");
		build.append(String.format("dataSource name is %s\n", getDataSourceName()));
		build.append(String.format("queryString is %s]", queryString));
		logger.info(build.toString());
		
		Resource res = null;
		try {
			
			res = getResource();
			MongoClient mongoClient = (MongoClient)res.object;
			
			DB db = null;
			try {
				db = mongoClient.getDB(dbName);

				try {
					Object resp = db.eval(queryStr, "");
					result = resp!=null;
				} catch (Exception e) {
					throw new AssertionError(e);
				}
			} catch (Exception e) {
				throw new AssertionError(String.format("Error occurred while executing query %s with the error %s", 
						queryString, ExceptionUtils.getStackTrace(e)));
			}
		} catch (Exception e) {
			throw new AssertionError(String.format("Error occurred while executing query %s with the error %s", 
					queryString, ExceptionUtils.getStackTrace(e)));
		} finally {
			if(res!=null)releaseToPool(res);
		}
		return result;
	}
	
	private Resource getResource() {
		Resource res = checkoutFromPool();
		MongoClient mongoClient = null;
		if(res==null)
		{
			try {
				Thread.sleep(newResourceCheckoutTimeMs/2);
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}
			
			res = checkoutFromPool();
			if(res!=null)return res;
			
			try {
				Thread.sleep(newResourceCheckoutTimeMs/2);
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}
			
			res = checkoutFromPool();
			if(res!=null)return res;
			
			try {
				mongoClient = new MongoClient(addresses);
			} catch (Exception e) {
				throw new AssertionError(String.format("Connection to MongoDB failed with the error %s", 
						ExceptionUtils.getStackTrace(e)));
			}
			res = addToPool(mongoClient, true);
		}
		return res;
	}
}
