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
package com.gatf.selenium.gatfjdb;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.jdiscript.util.VMLauncher;

import com.gatf.selenium.Command;
import com.gatf.selenium.SeleniumTest;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.ClassType;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.Location;
import com.sun.jdi.StackFrame;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.VMStartException;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.event.VMDeathEvent;
import com.sun.jdi.event.VMDisconnectEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;

@SuppressWarnings("rawtypes")
public class GatfSelDebugger {
	private static class DebugState {
		// user breakpoints, as 1-based SEL line numbers (delta-075: was mixing sel/java lines)
		private final Set<Integer> breaks = ConcurrentHashMap.newKeySet();
		private volatile int prevLine;
		private volatile int nextLine;
	}
	
	// Read by the SSE/WebSocket push threads while the JDI event thread + HTTP threads write → concurrent.
	private final Map<String, DebugState> stateMap = new ConcurrentHashMap<>();
	// Implicit breakpoints set to emulate step-over/step-into/initial pause; removed on the next hit so
	// they never linger as invisible user breakpoints.
	private final Set<BreakpointRequest> tempBps = ConcurrentHashMap.newKeySet();
	// Change notification for push channels (delta-075): bumped on every pushed-field mutation.
	private final Object changeLock = new Object();
	private volatile long version = 0;
	private Stack<String> selFiles = new Stack<>();
	private String selscript;
	private String srcCode;
	private volatile LocatableEvent cbe;
	private Class debugClass; 
	private VirtualMachine dvm;
	private ClassPrepareEvent cpe;
	private Map<String, List<List<Integer[]>>> selToJavaLineMap = null;
	private Thread eventThr = null;
	private volatile int running = 1;
	private volatile AtomicInteger activity = new AtomicInteger(0);
	private volatile String currFile;
	private volatile int state = 0;
	private Command allcmds;
	
	public boolean isActivityObserved() {
		// delta-075: returned true on both branches → the idle reaper never reaped an abandoned session
		// (closed tab → JDI VM + browser leaked). Checked every 10 s by GatfConfigToolUtil; a session is
		// reaped only after IDLE_CHECKS_BEFORE_REAP consecutive idle checks (~60 s). Any command or push-
		// channel read (connected viewer) counts as activity.
		if(activity.getAndSet(0) > 0) {
			idleChecks = 0;
			return true;
		}
		return ++idleChecks < IDLE_CHECKS_BEFORE_REAP;
	}
	static final int IDLE_CHECKS_BEFORE_REAP = 6;
	private volatile int idleChecks = 0;

	private void changed() {
		synchronized (changeLock) {
			version++;
			changeLock.notifyAll();
		}
	}

	private void setState(int s) {
		state = s;
		changed();
	}

	public long getVersion() {
		return version;
	}

	/** Block until the pushed state changes from {@code seen}, the session ends, or the timeout elapses. */
	public long awaitChange(long seen, long timeoutMs) throws InterruptedException {
		long end = System.currentTimeMillis() + timeoutMs;
		synchronized (changeLock) {
			while (version == seen && running == 1) {
				long w = end - System.currentTimeMillis();
				if (w <= 0) break;
				changeLock.wait(w);
			}
			return version;
		}
	}
	
    public String getSrcCode() {
		return srcCode;
	}
	
    public String getCurrFile() {
		return currFile;
	}

	public void setSrcCode(String srcCode) {
		this.srcCode = srcCode;
	}

	public String getSelscript() {
		return selscript;
	}

	public void setSelscript(String selscript) {
		this.selscript = selscript;
	}
	
	public Map<String, List<List<Integer[]>>> getDebuggableLines() {
		return selToJavaLineMap;
	}
    
    public int getState() {
    	activity.incrementAndGet();
    	return state;
    }
    
    public boolean getRunning() {
    	activity.incrementAndGet();
    	return running==1;
    }
	
	public int getPrevLine() {
		DebugState ds = currFile == null ? null : stateMap.get(currFile);
		return ds == null ? -1 : ds.prevLine;
	}
	
	public int getNextLine() {
		DebugState ds = currFile == null ? null : stateMap.get(currFile);
		return ds == null ? -1 : ds.nextLine;
	}
	
	private int resolveLines(boolean resolvePrevLine) {
		int nextLine = -1;
		activity.incrementAndGet();
		if(cbe!=null && cbe.location().toString().indexOf(debugClass.getName()+":")!=-1) {
			boolean found = !resolvePrevLine;
			if(resolvePrevLine) {
				outer: for(String file : selToJavaLineMap.keySet()) {
					for (List<Integer[]> fset : selToJavaLineMap.get(file)) {
						for (Integer[] linf : fset) {
							if(linf[1].equals(cbe.location().lineNumber())) {
								found = true;
								stateMap.get(currFile).prevLine = linf[0];
								break outer;
							}
						}
					}
				}
			}
			if(found) {
				found = false;
				outer: for (List<Integer[]> fset : selToJavaLineMap.get(currFile)) {
					for (Integer[] linf : fset) {
						if(stateMap.get(currFile).prevLine==linf[0]) {
							found = true;
						}
						if(found && stateMap.get(currFile).prevLine<linf[0]) {
							nextLine = linf[0];
							break outer;
						}
					}
				}
			}
		}
		stateMap.get(currFile).nextLine = nextLine;
		changed();
		return nextLine;
	}

	public void connectAndLaunchVM(String[] args) throws IOException, IllegalConnectorArgumentsException, VMStartException {
		activity.incrementAndGet();
    	File dir = new File(FileUtils.getTempDirectory(), "gatf-code/");
    	String cp = System.getProperty("java.class.path") + File.pathSeparatorChar + dir.getAbsolutePath();
    	dvm = new VMLauncher("-cp "+cp, debugClass.getName() + " " + StringUtils.join(args, " ")).start();
    }

    public void enableClassPrepareRequest() {
    	activity.incrementAndGet();
        ClassPrepareRequest classPrepareRequest = dvm.eventRequestManager().createClassPrepareRequest();
        classPrepareRequest.addClassFilter(debugClass.getName());
        classPrepareRequest.enable();
    }

    public boolean unsetBreakPoint(int selLineNum) throws AbsentInformationException {
    	activity.incrementAndGet();
    	if(!stateMap.get(currFile).breaks.contains(selLineNum)) {
    		return false;
    	}
    	System.out.println("Removing breakpoint at line " + selLineNum);
    	int lineNumber = -1;
    	outer: for (List<Integer[]> fset : selToJavaLineMap.get(currFile)) {
			for (Integer[] linf : fset) {
				if(linf[0].equals(selLineNum)) {
					lineNumber = linf[1];
					break outer;
				}
			}
		}
    	if(lineNumber==-1) {
    		return false;
    	}
    	stateMap.get(currFile).breaks.remove(selLineNum);
    	if(cpe==null) {
    		return true; // never applied to the VM (class not prepared yet)
    	}
    	List<BreakpointRequest> bpl = dvm.eventRequestManager().breakpointRequests();
    	for (BreakpointRequest bpr : bpl) {
			if(!tempBps.contains(bpr) && bpr.location().toString().indexOf(debugClass.getName()+":")!=-1 && bpr.location().lineNumber()==lineNumber) {
				dvm.eventRequestManager().deleteEventRequest(bpr);
				return true;
			}
    	}
    	return false;
    }

    public boolean setBreakPoint(int selLineNum) throws AbsentInformationException {
    	activity.incrementAndGet();
    	if(stateMap.get(currFile).breaks.contains(selLineNum)) {
    		return true;
    	}
    	System.out.println("Adding breakpoint at line " + selLineNum);
    	int lineNumber = -1;
    	outer: for (List<Integer[]> fset : selToJavaLineMap.get(currFile)) {
			for (Integer[] linf : fset) {
				if(linf[0].equals(selLineNum)) {
					lineNumber = linf[1];
					break outer;
				}
			}
		}
    	if(lineNumber==-1) {
    		return false;
    	}
    	stateMap.get(currFile).breaks.add(selLineNum);
    	if(cpe==null) {
    		return true; // applied when the class is prepared (see applyPendingBreakpoints)
    	}
    	return createBreakpoint(lineNumber, false);
    }

    private int javaLineFor(String file, int selLineNum) {
    	List<List<Integer[]>> fl = file == null ? null : selToJavaLineMap.get(file);
    	if(fl == null) return -1;
    	for (List<Integer[]> fset : fl) {
			for (Integer[] linf : fset) {
				if(linf[0].equals(selLineNum)) {
					return linf[1];
				}
			}
		}
    	return -1;
    }

    private boolean createBreakpoint(int javaLine, boolean temp) throws AbsentInformationException {
    	if(cpe==null || javaLine<0) return false;
        ClassType classType = (ClassType) cpe.referenceType();
        List<Location> locs = classType.locationsOfLine(javaLine);
        if(locs.isEmpty()) return false;
        BreakpointRequest bpReq = dvm.eventRequestManager().createBreakpointRequest(locs.get(0));
        if(temp) tempBps.add(bpReq);
        bpReq.enable();
        return true;
    }

    // implicit breakpoint at a SEL line of the current file (step emulation / initial pause); skipped
    // when a user breakpoint already covers that line
    private boolean setTempBreakPoint(int selLineNum) throws AbsentInformationException {
    	DebugState ds = stateMap.get(currFile);
    	if(ds!=null && ds.breaks.contains(selLineNum)) return true;
    	return createBreakpoint(javaLineFor(currFile, selLineNum), true);
    }

    private void clearTempBreakpoints() {
    	for (BreakpointRequest r : tempBps) {
    		try { dvm.eventRequestManager().deleteEventRequest(r); } catch (Exception ignore) {}
    	}
    	tempBps.clear();
    }

    // user breakpoints toggled before ClassPrepare
    private void applyPendingBreakpoints() throws AbsentInformationException {
    	for (Map.Entry<String, DebugState> e : stateMap.entrySet()) {
    		for (Integer sl : e.getValue().breaks) {
    			createBreakpoint(javaLineFor(e.getKey(), sl), false);
    		}
    	}
    }

    public void displayVariables(LocatableEvent event) throws IncompatibleThreadStateException, AbsentInformationException {
    	activity.incrementAndGet();
        StackFrame stackFrame = event.thread().frame(0);
        if(stackFrame.location().toString().contains(debugClass.getName())) {
            Map<LocalVariable, Value> visibleVariables = stackFrame.getValues(stackFrame.visibleVariables());
            System.out.println("Variables at " +stackFrame.location().toString() +  " > ");
            for (Map.Entry<LocalVariable, Value> entry : visibleVariables.entrySet()) {
                System.out.println(entry.getKey().name() + " = " + entry.getValue());
            }
        }
    }

    public boolean enableStepIntoRequest(String line) throws AbsentInformationException {
    	activity.incrementAndGet();
        if(cbe!=null && cbe.location().toString().indexOf(debugClass.getName()+":")!=-1) {
        	Object[] fld = Command.getSubtestFromCall(line, null, null, allcmds);
        	if(fld!=null) {
        		selFiles.push(currFile);
        		resolveLines(true);
        		currFile = fld[5].toString();
        		stateMap.get(currFile).prevLine = (Integer)fld[1];
            	setTempBreakPoint((Integer)fld[1]);
            	resolveLines(false);
            	dvm.resume();
        	}
        	setState(-1);
            return true;
        }
    	setState(-1);
    	//StepRequest stepRequest = dvm.eventRequestManager().createStepRequest(cbe.thread(), StepRequest.STEP_LINE, StepRequest.STEP_OVER);
    	//stepRequest.enable();
        return false;
    }

    public boolean enableStepOutRequest() throws AbsentInformationException {
    	activity.incrementAndGet();
        if(cbe!=null && cbe.location().toString().indexOf(debugClass.getName()+":")!=-1 && selFiles.size()>0) {
        	currFile = selFiles.pop();
        	resolveLines(false);
        	setState(-2);
            return true;
        }
    	setState(-2);
        return false;
    }

    public boolean enableStepOverRequest() throws AbsentInformationException {
    	activity.incrementAndGet();
        if(cbe!=null && cbe.location().toString().indexOf(debugClass.getName()+":")!=-1) {
        	setTempBreakPoint(resolveLines(true));
        	dvm.resume();
        	setState(-3);
            return true;
        }
    	setState(-3);
        return false;
    }
    
    public void resume() {
    	activity.incrementAndGet();
    	dvm.resume();
    	setState(-4);
    }
    
    public void suspend() {
    	activity.incrementAndGet();
    	dvm.suspend();
    	setState(-5);
    }
    
    public void destroy() {
    	// delta-075: bounded — was an unbounded wait (runs under the handler's execution lock) and threw
    	// VMDisconnectedException when the VM had already gone
    	try {
    		dvm.exit(0);
    	} catch (VMDisconnectedException alreadyGone) {
    	}
    	long end = System.currentTimeMillis() + 10000;
    	try {
    		while(running==1 && System.currentTimeMillis() < end) {
    			awaitChange(version, 500);
    		}
    	} catch (InterruptedException e) {
    		Thread.currentThread().interrupt();
    	}
    	setState(-6);
    }

    // paused at a breakpoint/step: temp breakpoints are spent; resolve the paused SEL line and push it
    private void onPaused() {
    	clearTempBreakpoints();
    	try {
    		resolveLines(true);
    	} catch (Exception e) {
    		changed();
    	}
    }

    public static GatfSelDebugger debugSession(Class<? extends SeleniumTest> testClass, String[] args, Map<String, List<List<Integer[]>>> selToJavaLineMap, Command out) throws Exception {
        GatfSelDebugger dbgIns = new GatfSelDebugger();
        dbgIns.debugClass = testClass;
        dbgIns.selToJavaLineMap = selToJavaLineMap;
        dbgIns.allcmds = out;
        
    	dbgIns.connectAndLaunchVM(args);
        dbgIns.enableClassPrepareRequest();
        dbgIns.running = 1;
        
        for(String file : selToJavaLineMap.keySet()) {
        	dbgIns.stateMap.put(file, new DebugState());
        }
        dbgIns.currFile = selToJavaLineMap.keySet().iterator().next();
        
		outer: for (List<Integer[]> fset : selToJavaLineMap.get(dbgIns.currFile)) {
			for (Integer[] linf : fset) {
				if(linf[2].equals(2)) {
					dbgIns.stateMap.get(dbgIns.currFile).prevLine = linf[0];
					break outer;
				}
			}
		}
        
        dbgIns.eventThr = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					dbgIns.dvm.resume();
					EventSet eventSet = null;
		            while ((eventSet = dbgIns.dvm.eventQueue().remove()) != null) {
		                for (Event event : eventSet) {
		                    if (event instanceof ClassPrepareEvent) {
		                    	System.out.println("ClassPrepareEvent received...");
		                    	dbgIns.cpe = (ClassPrepareEvent)event;
		                    	dbgIns.applyPendingBreakpoints();
		                    	dbgIns.setTempBreakPoint(dbgIns.stateMap.get(dbgIns.currFile).prevLine);
		                    	dbgIns.dvm.resume();
		                    } else if (event instanceof BreakpointEvent) {
		                    	System.out.println("BreakpointEvent received...");
		                    	dbgIns.cbe = (BreakpointEvent) event;
		                    	dbgIns.onPaused();
		                    } else if (event instanceof StepEvent) {
		                    	System.out.println("StepEvent received...");
		                    	dbgIns.cbe = (StepEvent) event;
		                    	dbgIns.onPaused();
		                    } else {
		                    	dbgIns.cbe = null;
		                    	System.out.println(event.getClass().getSimpleName() + " received...");
		                    	if(event instanceof VMDeathEvent) {
		                    		dbgIns.dvm.resume();
		                    	} else if(event instanceof VMDisconnectEvent) {
		                    		break;
		                    	} else {
		                    		dbgIns.dvm.resume();
		                    	}
		                    }
		                }
		            }
				} catch (VMDisconnectedException e) {
		            System.out.println("Virtual Machine disconnected");
		        } catch (InterruptedException e) {
		        	System.out.println("Stopping debugger session");
		        } catch (Exception e) {
		            e.printStackTrace();
		        } finally {
		        	dbgIns.eventThr = null;
		        	dbgIns.running = 0;
		        	dbgIns.changed();
		        	if(dbgIns.dvm!=null) {
			            InputStreamReader reader = new InputStreamReader(dbgIns.dvm.process().getInputStream());
			            OutputStreamWriter writer = new OutputStreamWriter(System.out);
			            char[] buf = new char[512];
			            try {
							int rn = reader.read(buf);
							if(rn>0) writer.write(buf, 0, rn);
				            writer.flush();
						} catch (IOException e) {
							e.printStackTrace();
						}
		        	}
		        }
			}
		});
        dbgIns.eventThr.start();
        System.out.println("Started debugger session");
        return dbgIns;
    }
}
