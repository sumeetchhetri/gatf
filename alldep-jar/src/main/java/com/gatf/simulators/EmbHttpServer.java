package com.gatf.simulators;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.takes.Request;
import org.takes.Response;
import org.takes.Take;
import org.takes.facets.fork.FkRegex;
import org.takes.facets.fork.TkFork;
import org.takes.http.Exit;
import org.takes.http.FtBasic;
import org.takes.rq.RqHeaders;
import org.takes.rs.RsFluent;

import com.gatf.executor.core.AcceptanceTestContext.SimulatorInt;
import com.gatf.executor.core.WorkflowContextHandler;

public class EmbHttpServer implements SimulatorInt {
	
	private volatile boolean exit = false;
	private int port = 8999;
	private FtBasic server;
	private LinkedBlockingQueue<Map<String, Object>> requests = new LinkedBlockingQueue<>();
	private Thread thr = null;
	
	public void start(Object[] args) {
		port = (Integer)args[0];
		String headersJson = args[1].toString();
		String body = args[2].toString();
		try {
			server = new FtBasic(new TkFork(new FkRegex("/", new Take() {
				@SuppressWarnings("unchecked")
				@Override
				public Response act(Request req) throws Exception {
					org.takes.rq.RqMethod.Base rw = new org.takes.rq.RqMethod.Base(req);
					RqHeaders.Smart hw = new RqHeaders.Smart(req);
					Map<String, Object> r = new HashMap<>();
					r.put("method", rw.method());
					for (String name : hw.names()) {
						r.put(name, hw.header(name));
					}
					r.put("content", rw.body()!=null?"":IOUtils.toString(rw.body(), "UTF-8"));
					synchronized (requests) {
						requests.add(r);
					}
					Map<String, String> hdrs = WorkflowContextHandler.OM.readValue(headersJson, Map.class);
					RsFluent rf = new RsFluent().withStatus(200).withBody(body);
					for (String key : hdrs.keySet()) {
						rf.withHeader(key, hdrs.get(key));
					}
					return rf;
				}
			})), port);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		if(server!=null) {
			thr = new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						server.start(new Exit() {
							@Override
							public boolean ready() {
								return exit;
							}
						});
					} catch (Exception e) {
						//e.printStackTrace();
					}
				}
			});
			thr.start();
		}
	}
	
	public void stop() {
		exit = true;
		try {
			Socket sock = new Socket();
			sock.connect(new InetSocketAddress("127.0.0.1", port), 2000);
			sock.close();
			thr.interrupt();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public boolean isEventReceived(Object[] args) {
		String from = args[0].toString();
		String to = args[1].toString();
		String content = args[2].toString();
		long timeout = Long.parseLong(args[3].toString());
		synchronized (requests) {
			try {
				Map<String, Object> req = requests.poll(timeout, TimeUnit.MILLISECONDS);
				if(req!=null) {
					if(req.get("from")!=null && req.get("from").toString().equalsIgnoreCase(from) && 
						req.get("to")!=null && req.get("to").toString().equalsIgnoreCase(to)) {
						if(StringUtils.isNotBlank(content)) {
							if(req.get("content")!=null && StringUtils.isNotBlank(req.get("content").toString())) {
								try {
									Pattern p = Pattern.compile(content);
									return p.matcher(req.get("content").toString()).matches();
								} catch (Exception e) {
									return req.get("content").toString().equalsIgnoreCase(content);
								}
							}
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return false;
	}
}
