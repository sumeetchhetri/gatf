package com.gatf.selenium.gatfjdb;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.StringTokenizer;

import com.sun.jdi.InternalException;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.Connector.Argument;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.Transport;
import com.sun.jdi.connect.VMStartException;
import com.sun.jdi.connect.spi.TransportService;

public class GatfDebuggerLauncher {

    static private final String ARG_HOME = "home";
    static private final String ARG_OPTIONS = "options";
    static private final String ARG_MAIN = "main";
    static private final String ARG_INIT_SUSPEND = "suspend";
    static private final String ARG_QUOTE = "quote";
    static private final String ARG_VM_EXEC = "vmexec";

    TransportService transportService;
    Transport transport;
    boolean usingSharedMemory = false;

    TransportService transportService() {
        return transportService;
    }

    public Transport transport() {
        return transport;
    }


    static boolean hasWhitespace(String string) {
        int length = string.length();
        for (int i = 0; i < length; i++) {
            if (Character.isWhitespace(string.charAt(i))) {
                return true;
            }
        }
        return false;
    }
    
    private String argument(Object o, String name, Map<String, ? extends Argument> arguments) {
    	try {
    		Class<?> claz = Class.forName("com.sun.tools.jdi.ConnectorImpl");
    		System.out.println("Found class " + claz.getName());
    		Method arg = claz.getDeclaredMethod("argument", new Class[] {String.class, Map.class});
    		System.out.println("Found method " + arg.getName());
    		arg.setAccessible(true);
    		Object a = arg.invoke(o, new Object[] {name, arguments});
    		claz = Class.forName("com.sun.tools.jdi.ConnectorImpl$ArgumentImpl");
    		System.out.println("Found class " + claz.getName());
    		arg = claz.getDeclaredMethod("value", new Class[] {});
    		System.out.println("Found method " + arg.getName());
    		arg.setAccessible(true);
    		return (String)arg.invoke(a, new Object[] {});
		} catch (Exception e) {
			e.printStackTrace();
		}
    	return null;
    }

    public VirtualMachine
        launch(Object o, Map<String, ? extends Connector.Argument> arguments)
        throws IOException, IllegalConnectorArgumentsException,
               VMStartException
    {
        VirtualMachine vm = null;
        System.out.println(o.getClass().getName());
        try {
    		Class<?> claz = Class.forName("com.sun.tools.jdi.SunCommandLineLauncher");
    		System.out.println("Found class " + claz.getName());
    		Method arg = claz.getDeclaredMethod("transportService", new Class[] {});
    		System.out.println("Found method " + arg.getName());
    		arg.setAccessible(true);
    		transportService = (TransportService)arg.invoke(o, new Object[] {});
    		arg = claz.getDeclaredMethod("transport", new Class[] {});
    		System.out.println("Found method " + arg.getName());
    		arg.setAccessible(true);
    		transport = (Transport)arg.invoke(o, new Object[] {});
    		Field fei = claz.getDeclaredField("usingSharedMemory");
    		System.out.println("Found field " + fei.getName());
    		fei.setAccessible(true);
    		usingSharedMemory = (Boolean)fei.get(o);
		} catch (Exception e) {
			e.printStackTrace();
		}

        String home = argument(o, ARG_HOME, arguments);
        String options = argument(o, ARG_OPTIONS, arguments);
        String mainClassAndArgs = argument(o, ARG_MAIN, arguments);
        String wait = argument(o, ARG_INIT_SUSPEND,
                                                  arguments);
        String quote = argument(o, ARG_QUOTE, arguments);
        String exe = argument(o, ARG_VM_EXEC, arguments);
        String exePath = null;

        if (quote.length() > 1) {
            throw new IllegalConnectorArgumentsException("Invalid length", ARG_QUOTE);
        }

        if ((options.indexOf("-Djava.compiler=") != -1) &&
            (options.toLowerCase().indexOf("-djava.compiler=none") == -1)) {
            throw new IllegalConnectorArgumentsException("Cannot debug with a JIT compiler",
                                                         ARG_OPTIONS);
        }

        /*
         * Start listening.
         * If we're using the shared memory transport then we pick a
         * random address rather than using the (fixed) default.
         * Random() uses System.currentTimeMillis() as the seed
         * which can be a problem on windows (many calls to
         * currentTimeMillis can return the same value), so
         * we do a few retries if we get an IOException (we
         * assume the IOException is the filename is already in use.)
         */
        TransportService.ListenKey listenKey;
        if (usingSharedMemory) {
            Random rr = new Random();
            int failCount = 0;
            while(true) {
                try {
                    String address = "javadebug" +
                        String.valueOf(rr.nextInt(100000));
                    listenKey = transportService().startListening(address);
                    break;
                } catch (IOException ioe) {
                    if (++failCount > 5) {
                        throw ioe;
                    }
                }
            }
        } else {
            listenKey = transportService().startListening();
        }
        String address = listenKey.address();

        try {
            if (home.length() > 0) {
                exePath = home + File.separator + "bin" + File.separator + exe;
            } else {
                exePath = exe;
            }
            // Quote only if necessary in case the quote arg value is bogus
            if (hasWhitespace(exePath)) {
                exePath = quote + exePath + quote;
            }

        	if(!usingSharedMemory) {
            	address = address.substring(address.lastIndexOf(":"));
            	address = "127.0.0.1" + address;
        	}

            String xrun = "transport=" + transport().name() +
                          ",address=" + address +
                          ",suspend=" + (wait.equalsIgnoreCase("true")? 'y' : 'n');
            // Quote only if necessary in case the quote arg value is bogus
            if (hasWhitespace(xrun)) {
                xrun = quote + xrun + quote;
            }

            String command = exePath + ' ' +
                             options + ' ' +
                             "-Xdebug " +
                             "-Xrunjdwp:" + xrun + ' ' +
                             mainClassAndArgs;

            try {
        		Class<?> claz = Class.forName("com.sun.tools.jdi.AbstractLauncher");
        		System.out.println("Found class " + claz.getName());
        		Method arg = claz.getDeclaredMethod("launch", new Class[] {String[].class, String.class, TransportService.ListenKey.class, TransportService.class});
        		System.out.println("Found method " + arg.getName());
        		arg.setAccessible(true);
        		vm = (VirtualMachine)arg.invoke(o, new Object[] {tokenizeCommand(command, quote.charAt(0)), address, listenKey, transportService()});
    		} catch (Exception e) {
    			e.printStackTrace();
    		}
            // System.err.println("Command: \"" + command + '"');
            // = launch(tokenizeCommand(command, quote.charAt(0)), address, listenKey,
            //            transportService());
        } finally {
            transportService().stopListening(listenKey);
        }

        return vm;
    }
    
    String[] tokenizeCommand(String command, char quote) {
        String quoteStr = String.valueOf(quote); // easier to deal with

        /*
         * Tokenize the command, respecting the given quote character.
         */
        StringTokenizer tokenizer = new StringTokenizer(command,
                                                        quote + " \t\r\n\f",
                                                        true);
        String quoted = null;
        String pending = null;
        List<String> tokenList = new ArrayList<>();
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            if (quoted != null) {
                if (token.equals(quoteStr)) {
                    tokenList.add(quoted);
                    quoted = null;
                } else {
                    quoted += token;
                }
            } else if (pending != null) {
                if (token.equals(quoteStr)) {
                    quoted = pending;
                } else if ((token.length() == 1) &&
                           Character.isWhitespace(token.charAt(0))) {
                    tokenList.add(pending);
                } else {
                    throw new InternalException("Unexpected token: " + token);
                }
                pending = null;
            } else {
                if (token.equals(quoteStr)) {
                    quoted = "";
                } else if ((token.length() == 1) &&
                           Character.isWhitespace(token.charAt(0))) {
                    // continue
                } else {
                    pending = token;
                }
            }
        }

        /*
         * Add final token.
         */
        if (pending != null) {
            tokenList.add(pending);
        }

        /*
         * An unclosed quote at the end of the command. Do an
         * implicit end quote.
         */
        if (quoted != null) {
            tokenList.add(quoted);
        }

        String[] tokenArray = new String[tokenList.size()];
        for (int i = 0; i < tokenList.size(); i++) {
            tokenArray[i] = tokenList.get(i);
        }
        return tokenArray;
    }
}
