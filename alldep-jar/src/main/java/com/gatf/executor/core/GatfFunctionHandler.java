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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;

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
	private static final String NUMBER_REGEX = "^number(\\([\t ]*[0-9]+[\t ]*\\))*$";
	private static final String NUMBER_MINUS_REGEX = "^-number(\\([\t ]*[0-9]+[\t ]*\\))*$";
	private static final String NUMBER_PLUS_REGEX = "^\\+number(\\([\t ]*[0-9]+[\t ]*\\))*$";
	public static final String NUMBER_RANGE_REGEX = "^number\\([\t ]*([0-9]+)[\t ]*,[\t ]*([0-9]+)[\t ]*\\)$";
	private static final String ALPHANUM_REGEX = "^alphanum(\\([\t ]*[0-9]+[\t ]*\\))*$";
	private static final String ALPHA_REGEX = "^alpha(\\([\t ]*[0-9]+[\t ]*\\))*$";
	private static final String DECIMAL_REGEX = "^decimal(\\([\t ]*\\d+\\.?\\d+?[\t ]*\\))*$";
	private static final String DECIMAL_RANGE_REGEX = "^decimal\\([\t ]*(\\d+\\.?\\d+?)[\t ]*,[\t ]*(\\d+\\.?\\d+?)[\t ]*\\)$";
	private static final String BOOLEAN = "boolean";
	private static final String DT_FUNC_FMT_REGEX = "^date\\([\t ]*([0-9a-zA-Z\\-:/\\s'\\.]*)[\t ]+([-+])[\t ]+(\\d+)[\t ]*([y|M|d|h|m|s|S|T|Z])[\t ]*\\)$";
	private static final String DT_FMT_REGEX = "^date\\([\t ]*([0-9a-zA-Z\\-:/\\s'\\.]*)[\t ]*\\)$";
	
	static Pattern specialDatePattern = Pattern.compile(DT_FUNC_FMT_REGEX);
	static Pattern datePattern = Pattern.compile(DT_FMT_REGEX);
	static Pattern numberRangePattern = Pattern.compile(NUMBER_RANGE_REGEX);
	static Pattern decimalPattern = Pattern.compile(DECIMAL_REGEX);
	static Pattern decimaRangePattern = Pattern.compile(DECIMAL_RANGE_REGEX);
	static Pattern numberPattern = Pattern.compile(NUMBER_REGEX);
	static Pattern mnumberPattern = Pattern.compile(NUMBER_MINUS_REGEX);
	static Pattern pnumberPattern = Pattern.compile(NUMBER_PLUS_REGEX);
	static Pattern alphaPattern = Pattern.compile(ALPHA_REGEX);
	static Pattern alphanumericPattern = Pattern.compile(ALPHANUM_REGEX);

	public static String handleFunction(String function) {
		function = function.trim();
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
		} else if(function.matches(DECIMAL_REGEX)) {
			Matcher match = decimalPattern.matcher(function);
			match.matches();
			if(match.groupCount()==1) {
				double maxVal = Double.valueOf(match.group(1).substring(1, match.group(1).length()-1));
				return String.valueOf(RandomUtils.nextDouble(0, maxVal));
			} else {
				return String.valueOf(RandomUtils.nextFloat());
			}
		} else if(function.matches(DECIMAL_RANGE_REGEX)) {
			Matcher match = decimaRangePattern.matcher(function);
			match.matches();
			String min = match.group(1);
			String max = match.group(2);
			try {
				double nmin = Double.valueOf(min);
				double nmax = Double.valueOf(max);
				return String.valueOf(RandomUtils.nextDouble(nmin, nmax));
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else if(function.matches(ALPHA_REGEX)) {
			Matcher match = alphaPattern.matcher(function);
			match.matches();
			if(match.groupCount()==1) {
				int maxCount = Integer.valueOf(match.group(1).substring(1, match.group(1).length()-1));
				return RandomStringUtils.randomAlphabetic(maxCount);
			} else {
				return RandomStringUtils.randomAlphabetic(10);
			}
		} else if(function.matches(ALPHANUM_REGEX)) {
			Matcher match = alphanumericPattern.matcher(function);
			match.matches();
			if(match.groupCount()==1) {
				int maxCount = Integer.valueOf(match.group(1).substring(1, match.group(1).length()-1));
				return RandomStringUtils.randomAlphanumeric(maxCount);
			} else {
				return RandomStringUtils.randomAlphanumeric(10);
			}
		} else if(function.matches(NUMBER_PLUS_REGEX)) {
			Matcher match = pnumberPattern.matcher(function);
			match.matches();
			if(match.groupCount()==1) {
				long maxVal = Long.valueOf(match.group(1).substring(1, match.group(1).length()-1));
				return String.valueOf(RandomUtils.nextLong(0, maxVal));
			} else {
				return String.valueOf(RandomUtils.nextLong());
			}
		} else if(function.matches(NUMBER_MINUS_REGEX)) {
			Matcher match = mnumberPattern.matcher(function);
			match.matches();
			if(match.groupCount()==1) {
				long maxVal = Long.valueOf(match.group(1).substring(1, match.group(1).length()-1));
				return String.valueOf(-RandomUtils.nextLong(0, maxVal));
			} else {
				return String.valueOf(-RandomUtils.nextLong());
			}
		} else if(function.matches(NUMBER_REGEX)) {
			Matcher match = numberPattern.matcher(function);
			match.matches();
			if(match.groupCount()==1) {
				long maxVal = Long.valueOf(match.group(1).substring(1, match.group(1).length()-1));
				long randVal = RandomUtils.nextLong(0, maxVal);
				if(RandomUtils.nextBoolean()) {
					randVal = -randVal;
				}
				return String.valueOf(randVal);
			} else {
				long randVal = RandomUtils.nextLong();
				if(RandomUtils.nextBoolean()) {
					randVal = -randVal;
				}
				return String.valueOf(randVal);
			}
		} else if(function.matches(NUMBER_RANGE_REGEX)) {
			Matcher match = numberRangePattern.matcher(function);
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
	
	public static void executeCmd(List<String> inpBl, StringBuilder out, StringBuilder err, boolean withBash) {
		if(inpBl!=null && inpBl.size()>0) {
			ProcessBuilder processBuilder = new ProcessBuilder();
			
			try {
				if(withBash) {
					if(SystemUtils.IS_OS_WINDOWS) {
						processBuilder.command("cmd.exe", "/C", StringUtils.join(inpBl, " "));
					} else {
						processBuilder.command("bash", "-c", StringUtils.join(inpBl, " "));
					}
				} else {
					processBuilder.command(inpBl);
				}
				Process process = processBuilder.start();

				BufferedReader outreader = new BufferedReader(new InputStreamReader(process.getInputStream()));
				BufferedReader errreader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
				
				String line;
				while ((line = outreader.readLine()) != null) {
					if(out!=null) {
						out.append(line);
						out.append("\n");
					} else {
						System.out.println(line);
					}
				}
				while ((line = errreader.readLine()) != null) {
					if(err!=null) {
						err.append(line);
						err.append("\n");
					} else {
						System.out.println(line);
					}
				}

				process.waitFor();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
