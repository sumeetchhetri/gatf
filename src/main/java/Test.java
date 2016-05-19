/*
    Copyright 2013-2016, Sumeet Chhetri
    
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

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.AsyncHttpClientConfig.Builder;
import com.ning.http.client.ListenableFuture;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.Response;


public class Test {

	public static void main2(String[] args) throws Exception
	{
		Builder builder = new AsyncHttpClientConfig.Builder();
		builder.setConnectionTimeoutInMs(100000)
				.setMaximumConnectionsPerHost(1)
				.setMaximumConnectionsTotal(2)
				.setRequestTimeoutInMs(100000)
				.setAllowPoolingConnection(true)
				.setCompressionEnabled(true)
				.setIOThreadMultiplier(2)
				.build();
		
		AsyncHttpClient client = new AsyncHttpClient(builder.build());
		
		RequestBuilder rbuilder = new RequestBuilder("GET");
		rbuilder.setUrl("http://127.0.0.1:6868/indexmain.html");
		
		ListenableFuture<Response> respf = client.executeRequest(rbuilder.build());
		while(!respf.isDone())Thread.sleep(100);
		
		Response resp = respf.get();
		System.out.println(resp.toString());
		
		client.close();
		
	}
	
	public static void main(String[] args) throws Exception
	{
	}
}
