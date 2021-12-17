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

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.jdiscript.util.VMLauncher;

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
import com.sun.jdi.event.VMDeathEvent;
import com.sun.jdi.event.VMDisconnectEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;

@SuppressWarnings("rawtypes")
public class GatfSelDebugger {
	private String selscript;
	private BreakpointEvent cbe;
	private Class debugClass; 
	private VirtualMachine dvm;
	private ClassPrepareEvent cpe;
	private Set<Integer> breaks = new HashSet<Integer>();
	private Map<Integer, Object[]> selToJavaLineMap = new HashMap<Integer, Object[]>();
	private Thread eventThr = null;
	private volatile int running = 1;
	
    public String getSelscript() {
		return selscript;
	}

	public void setSelscript(String selscript) {
		this.selscript = selscript;
	}
	
	public Set<Integer> getDebuggableLines() {
		return selToJavaLineMap.keySet();
	}
	
	public int getCurrentLine() {
		if(cbe!=null && cbe.location().toString().indexOf(debugClass.getName()+":")!=-1) {
			for (Integer selln : selToJavaLineMap.keySet()) {
				int lineNumber = (Integer)selToJavaLineMap.get(selln)[1];
				if(lineNumber==cbe.location().lineNumber()) {
					return selln;
				}
			}
		}
		return 0;
	}
	
	public int getNextLine() {
		if(cbe!=null && cbe.location().toString().indexOf(debugClass.getName()+":")!=-1) {
			boolean found = false;
			for (Integer selln : selToJavaLineMap.keySet()) {
				int lineNumber = (Integer)selToJavaLineMap.get(selln)[1];
				if(lineNumber==cbe.location().lineNumber()) {
					found = true;
				} else if(found) {
					return selln;
				}
			}
		}
		return 0;
	}

	public void connectAndLaunchVM(String[] args) throws IOException, IllegalConnectorArgumentsException, VMStartException {
    	File dir = new File(FileUtils.getTempDirectory(), "gatf-code/");
    	String cp = System.getProperty("java.class.path") + File.pathSeparatorChar + dir.getAbsolutePath();
    	dvm = new VMLauncher("-cp "+cp, debugClass.getName() + " " + StringUtils.join(args, " ")).start();
    }

    public void enableClassPrepareRequest() {
        ClassPrepareRequest classPrepareRequest = dvm.eventRequestManager().createClassPrepareRequest();
        classPrepareRequest.addClassFilter(debugClass.getName());
        classPrepareRequest.enable();
    }

    public boolean unsetBreakPoint(int selLineNum) throws AbsentInformationException {
    	if(!selToJavaLineMap.containsKey(selLineNum) || !breaks.contains(selLineNum)) {
    		return false;
    	}
    	breaks.remove(selLineNum);
    	System.out.println("Removing breakpoint at line " + selLineNum);
    	int lineNumber = (Integer)selToJavaLineMap.get(selLineNum)[1];
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
    	if(!selToJavaLineMap.containsKey(selLineNum)) {
    		return false;
    	}
    	System.out.println("Adding breakpoint at line " + selLineNum);
    	int lineNumber = (Integer)selToJavaLineMap.get(selLineNum)[1];
    	breaks.add(lineNumber);
        ClassType classType = (ClassType) cpe.referenceType();
        Location location = classType.locationsOfLine(lineNumber).get(0);
        BreakpointRequest bpReq = dvm.eventRequestManager().createBreakpointRequest(location);
        bpReq.enable();
        dvm.resume();
        return true;
    }

    public void displayVariables(LocatableEvent event) throws IncompatibleThreadStateException, AbsentInformationException {
        StackFrame stackFrame = event.thread().frame(0);
        if(stackFrame.location().toString().contains(debugClass.getName())) {
            Map<LocalVariable, Value> visibleVariables = stackFrame.getValues(stackFrame.visibleVariables());
            System.out.println("Variables at " +stackFrame.location().toString() +  " > ");
            for (Map.Entry<LocalVariable, Value> entry : visibleVariables.entrySet()) {
                System.out.println(entry.getKey().name() + " = " + entry.getValue());
            }
        }
    }

    public boolean enableStepRequest() throws AbsentInformationException {
        if(cbe!=null && cbe.location().toString().indexOf(debugClass.getName()+":")!=-1) {
        	setBreakPoint(getNextLine());
            return true;
        }
        return false;
    }
    
    public void suspend() {
    	dvm.suspend();
    }
    
    public void resume() {
    	dvm.resume();
    }
    
    public int getRunning() {
    	return running;
    }
    
    public void destroy() {
    	if(eventThr!=null) {
    		eventThr.interrupt();
    	}
    	while(running==1) {
    		try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}
    	}
    	dvm.eventRequestManager().createVMDeathRequest();
    }

    public static GatfSelDebugger debugSession(Class<? extends SeleniumTest> testClass, String[] args, Map<Integer, Object[]> selToJavaLineMap) throws Exception {
        GatfSelDebugger dbgIns = new GatfSelDebugger();
        dbgIns.debugClass = testClass;
        dbgIns.selToJavaLineMap = selToJavaLineMap;
        
    	dbgIns.connectAndLaunchVM(args);
        dbgIns.enableClassPrepareRequest();
        dbgIns.running = 1;
        
        dbgIns.eventThr = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					EventSet eventSet = null;
		            while ((eventSet = dbgIns.dvm.eventQueue().remove()) != null) {
		                for (Event event : eventSet) {
		                    if (event instanceof ClassPrepareEvent) {
		                    	System.out.println("ClassPrepareEvent received...");
		                    	dbgIns.cpe = (ClassPrepareEvent)event;
		                    	dbgIns.setBreakPoint(selToJavaLineMap.keySet().iterator().next());
		                    } else if (event instanceof BreakpointEvent) {
		                    	System.out.println("BreakpointEvent received...");
		                    	dbgIns.cbe = (BreakpointEvent) event;
		                        dbgIns.displayVariables(dbgIns.cbe);
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
