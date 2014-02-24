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

public class MongoDBTestDataProvider implements TestDataProvider {

	private Logger logger = Logger.getLogger(MongoDBTestDataProvider.class.getSimpleName());
	
	public List<Map<String, String>> provide(String[] args, AcceptanceTestContext context) {
		
		List<Map<String, String>> result = new ArrayList<Map<String,String>>();
		
		if(args==null || args.length==0) {
			throw new AssertionError("No arguments passed to the MongoDBTestDataProvider");
		}
		
		if(args.length<6) {
			throw new AssertionError("The arguments, namely mongodb-host, mongodb-port, mongodb-database," +
					"mongodb-collection and variableNames are mandatory for MongoDBTestDataProvider");
		}
		
		Assert.assertNotNull("mongodb-host cannot be empty", args[0]);
		Assert.assertNotNull("mongodb-port cannot be empty", args[1]);
		Assert.assertNotNull("mongodb-database cannot be empty", args[2]);
		Assert.assertNotNull("mongodb-collection cannot be empty", args[3]);
		Assert.assertNotNull("queryString cannot be empty", args[4]);
		Assert.assertNotNull("variableNames cannot be empty", args[5]);
		
		
		String host = args[0].trim();
		String port = args[1].trim();
		String dbName = args[2].trim();
		String collName = args[3].trim();
		String queryString = args[4].trim();
		String variableNames = args[5].trim();
		
		Assert.assertFalse("mongodb-host cannot be empty", host.isEmpty());
		Assert.assertFalse("mongodb-port cannot be empty", port.isEmpty());
		Assert.assertFalse("mongodb-database cannot be empty", dbName.isEmpty());
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
		
		String username = null, password = null;
		if(args.length>6) {
			Assert.assertTrue("mongodb-password not provided", args.length==8);
			Assert.assertFalse("mongodb-user cannot be empty", args[6].isEmpty());
			Assert.assertFalse("mongodb-password cannot be empty", args[7].isEmpty());
			
			username = args[6].trim();
			password = args[7].trim();
		}
		
		StringBuilder build = new StringBuilder();
		build.append("MongoDBTestDataProvider configuration [\n");
		build.append(String.format("mongodb-host is %s\n", host));
		build.append(String.format("mongodb-port is %s\n", port));
		build.append(String.format("mongodb-database is %s\n", dbName));
		build.append(String.format("mongodb-collection is %s\n", collName));
		if(username!=null) {
			build.append(String.format("mongodb-user is %s\n", username));
			build.append(String.format("mongodb-password is %s\n", password));
		}
		build.append(String.format("queryString is %s\n", queryString));
		build.append(String.format("variableNames is %s]", variableNames));
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
			
			List<ServerAddress> addresses = new ArrayList<ServerAddress>();
			for (int i=0;i<hosts.length;i++) {
				ServerAddress address = new ServerAddress(hosts[i], Integer.valueOf(ports[i]));
				addresses.add(address);
			}
			
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
									variableNamesArr.get(i)), object.containsField(variableNamesArr.get(i)));
							row.put(variableNamesArr.get(i), object.get(variableNamesArr.get(i)).toString());
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
		}
		return result;
	}
}
