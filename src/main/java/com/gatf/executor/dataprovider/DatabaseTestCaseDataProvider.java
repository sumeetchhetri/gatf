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

import java.sql.Connection;
import java.sql.Driver;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.Assert;

import com.CustomDriverManager;
import com.gatf.executor.core.AcceptanceTestContext;

/**
 * @author Sumeet Chhetri
 * The database test case data provider implementation
 */
public class DatabaseTestCaseDataProvider implements TestDataProvider {

	private Logger logger = Logger.getLogger(DatabaseTestCaseDataProvider.class.getSimpleName());
	
	public List<Map<String, String>> provide(String[] args, AcceptanceTestContext context) {
		
		List<Map<String, String>> result = new ArrayList<Map<String,String>>();
		
		Thread currentThread = Thread.currentThread();
		ClassLoader oldClassLoader = currentThread.getContextClassLoader();
		try {
			currentThread.setContextClassLoader(context.getProjectClassLoader());
			if(args==null || args.length==0) {
				throw new AssertionError("No arguments passed to the DatabaseProvider");
			}
			
			if(args.length<6) {
				throw new AssertionError("All 6 arguments, namely jdbcUrl, jdbcDriver, dbUserName, " +
						"dbPassword, queryString and variableNames are mandatory for DatabaseProvider");
			}
			
			Assert.assertNotNull("jdbcUrl cannot be empty", args[0]);
			Assert.assertNotNull("jdbcDriver cannot be empty", args[1]);
			Assert.assertNotNull("dbUserName cannot be empty", args[2]);
			Assert.assertNotNull("dbPassword cannot be empty", args[3]);
			Assert.assertNotNull("queryString cannot be empty", args[4]);
			Assert.assertNotNull("variableNames cannot be empty", args[5]);
			
			String jdbcUrl = args[0].trim();
			String jdbcDriver = args[1].trim();
			String dbUserName = args[2].trim();
			String dbPassword = args[3].trim();
			String queryString = args[4].trim();
			String variableNames = args[5].trim();
			
			Assert.assertFalse("jdbcUrl cannot be empty", jdbcUrl.isEmpty());
			Assert.assertFalse("jdbcDriver cannot be empty", jdbcDriver.isEmpty());
			Assert.assertFalse("dbUserName cannot be empty", dbUserName.isEmpty());
			//Assert.assertFalse("dbPassword cannot be empty", dbPassword.isEmpty());
			Assert.assertFalse("queryString cannot be empty", queryString.isEmpty());
			Assert.assertTrue("only select queries allowed", queryString.toLowerCase().indexOf("select ")==0);
			
			List<String> variableNamesArr = new ArrayList<String>();
			for (String varName : variableNames.split(",")) {
				if(!varName.trim().isEmpty()) {
					variableNamesArr.add(varName);
				}
			}
			Assert.assertTrue("need to define at-least a single variable name", 
					!variableNames.isEmpty() && variableNames.split(",").length>0 && variableNamesArr.size()>0);
			
			StringBuilder build = new StringBuilder();
			build.append("DatabaseProvider configuration [\n");
			build.append(String.format("jdbcUrl is %s\n", jdbcUrl));
			build.append(String.format("jdbcDriver is %s\n", jdbcDriver));
			build.append(String.format("dbUserName is %s\n", dbUserName));
			build.append(String.format("dbPassword is %s\n", dbPassword));
			build.append(String.format("queryString is %s\n", queryString));
			build.append(String.format("variableNames is %s]", variableNames));
			logger.info(build.toString());
			
			//Load the driver first
			try {
				Driver driver = (Driver)context.getProjectClassLoader().loadClass(jdbcDriver).newInstance();
				CustomDriverManager.registerDriver(driver);
			} catch (Exception e) {
				throw new AssertionError(String.format("Driver class %s not found", jdbcDriver));
			}
			
			//Now try connecting to the Database
			Connection conn = null;
			try {
				conn = CustomDriverManager.getConnection(jdbcUrl, dbUserName, dbPassword);
			} catch (Exception e) {
				throw new AssertionError(String.format("Connection to the Database using the JDBC URL %s failed with the error %s", 
						jdbcUrl, ExceptionUtils.getStackTrace(e)));
			}
			
			Statement statement = null;
			ResultSet resultSet = null;
			try {
				statement = conn.createStatement();
				resultSet = statement.executeQuery(queryString);
				if(resultSet.getMetaData().getColumnCount()!=variableNamesArr.size())
					throw new AssertionError("The number of columns from the query does not match the variable Names provided");
				for (int i = 0; i < variableNamesArr.size(); i++) {
					Assert.assertTrue(String.format("Invalid variable/sql type, only strings,boolean,numbers allowed for %s", variableNamesArr.get(i)), 
							isValidDataType(resultSet.getMetaData().getColumnType(i+1)));
				}
				while (resultSet.next()) {
					Map<String, String> row = new HashMap<String, String>();
					for (int i = 0; i < variableNamesArr.size(); i++) {
						row.put(variableNamesArr.get(i), resultSet.getString(i+1));
					}
					result.add(row);
				}
			} catch (Exception e) {
				throw new AssertionError(String.format("Fetching Test Data failed while executing query %s with the error %s", 
						queryString, ExceptionUtils.getStackTrace(e)));
			} finally {
				try {
					if(resultSet!=null)resultSet.close();
				} catch (SQLException e) {
				}
				try {
					if(statement!=null)statement.close();
				} catch (SQLException e) {
				}
				try {
					if(conn!=null)conn.close();
				} catch (SQLException e) {
				}
			}
		} catch (Exception t) {
			t.printStackTrace();
		} finally {
			currentThread.setContextClassLoader(oldClassLoader);
		}
		return result;
	}
	
	private boolean isValidDataType(int type) {
		return type == java.sql.Types.BIGINT || type == java.sql.Types.BIT || type == java.sql.Types.BOOLEAN || 
				type == java.sql.Types.CHAR || type == java.sql.Types.DATE || type == java.sql.Types.DECIMAL || 
				type == java.sql.Types.DOUBLE || type == java.sql.Types.FLOAT || type == java.sql.Types.INTEGER ||
				type == java.sql.Types.LONGNVARCHAR || type == java.sql.Types.LONGVARCHAR || type == java.sql.Types.NCHAR ||
				type == java.sql.Types.NULL || type == java.sql.Types.NUMERIC || type == java.sql.Types.NVARCHAR ||
				type == java.sql.Types.REAL || type == java.sql.Types.SMALLINT || type == java.sql.Types.TIME ||
				type == java.sql.Types.TINYINT || type == java.sql.Types.TIMESTAMP || type == java.sql.Types.VARCHAR;
	}
}
