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
package com.gatf.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.junit.jupiter.api.Test;

/**
 * Δ148b: a recorded {@code type "…"} / {@code select value@"…"} value reaches {@code SeleniumTest.evaluate}, which hands
 * any value with a {@code $} to Velocity ({@code WorkflowContextHandler.templatize}: a default {@code VelocityEngine},
 * {@code evaluate(context, writer, "ERROR", template)}). This runs the recorded string the same way and checks that it
 * comes out literally and that no directive ran: the quoted content as the Java literal of the generated test
 * ({@code evaluate("<content>")}), unescaped, then templated with a context that holds a secret.
 */
class RecordedValueVelocityTest {

    /** The Java string literal {@code "<content>"} as javac reads it; stepValue emits only {@code \\} and {@code \"}. */
    static String javaLiteral(String content) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '\\') {
                char n = content.charAt(++i);
                assertTrue(n == '\\' || n == '"', "unexpected escape \\" + n + " in " + content);
                b.append(n);
            } else {
                assertTrue(c != '"', "unescaped quote in " + content);
                b.append(c);
            }
        }
        return b.toString();
    }

    @Test
    void recordedValuesComeOutOfVelocityLiterally() throws Exception {
        VelocityEngine engine = new VelocityEngine();
        engine.init();
        for (String[] v : PickLocatorTest.VALUE_VECTORS) {
            if (v[1] == null) continue;
            String template = javaLiteral(PickLocator.stepValue(v[0]));
            if (template.indexOf('$') < 0) continue;   // evaluate returns such a value untouched, without Velocity
            Map<String, Object> vars = new HashMap<String, Object>();
            vars.put("x", "SECRET");
            vars.put("y", "SECRET");
            VelocityContext ctx = new VelocityContext(vars);
            StringWriter w = new StringWriter();
            engine.evaluate(ctx, w, "ERROR", template);
            String expected = v[0].replaceAll("[\\x00-\\x1f\\x7f\\u0085\\u2028\\u2029]", " ");
            assertEquals(expected, w.toString(), v[0]);
            assertEquals("SECRET", ctx.get("x"), v[0] + ": a directive ran");
            assertEquals(2, ctx.getKeys().length, v[0] + ": the context changed");
        }
        // control: unwrapped, the same text does run (this is what Δ148b closes)
        VelocityContext ctx = new VelocityContext(new HashMap<String, Object>(Map.of("x", "SECRET")));
        StringWriter w = new StringWriter();
        engine.evaluate(ctx, w, "ERROR", "#set($x=1)$x");
        assertEquals("1", w.toString());
    }
}
