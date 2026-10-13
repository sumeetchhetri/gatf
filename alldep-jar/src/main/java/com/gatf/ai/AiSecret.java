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
package com.gatf.ai;

/**
 * H1 (Δ149): a provider key in memory. {@link #toString()} never shows it, and {@link #redact} scrubs it (and any
 * long-enough fragment a provider might echo) from text before that text can reach an error, a log or a reply.
 */
public final class AiSecret {

    private final String value;

    private AiSecret(String value) { this.value = value; }

    /** Null for a null, blank or multi-line value. */
    public static AiSecret of(String v) {
        if (v == null) return null;
        String t = v.trim();
        if (t.isEmpty() || t.indexOf('\n') >= 0 || t.indexOf('\r') >= 0) return null;
        return new AiSecret(t);
    }

    /** The raw key, for the one place it belongs: a request header built by a provider. */
    public String reveal() { return value; }

    public String redact(String text) {
        return redact(text, value);
    }

    /** Shortest slice of a key treated as a leak (keys of at least {@value} chars). */
    public static final int SLICE = 12;

    /**
     * {@code text} with {@code secret} replaced by {@code ***}, and, for keys of {@link #SLICE}+ chars, every run of
     * {@link #SLICE} or more consecutive key characters (a cut-off or partial echo). One indexOf per key slice, so it
     * stays linear in the text for a fixed key.
     */
    public static String redact(String text, String secret) {
        if (text == null || secret == null || secret.isEmpty()) return text;
        String out = text.replace(secret, "***");
        if (secret.length() < SLICE) return out;
        boolean[] hit = new boolean[out.length()];
        boolean any = false;
        for (int j = 0; j + SLICE <= secret.length(); j++) {
            String part = secret.substring(j, j + SLICE);
            for (int i = out.indexOf(part); i >= 0; i = out.indexOf(part, i + 1)) {
                for (int k = i; k < i + SLICE; k++) hit[k] = true;
                any = true;
            }
        }
        if (!any) return out;
        StringBuilder b = new StringBuilder(out.length());
        for (int i = 0; i < out.length(); i++) {
            if (!hit[i]) { b.append(out.charAt(i)); continue; }
            if (i == 0 || !hit[i - 1]) b.append("***");
        }
        return b.toString();
    }

    @Override public String toString() { return "AiSecret[***]"; }
}
