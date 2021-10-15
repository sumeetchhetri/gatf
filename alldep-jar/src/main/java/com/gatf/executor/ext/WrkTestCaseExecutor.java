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
import com.gatf.executor.report.TestCaseReport.TestStatus;

public class WrkTestCaseExecutor {

	//Transfomed from https://github.com/MaartenSmeets/db_perftest/blob/master/test_scripts/wrk_parser.py
	
	static Pattern B1_PAT = Pattern.compile("^(\\d+\\.*\\d*)(\\w+)$");
	private static BigDecimal get_bytes(String size_str) {
		Matcher m = B1_PAT.matcher(size_str);
		if(m.matches()) {
			float size = Float.valueOf(m.group(1)).floatValue();
			String suffix = m.group(2).toLowerCase();
			if(suffix.equals("b"))
		        return new BigDecimal(size).setScale(2, BigDecimal.ROUND_HALF_DOWN);
		    else if(suffix.equals("kb") || suffix.equals("kib"))
		        return new BigDecimal(size * 1024).setScale(2, BigDecimal.ROUND_HALF_DOWN);
		    else if(suffix.equals("mb") || suffix.equals("mib"))
		        return new BigDecimal(size * 1024 * 1024).setScale(2, BigDecimal.ROUND_HALF_DOWN);
		    else if(suffix.equals("gb") || suffix.equals("gib"))
		        return new BigDecimal(size * 1024 * 1024 * 1024).setScale(2, BigDecimal.ROUND_HALF_DOWN);
		    else if(suffix.equals("tb") || suffix.equals("tib"))
		        return new BigDecimal(size * 1024 * 1024 * 1024 * 1024).setScale(2, BigDecimal.ROUND_HALF_DOWN);
		    else if(suffix.equals("pb") || suffix.equals("pib"))
		        return new BigDecimal(size * 1024 * 1024 * 1024 * 1024 * 1024).setScale(2, BigDecimal.ROUND_HALF_DOWN);
		}
	    return BigDecimal.ZERO;
	}


	static Pattern B2_PAT = Pattern.compile("^(\\d+\\.*\\d*)(\\w*)$");
	private static BigDecimal get_number(String size_str) {
		Matcher m = B2_PAT.matcher(size_str);
		if(m.matches()) {
			float size = Float.valueOf(m.group(1)).floatValue();
			String suffix = m.group(2).toLowerCase();
			if(suffix.equals("k"))
		        return new BigDecimal(size * 1000).setScale(2, BigDecimal.ROUND_HALF_DOWN);
		    else if(suffix.equals("m"))
		        return new BigDecimal(size * 1000 * 1000).setScale(2, BigDecimal.ROUND_HALF_DOWN);
		    else if(suffix.equals("g"))
		        return new BigDecimal(size * 1000 * 1000 * 1000).setScale(2, BigDecimal.ROUND_HALF_DOWN);
		    else if(suffix.equals("t"))
		        return new BigDecimal(size * 1000 * 1000 * 1000 * 1000).setScale(2, BigDecimal.ROUND_HALF_DOWN);
		    else if(suffix.equals("p"))
		        return new BigDecimal(size * 1000 * 1000 * 1000 * 1000 * 1000).setScale(2, BigDecimal.ROUND_HALF_DOWN);
		    else
		        return new BigDecimal(size).setScale(2, BigDecimal.ROUND_HALF_DOWN);
		}
	    return BigDecimal.ZERO;
	}

	private static BigDecimal get_ms(String size_str) {
		Matcher m = B2_PAT.matcher(size_str);
		if(m.matches()) {
			float size = Float.valueOf(m.group(1)).floatValue();
			String suffix = m.group(2).toLowerCase();
			if(suffix.equals("us"))
		        return new BigDecimal(size / 1000).setScale(2, BigDecimal.ROUND_HALF_DOWN);
		    else if(suffix.equals("ms"))
		        return new BigDecimal(size).setScale(2, BigDecimal.ROUND_HALF_DOWN);
		    else if(suffix.equals("s"))
		        return new BigDecimal(size * 1000).setScale(2, BigDecimal.ROUND_HALF_DOWN);
		    else if(suffix.equals("m"))
		        return new BigDecimal(size * 1000 * 60).setScale(2, BigDecimal.ROUND_HALF_DOWN);
		    else if(suffix.equals("h"))
		        return new BigDecimal(size * 1000 * 60 * 60).setScale(2, BigDecimal.ROUND_HALF_DOWN);
		    else
		        return new BigDecimal(size).setScale(2, BigDecimal.ROUND_HALF_DOWN);
		}
	    return BigDecimal.ZERO;
	}
	
	static Pattern LAT_PAT = Pattern.compile("^\\s*Latency\\s+(\\d+\\.\\d+\\w*)\\s+(\\d+\\.\\d+\\w*)\\s+(\\d+\\.\\d+\\w*)\\s+(\\d+\\.\\d+%).*$");
	static Pattern RQS_PAT = Pattern.compile("^\\s*Req/Sec\\s+(\\d+\\.\\d+\\w*)\\s+(\\d+\\.\\d+\\w*)\\s+(\\d+\\.\\d+\\w*)\\s+(\\d+\\.\\d+%).*$");
	static Pattern RQI_PAT = Pattern.compile("^\\s*(\\d+)\\ requests in (\\d+\\.\\d+\\w*)\\,\\ (\\d+\\.\\d+\\w*)\\ read.*$");
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
		boolean onelatdone = false;
		boolean onehdrdone = false;
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
						String key = onehdrdone?"dps_details_2":"dps_details";
						List<String[]> rows = (List<String[]>)retval.get(key);
						if(rows==null) {
							rows = new ArrayList<String[]>();
							retval.put(key, rows);
						}
						rows.add(new String[] {m.group(1), m.group(2), m.group(3), m.group(4)});
						
					}
					if(matched) {
						continue;
					} else {
						hdrstarted = 0;
						onehdrdone = true;
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
					retval.put("lat_dis_"+m.group(1)+(onelatdone?"_2":"")+"_s", m.group(2));
					retval.put("lat_dis_"+m.group(1)+(onelatdone?"_2":""), get_ms(m.group(2)));
				} else {
					m = LPER_PAT2.matcher(line);
					if(m.matches()) {
						String vals = "";
						BigDecimal val = new BigDecimal(m.group(1)).setScale(3, BigDecimal.ROUND_UNNECESSARY);
						BigDecimal frc = val.remainder(BigDecimal.ONE);
						if(frc.floatValue()>0) {
							frc = frc.multiply(new BigDecimal(1000)).setScale(0, BigDecimal.ROUND_UNNECESSARY);
							vals = val.intValue() + "_" +frc.toPlainString();
						} else {
							vals = val.intValue() + "";
						}
						retval.put("lat_dis_"+vals+(onelatdone?"_2":"")+"_s", m.group(2));
						retval.put("lat_dis_"+vals+(onelatdone?"_2":""), get_ms(m.group(2)));
						matched = true;
					}
				}
				if(matched) {
					continue;
				} else {
					latstarted = false;
					onelatdone = true;
				}
			}
			
			Matcher m = LAT_PAT.matcher(line);
			if(m.matches()) {
				retval.put("lat_avg_s", m.group(1));
	            retval.put("lat_stdev_s", m.group(2));
	            retval.put("lat_max_s", m.group(3));
				retval.put("lat_avg", get_ms(m.group(1)));
	            retval.put("lat_stdev", get_ms(m.group(2)));
	            retval.put("lat_max", get_ms(m.group(3)));
	            retval.put("lat_stdev_perc", m.group(4));
			} else {
				m = RQS_PAT.matcher(line);
				if(m.matches()) {
					retval.put("req_avg_s", m.group(1));
		            retval.put("req_stdev_s", m.group(2));
		            retval.put("req_max_s", m.group(3));
					retval.put("req_avg", get_number(m.group(1)));
		            retval.put("req_stdev", get_number(m.group(2)));
		            retval.put("req_max", get_number(m.group(3)));
		            retval.put("req_stdev_perc", m.group(4));
				} else {
					m = RQI_PAT.matcher(line);
					if(m.matches()) {
						retval.put("tot_requests_s", m.group(1));
			            retval.put("tot_duration_s", m.group(2));
			            retval.put("read_s", m.group(3));
						retval.put("tot_requests", get_number(m.group(1)));
			            retval.put("tot_duration", get_ms(m.group(2)));
			            retval.put("read", get_bytes(m.group(3)));
					} else {
						m = FRQS_PAT.matcher(line);
						if(m.matches()) {
							retval.put("req_sec_tot_s", m.group(1));
							retval.put("req_sec_tot", get_number(m.group(1)));
						} else {
							m = TRFS_PAT.matcher(line);
							if(m.matches()) {
								retval.put("read_tot_s", m.group(1));
								retval.put("read_tot", get_bytes(m.group(1)));
							} else {
								m = SCE_PAT.matcher(line);
								if(m.matches()) {
									retval.put("err_connect_s", m.group(1));
						            retval.put("err_read_s", m.group(2));
						            retval.put("err_write_s", m.group(3));
						            retval.put("err_timeout_s", m.group(4));
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
	    if(!retval.containsKey("err_connect")) {
	        retval.put("err_connect_s", "0");
	        retval.put("err_connect", 0);
	    }
	    if(!retval.containsKey("err_read")) {
	        retval.put("err_read_s", "0");
	        retval.put("err_read", 0);
	    }
	    if(!retval.containsKey("err_write")) {
	        retval.put("err_write_s", "0");
	        retval.put("err_write", 0);
	    }
	    if(!retval.containsKey("err_timeout")) {
	        retval.put("err_timeout_s", "0");
	        retval.put("err_timeout", 0);
	    }
	    
	    return retval;
	}

	public static void execute(AcceptanceTestContext context, TestCase tc, TestCaseReport tcr, boolean isSingleExecutionContext) throws Exception {
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
			if(tc.getPerfConfig().getType().equals("wrk2")) {
				if(r<=0) {
					r = 1000;
				}
				builderList.add("-R");
				builderList.add(r.toString());
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
			if(to>0) {
				builderList.add("--timeout");
				builderList.add(to+"s");
			}
			
			if(tc.getPerfConfig().isLatency()) {
				builderList.add("--latency");
			}
			
			if(tc.getPerfConfig().getType().equals("wrk2")) {
				builderList.add("--u_latency");
			}
			
			File script = null;
			
			if(StringUtils.isNotBlank(tc.getPerfConfig().getFilePath()) && context.getResourceFile(tc.getPerfConfig().getFilePath().trim()).exists()) {
				script = context.getResourceFile(tc.getPerfConfig().getFilePath().trim());
				builderList.add("-s ");
				builderList.add(context.getResourceFile(tc.getPerfConfig().getFilePath().trim()).getAbsolutePath());
			}
			
			if(script==null) {
				if(tc.getHeaders()!=null && tc.getHeaders().size()>0) {
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
					String content = tc.getContent();
					if(StringUtils.isBlank(content) && tc.getContentFile()!=null) {
						if(context.getResourceFile(tc.getContentFile().trim()).exists()) {
							content = FileUtils.readFileToString(context.getResourceFile(tc.getContentFile().trim()), "UTF-8");
						}
					}
					if(content==null) {
						content = "";
					}
					prog.append(content);
					prog.append("\n]====================]\n");
					
					script = File.createTempFile(UUID.randomUUID().toString(), ".lua");
					FileUtils.write(script, prog.toString(), "UTF-8");
					
					builderList.add("-s");
					builderList.add(script.getAbsolutePath());
				}
			}
			
			builderList.add(tc.getAurl());
 			
 			if(tcr.getTestCase()==null) {
 				tcr.setTestCase(tc);
 			}
			
			StringBuilder out = new StringBuilder();
			StringBuilder err = new StringBuilder();
			
			long start = System.currentTimeMillis();
			executeInt(builderList, out, err, tcr, tc.getBaseUrl());
			
			if(isSingleExecutionContext) {
				if(config.getGatfTestDataConfig()!=null && config.isCompareEnabled() && config.getGatfTestDataConfig().getCompareEnvBaseUrls()!=null) {
					for (String burl : config.getGatfTestDataConfig().getCompareEnvBaseUrls()) {
						String aurl = tc.getAurl().replaceFirst(config.getBaseUrl(), burl);
						builderList.set(builderList.size()-1, aurl);
						executeInt(builderList, out, err, tcr, burl);
					}
				}
			}
			
			if(script!=null) {
				script.delete();
			}

			if(out.length()>0) {
				tcr.setResponseContent(out.toString());
				tcr.setResponseContentType(MediaType.TEXT_PLAIN);
				tcr.setStatus(TestStatus.Success.status);
			}
 			if(err.length()>0) {
 				tcr.setErrorText(err.toString());
				tcr.setStatus(TestStatus.Failed.status);
			}
 			
 			tcr.setExecutionTime(System.currentTimeMillis() - start);
		}
	}
	
	public static void executeInt(List<String> builderList, StringBuilder out, StringBuilder err, TestCaseReport tcr, String baseUrl) {
		StringBuilder out1 = new StringBuilder();
		StringBuilder err1 = new StringBuilder();
		GatfFunctionHandler.executeCmd(builderList, out1, err1, false);
		if(out1.length()>0) {
			Map<String, Object> retval = parse_wrk_output(out1);
			tcr.getPerfResult().add(retval);
			out.append(String.join(" ", builderList)+"\n\n");
			out.append("Result for ");
			out.append(baseUrl);
			out.append("\n");
			out.append(out1.toString());
			out.append("\n\n");
		}
		if(err1.length()>0) {
			err.append(String.join(" ", builderList)+"\n\n");
			err.append("Error for ");
			err.append(baseUrl);
			err.append("\n");
			err.append(err1.toString());
			err.append("\n\n");
		}
	}
}
