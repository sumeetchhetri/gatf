package com.gatf.view;


/**
 * @author Sumeet Chhetri<br/>
 * The Form element class provides data for generating actual form fields
 */
@SuppressWarnings("rawtypes")
public class ViewField {
	
	private String name;
	
	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param name the name to set
	 */
	public void setName(String name) {
		this.name = name;
	}

	private Class claz;
	
	private Object value;

	/**
	 * @return the claz
	 */
	public Class getClaz() {
		return claz;
	}

	/**
	 * @param claz the claz to set
	 */
	public void setClaz(Class claz) {
		this.claz = claz;
	}

	public Object getValue() {
		return value;
	}

	public void setValue(Object value) {
		this.value = value;
	}
}
