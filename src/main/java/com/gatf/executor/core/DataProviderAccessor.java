package com.gatf.executor.core;

import java.util.List;
import java.util.Map;
import org.junit.Assert;

/**
 * @author Sumeet Chhetri
 *
 */
public class DataProviderAccessor {

	private AcceptanceTestContext acontext;
	
	private TestCase testCase;
	
	private String name;
	
	private String func;
	
	private int index = -1;
	
	public DataProviderAccessor(AcceptanceTestContext acontext, TestCase testCase) {
		this.acontext = acontext;
		this.testCase = testCase;
	}

	public DataProviderAccessor n(String name) {
		System.out.println("name = " +name);
		this.name = name;
		return this;
	}
	
	public DataProviderAccessor w(String func) {
		System.out.println("func = " +func);
		this.func = func;
		return this;
	}
	
	public DataProviderAccessor w(int index) {
		System.out.println("index = " +index);
		this.index = index;
		return this;
	}
	
	public String pv(String propertyName) {
		System.out.println("propertyName = " +propertyName);
		if(name==null)
		{
			throw new AssertionError("DataProviderAccessor needs to define a provider name");
		}
		if(propertyName==null)
		{
			throw new AssertionError("DataProviderAccessor needs to define a property name");
		}
		if(func==null && index<0)
		{
			throw new AssertionError("DataProviderAccessor needs to define a either and index or a function for the index");
		}
		
		List<Map<String, String>> provData = acontext.getAnyProviderData(name, testCase);
		Assert.assertNotNull("Provider not found - " + name, provData);
		if(index<0)
		{
			if(!func.matches(GatfFunctionHandler.RANDOM_RANGE_REGEX))
			{
				throw new AssertionError("Invalid index function provided for dataprovider - " + name);
			}
			func = GatfFunctionHandler.handleFunction(func);
			try {
				index = Integer.parseInt(func);
			} catch (Exception e) {
			}
		}
		Assert.assertNotNull("Index out of range for Provider - " + name, provData.size()<=index);
		return provData.get(index).get(propertyName);
	}
	
	public String fx(String func) {
		return GatfFunctionHandler.handleFunction(func);
	}
}
