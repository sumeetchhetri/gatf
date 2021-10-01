package com.gatf.executor.ext;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.core.MediaType;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.core.GatfFunctionHandler;
import com.gatf.executor.core.TestCase;
import com.gatf.executor.report.TestCaseReport;

public class WrkTestCaseExecutor {

	//Transfomed from https://github.com/MaartenSmeets/db_perftest/blob/master/test_scripts/wrk_parser.py
	
	static Pattern B1_PAT = Pattern.compile("^(\\d+\\.*\\d*)(\\w+)$");
	private static BigDecimal get_bytes(String size_str) {
		Matcher m = B1_PAT.matcher(size_str);
		if(m.matches()) {
			float size = Float.valueOf(m.group(1)).floatValue();
			String suffix = m.group(2);
			if(suffix.equals("b"))
		        return new BigDecimal(size).setScale(2);
		    else if(suffix.equals("kb") || suffix.equals("kib"))
		        return new BigDecimal(size * 1024).setScale(2);
		    else if(suffix.equals("mb") || suffix.equals("mib"))
		        return new BigDecimal(size * 1024 * 1024).setScale(2);
		    else if(suffix.equals("gb") || suffix.equals("gib"))
		        return new BigDecimal(size * 1024 * 1024 * 1024).setScale(2);
		    else if(suffix.equals("tb") || suffix.equals("tib"))
		        return new BigDecimal(size * 1024 * 1024 * 1024 * 1024).setScale(2);
		    else if(suffix.equals("pb") || suffix.equals("pib"))
		        return new BigDecimal(size * 1024 * 1024 * 1024 * 1024 * 1024).setScale(2);
		}
	    return BigDecimal.ZERO;
	}


	static Pattern B2_PAT = Pattern.compile("^(\\d+\\.*\\d*)(\\w*)$");
	private static BigDecimal get_number(String size_str) {
		Matcher m = B2_PAT.matcher(size_str);
		if(m.matches()) {
			float size = Float.valueOf(m.group(1)).floatValue();
			String suffix = m.group(2);
			if(suffix.equals("k"))
		        return new BigDecimal(size * 1000).setScale(2);
		    else if(suffix.equals("m"))
		        return new BigDecimal(size * 1000 * 1000).setScale(2);
		    else if(suffix.equals("g"))
		        return new BigDecimal(size * 1000 * 1000 * 1000).setScale(2);
		    else if(suffix.equals("t"))
		        return new BigDecimal(size * 1000 * 1000 * 1000 * 1000).setScale(2);
		    else if(suffix.equals("p"))
		        return new BigDecimal(size * 1000 * 1000 * 1000 * 1000 * 1000).setScale(2);
		    else
		        return new BigDecimal(size).setScale(2);
		}
	    return BigDecimal.ZERO;
	}

	private static BigDecimal get_ms(String size_str) {
		Matcher m = B2_PAT.matcher(size_str);
		if(m.matches()) {
			float size = Float.valueOf(m.group(1)).floatValue();
			String suffix = m.group(2);
			if(suffix.equals("us"))
		        return new BigDecimal(size / 1000).setScale(2);
		    else if(suffix.equals("ms"))
		        return new BigDecimal(size).setScale(2);
		    else if(suffix.equals("s"))
		        return new BigDecimal(size * 1000).setScale(2);
		    else if(suffix.equals("m"))
		        return new BigDecimal(size * 1000 * 60).setScale(2);
		    else if(suffix.equals("h"))
		        return new BigDecimal(size * 1000 * 60 * 60).setScale(2);
		    else
		        return new BigDecimal(size).setScale(2);
		}
	    return BigDecimal.ZERO;
	}
	
	static Pattern LAT_PAT = Pattern.compile("^\\s+Latency\\s+(\\d+\\.\\d+\\w*)\\s+(\\d+\\.\\d+\\w*)\\s+(\\d+\\.\\d+\\w*).*$");
	static Pattern RQS_PAT = Pattern.compile("^\\s+Req/Sec\\s+(\\d+\\.\\d+\\w*)\\s+(\\d+\\.\\d+\\w*)\\s+(\\d+\\.\\d+\\w*).*$");
	static Pattern RQI_PAT = Pattern.compile("^\\s+(\\d+)\\ requests in (\\d+\\.\\d+\\w*)\\,\\ (\\d+\\.\\d+\\w*)\\ read.*$");
	static Pattern FRQS_PAT = Pattern.compile("^Requests\\/sec\\:\\s+(\\d+\\.*\\d*).*$");
	static Pattern TRFS_PAT = Pattern.compile("^Transfer\\/sec\\:\\s+(\\d+\\.*\\d*\\w+).*$");
	static Pattern SCE_PAT = Pattern.compile("^\\s+Socket errors:\\ connect (\\d+\\w*)\\,\\ read (\\d+\\w*)\\,\\ write\\ (\\d+\\w*)\\,\\ timeout\\ (\\d+\\w*).*$");
	static Pattern LPER_PAT = Pattern.compile("^(50|75|90|99)%\\s+(\\d+\\.\\d+\\w*)$");
	static Pattern LPER_PAT2 = Pattern.compile("^(\\d+\\.\\d+)%\\s+(\\d+\\.\\d+\\w*)$");
	static Pattern HDR_PAT2 = Pattern.compile("^(\\d+\\.\\d+)\\s+(\\d+\\.\\d+)\\s+(\\d+)\\s+(\\d+\\.\\d+|inf)$");
	@SuppressWarnings("unchecked")
	private static Map<String, Object> parse_wrk_output(StringBuilder out) {
		Map<String, Object> retval = new HashMap<String, Object>();
		String[] lines = out.toString().split("\\r?\\n");
		boolean latstarted = false;
		int hdrstarted = 0;
		for (String line : lines) {
			line = line.trim();
			if(line.startsWith("Latency Distribution")) {
				latstarted = true;
				continue;
			} else if(line.startsWith("Non-2xx or 3xx responses: ")) {
				retval.put("non_2xx_resp", Long.valueOf(line.substring(line.lastIndexOf(" ")+1)));
				continue;
			} else if(line.startsWith("Detailed Percentile spectrum:")) {
				hdrstarted++;
				continue;
			} else if(hdrstarted>0) {
				if(hdrstarted==3) {
					boolean matched = false;
					Matcher m = HDR_PAT2.matcher(line);
					if(m.matches()) {
						matched = true;
						List<String[]> rows = (List<String[]>)retval.get("dps_details");
						if(rows==null) {
							rows = new ArrayList<String[]>();
							retval.put("dps_details", rows);
						}
						rows.add(new String[] {m.group(1), m.group(2), m.group(3), m.group(4)});
						
					}
					if(matched) {
						continue;
					} else {
						hdrstarted = 5;
					}
				} else {
					hdrstarted++;
					continue;
				}
			} else if(latstarted) {
				boolean matched = false;
				Matcher m = LPER_PAT.matcher(line);
				if(m.matches()) {
					matched = true;
					retval.put("lat_dis_"+m.group(1), get_ms(m.group(2)));
				} else {
					m = LPER_PAT2.matcher(line);
					if(m.matches()) {
						int val = (int)Float.valueOf(m.group(1)).floatValue();
						retval.put("lat_dis_"+val, get_ms(m.group(2)));
						matched = true;
					}
				}
				if(matched) {
					continue;
				} else {
					latstarted = false;
				}
			}
			
			Matcher m = LAT_PAT.matcher(line);
			if(m.matches()) {
				retval.put("lat_avg", get_ms(m.group(1)));
	            retval.put("lat_stdev", get_ms(m.group(2)));
	            retval.put("lat_max", get_ms(m.group(3)));
			} else {
				m = RQS_PAT.matcher(line);
				if(m.matches()) {
					retval.put("req_avg", get_number(m.group(1)));
		            retval.put("req_stdev", get_number(m.group(2)));
		            retval.put("req_max", get_number(m.group(3)));
				} else {
					m = RQI_PAT.matcher(line);
					if(m.matches()) {
						retval.put("tot_requests", get_number(m.group(1)));
			            retval.put("tot_duration", get_ms(m.group(2)));
			            retval.put("read", get_bytes(m.group(3)));
					} else {
						m = FRQS_PAT.matcher(line);
						if(m.matches()) {
							retval.put("req_sec_tot", get_number(m.group(1)));
						} else {
							m = TRFS_PAT.matcher(line);
							if(m.matches()) {
								retval.put("read_tot", get_bytes(m.group(1)));
							} else {
								m = SCE_PAT.matcher(line);
								if(m.matches()) {
									retval.put("err_connect", get_number(m.group(1)));
						            retval.put("err_read", get_number(m.group(2)));
						            retval.put("err_write", get_number(m.group(3)));
						            retval.put("err_timeout", get_number(m.group(4)));
								}
							}
						}
					}
				}
			}
		}
	    if(!retval.containsKey("err_connect"))
	        retval.put("err_connect", 0);
	    if(!retval.containsKey("err_read"))
	        retval.put("err_read", 0);
	    if(!retval.containsKey("err_write"))
	        retval.put("err_write", 0);
	    if(!retval.containsKey("err_timeout"))
	        retval.put("err_timeout", 0);
	    
	    return retval;
	}

	public static void execute(AcceptanceTestContext context, TestCase tc, TestCaseReport tcr) throws Exception {
		GatfExecutorConfig config = context.getGatfExecutorConfig();

		if(tc.getPerfConfig()!=null && tc.getPerfConfig().getType()!=null && tc.getPerfConfig().getType().startsWith("wrk")) {
			List<String> builderList = new ArrayList<>();
			builderList.add(tc.getPerfConfig().getType().equals("wrk")?config.getWrkPath():config.getWrk2Path());
			
			Integer c = tc.getPerfConfig().getConnections();
			if(c<=0) {
				c = 10; 
			}
			builderList.add("-c");
			builderList.add(c.toString());
			
			Integer r = tc.getPerfConfig().getRate();
			if(r>=0 && tc.getPerfConfig().getType().equals("wrk2")) {
				builderList.add("-R");
				builderList.add(c.toString());
			}
			
			Integer d = tc.getPerfConfig().getDurationSeconds();
			if(d<=0) {
				d = 10;
			}
			builderList.add("-d");
			builderList.add(d+"s");
			
			Integer t = tc.getPerfConfig().getThreads();
			if(t<=0) {
				t = 10;
			}
			builderList.add("-t");
			builderList.add(t.toString());
			
			Integer to = tc.getPerfConfig().getTimeout();
			if(to<=0) {
				to = 10;
			}
			builderList.add("--timeout ");
			builderList.add(to+"s");
			
			if(tc.getPerfConfig().isLatency()) {
				builderList.add("--latency");
			}
			
			if(tc.getPerfConfig().getType().equals("wrk2")) {
				builderList.add("--u_latency");
			}
			
			File script = null;
			if(!tc.getMethod().equalsIgnoreCase("GET")) {
				StringBuilder prog = new StringBuilder();
				prog.append("wrk.method = \"");
				prog.append(tc.getMethod().toUpperCase());
				prog.append("\"\n");
				for (String hdr : tc.getHeaders().keySet()) {
					prog.append("wrk.headers[\""+hdr+"] = \"");
					prog.append(tc.getHeaders().get(hdr));
					prog.append("\"\n");
				}
				prog.append("wrk.body = [====================[\n");
				prog.append(tc.getContent());
				prog.append("\n]====================]\n");
				
				script = File.createTempFile(UUID.randomUUID().toString(), ".lua");
				FileUtils.write(script, prog.toString(), "UTF-8");
				
				builderList.add("-s ");
				builderList.add(script.getAbsolutePath());
			}
			
			StringBuilder out = new StringBuilder();
			StringBuilder err = new StringBuilder();
			GatfFunctionHandler.executeCmd(builderList, out, err);
			if(script!=null) {
				script.delete();
			}

			if(out.length()>0) {
				Map<String, Object> retval = parse_wrk_output(out);
				tcr.setPerfResult(retval);
				out.insert(0, StringUtils.join(" ", builderList)+"\n");
				tcr.setResponseContent(out.toString());
				tcr.setResponseContentType(MediaType.TEXT_PLAIN);
			}
 			if(err.length()>0) {
 				tcr.setErrorText(StringUtils.join(" ", builderList)+"\n"+err.toString());
			}
 			
 			if(tcr.getTestCase()==null) {
 				tcr.setTestCase(tc);
 			}
		}
	}
}
