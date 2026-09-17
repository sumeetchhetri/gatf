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
package com.gatf.selenium.plugins;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gatf.executor.core.WorkflowContextHandler;
import com.jayway.jsonpath.JsonPath;

/**
 * @author Sumeet Chhetri<br/>
 *
 */
public class JsonPlugin {

    public static Object read(Object[] args) throws Exception {
        if(args.length>0 && args[0] instanceof String) {
            return WorkflowContextHandler.OM.readValue(args[0].toString(), Map.class);
        }
        return null;
    }
    
    /** A JSON document: an already-parsed Map/List/Set, or text that starts like JSON. */
    static boolean isJsonDoc(Object o) {
        if(o instanceof Map || o instanceof List || o instanceof Set) return true;
        if(!(o instanceof String)) return false;
        String s = ((String)o).trim();
        return s.startsWith("{") || s.startsWith("[");
    }

    public static Object write(Object[] args) throws Exception {
        // Q1: the documented order is `jsonwrite {file} {object}`; the original code read {object} {file}.
        // Accept both: if the first arg is a plain path string and the second is a document, swap them.
        if(args.length==5 && args[0] instanceof String && !isJsonDoc(args[0]) && isJsonDoc(args[1])) {
            Object t = args[0]; args[0] = args[1]; args[1] = t;
        }
        if(args.length==4) {
            if(args[0] instanceof List || args[0] instanceof Set || args[0] instanceof Map) {
                return WorkflowContextHandler.OM.writeValueAsString(args[0]);
            } else {
                return WorkflowContextHandler.OM.writeValueAsString(args[0]);
            }
        } else if(args.length==5 && args[1] instanceof String) {
            if(args[0] instanceof List || args[0] instanceof Set || args[0] instanceof Map) {
                WorkflowContextHandler.OM.writeValue(new File(args[1].toString()), args[0]);
            } else {
                WorkflowContextHandler.OM.writeValue(new File(args[1].toString()), args[0]);
            }
            return true;
        }
        return null;
    }
    
    public static Object path(Object[] args) throws Exception {
        if(args.length==5) {
            // Q1: the documented order is `jsonpath {json} {path}`, but the code used args[1] as the document and
            // args[0] as the path, and cast the document to String (a Map from jsonread -> ClassCastException).
            // Accept either order: the argument that is a JSON document is the document.
            Object doc = args[0], path = args[1];
            if(!isJsonDoc(doc) && isJsonDoc(path)) {
                doc = args[1]; path = args[0];
            }
            return doc instanceof String ? JsonPath.read((String)doc, path.toString()) : JsonPath.read(doc, path.toString());
        }
        return null;
    }
    
    public static String[] toSampleSelCmd() {
    	return new String[] {
    		"JSON Plugin",
    		"\tjsonread {json-text}",
    		"\tjsonwrite {optional-path-to-file} {jackson-annotated-json-object-or-map-list-set}",
    		"\tjsonpath {json-text} {json-path-string}",
    		"Examples :-",
    		"\tjsonread '{\"a\": \"abc\", \"b\": 1}'",
    		"\tjsonwrite '/path/to/file.json' $jsonObjectVar",
    		"\tjsonpath '{\"a\": \"abc\", \"b\": 1}' '$.a'",
        };
    }
}
