/*
    Copyright 2013-2026, Sumeet Chhetri

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
package com.gatf.ui;

import org.glassfish.grizzly.http.server.Request;

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.TestCase;

public interface GatfConfigToolMojoInt {

	void setContext(AcceptanceTestContext context);
	
	AcceptanceTestContext getContext();
	
	String getRootDir();
	
	void setRootDir(String rootDir);
	
	void setAuthTestCase(TestCase tc);
	
	boolean isAuthenticated(Request request);
}
