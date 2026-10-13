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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * H1 (Δ149): the recorded failure in a trace (the {@code *-trace.zip} or its directory): the failure event's detail and
 * the failing step's DOM snapshot. {@code TraceRecorder.failure} captures the page at the moment of failure as the
 * "after" of the last traced action, labelled {@code "<action> (failure)"} (a step whose element was never found is
 * not itself an action event); that capture is the snapshot the healer validates against. Reads only
 * {@code events.ndjson}, {@code snapshots.ndjson} and the referenced {@code snapshots/snapshot-N.html|json}, each
 * size-capped; a reference outside that pattern is ignored. Pure (Jackson + java.util.zip).
 */
public final class TraceFailure {

    static final long MAX_INDEX = 16L * 1024 * 1024;
    static final java.util.regex.Pattern SNAP_REF = java.util.regex.Pattern.compile("snapshots/snapshot-\\d{1,9}\\.(html|json)");

    /** The failure event's detail (the exception message), or null when the trace recorded no failure. */
    public final String failure;
    /** The failing step's snapshot HTML, or null (snapshots off, over budget, or no traced action before the failure). */
    public final String html;
    /** The page URL of that snapshot (from its v2 manifest), or null. */
    public final String url;
    /** Its {@code snapshots.ndjson} label and action id (-1 if none). */
    public final String label;
    public final int action;
    /** The last action names before the failure (oldest first, at most 10), as context. */
    public final List<String> lastActions;

    private TraceFailure(String failure, String html, String url, String label, int action, List<String> lastActions) {
        this.failure = failure;
        this.html = html;
        this.url = url;
        this.label = label;
        this.action = action;
        this.lastActions = lastActions;
    }

    /** For callers that already hold the snapshot (the editor, tests). */
    public static TraceFailure of(String failure, String html, String url) {
        return new TraceFailure(failure, html, url, null, -1, new ArrayList<String>());
    }

    public static TraceFailure read(Path trace, ObjectMapper om) throws IOException {
        if (trace == null || !Files.exists(trace)) throw new IOException("trace not found");
        if (Files.isDirectory(trace)) return parse(name -> {
            Path p = trace.resolve(name).normalize();
            if (!p.startsWith(trace.normalize()) || !Files.isRegularFile(p, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return null;
            for (Path q = p.getParent(); q != null && q.startsWith(trace.normalize()) && !q.equals(trace.normalize()); q = q.getParent()) {
                if (Files.isSymbolicLink(q)) return null;   // no planted link sends a local file's text to a model
            }
            if (Files.size(p) > cap(name)) return null;
            return Files.readAllBytes(p);
        }, om);
        try (ZipFile z = new ZipFile(trace.toFile())) {
            return parse(name -> {
                ZipEntry e = z.getEntry(name);
                if (e == null || e.isDirectory()) return null;
                try (InputStream in = z.getInputStream(e)) {
                    byte[] b = in.readNBytes((int) cap(name) + 1);   // the declared size can lie: cap what is inflated
                    return b.length > cap(name) ? null : b;
                }
            }, om);
        } catch (java.util.zip.ZipException ze) {
            throw new IOException("not a trace zip");
        }
    }

    private static long cap(String name) {
        return name.endsWith(".html") ? SnapshotDom.MAX_CHARS : MAX_INDEX;
    }

    private interface Files2 { byte[] get(String name) throws IOException; }

    private static TraceFailure parse(Files2 files, ObjectMapper om) throws IOException {
        String failure = null;
        List<String> actions = new ArrayList<String>();
        byte[] ev = files.get("events.ndjson");
        if (ev != null) {
            for (String l : new String(ev, StandardCharsets.UTF_8).split("\n")) {
                JsonNode n = line(l, om);
                if (n == null) continue;
                String type = n.path("type").asText(""), name = n.path("name").asText("");
                if (type.equals("action")) {
                    actions.add(name);
                    if (actions.size() > 10) actions.remove(0);
                } else if (type.equals("lifecycle") && name.equals("failure") && failure == null) {
                    failure = n.path("detail").isNull() ? "" : n.path("detail").asText("");
                }
            }
        }
        String html = null, url = null, label = null;
        int action = -1;
        byte[] idx = files.get("snapshots.ndjson");
        if (failure != null && idx != null) {
            JsonNode pick = null;
            for (String l : new String(idx, StandardCharsets.UTF_8).split("\n")) {
                JsonNode n = line(l, om);
                if (n == null || !"after".equals(n.path("phase").asText(""))) continue;
                if (n.path("label").asText("").endsWith("(failure)")) { pick = n; break; }   // the first failure capture
            }
            if (pick != null) {
                label = pick.path("label").asText("");
                action = pick.path("action").asInt(-1);
                String h = pick.path("html").asText(""), d = pick.path("dom").asText("");
                if (SNAP_REF.matcher(h).matches() && h.endsWith(".html")) {
                    byte[] b = files.get(h);
                    if (b != null) html = new String(b, StandardCharsets.UTF_8);
                }
                if (SNAP_REF.matcher(d).matches() && d.endsWith(".json")) {
                    byte[] b = files.get(d);
                    JsonNode m = b == null ? null : safe(b, om);
                    if (m != null && m.path("url").isTextual()) url = m.path("url").asText();
                }
            }
        }
        return new TraceFailure(failure, html, url, label, action, actions);
    }

    private static JsonNode line(String l, ObjectMapper om) {
        String t = l.trim();
        if (t.isEmpty()) return null;
        return safe(t.getBytes(StandardCharsets.UTF_8), om);
    }

    private static JsonNode safe(byte[] b, ObjectMapper om) {
        try {
            JsonNode n = om.readTree(b);
            return n != null && n.isObject() ? n : null;
        } catch (IOException e) {
            return null;
        }
    }
}
