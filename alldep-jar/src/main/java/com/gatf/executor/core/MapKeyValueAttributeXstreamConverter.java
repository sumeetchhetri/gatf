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
package com.gatf.executor.core;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map.Entry;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

/**
 * @author Sumeet Chhetri
 * 
 */
public class MapKeyValueAttributeXstreamConverter implements Converter
{
    @SuppressWarnings("rawtypes")
	public boolean canConvert(final Class clazz)
    {
        return AbstractMap.class.isAssignableFrom(clazz);
    }

    @SuppressWarnings("unchecked")
	public void marshal(final Object value, final HierarchicalStreamWriter writer, final MarshallingContext context)
    {
        final AbstractMap<String, String> map = (AbstractMap<String, String>) value;
        for (final Entry<String, String> entry : map.entrySet())
        {
            writer.startNode("property");
            writer.addAttribute("key", entry.getKey());
            writer.setValue(entry.getValue().toString());
            writer.endNode();
        }
    }

    public Object unmarshal(final HierarchicalStreamReader reader, final UnmarshallingContext context)
    {
        final AbstractMap<String, String> map = new HashMap<String, String>();
        while (reader.hasMoreChildren())
        {
            reader.moveDown();
            if(reader.getNodeName().equals("property")) {
            	map.put(reader.getAttribute("key"), reader.getValue());
            }
            reader.moveUp();
        }
        return map;
    }
}