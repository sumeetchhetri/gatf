package com.gatf.ui;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.websockets.DataFrame;
import org.glassfish.grizzly.websockets.WebSocket;
import org.glassfish.grizzly.websockets.WebSocketApplication;

import com.gatf.executor.core.WorkflowContextHandler;
import com.gatf.selenium.gatfjdb.GatfSelDebugger;

/**
 * WebSocket push channel for the online seleasy debugger (delta-041).
 *
 * The client connects to <code>/gatfjdb</code> and sends the debug sessionId as its first (text) message;
 * this app then pushes <code>{s,r,p,n,v,t}</code> frames whenever the debugger's line/state changes, by
 * fast-polling the shared {@link GatfReportsHandler#dbgSessions} getters (gatfjdb exposes only pollable
 * getters, no push events at that layer).
 *
 * This mirrors the SSE stream ({@code GatfReportsHandler} action=debugStream), which stays as an automatic
 * client fallback. Debug COMMANDS still travel over {@code PUT /reports?action=debug} — this channel is
 * push-only, so the existing command dispatch is untouched.
 */
public class GatfDebugWebSocket extends WebSocketApplication {

    private final Map<WebSocket, Thread> pushers = new ConcurrentHashMap<WebSocket, Thread>();

    @Override
    public boolean isApplicationRequest(HttpRequestPacket request) {
        String uri = request.getRequestURI();
        return uri != null && uri.endsWith("/gatfjdb");
    }

    @Override
    public void onClose(WebSocket socket, DataFrame frame) {
        Thread t = pushers.remove(socket);
        if (t != null) {
            t.interrupt();
        }
        super.onClose(socket, frame);
    }

    @Override
    public void onMessage(final WebSocket socket, final String text) {
        // First message = the debug sessionId. Ignore later messages (one pusher per socket).
        if (pushers.containsKey(socket)) {
            return;
        }
        final String sessionId = (text == null) ? "" : text.trim();
        Thread t = new Thread(new Runnable() {
            public void run() {
                pushLoop(socket, sessionId);
            }
        }, "gatfjdb-ws-" + sessionId);
        t.setDaemon(true);
        pushers.put(socket, t);
        t.start();
    }

    private void pushLoop(WebSocket socket, String sessionId) {
        int lp = Integer.MIN_VALUE, ln = Integer.MIN_VALUE, lv = Integer.MIN_VALUE;
        boolean lr = true, first = true;
        String lt = null;
        try {
            // init (line=0) may race the connect — wait briefly for the session to appear
            GatfSelDebugger d = GatfReportsHandler.dbgSessions.get(sessionId);
            for (int i = 0; d == null && i < 40 && socket.isConnected(); i++) {
                Thread.sleep(50);
                d = GatfReportsHandler.dbgSessions.get(sessionId);
            }
            if (d == null) {
                if (socket.isConnected()) {
                    socket.send("{\"s\":false,\"m\":\"No debug session\"}");
                }
                return;
            }
            while (socket.isConnected() && !Thread.currentThread().isInterrupted()) {
                GatfSelDebugger s = GatfReportsHandler.dbgSessions.get(sessionId);
                if (s == null) {
                    if (socket.isConnected()) {
                        socket.send("{\"s\":true,\"r\":false}");
                    }
                    break;
                }
                boolean r = s.getRunning();
                int p = s.getPrevLine(), n = s.getNextLine(), v = s.getState();
                String t = s.getCurrFile();
                boolean changed = first || p != lp || n != ln || v != lv || r != lr
                        || (t == null ? lt != null : !t.equals(lt));
                if (changed) {
                    socket.send(frame(r, p, n, v, t));
                    lp = p; ln = n; lv = v; lr = r; lt = t; first = false;
                }
                if (!r) {
                    break;
                }
                Thread.sleep(150);
            }
        } catch (InterruptedException ie) {
            // socket closed / teardown — exit quietly
        } catch (Exception e) {
            // send failed / client gone — end the loop
        } finally {
            pushers.remove(socket);
        }
    }

    private static String frame(boolean r, int p, int n, int v, String t) {
        Map<String, Object> out = new HashMap<String, Object>();
        out.put("s", Boolean.TRUE);
        out.put("r", r);
        out.put("p", p);
        out.put("n", n);
        out.put("v", v);
        out.put("t", t);
        try {
            return WorkflowContextHandler.OM.writeValueAsString(out);
        } catch (Exception e) {
            return "{\"s\":true,\"r\":" + r + ",\"p\":" + p + ",\"n\":" + n + ",\"v\":" + v + "}";
        }
    }
}
