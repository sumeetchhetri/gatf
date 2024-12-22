package com.gatf.selenium.gatfjdb;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
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
		private Set<Integer> breaks = new HashSet<Integer>();
		private int prevLine;
		private int nextLine;
	}
	
	private Map<String, DebugState> stateMap = new HashMap<>();
	private Stack<String> selFiles = new Stack<>();
	private String selscript;
	private String srcCode;
	private LocatableEvent cbe;
	private Class debugClass; 
	private VirtualMachine dvm;
	private ClassPrepareEvent cpe;
	private Map<String, List<List<Integer[]>>> selToJavaLineMap = null;
	private Thread eventThr = null;
	private volatile int running = 1;
	private volatile AtomicInteger activity = new AtomicInteger(0);
	private String currFile;
	private volatile int state = 0;
	private Command allcmds;
	
	public boolean isActivityObserved() {
		int actCount = activity.get();
		activity.set(0);
		if(actCount>0) {
			return true;
		}
		return true;
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
		return stateMap.get(currFile).prevLine;
	}
	
	public int getNextLine() {
		return stateMap.get(currFile).nextLine;
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
    	List<BreakpointRequest> bpl = dvm.eventRequestManager().breakpointRequests();
    	for (BreakpointRequest bpr : bpl) {
			if(bpr.location().toString().indexOf(debugClass.getName()+":")!=-1 && bpr.location().lineNumber()==lineNumber) {
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
    	stateMap.get(currFile).breaks.add(lineNumber);
        ClassType classType = (ClassType) cpe.referenceType();
        Location location = classType.locationsOfLine(lineNumber).get(0);
        BreakpointRequest bpReq = dvm.eventRequestManager().createBreakpointRequest(location);
        bpReq.enable();
        return true;
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
            	setBreakPoint((Integer)fld[1]);
            	resolveLines(false);
            	dvm.resume();
        	}
        	state = -1;
            return true;
        }
    	state = -1;
    	//StepRequest stepRequest = dvm.eventRequestManager().createStepRequest(cbe.thread(), StepRequest.STEP_LINE, StepRequest.STEP_OVER);
    	//stepRequest.enable();
        return false;
    }

    public boolean enableStepOutRequest() throws AbsentInformationException {
    	activity.incrementAndGet();
        if(cbe!=null && cbe.location().toString().indexOf(debugClass.getName()+":")!=-1 && selFiles.size()>0) {
        	currFile = selFiles.pop();
        	resolveLines(false);
        	state = -2;
            return true;
        }
    	state = -2;
        return false;
    }

    public boolean enableStepOverRequest() throws AbsentInformationException {
    	activity.incrementAndGet();
        if(cbe!=null && cbe.location().toString().indexOf(debugClass.getName()+":")!=-1) {
        	setBreakPoint(resolveLines(true));
        	dvm.resume();
        	state = -3;
            return true;
        }
    	state = -3;
        return false;
    }
    
    public void resume() {
    	activity.incrementAndGet();
    	dvm.resume();
    	state = -4;
    }
    
    public void suspend() {
    	activity.incrementAndGet();
    	dvm.suspend();
    	state = -5;
    }
    
    public void destroy() {
    	dvm.exit(0);
    	while(running==1) {
    		try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
			}
    	}
    	state = -6;
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
		                    	dbgIns.setBreakPoint(dbgIns.stateMap.get(dbgIns.currFile).prevLine);
		                    	dbgIns.dvm.resume();
		                    } else if (event instanceof BreakpointEvent) {
		                    	System.out.println("BreakpointEvent received...");
		                    	dbgIns.cbe = (BreakpointEvent) event;
		                        //dbgIns.displayVariables(dbgIns.cbe);
		                    } else if (event instanceof StepEvent) {
		                    	System.out.println("StepEvent received...");
		                    	dbgIns.cbe = (StepEvent) event;
		                        //dbgIns.displayVariables(dbgIns.cbe);
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
		        	if(dbgIns.dvm!=null) {
			            InputStreamReader reader = new InputStreamReader(dbgIns.dvm.process().getInputStream());
			            OutputStreamWriter writer = new OutputStreamWriter(System.out);
			            char[] buf = new char[512];
			            try {
							reader.read(buf);
							writer.write(buf);
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
