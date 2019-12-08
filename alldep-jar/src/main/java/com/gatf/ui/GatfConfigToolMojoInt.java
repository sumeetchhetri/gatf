package com.gatf.ui;

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.TestCase;

public interface GatfConfigToolMojoInt {

	void setContext(AcceptanceTestContext context);
	
	AcceptanceTestContext getContext();
	
	String getRootDir();
	
	void setRootDir(String rootDir);
	
	void setAuthTestCase(TestCase tc);
}
