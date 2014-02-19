package com.gatf.test.dataprovider;

import java.util.List;
import java.util.Map;

import com.gatf.test.core.AcceptanceTestContext;

public interface TestDataProvider {

	List<Map<String, String>> provide(String[] args, AcceptanceTestContext context);
}
