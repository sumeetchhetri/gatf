package com.gatf.executor.report;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Sumeet Chhetri
 *
 */
public class TestExecutionPercentile {

	private Map<String, List<Long>> executionTimes90Percentile = new HashMap<String, List<Long>>();
	
	private Map<String, List<Long>> executionTimes50Percentile = new HashMap<String, List<Long>>();
	
	protected void addExecutionTime(String identifier, Long time)
	{
		addExecutionTime(identifier, time, true);
		addExecutionTime(identifier, time, false);
	}
	
	private void addExecutionTime(String identifier, Long time, boolean is90)
	{
		Map<String, List<Long>> mapo = is90?executionTimes90Percentile:executionTimes50Percentile;
		if(!mapo.containsKey(identifier)) {
			mapo.put(identifier, new ArrayList<Long>());
		}
		List<Long> times = mapo.get(identifier);
		if(times.size()==1000)
		{
			Collections.sort(times);
			int index = Math.round((float)(is90?0.9:0.5) * times.size());
			times = times.subList(index+1, times.size());
			mapo.put(identifier, times);
		}
		else
		{
			times.add(time);
		}
	}
	
	private void deducePercentileTimes()
	{
		Map<String, List<Long>> mapo = executionTimes90Percentile;
		for (Map.Entry<String, List<Long>> entry : mapo.entrySet()) {
			List<Long> times90 = entry.getValue();
			Collections.sort(times90);
			int index = Math.round((float)0.9 * times90.size());
			long time = times90.get(index-1);
			times90.clear();
			times90.add(time);
			
			List<Long> times50 = executionTimes50Percentile.get(entry.getKey());
			Collections.sort(times50);
			index = Math.round((float)0.5 * times50.size());
			time = times50.get(index-1);
			times50.clear();
			
			times90.add(time);
			mapo.put(entry.getKey(), times90);
		}
	}
	
	public Map<String, List<Long>> getPercentileTimes()
	{
		deducePercentileTimes();
		return executionTimes90Percentile;
	}
	
	public void mergePercentileTimes(Map<String, List<Long>> times)
	{
		for (Map.Entry<String, List<Long>> entry : times.entrySet()) {
			List<Long> times90 = executionTimes90Percentile.get(entry.getKey());
			List<Long> times50 = executionTimes50Percentile.get(entry.getKey());
			
			if(times90!=null)
			{
				times90.add(entry.getValue().get(0));
				times50.add(entry.getValue().get(1));
			}
			else
			{
				executionTimes90Percentile.put(entry.getKey(), new ArrayList<Long>());
				executionTimes90Percentile.get(entry.getKey()).add(entry.getValue().get(0));
				executionTimes50Percentile.put(entry.getKey(), new ArrayList<Long>());
				executionTimes50Percentile.get(entry.getKey()).add(entry.getValue().get(1));
			}
		}
	}
}
