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
package com.gatf.selenium.trace;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.bidi.BiDi;
import org.openqa.selenium.bidi.Command;
import org.openqa.selenium.bidi.HasBiDi;

/**
 * G9 — the ONE place GATF touches Selenium's BiDi connection object directly (for raw commands such as
 * network.addDataCollector / getData, which Selenium's modules don't wrap).
 *
 * Why reflective: {@code HasBiDi.getBiDi()/maybeGetBiDi()} are deprecated-for-removal since Selenium
 * 4.46, and their replacement {@code Handle.send} is package-private. Resolving both reflectively keeps
 * GATF compiling on the Selenium release that removes them; if neither path exists any more, raw
 * commands simply become unavailable (callers degrade) — nothing breaks.
 *
 * {@link #available} is the truthful "a BiDi socket is actually open" check: in 4.48 every
 * RemoteWebDriver implements HasBiDi, even when the session never negotiated {@code webSocketUrl}.
 */
public final class BidiRaw {

    private BidiRaw() {}

    /** True only if the driver has a live BiDi connection (webSocketUrl negotiated). */
    public static boolean available(WebDriver d) {
        return connection(d) != null;
    }

    /** Send a raw BiDi command; returns the result map, or throws if BiDi is unavailable / the browser rejects it. */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static Map<String, Object> send(WebDriver d, String method, Map<String, Object> params) {
        Object c = connection(d);
        if (c == null) throw new IllegalStateException("no BiDi connection (is 'webSocketUrl: true' set?)");
        Command<Map> cmd = new Command<Map>(method, (Map) params, Map.class);
        try {
            if (c instanceof BiDi) return (Map<String, Object>) ((BiDi) c).send(cmd);
            Method m = c.getClass().getDeclaredMethod("send", Command.class);   // org.openqa.selenium.bidi.Handle
            m.setAccessible(true);
            return (Map<String, Object>) m.invoke(c, cmd);
        } catch (RuntimeException e) {
            throw e;
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable t = e.getCause();
            throw t instanceof RuntimeException ? (RuntimeException) t : new IllegalStateException(t);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // BiDi (via maybeGetBiDi) or Handle (via getHandle), or null
    private static Object connection(WebDriver d) {
        if (!(d instanceof HasBiDi)) return null;
        try {
            Method m = d.getClass().getMethod("maybeGetBiDi");
            Object o = m.invoke(d);
            if (o instanceof Optional) return ((Optional<?>) o).orElse(null);
        } catch (NoSuchMethodException gone) {
            // removed in a later Selenium: fall through to getHandle()
        } catch (Exception e) {
            return null;
        }
        try {
            return d.getClass().getMethod("getHandle").invoke(d);
        } catch (Exception e) {
            return null;
        }
    }
}
