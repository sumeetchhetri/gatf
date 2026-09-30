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
package com.gatf.docs;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Q11 — minimal, dependency-free .xlsx and .csv writing/reading for the test-case documents. Output is a plain
 * SpreadsheetML workbook (inline strings, a bold filled header row, wrapped text, frozen header, autofilter) that
 * Excel, LibreOffice and Google Sheets open without repair.
 */
public final class Sheets {

    public static final class Sheet {
        public final String name;
        public final String[] header;
        public final List<String[]> rows;
        public final int[] widths;

        public Sheet(String name, String[] header, List<String[]> rows, int[] widths) {
            this.name = name;
            this.header = header;
            this.rows = rows;
            this.widths = widths;
        }
    }

    private Sheets() {}

    // ---------------------------------------------------------------- xlsx

    public static void writeXlsx(Path file, List<Sheet> sheets) throws IOException {
        try (OutputStream os = Files.newOutputStream(file); ZipOutputStream z = new ZipOutputStream(os)) {
            StringBuilder ct = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                    + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                    + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                    + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
            StringBuilder wb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                    + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                    + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>");
            StringBuilder rels = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
            for (int i = 0; i < sheets.size(); i++) {
                int n = i + 1;
                ct.append("<Override PartName=\"/xl/worksheets/sheet").append(n)
                        .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
                wb.append("<sheet name=\"").append(esc(sheetName(sheets.get(i).name))).append("\" sheetId=\"").append(n)
                        .append("\" r:id=\"rId").append(n).append("\"/>");
                rels.append("<Relationship Id=\"rId").append(n)
                        .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
                        .append(n).append(".xml\"/>");
            }
            rels.append("<Relationship Id=\"rId").append(sheets.size() + 1)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
            put(z, "[Content_Types].xml", ct.append("</Types>").toString());
            put(z, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                    + "</Relationships>");
            put(z, "xl/workbook.xml", wb.append("</sheets></workbook>").toString());
            put(z, "xl/_rels/workbook.xml.rels", rels.append("</Relationships>").toString());
            put(z, "xl/styles.xml", STYLES);
            for (int i = 0; i < sheets.size(); i++) put(z, "xl/worksheets/sheet" + (i + 1) + ".xml", sheetXml(sheets.get(i)));
        }
    }

    // style 0 default, 1 header (bold, grey fill, border), 2 body (wrap, top), 3 number (top)
    private static final String STYLES = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
            + "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"
            + "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill>"
            + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFD9E1F2\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>"
            + "<borders count=\"2\"><border><left/><right/><top/><bottom/><diagonal/></border>"
            + "<border><left style=\"thin\"><color rgb=\"FFBFBFBF\"/></left><right style=\"thin\"><color rgb=\"FFBFBFBF\"/></right>"
            + "<top style=\"thin\"><color rgb=\"FFBFBFBF\"/></top><bottom style=\"thin\"><color rgb=\"FFBFBFBF\"/></bottom><diagonal/></border></borders>"
            + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
            + "<cellXfs count=\"4\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
            + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment vertical=\"top\" wrapText=\"1\"/></xf>"
            + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyBorder=\"1\" applyAlignment=\"1\"><alignment vertical=\"top\" wrapText=\"1\"/></xf>"
            + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyBorder=\"1\" applyAlignment=\"1\"><alignment vertical=\"top\"/></xf>"
            + "</cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>";

    static String sheetXml(Sheet s) {
        int cols = s.header.length;
        StringBuilder b = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>"
                + "</sheetView></sheetViews><sheetFormatPr defaultRowHeight=\"15\"/>");
        if (s.widths != null && s.widths.length > 0) {
            b.append("<cols>");
            for (int i = 0; i < Math.min(cols, s.widths.length); i++) {
                b.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1).append("\" width=\"").append(s.widths[i])
                        .append("\" customWidth=\"1\"/>");
            }
            b.append("</cols>");
        }
        b.append("<sheetData>");
        row(b, 1, s.header, 1);
        for (int r = 0; r < s.rows.size(); r++) row(b, r + 2, s.rows.get(r), 2);
        b.append("</sheetData>");
        if (!s.rows.isEmpty()) b.append("<autoFilter ref=\"A1:").append(col(cols - 1)).append(s.rows.size() + 1).append("\"/>");
        return b.append("</worksheet>").toString();
    }

    private static void row(StringBuilder b, int r, String[] cells, int style) {
        b.append("<row r=\"").append(r).append("\">");
        for (int c = 0; c < cells.length; c++) {
            String v = cells[c] == null ? "" : cells[c];
            String ref = col(c) + r;
            if (style == 2 && isNumber(v)) {
                b.append("<c r=\"").append(ref).append("\" s=\"3\"><v>").append(v).append("</v></c>");
            } else {
                if (v.length() > 32000) v = v.substring(0, 32000) + "…";
                b.append("<c r=\"").append(ref).append("\" s=\"").append(style).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                        .append(esc(v)).append("</t></is></c>");
            }
        }
        b.append("</row>");
    }

    static boolean isNumber(String v) {
        return v.matches("0|-?[1-9][0-9]{0,13}(\\.[0-9]+)?");
    }

    /** 0 → A, 25 → Z, 26 → AA */
    static String col(int i) {
        StringBuilder s = new StringBuilder();
        for (int n = i + 1; n > 0; n = (n - 1) / 26) s.insert(0, (char) ('A' + (n - 1) % 26));
        return s.toString();
    }

    private static String sheetName(String n) {
        String s = n.replaceAll("[\\\\/?*\\[\\]:]", "_");
        return s.length() > 31 ? s.substring(0, 31) : s;
    }

    static String esc(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': b.append("&amp;"); break;
                case '<': b.append("&lt;"); break;
                case '>': b.append("&gt;"); break;
                case '"': b.append("&quot;"); break;
                default:
                    if (c == '\t' || c == '\n' || c == '\r' || c >= 0x20 && c != 0xFFFE && c != 0xFFFF) b.append(c);   // drop XML-illegal controls
            }
        }
        return b.toString();
    }

    private static void put(ZipOutputStream z, String name, String content) throws IOException {
        z.putNextEntry(new ZipEntry(name));
        z.write(content.getBytes(StandardCharsets.UTF_8));
        z.closeEntry();
    }

    /** Rows of the first worksheet (all cells as text; empty cells as ""). */
    public static List<String[]> readXlsx(Path file) throws Exception {
        try (ZipFile zf = new ZipFile(file.toFile())) {
            List<String> shared = new ArrayList<String>();
            ZipEntry ss = zf.getEntry("xl/sharedStrings.xml");
            if (ss != null) {
                NodeList sis = parse(zf.getInputStream(ss).readAllBytes()).getElementsByTagNameNS("*", "si");
                for (int i = 0; i < sis.getLength(); i++) shared.add(text((Element) sis.item(i)));
            }
            String sheetPath = firstSheet(zf);
            ZipEntry se = zf.getEntry(sheetPath);
            if (se == null) throw new IllegalArgumentException("no worksheet in " + file);
            NodeList rows = parse(zf.getInputStream(se).readAllBytes()).getElementsByTagNameNS("*", "row");
            List<String[]> out = new ArrayList<String[]>();
            for (int i = 0; i < rows.getLength(); i++) {
                Map<Integer, String> cells = new HashMap<Integer, String>();
                int max = -1, next = 0;
                NodeList cs = ((Element) rows.item(i)).getElementsByTagNameNS("*", "c");
                for (int j = 0; j < cs.getLength(); j++) {
                    Element c = (Element) cs.item(j);
                    int idx = c.hasAttribute("r") ? colIndex(c.getAttribute("r")) : next;
                    next = idx + 1;
                    String t = c.getAttribute("t");
                    String v;
                    if ("inlineStr".equals(t)) {
                        NodeList is = c.getElementsByTagNameNS("*", "is");
                        v = is.getLength() > 0 ? text((Element) is.item(0)) : "";
                    } else {
                        NodeList vs = c.getElementsByTagNameNS("*", "v");
                        v = vs.getLength() > 0 ? vs.item(0).getTextContent() : "";
                        if ("s".equals(t) && !v.isEmpty()) v = shared.get(Integer.parseInt(v.trim()));
                        else if (v.matches("-?\\d+\\.0")) v = v.substring(0, v.length() - 2);
                    }
                    cells.put(idx, v);
                    max = Math.max(max, idx);
                }
                String[] row = new String[max + 1];
                for (int k = 0; k <= max; k++) row[k] = cells.getOrDefault(k, "");
                out.add(row);
            }
            return out;
        }
    }

    private static String firstSheet(ZipFile zf) throws Exception {
        ZipEntry wbe = zf.getEntry("xl/workbook.xml"), re = zf.getEntry("xl/_rels/workbook.xml.rels");
        if (wbe != null && re != null) {
            NodeList sheets = parse(zf.getInputStream(wbe).readAllBytes()).getElementsByTagNameNS("*", "sheet");
            if (sheets.getLength() > 0) {
                Element s = (Element) sheets.item(0);
                String rid = s.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id");
                NodeList rels = parse(zf.getInputStream(re).readAllBytes()).getElementsByTagNameNS("*", "Relationship");
                for (int i = 0; i < rels.getLength(); i++) {
                    Element r = (Element) rels.item(i);
                    if (r.getAttribute("Id").equals(rid)) {
                        String t = r.getAttribute("Target");
                        return t.startsWith("/") ? t.substring(1) : "xl/" + t;
                    }
                }
            }
        }
        return "xl/worksheets/sheet1.xml";
    }

    private static int colIndex(String ref) {
        int n = 0;
        for (char c : ref.toCharArray()) {
            if (c < 'A' || c > 'Z') break;
            n = n * 26 + (c - 'A' + 1);
        }
        return n - 1;
    }

    /** Text of an si / is element: every t, skipping phonetic runs (rPh). */
    private static String text(Element e) {
        StringBuilder b = new StringBuilder();
        NodeList ts = e.getElementsByTagNameNS("*", "t");
        for (int i = 0; i < ts.getLength(); i++) {
            Node p = ts.item(i).getParentNode();
            if (p != null && "rPh".equals(p.getLocalName())) continue;
            b.append(ts.item(i).getTextContent());
        }
        return b.toString();
    }

    private static Document parse(byte[] xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setExpandEntityReferences(false);
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }

    // ---------------------------------------------------------------- csv

    /** RFC 4180, UTF-8 with BOM (so Excel detects the encoding), CRLF line ends. */
    public static void writeCsv(Path file, String[] header, List<String[]> rows) throws IOException {
        StringBuilder b = new StringBuilder("﻿");
        line(b, header);
        for (String[] r : rows) line(b, r);
        Files.write(file, b.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void line(StringBuilder b, String[] cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) b.append(',');
            String v = csvSafe(cells[i] == null ? "" : cells[i]);
            if (v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0) {
                b.append('"').append(v.replace("\"", "\"\"")).append('"');
            } else {
                b.append(v);
            }
        }
        b.append("\r\n");
    }

    /**
     * E7: CSV injection (OWASP) — a cell starting with = + - @ tab or CR is a formula to Excel (a "@smoke" tag shows
     * #NAME?), so it is written with a leading ' (csv only; xlsx cells are typed strings).
     */
    static String csvSafe(String v) {
        return !v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0 ? "'" + v : v;
    }

    public static List<String[]> readCsv(Path file) throws IOException {
        String s = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        if (s.startsWith("﻿")) s = s.substring(1);
        List<String[]> rows = new ArrayList<String[]>();
        List<String> cur = new ArrayList<String>();
        StringBuilder f = new StringBuilder();
        boolean q = false, any = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (q) {
                if (c == '"') {
                    if (i + 1 < s.length() && s.charAt(i + 1) == '"') { f.append('"'); i++; }
                    else q = false;
                } else {
                    f.append(c);
                }
                continue;
            }
            if (c == '"') { q = true; any = true; }
            else if (c == ',') { cur.add(f.toString()); f.setLength(0); any = true; }
            else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < s.length() && s.charAt(i + 1) == '\n') i++;
                if (any || f.length() > 0) { cur.add(f.toString()); rows.add(cur.toArray(new String[0])); }
                cur = new ArrayList<String>();
                f.setLength(0);
                any = false;
            } else { f.append(c); any = true; }
        }
        if (any || f.length() > 0) { cur.add(f.toString()); rows.add(cur.toArray(new String[0])); }
        for (String[] r : rows) for (int i = 0; i < r.length; i++) {
            // E7: undo csvSafe's ' prefix
            if (r[i].length() > 1 && r[i].charAt(0) == '\'' && "=+-@\t\r".indexOf(r[i].charAt(1)) >= 0) r[i] = r[i].substring(1);
        }
        return rows;
    }

    /** id, title, module from a requirements .csv/.xlsx; a header row (first cell "id"/"requirement…") is skipped. */
    public static List<Traceability.Req> readRequirements(Path file) throws Exception {
        String n = file.getFileName().toString().toLowerCase(Locale.ROOT);
        List<String[]> rows = n.endsWith(".xlsx") ? readXlsx(file) : readCsv(file);
        List<Traceability.Req> out = new ArrayList<Traceability.Req>();
        for (int i = 0; i < rows.size(); i++) {
            String[] r = rows.get(i);
            if (r.length == 0 || r[0].isBlank()) continue;
            String first = r[0].trim().toLowerCase(Locale.ROOT);
            if (i == 0 && (first.equals("id") || first.startsWith("requirement") || first.startsWith("req"))
                    && !first.matches("req-?\\d+")) {
                continue;
            }
            out.add(new Traceability.Req(r[0].trim(), r.length > 1 ? r[1].trim() : "", r.length > 2 ? r[2].trim() : ""));
        }
        return out;
    }
}
