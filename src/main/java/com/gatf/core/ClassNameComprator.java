package com.gatf.core;

import java.util.Comparator;

/**
 * @author Sumeet Chhetri<br/>
 * The Comparator class to compare class names with packages 
 */
public class ClassNameComprator implements Comparator<String> {

	public int compare(String clas1, String clas2) {
		if(clas1!=null && clas1.indexOf(".")!=-1)
			clas1 = clas1.substring(clas1.lastIndexOf(".")+1);
		if(clas2!=null && clas2.indexOf(".")!=-1)
			clas2 = clas2.substring(clas2.lastIndexOf(".")+1);
		return clas1.compareTo(clas2);
	}
}
