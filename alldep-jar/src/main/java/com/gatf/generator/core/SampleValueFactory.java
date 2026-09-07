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
package com.gatf.generator.core;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.Logger;

/**
 * G8 — sample-object synthesis for generated REST test cases (reflection over request/response types:
 * nested beans, collections, maps, primitives), extracted verbatim from GatfTestGeneratorUtil.
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
class SampleValueFactory {

	private final Logger logger;
	private final boolean debugEnabled;

	SampleValueFactory(Logger logger, boolean debugEnabled) {
		this.logger = logger;
		this.debugEnabled = debugEnabled;
	}

	private boolean isDebugEnabled() {
		return debugEnabled;
	}

    ViewField getViewField(Type claz) throws Exception
    {
    	ViewField viewField = null;
    	try 
    	{
    		ParameterizedType type = null;
    		Class clas = null;
    		if(claz instanceof ParameterizedType)
    		{
    			type = (ParameterizedType)claz;
    			clas = (Class)type.getRawType();
    		}
    		else
    		{
    			clas = (Class)claz;
    		}
    		
    		List<Type> heirarchies = new ArrayList<Type>();
	        if (isPrimitive(clas))
	        {
	            viewField = new ViewField();
	            viewField.setClaz(clas);
	            viewField.setValue(getPrimitiveValue(claz));
	        }
	        else if (clas.getName().equals("org.apache.cxf.jaxrs.ext.multipart.Attachment")
	        		|| clas.getName().equals("org.apache.cxf.message.Attachment")
	        		|| clas.getName().equals("org.apache.cxf.jaxrs.ext.multipart.MultipartBody")
	        		|| clas.getName().equals("javax.ws.rs.core.MultivaluedMap")
	        		|| clas.getName().equals("javax.ws.rs.core.MultivaluedHashMap")
	        		|| 
	        		(isCollection(clas) && clas.getTypeParameters().length>0 
	        				&& clas.getTypeParameters()[0].toString().equals("class org.apache.cxf.jaxrs.ext.multipart.Attachment"))
	        		||
	        		(isCollection(clas) && clas.getTypeParameters().length>0 
	        				&& clas.getTypeParameters()[0].toString().equals("class org.apache.cxf.message.Attachment")))
	        {
	            return null;
	        }
	        else if (isMap(clas))
	        {
	        	viewField = new ViewField();
	            viewField.setClaz(clas);
	            viewField.setValue(getMapValue(clas, type.getActualTypeArguments(), heirarchies));
	        }
	        else if (isCollection(clas))
	        {
	        	viewField = new ViewField();
	            viewField.setClaz(clas);
	            viewField.setValue(getListSetValue(clas, type.getActualTypeArguments(), heirarchies));
	        }
	        else if(!clas.isInterface())
        	{
	            viewField = new ViewField();
	            viewField.setClaz(clas);
	            viewField.setValue(getObject(clas, heirarchies));
        	}
    	} catch (Exception e) {
    		logger.error(ExceptionUtils.getStackTrace(e));
    		logger.info("Invalid class, cannot be represented as a form/object in a test case - class name = " + claz);
    	}
        return viewField;
    }
    
    @SuppressWarnings("rawtypes")
    private List<Field> getAllFields(Class claz) {
    	List<Field> allFields = new ArrayList<Field>();
    	allFields.addAll(Arrays.asList(claz.getFields()));
    	allFields.addAll(Arrays.asList(claz.getDeclaredFields()));
    	
    	if(!Object.class.equals(claz.getSuperclass())) {
    		allFields.addAll(getAllFields(claz.getSuperclass()));
    	}
    	return allFields;
    }
    
    private String getHeirarchyStr(List<Type> fheirlst) {
    	StringBuilder b = new StringBuilder();
    	if(!fheirlst.isEmpty()) {
    		for (Type type : fheirlst) {
				b.append(type.toString()+".");
			}
    	}
    	return b.toString();
    }
    
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Object getObject(Class claz, List<Type> heirarchies) throws Exception {

    	if(claz.isEnum())return claz.getEnumConstants()[0];
    	
    	if (isMap(claz))
        {
    		return getMapValue(claz, claz.getTypeParameters(), heirarchies);
        }
        else if (isCollection(claz))
        {
        	return getListSetValue(claz, claz.getTypeParameters(), heirarchies);
        }
        else if(claz.isInterface() || Modifier.isAbstract(claz.getModifiers())) 
        {
        	return null;
        }
    	
    	if(heirarchies.contains(claz) || heirarchies.size()>=2)return null;
    	heirarchies.add(claz);
    	
    	Constructor cons = null;
    	try {
    		cons = claz.getConstructor(new Class[]{});
		} catch (Exception e) {
			if(isPrimitive(claz)) {
				return getPrimitiveValue(claz);
			}
			logger.error("No public no-args constructor found for class " + claz.getName());
			return null;
		}
    	
    	Object object = cons.newInstance(new Object[]{});
    	if(claz.equals(Object.class)) return null;
    	List<Field> allFields = getAllFields(claz);
    	
    	for (Field field : allFields) {
    		
    		if (Modifier.isStatic(field.getModifiers()))
    			continue;
    			
			//if(!field.isAccessible())
			{
				field.setAccessible(true);
			}
    		
			List<Type> fheirlst = new ArrayList<Type>(heirarchies);
			
			if(isDebugEnabled())
				logger.info("Parsing Class " + getHeirarchyStr(fheirlst) + " field " + field.getName() + " type " + field.getType().equals(boolean.class));
            
			if (isPrimitive(field.getType()))
            {
                field.set(object, getPrimitiveValue(field.getType()));
            }
            else if (isMap(field.getType()) && field.getGenericType() instanceof ParameterizedType)
            {
            	ParameterizedType type = (ParameterizedType)field.getGenericType();
            	field.set(object, getMapValue(field.getType(), type.getActualTypeArguments(), fheirlst));
            }
            else if (isCollection(field.getType()) && field.getGenericType() instanceof ParameterizedType)
            {
            	ParameterizedType type = (ParameterizedType)field.getGenericType();
            	field.set(object, getListSetValue(field.getType(), type.getActualTypeArguments(), fheirlst));
            }
            else if (!claz.equals(field.getType()))
            {
            	Object fieldval = getObject(field.getType(), fheirlst);
            	field.set(object, fieldval);
            }
            else if (claz.equals(field.getType()))
            {
            	if(isDebugEnabled())
            		logger.info("Ignoring recursive fields...");
            }
		}
        return object;
    }
    
    @SuppressWarnings("rawtypes")
	private Object getObject(Type claz, List<Type> heirarchies) throws Exception {
    	
    	ParameterizedType type = null;
		Class clas = null;
		if(claz instanceof Class)
		{
			clas = (Class)claz;
		}
		else if(claz instanceof ParameterizedType)
		{
			type = (ParameterizedType)claz;
			clas = (Class)type.getRawType();
		}
		else return null;
		if (isPrimitive(clas))
        {
            return getPrimitiveValue(clas);
        }
        else if (isMap(clas))
        {
        	return getMapValue(clas, type.getActualTypeArguments(), heirarchies);
        }
        else if (isCollection(clas))
        {
        	return getListSetValue(clas, type.getActualTypeArguments(), heirarchies);
        }
        else if(!clas.isInterface())
    	{
            return getObject(clas, heirarchies);
    	}
    	return null;
    }
    
    Object getPrimitiveValue(Type claz) {
    	 if (isPrimitive(claz))
    	 {
    		 if(claz.equals(boolean.class) || claz.equals(Boolean.class)) {
    			 Random rand = new Random();
    			 return rand.nextBoolean();
    		 } else if(claz.equals(Date.class)) {
    			 return new Date();
    		 } else if(claz.equals(java.sql.Date.class)) {
    			 return new java.sql.Date(new Date().getTime());
    		 } else if(claz.equals(java.sql.Time.class)) {
    			 return new java.sql.Time(new Date().getTime());
    		 } else if(claz.equals(java.sql.Timestamp.class)) {
    			 return new java.sql.Timestamp(new Date().getTime());
    		 } else if(claz.equals(Double.class) || claz.equals(double.class)) {
    			 Random rand = new Random(12345678L);
    			 return rand.nextDouble();
    		 } else if(claz.equals(Float.class) || claz.equals(float.class)) {
    			 Random rand = new Random(12345678L);
    			 return rand.nextFloat();
    		 } else if(claz.equals(String.class)) {
    			 return RandomStringUtils.insecure().nextAlphabetic(10);
    		 } else if(claz.equals(Long.class) || claz.equals(long.class) || claz.equals(Number.class)) {
    			 Random rand = new Random();
    			 return Long.valueOf(rand.nextInt(123));
    		 } else if(claz.equals(Integer.class) || claz.equals(int.class)) {
    			 Random rand = new Random();
    			 return Integer.valueOf(rand.nextInt(123));
    		 } else if(claz.equals(BigInteger.class)) {
    			 Random rand = new Random();
    			 return new BigInteger(new BigInteger("1234567890123456789").bitLength(), rand);
    		 } else if(claz.equals(BigDecimal.class)) {
    			 Random rand = new Random();
    			 return new BigDecimal(rand.nextInt(123));
    		 } else if(claz.equals(Short.class) || claz.equals(short.class)) {
    			 Random rand = new Random();
    			 return Short.valueOf((short)rand.nextInt(123));
    		 }
    	 }
    	 return null;
    }
    
    private boolean isPrimitive(Type claz) {
    	return (claz.equals(Integer.class) || claz.equals(String.class) || claz.equals(Short.class)
                || claz.equals(Long.class) || claz.equals(Double.class) || claz.equals(Float.class)
                || claz.equals(Boolean.class) || claz.equals(int.class) || claz.equals(short.class)
                || claz.equals(long.class) || claz.equals(double.class) || claz.equals(float.class)
                || claz.equals(boolean.class) || claz.equals(Number.class) || claz.equals(Date.class)
                || claz.equals(BigInteger.class) || claz.equals(BigDecimal.class)
                || claz.equals(java.sql.Date.class) || claz.equals(java.sql.Timestamp.class)
                || claz.equals(java.sql.Time.class));
    }
    
    private boolean isCollection(Type claz) {
    	return (claz.equals(List.class) || claz.equals(ArrayList.class)
                || claz.equals(LinkedList.class) || claz.equals(Set.class) 
                || claz.equals(HashSet.class) || claz.equals(LinkedHashSet.class)
                || claz.equals(Collection.class));
    }
    
    private boolean isMap(Type claz) {
    	return (claz.equals(Map.class) || claz.equals(HashMap.class)
                || claz.equals(LinkedHashMap.class) || claz.equals(TreeMap.class));
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
	private Object getMapValue(Type type, Type[] types, List<Type> heirarchies) throws Exception {
    	
    	Class claz = null;
    	if(type.equals(Map.class) || type.equals(HashMap.class)
                || type.equals(LinkedHashMap.class))
    	{
    		if(type.equals(Map.class))
    		{
    			claz = HashMap.class;
    		}
    		else
    		{
    			claz = (Class)type;
    		}
    	}
    	
    	Constructor cons = claz.getConstructor(new Class[]{});
    	Object object = cons.newInstance(new Object[]{});
    	
    	for (int i = 0; i < 1; i++)
    	{
	    	Object k = null;
	    	if(isPrimitive(types[0])) {
	    		k = getPrimitiveValue(types[0]);
	    	} else if(!heirarchies.contains(types[0])) {
	    		if(types[0] instanceof Class)
	    			k = getObject((Class)types[0], heirarchies);
	    		else
	    			k = getObject(types[0], heirarchies);
	    		heirarchies.remove(types[0]);
	    	}
	    	Object v = null;
	    	if(isPrimitive(types[1])) {
	    		v = getPrimitiveValue(types[1]);
	    	} else if(!heirarchies.contains(types[1])) {
	    		if(types[1] instanceof Class)
	    			v = getObject((Class)types[1], heirarchies);
	    		else
	    			v = getObject(types[1], heirarchies);
	    		heirarchies.remove(types[1]);
	    	}
	    	if(k==null && isDebugEnabled()) {
	    		logger.info(types[0].toString());
	    		logger.info(types[1].toString());
	    		logger.error("Null key " + types[0]);
	    	}
	    	if(k!=null) {
	    	    ((Map)object).put(k, v);
	    	}
    	}
    	if(!isPrimitive(types[0]))
    	{
    		heirarchies.add(types[0]);
    	}
    	if(!isPrimitive(types[1]))
    	{
    		heirarchies.add(types[1]);
    	}
    	return object;
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
	private Object getListSetValue(Type type, Type[] types, List<Type> heirarchies) throws Exception {
    	
    	Class claz = null;
    	if(type.equals(List.class) || type.equals(Collection.class)
    			|| type.equals(ArrayList.class) || type.equals(LinkedList.class))
    	{
    		if(type.equals(List.class) || type.equals(Collection.class))
    		{
    			claz = ArrayList.class;
    		}
    		else
    		{
    			claz = (Class)type;
    		}
    	}
    	else if(type.equals(Set.class) || type.equals(HashSet.class) || type.equals(LinkedHashSet.class))
    	{
    		if(type.equals(Set.class))
    		{
    			claz = HashSet.class;
    		}
    		else
    		{
    			claz = (Class)type;
    		}
    	}
    	
    	Constructor cons = claz.getConstructor(new Class[]{});
    	Object object = cons.newInstance(new Object[]{});
    	
        if (types.length == 1)
        {
        	for (int i = 0; i < 1; i++) {
	        	Object v = null;
	        	if(isPrimitive(types[0])) {
	        		v = getPrimitiveValue(types[0]);
	        	} else if(!heirarchies.contains(types[0])) {
		    		if(types[0] instanceof Class)
		    			v = getObject((Class)types[0], heirarchies);
		    		else
		    			v = getObject(types[0], heirarchies);
		    		heirarchies.remove(types[0]);
		    	}
	        	((Collection)object).add(v);
			}
        	if(!isPrimitive(types[0]))
        	{
        		heirarchies.add(types[0]);
        	}
        }
    	return object;
    }
    

}
