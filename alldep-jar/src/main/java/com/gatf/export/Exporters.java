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
package com.gatf.export;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Δ160 — the export backends by CLI name; Δ161 adds {@code selenium-java}. */
public final class Exporters {

    private static final Map<String, Exporter> BACKENDS = new LinkedHashMap<String, Exporter>();

    static {
        register(new PlaywrightExporter());
        register(new SeleniumJavaExporter());
    }

    private Exporters() {}

    public static void register(Exporter e) {
        BACKENDS.put(e.id().toLowerCase(Locale.ROOT), e);
    }

    public static List<String> ids() {
        return new ArrayList<String>(BACKENDS.keySet());
    }

    /** The backend for {@code id}, or null. */
    public static Exporter get(String id) {
        return id == null ? null : BACKENDS.get(id.toLowerCase(Locale.ROOT));
    }

    /** Parses {@code source} once and exports it with the {@code id} backend. */
    public static ExportResult export(String id, String scriptName, String source, ExportOptions options) {
        Exporter e = get(id);
        if (e == null) throw new IllegalArgumentException("unknown export target '" + id + "' (" + String.join("|", ids()) + ")");
        return e.export(new SelParser().parse(scriptName, source), options == null ? new ExportOptions() : options);
    }
}
