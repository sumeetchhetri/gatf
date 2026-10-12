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
package com.gatf.coverage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * H6 (Δ156): reads script bodies and captured source maps out of a run's trace bundles, for {@link ImpactSelector}.
 * Bounded; a trace name must be a plain file name in the run folder. Never throws: what it can't read is null.
 *
 * @author Sumeet Chhetri
 */
public final class TraceStore implements ImpactSelector.Bodies {

    static final long MAX_BODY = 64L << 20;
    static final long MAX_HAR = 32L << 20;

    private final Path runDir;
    private final Map<String, Map<String, String>> harFiles = new HashMap<>();   // trace -> url -> zip entry

    public TraceStore(Path runDir) {
        this.runDir = runDir;
    }

    private Path trace(String name) {
        if (runDir == null || name == null || name.isEmpty() || name.contains("/") || name.contains("\\") || name.equals("..") || name.indexOf('\0') >= 0) {
            return null;
        }
        Path p = runDir.resolve(name);
        return Files.isRegularFile(p) ? p : null;
    }

    @Override public byte[] body(String sha1, Collection<String> traces) {
        if (sha1 == null || !sha1.matches("[0-9a-f]{40}")) return null;
        for (String t : traces) {
            byte[] b = entry(t, "resources/" + sha1, MAX_BODY);
            if (b != null) return b;
        }
        return null;
    }

    @Override public byte[] harFile(String url, Collection<String> traces) {
        for (String t : traces) {
            String e = har(t).get(url);
            if (e == null) continue;
            byte[] b = entry(t, e, MAX_BODY);
            if (b != null) return b;
        }
        return null;
    }

    // url (as CoverageUrls.script stores it) -> the resources/ entry of its captured body
    private synchronized Map<String, String> har(String t) {
        Map<String, String> m = harFiles.get(t);
        if (m != null) return m;
        m = new HashMap<>();
        harFiles.put(t, m);
        byte[] b = entry(t, "network.har", MAX_HAR);
        if (b == null) return m;
        try {
            JsonNode har = CoverageMerge.OM.readTree(b);
            for (JsonNode e : har.path("log").path("entries")) {
                String u = CoverageUrls.script(e.path("request").path("url").asText(null));
                int status = e.path("response").path("status").asInt(0);
                String file = e.path("response").path("content").path("_file").asText("");
                if (u == null || file.isEmpty() || !((status >= 200 && status < 300) || status == 304)) continue;
                int s = file.lastIndexOf('/');
                String sha = s >= 0 ? file.substring(s + 1) : file;
                if (sha.matches("[0-9a-f]{40}")) m.putIfAbsent(u, "resources/" + sha);
            }
        } catch (Exception ignored) {
            // unreadable HAR: nothing captured
        }
        return m;
    }

    private byte[] entry(String trace, String name, long max) {
        Path p = trace(trace);
        if (p == null) return null;
        try (ZipFile zf = new ZipFile(p.toFile())) {
            ZipEntry e = zf.getEntry(name);
            if (e == null || e.getSize() > max) return null;
            try (InputStream in = new CoverageIndex.Capped(zf.getInputStream(e), max)) {
                return in.readAllBytes();
            }
        } catch (IOException e) {
            return null;
        }
    }
}
