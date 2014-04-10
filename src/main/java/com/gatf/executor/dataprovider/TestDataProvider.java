package com.gatf.executor.dataprovider;

import java.util.List;
import java.util.Map;

import com.gatf.executor.core.AcceptanceTestContext;

public interface TestDataProvider {

	List<Map<String, String>> provide(GatfTestDataProvider provider, AcceptanceTestContext context);
}
