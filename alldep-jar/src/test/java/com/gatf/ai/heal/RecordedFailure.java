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
package com.gatf.ai.heal;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.gatf.selenium.trace.BrowserControl;
import com.gatf.selenium.trace.Capability;
import com.gatf.selenium.trace.ConsoleListener;
import com.gatf.selenium.trace.NetworkListener;
import com.gatf.selenium.trace.RichSnapshot;
import com.gatf.selenium.trace.TraceConfig;
import com.gatf.selenium.trace.TraceRecorder;

/**
 * H1 (Δ149): records a real failing run through {@link TraceRecorder} (per-action DOM snapshots on, a fake transport
 * serving the pages) and returns the trace zip: the "recorded failure" the healer tests start from.
 */
final class RecordedFailure {

    static final String URL = "https://shop.example.test/checkout";

    static final List<String> SCRIPT = List.of(
            "open chrome",
            "goto https://shop.example.test/cart",
            "click id@checkout",
            "type 'pune' id@city",
            "click id@place-order",
            "?? css@\"h1\" text = 'Thank you'");
    static final int FAILING_LINE = 5;

    private RecordedFailure() {}

    static String page(String name) throws Exception {
        return new String(Files.readAllBytes(SnapshotResolverParityTest.res(name)), StandardCharsets.UTF_8);
    }

    /** A transport serving rich snapshots from a queue of pages (the last page repeats). */
    static final class Pages implements BrowserControl {
        final Deque<String> pages = new ArrayDeque<String>();
        String last;
        public boolean supports(Capability c) { return c == Capability.DOM_SNAPSHOT || c == Capability.DOM_SNAPSHOT_RICH; }
        public void enableNetwork(NetworkListener l) { }
        public void disableNetwork() { }
        public void enableConsole(ConsoleListener l) { }
        public void disableConsole() { }
        public String captureDomSnapshot() { return null; }
        public byte[] captureScreenshot() { return null; }
        public byte[] fetchBody(String id) { return null; }
        public void close() { }
        public RichSnapshot captureRichSnapshot(List<Object> targets, List<String> marks) {
            if (!pages.isEmpty()) last = pages.poll();
            RichSnapshot r = new RichSnapshot();
            r.source = "cdp";
            r.html = last;
            r.url = last != null && last.contains("<title>Cart") ? "https://shop.example.test/cart" : URL;
            r.viewportW = 1280;
            r.viewportH = 800;
            for (int i = 0; i < targets.size(); i++) r.targetRects.add(null);
            return r;
        }
    }

    /** goto cart, click #checkout, type city, then {@code click id@place-order} fails (the button was renamed). */
    static Path record(Path dir) throws Exception {
        Pages b = new Pages();
        b.pages.add(page("cart.html"));
        b.pages.add(page("checkout-failure.html"));
        TraceRecorder r = TraceRecorder.attach(b, new TraceConfig().setEnableTrace(true).setDomSnapshots("full"), dir.resolve("wd"), "checkout.sel", "chrome");
        r.action("click", "id@checkout", "el-checkout");
        r.action("type", "id@city", "el-city");
        r.failure("Element not found: id@place-order (timeout 10s)");
        return r.writeZip(dir.resolve("checkout-trace.zip"), "failed");
    }

    static Set<Capability> unused() { return EnumSet.noneOf(Capability.class); }
}
