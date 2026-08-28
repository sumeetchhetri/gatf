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
package com.gatf.executor.distributed;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocketFactory;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

/**
 * Optional, default-disabled hardening for the distributed coordinator&lt;-&gt;node socket. All three
 * knobs are system properties and are OFF unless set, so existing setups are byte-for-byte unchanged:
 *
 * <pre>
 *   -Dgatf.dist.tls=true         wrap the socket in TLS. Uses the standard javax.net.ssl.* keystore
 *                                (node/server side) and truststore (coordinator/client side); point
 *                                them at your keys with -Djavax.net.ssl.keyStore / .trustStore etc.
 *   -Dgatf.dist.secret=<secret>  require a shared-secret handshake before the protocol runs. The
 *                                coordinator sends it on connect; the node verifies it (constant-time)
 *                                and drops the connection on mismatch.
 *   -Dgatf.dist.bind=<address>   bind the node's listener to one address (e.g. 127.0.0.1 or a specific
 *                                NIC). Default: all interfaces (the pre-existing behaviour).
 * </pre>
 *
 * Set the SAME flags on the coordinator and on every node — the secret handshake and TLS must match on
 * both ends or the connection is refused. Note: a secret without TLS is sent in the clear, so enable
 * both together for real protection.
 */
public class DistributedTransport {

    /** Wire protocol version; bump when the coordinator<->node framing/payload changes incompatibly. */
    public static final int PROTOCOL_VERSION = 1;

    public static boolean tls() {
        return Boolean.getBoolean("gatf.dist.tls");
    }

    public static String secret() {
        String s = System.getProperty("gatf.dist.secret");
        return (s != null && !s.isEmpty()) ? s : null;
    }

    private static String bindAddr() {
        String s = System.getProperty("gatf.dist.bind");
        return (s != null && !s.isEmpty()) ? s : null;
    }

    /** Node side: build the (optionally TLS, optionally bound) listener socket. */
    public static ServerSocket serverSocket(int port) throws IOException {
        String bind = bindAddr();
        InetAddress addr = bind != null ? InetAddress.getByName(bind) : null; // null = all interfaces
        if (tls()) {
            SSLServerSocketFactory f = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
            return addr != null ? f.createServerSocket(port, 50, addr) : f.createServerSocket(port);
        }
        return addr != null ? new ServerSocket(port, 50, addr) : new ServerSocket(port);
    }

    /** Coordinator side: connect to a node (optionally over TLS). */
    public static Socket clientSocket(String host, int port) throws IOException {
        if (tls()) {
            return SSLSocketFactory.getDefault().createSocket(host, port);
        }
        return new Socket(host, port);
    }

    /**
     * Coordinator side: before the protocol runs, present the shared secret (if configured) and always
     * stamp the protocol version, so a version-mismatched node is refused cleanly instead of failing
     * mid-deserialization.
     */
    public static void clientHandshake(Output out) {
        String sec = secret();
        if (sec != null) {
            out.writeString(sec);
        }
        out.writeInt(PROTOCOL_VERSION);
        out.flush();
    }

    /**
     * Node side: verify the shared secret (if configured) and the protocol-version stamp before the
     * protocol runs. Returns false (caller drops the socket) on secret mismatch, version mismatch, or a
     * truncated handshake.
     */
    public static boolean serverHandshake(Input in) {
        try {
            String sec = secret();
            if (sec != null && !constantTimeEquals(sec, in.readString())) {
                return false;
            }
            return in.readInt() == PROTOCOL_VERSION;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);
        if (x.length != y.length) {
            return false;
        }
        int r = 0;
        for (int i = 0; i < x.length; i++) {
            r |= x[i] ^ y[i];
        }
        return r == 0;
    }
}
