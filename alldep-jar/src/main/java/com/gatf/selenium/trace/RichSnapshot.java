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

import java.util.ArrayList;
import java.util.List;

/**
 * H2 (Δ146): one styled DOM snapshot as a transport returns it, before {@link SnapshotRecorder} writes it to the
 * bundle. Protocol-neutral: CDP fills it from {@code DOMSnapshot.captureSnapshot} (serialized by
 * {@link DomSnapshotSerializer}), BiDi and any script-capable driver from {@link DomCapture#SERIALIZE_JS}.
 *
 * The HTML has scripts and inline event handlers removed, form state (value / checked / selected) materialised as
 * attributes, open shadow roots as declarative {@code <template shadowrootmode>} and same-origin frames as
 * {@code srcdoc}. The action targets are marked in the HTML with {@code data-gatf-target="<action id>"} (several ids
 * space-separated when one element is the target of both actions sharing the capture).
 */
public final class RichSnapshot {

    /** One stylesheet the HTML alone cannot reproduce: a linked sheet (href), an adopted sheet, or a {@code <style>}
     *  element whose rules were inserted through the CSSOM (CSS-in-JS: its text content is empty). */
    public static final class Style {
        /** "link" | "style" | "adopted". */
        public String owner;
        /** Absolute stylesheet URL (link), else the document URL; relative {@code url()}s resolve against it. */
        public String href;
        public String media;
        /** CSS text from the CSSOM, or null when the sheet is cross-origin (the viewer falls back to the HAR body). */
        public String text;
        /** For owner=style: the index of the {@code <style>} element in document order (whose text this replaces). */
        public int index = -1;
    }

    /** "cdp" (DOMSnapshot) or "script" (serialized in the page). */
    public String source;
    public String html;
    public String url;
    public String title;
    public long viewportW, viewportH;
    public long scrollX, scrollY;
    public final List<Style> styles = new ArrayList<>();
    /** Per requested target, its viewport rect {x,y,w,h} at capture time, or null (detached / not passed). */
    public final List<double[]> targetRects = new ArrayList<>();
    /** Whether the HTML hit a node/size cap and is incomplete. */
    public boolean truncated;

    public double[] rect(int i) { return i >= 0 && i < targetRects.size() ? targetRects.get(i) : null; }
}
