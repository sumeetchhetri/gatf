package com.gatf.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.MediaType;

import org.codehaus.jackson.map.ObjectMapper;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.util.HttpStatus;

import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.dataprovider.DatabaseTestDataSource;
import com.gatf.executor.dataprovider.FileTestDataProvider;
import com.gatf.executor.dataprovider.GatfTestDataProvider;
import com.gatf.executor.dataprovider.GatfTestDataSource;
import com.gatf.executor.dataprovider.GatfTestDataSourceHook;
import com.gatf.executor.dataprovider.InlineValueTestDataProvider;
import com.gatf.executor.dataprovider.MongoDBTestDataSource;
import com.gatf.executor.dataprovider.RandomValueTestDataProvider;

public class GatfMiscHandler extends HttpHandler {

	private GatfConfigToolMojo mojo;
	
	protected GatfMiscHandler(GatfConfigToolMojo mojo) {
		super();
		this.mojo = mojo;
	}

	@Override
	public void service(Request request, Response response) throws Exception {
    	if(request.getMethod().equals(Method.GET) ) {
    		try {
    			GatfExecutorConfig gatfConfig = GatfConfigToolMojo.getGatfExecutorConfig(mojo, null);
    			
    			Map<String, List<String>> miscMap = new HashMap<String, List<String>>();
				if(gatfConfig.getGatfTestDataConfig()!=null && 
						gatfConfig.getGatfTestDataConfig().getDataSourceList()!=null) {
					List<String> dataLst = new ArrayList<String>();
					dataLst.add("");
					for (GatfTestDataSource ds : gatfConfig.getGatfTestDataConfig().getDataSourceList()) {
						dataLst.add(ds.getDataSourceName());
					}
					miscMap.put("datasources", dataLst);
    			}
				if(gatfConfig.getGatfTestDataConfig()!=null && 
						gatfConfig.getGatfTestDataConfig().getProviderTestDataList()!=null) {
					List<String> dataLst = new ArrayList<String>();
					dataLst.add("");
					for (GatfTestDataProvider pv : gatfConfig.getGatfTestDataConfig().getProviderTestDataList()) {
						dataLst.add(pv.getProviderName());
					}
					miscMap.put("providers", dataLst);
				}
				if(gatfConfig.getGatfTestDataConfig()!=null && 
						gatfConfig.getGatfTestDataConfig().getDataSourceHooks()!=null) {
					List<String> dataLst = new ArrayList<String>();
					dataLst.add("");
					for (GatfTestDataSourceHook hk : gatfConfig.getGatfTestDataConfig().getDataSourceHooks()) {
						dataLst.add(hk.getHookName());
					}
					miscMap.put("hooks", dataLst);
				}
				List<String> dataLst = new ArrayList<String>();
				dataLst.add("");
				dataLst.add(MongoDBTestDataSource.class.getName());
				dataLst.add(DatabaseTestDataSource.class.getName());
				miscMap.put("datasourcecls", dataLst);
    			
				dataLst = new ArrayList<String>();
				dataLst.add("");
				dataLst.add(FileTestDataProvider.class.getName());
				dataLst.add(InlineValueTestDataProvider.class.getName());
				dataLst.add(RandomValueTestDataProvider.class.getName());
				miscMap.put("providercls", dataLst);
    			
    			String configJson = new ObjectMapper().writeValueAsString(miscMap);
    			response.setContentType(MediaType.APPLICATION_JSON);
	            response.setContentLength(configJson.length());
	            response.getWriter().write(configJson);
	            response.setStatus(HttpStatus.OK_200);
			} catch (Exception e) {
				GatfConfigToolMojo.handleError(e, response, null);
			}
    	}
    }
}
