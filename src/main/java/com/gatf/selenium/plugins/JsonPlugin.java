package com.gatf.selenium.plugins;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

/**
 * @author Sumeet Chhetri<br/>
 *
 */
public class JsonPlugin {

    public static Object read(Object[] args) throws Exception {
        if(args.length>0 && args[0] instanceof String) {
            return new ObjectMapper().readTree(args[0].toString());
        }
        return null;
    }
    
    public static Object write(Object[] args) throws Exception {
        if(args.length==4) {
            if(args[0] instanceof List || args[0] instanceof Set || args[0] instanceof Map) {
                return new ObjectMapper().writeValueAsString(args[0]);
            } else {
                return new ObjectMapper().writeValueAsString(args[0]);
            }
        } else if(args.length==5 && args[1] instanceof String) {
            if(args[0] instanceof List || args[0] instanceof Set || args[0] instanceof Map) {
                new ObjectMapper().writeValue(new File(args[1].toString()), args[0]);
            } else {
                new ObjectMapper().writeValue(new File(args[1].toString()), args[0]);
            }
            return true;
        }
        return null;
    }
    
    public static Object path(Object[] args) throws Exception {
        if(args.length==5) {
            return JsonPath.read(args[1].toString(), (String)args[0]);
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
