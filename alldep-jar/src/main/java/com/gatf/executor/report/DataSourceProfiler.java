/*
    Copyright 2013-2019, Sumeet Chhetri
    
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
package com.gatf.executor.report;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.MultiMap;
import org.apache.commons.collections.map.MultiValueMap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.WorkflowContextHandler;
import com.gatf.executor.dataprovider.GatfTestDataProvider;
import com.gatf.executor.dataprovider.MongoDBTestDataSource;
import com.gatf.executor.dataprovider.SQLDatabaseTestDataSource;
import com.gatf.executor.dataprovider.TestDataSource;

/**
 * @author Sumeet Chhetri
 *
 */
public class DataSourceProfiler {

	AcceptanceTestContext context;
	
	MultiMap dsprofileMap;
	
	public DataSourceProfiler(AcceptanceTestContext context) throws Exception {
		
		if(DataSourceProfiler.class.getResourceAsStream("/profile-providers.xml")!=null)
		{
			this.context = context;
			
			dsprofileMap = new MultiValueMap();
			
			List<GatfTestDataProvider> provs = (List<GatfTestDataProvider>)WorkflowContextHandler.XOM.readValue(DataSourceProfiler.class.getResourceAsStream("/profile-providers.xml"), new TypeReference<List<GatfTestDataProvider>>() {});
			for (GatfTestDataProvider gatfTestDataProvider : provs) {
				dsprofileMap.put(gatfTestDataProvider.getProviderName(), gatfTestDataProvider);
			}
		}
		
		if(context.getResourceFile("profile-providers.xml")!=null && context.getResourceFile("profile-providers.xml").exists())
		{
			List<GatfTestDataProvider> provs = (List<GatfTestDataProvider>)WorkflowContextHandler.XOM.readValue(context.getResourceFile("profile-providers.xml"), new TypeReference<List<GatfTestDataProvider>>() {});
			for (GatfTestDataProvider gatfTestDataProvider : provs) {
				if(dsprofileMap.containsKey(gatfTestDataProvider.getProviderName()))
					dsprofileMap.remove(gatfTestDataProvider.getProviderName());
			}
			for (GatfTestDataProvider gatfTestDataProvider : provs) {
				dsprofileMap.put(gatfTestDataProvider.getProviderName(), gatfTestDataProvider);
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	private void doProfile(TestDataSource source, String provName, Map<String, List<List<String>>> out)
	{
		Collection<GatfTestDataProvider> coll = (Collection<GatfTestDataProvider>)dsprofileMap.get(provName);
		int counter = 0;
		for (GatfTestDataProvider prov : coll) {
			List<List<String>> res = new ArrayList<List<String>>();
			List<Map<String, String>> ret = source.provide(prov, context);
			if(ret==null || ret.size()==0)
				continue;
			String[] provProps = prov.getProviderProperties().split(",");
			List<String> hdr = new ArrayList<String>();
			for (String name : provProps) {
				hdr.add(name);
			}
			res.add(hdr);
			for (Map<String, String> map : ret) {
				List<String> row = new ArrayList<String>();
				for (String name : provProps) {
					row.add(map.get(name));
				}
				res.add(row);
			}
			out.put(source.getDataSourceName()+"-"+provName+"-"+counter++, res);
		}
	}
	
	public Map<String, List<List<String>>> getProfileStats(String dsn)
	{
		Map<String, List<List<String>>> out = new LinkedHashMap<String, List<List<String>>>();
		if(context!=null)
		{
			Map<String, TestDataSource> dataSourceMapForProfiling = context.getDataSourceMapForProfiling();
			for (Map.Entry<String, TestDataSource> entry : dataSourceMapForProfiling.entrySet()) {
				TestDataSource source = entry.getValue();
				if(dsn!=null && !source.getDataSourceName().equals(dsn))
					continue;
				if(SQLDatabaseTestDataSource.class.getCanonicalName().equals(source.getClassName()))
				{
					if(source.getArgs().length<=4) {
						System.out.println("SQL Datasource does not define SQL type, needs to be one of " +
								"(mysql/oracle/postgre/sql-server)");
					} else {
						String type = source.getArgs()[4];
						if(type.equalsIgnoreCase("mysql") && dsprofileMap.containsKey("mysql-profile")) {
							doProfile(source, "mysql-profile", out);
						} else if(type.equalsIgnoreCase("postgre")) {
							doProfile(source, "postgre-profile", out);
						} else if(type.equalsIgnoreCase("sql-server")) {
							doProfile(source, "sql-server-profile", out);
						} else if(type.equalsIgnoreCase("oracle")) {
							doProfile(source, "oracle-profile", out);
						} else {
							System.out.println("SQL Datasource does not define SQL type, needs to be one of " +
									"(mysql/oracle/postgre/sql-server)");
						}
					}
				}
				else if(MongoDBTestDataSource.class.getCanonicalName().equals(source.getClassName()))
				{
					doProfile(source, "mongo-profile", out);
				}
				else
				{
					System.out.println("Profiles only available for DatabaseTestDataSource/MongoDBTestDataSource");
				}
			}
		}
		return out;
	}
}
