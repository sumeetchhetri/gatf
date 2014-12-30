package com.gatf.executor.core;

/*
Copyright 2013-2014, Sumeet Chhetri

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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.RandomStringUtils;

/**
 * @author Sumeet Chhetri
 *
 */
public class GatfFunctionHandler {
	
	private static final String MINUS = "-";
	private static final String MILLISECOND = "S";
	private static final String SECOND = "s";
	private static final String MINUITE = "m";
	private static final String HOUR = "h";
	private static final String DAY = "d";
	private static final String MONTH = "M";
	private static final String YEAR = "y";
	private static final String NUMBER = "number";
	private static final String NUMBER_MINUS = "-number";
	private static final String NUMBER_PLUS = "+number";
	private static final String ALPHANUM = "alphanum";
	private static final String ALPHA = "alpha";
	private static final String FLOAT = "float";
	private static final String BOOLEAN = "boolean";
	static final String DT_FUNC_FMT_REGEX = "^date\\(([a-zA-Z\\-:/\\s']*) ([-+]) (\\d+)([y|M|d|h|m|s|S|])\\)$";
	static final String DT_FMT_REGEX = "^date\\(([a-zA-Z\\-:/\\s']*)\\)";
	public static final String RANDOM_RANGE_REGEX = "^number\\(([0-9]*)[\t ]*,[\t ]*([0-9]*)\\)$";
	
	static Pattern specialDatePattern = Pattern.compile(DT_FUNC_FMT_REGEX);
	static Pattern datePattern = Pattern.compile(DT_FMT_REGEX);
	static Pattern randRangeNum = Pattern.compile(RANDOM_RANGE_REGEX);

	public static String handleFunction(String function) {
		if(function.equals(BOOLEAN)) {
			Random rand = new Random();
			return String.valueOf(rand.nextBoolean());
		} else if(function.matches(DT_FMT_REGEX)) {
			Matcher match = datePattern.matcher(function);
			match.matches();
			SimpleDateFormat format = new SimpleDateFormat(match.group(1));
			return format.format(new Date());
		} else if(function.matches(DT_FUNC_FMT_REGEX)) {
			Matcher match = specialDatePattern.matcher(function);
			match.matches();
			String formatStr = match.group(1);
			String operation = match.group(2);
			int value = Integer.valueOf(match.group(3));
			String unit = match.group(4);
			SimpleDateFormat format = new SimpleDateFormat(formatStr);
			try {
				Date dt = format.parse(format.format(new Date()));
				Calendar cal = Calendar.getInstance();
				cal.setTime(dt);
				
				value = (operation.equals(MINUS)?-value:value);
				if(unit.equals(YEAR)) {
					cal.add(Calendar.YEAR, value);
				} else if(unit.equals(MONTH)) {
					cal.add(Calendar.MONTH, value);
				} else if(unit.equals(DAY)) {
					cal.add(Calendar.DAY_OF_YEAR, value);
				} else if(unit.equals(HOUR)) {
					cal.add(Calendar.HOUR_OF_DAY, value);
				} else if(unit.equals(MINUITE)) {
					cal.add(Calendar.MINUTE, value);
				} else if(unit.equals(SECOND)) {
					cal.add(Calendar.SECOND, value);
				} else if(unit.equals(MILLISECOND)) {
					cal.add(Calendar.MILLISECOND, value);
				}
				return format.format(cal.getTime());
			} catch (Exception e) {
				throw new AssertionError("Invalid date format specified - " + formatStr);
			}
		} else if(function.equals(FLOAT)) {
			Random rand = new Random(12345678L);
			return String.valueOf(rand.nextFloat());
		} else if(function.equals(ALPHA)) {
			return RandomStringUtils.randomAlphabetic(10);
		} else if(function.equals(ALPHANUM)) {
			return RandomStringUtils.randomAlphanumeric(10);
		} else if(function.equals(NUMBER_PLUS)) {
			Random rand = new Random();
			return String.valueOf(rand.nextInt(1234567));
		} else if(function.equals(NUMBER_MINUS)) {
			Random rand = new Random();
			return String.valueOf(-rand.nextInt(1234567));
		} else if(function.equals(NUMBER)) {
			Random rand = new Random();
			boolean bool = rand.nextBoolean();
			return bool?String.valueOf(rand.nextInt(1234567)):String.valueOf(-rand.nextInt(1234567));
		} else if(function.matches(RANDOM_RANGE_REGEX)) {
			Matcher match = randRangeNum.matcher(function);
			match.matches();
			String min = match.group(1);
			String max = match.group(2);
			try {
				int nmin = Integer.parseInt(min);
				int nmax = Integer.parseInt(max);
				return String.valueOf(nmin + (int)(Math.random() * ((nmax - nmin) + 1)));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return null;
	}
}
