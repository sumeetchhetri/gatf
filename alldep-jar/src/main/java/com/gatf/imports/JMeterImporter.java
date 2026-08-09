/*
    Copyright 2013-2019, Sumeet Chhetri

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
package com.gatf.imports;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.gatf.executor.core.TestCase;

/**
 * Best-effort importer: a JMeter test plan (.jmx) → GATF API test cases.
 *
 * Each HTTPSamplerProxy becomes a {@link TestCase} (baseUrl/url/method/headers/body); the sampler's
 * following-sibling hashTree supplies its HeaderManager (headers), ResponseAssertion (expected code /
 * expected nodes) and JSON/Regex post-processors (workflow variable captures). JMeter's ${var} syntax
 * is GATF's too, so it passes through untouched.
 *
 * Constructs with no GATF equivalent (script samplers, controllers, CSV data sets, timers, thread-group
 * ramp settings) are recorded in the ImportResult report — not silently dropped.
 *
 * The built TestCase list is serialized by the handler with WorkflowContextHandler.XOM — the very mapper
 * GATF uses to read/write test XML — so the output round-trips exactly and opens in the XML case editor.
 * The field mapping is mirrored by tools/imports/jmeter_to_gatf.py for verification.
 */
public class JMeterImporter {

    public ImportResult importSource(String jmx, String suiteName) throws Exception {
        ImportResult res = new ImportResult();
        res.type = "jmeter";
        res.kind = "xml";
        res.fileName = safeName(suiteName) + ".xml";
        List<TestCase> cases = new ArrayList<TestCase>();
        res.testCases = cases;

        Document doc = parse(jmx);

        // advisory: user-defined variables live in gatf-config globalVariables, not in a test file
        int udv = countUserDefinedVars(doc);
        if (udv > 0) {
            res.unsupported(0, udv + " user-defined variable(s)",
                    "JMeter variables map to gatf-config globalVariables — add them there; ${var} refs are kept as-is");
        }
        // advisory: script samplers / controllers / timers / CSV data sets have no direct GATF mapping
        noteUnsupportedElements(doc, res);

        NodeList samplers = doc.getElementsByTagName("HTTPSamplerProxy");
        int seq = 0, headerCount = 0, assertCount = 0, captureCount = 0;
        for (int i = 0; i < samplers.getLength(); i++) {
            Element sampler = (Element) samplers.item(i);
            TestCase tc = new TestCase();
            String name = attr(sampler, "testname");
            tc.setName(name.isEmpty() ? ("Request " + (seq + 1)) : name);
            tc.setSecure(false);

            String protocol = childProp(sampler, "HTTPSampler.protocol");
            String domain = childProp(sampler, "HTTPSampler.domain");
            String port = childProp(sampler, "HTTPSampler.port");
            String path = childProp(sampler, "HTTPSampler.path");
            String method = childProp(sampler, "HTTPSampler.method");
            tc.setMethod(method.isEmpty() ? "GET" : method.toUpperCase(Locale.ROOT));

            if (!domain.isEmpty()) {
                String scheme = protocol.isEmpty() ? "https" : protocol;
                String base = scheme + "://" + domain + (port.isEmpty() || port.equals("80") || port.equals("443") ? "" : ":" + port);
                tc.setBaseUrl(base);
                tc.setUrl(path.isEmpty() ? "/" : path);
            } else {
                // no domain in the plan → relative path, GATF supplies baseUrl from config
                tc.setUrl(path.isEmpty() ? "/" : path);
            }

            // body: raw post body, or reconstructed form/query args
            String body = extractBody(sampler);
            if (body != null && !body.isEmpty()) tc.setContent(body);

            // config from the sampler's following-sibling hashTree
            Element cfg = followingHashTree(sampler);
            Map<String, String> headers = new LinkedHashMap<String, String>();
            if (cfg != null) {
                headerCount += collectHeaders(cfg, headers);
                assertCount += applyAssertions(cfg, tc);
                captureCount += applyExtractors(cfg, tc);
            }
            if (!headers.isEmpty()) tc.setHeaders(headers);

            tc.setSequence(++seq);
            cases.add(tc);
        }

        res.stat("requests", cases.size());
        res.stat("headers", headerCount);
        res.stat("assertions", assertCount);
        res.stat("captures", captureCount);
        res.stat("unsupported", res.unsupported.size());
        return res;
    }

    // ---- body ------------------------------------------------------------------------------------

    private String extractBody(Element sampler) {
        boolean raw = "true".equalsIgnoreCase(childProp(sampler, "HTTPSampler.postBodyRaw"));
        Element args = childElementPropByName(sampler, "HTTPsampler.Arguments");
        if (args == null) return null;
        Element coll = firstChildByTag(args, "collectionProp");
        if (coll == null) return null;
        List<Element> argEls = childrenByTag(coll, "elementProp");
        if (raw) {
            // raw body → a single argument whose Argument.value is the body
            for (Element a : argEls) {
                String v = propOf(a, "Argument.value");
                if (v != null) return v;
            }
            return null;
        }
        // form/query args → name=value&... (best-effort; the method decides if it's query vs form)
        StringBuilder sb = new StringBuilder();
        for (Element a : argEls) {
            String n = propOf(a, "Argument.name");
            String v = propOf(a, "Argument.value");
            if (n == null) continue;
            if (sb.length() > 0) sb.append("&");
            sb.append(n).append("=").append(v == null ? "" : v);
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    // ---- headers ---------------------------------------------------------------------------------

    private int collectHeaders(Element cfg, Map<String, String> headers) {
        int n = 0;
        for (Element hm : childrenByTag(cfg, "HeaderManager")) {
            Element coll = firstChildByTag(hm, "collectionProp");
            if (coll == null) continue;
            for (Element e : childrenByTag(coll, "elementProp")) {
                String name = propOf(e, "Header.name");
                String value = propOf(e, "Header.value");
                if (name != null && !name.isEmpty()) {
                    headers.put(name, value == null ? "" : value);
                    n++;
                }
            }
        }
        return n;
    }

    // ---- assertions → expectedResCode / expectedNodes --------------------------------------------

    private int applyAssertions(Element cfg, TestCase tc) {
        int n = 0;
        List<String> nodes = new ArrayList<String>(tc.getExpectedNodes());
        for (Element ra : childrenByTag(cfg, "ResponseAssertion")) {
            String field = propOf(ra, "Assertion.test_field");
            Element strings = childCollectionByName(ra, "Asserion.test_strings"); // JMeter's (sic) element name
            if (strings == null) strings = childCollectionByName(ra, "Assertion.test_strings");
            List<String> vals = new ArrayList<String>();
            if (strings != null) {
                NodeList sp = strings.getElementsByTagName("stringProp");
                for (int i = 0; i < sp.getLength(); i++) {
                    String t = text(sp.item(i));
                    if (t != null && !t.isEmpty()) vals.add(t);
                }
            }
            if (field != null && field.contains("response_code")) {
                for (String v : vals) {
                    try {
                        tc.setExpectedResCode(Integer.parseInt(v.trim()));
                        n++;
                    } catch (NumberFormatException ignore) {
                    }
                }
            } else {
                // response_data / substring / document → treat each as an expected node (substring match)
                for (String v : vals) {
                    nodes.add(v);
                    n++;
                }
            }
        }
        if (!nodes.isEmpty()) tc.setExpectedNodes(nodes);
        return n;
    }

    // ---- extractors → workflowContextParameterMap ------------------------------------------------

    private int applyExtractors(Element cfg, TestCase tc) {
        int n = 0;
        Map<String, String> caps = new LinkedHashMap<String, String>(tc.getWorkflowContextParameterMap());
        for (Element jp : childrenByTag(cfg, "JSONPostProcessor")) {
            String names = propOf(jp, "JSONPostProcessor.referenceNames");
            String exprs = propOf(jp, "JSONPostProcessor.jsonPathExprs");
            if (names != null && exprs != null) {
                String[] nm = names.split(";");
                String[] ex = exprs.split(";");
                for (int i = 0; i < nm.length; i++) {
                    String key = nm[i].trim();
                    String val = i < ex.length ? ex[i].trim() : "";
                    if (!key.isEmpty()) {
                        caps.put(key, val);
                        n++;
                    }
                }
            }
        }
        for (Element re : childrenByTag(cfg, "RegexExtractor")) {
            String ref = propOf(re, "RegexExtractor.refname");
            String rex = propOf(re, "RegexExtractor.regex");
            if (ref != null && !ref.trim().isEmpty()) {
                caps.put(ref.trim(), rex == null ? "" : rex);
                n++;
            }
        }
        if (!caps.isEmpty()) tc.setWorkflowContextParameterMap(caps);
        return n;
    }

    // ---- advisories ------------------------------------------------------------------------------

    private int countUserDefinedVars(Document doc) {
        int n = 0;
        NodeList args = doc.getElementsByTagName("Arguments");
        for (int i = 0; i < args.getLength(); i++) {
            Element coll = firstChildByTag((Element) args.item(i), "collectionProp");
            if (coll != null) n += childrenByTag(coll, "elementProp").size();
        }
        return n;
    }

    private void noteUnsupportedElements(Document doc, ImportResult res) {
        String[] unsupported = {
                "JSR223Sampler", "BeanShellSampler", "IfController", "LoopController", "WhileController",
                "ForeachController", "CSVDataSet", "ConstantTimer", "UniformRandomTimer", "GaussianRandomTimer",
                "TCPSampler", "JDBCSampler", "FTPSampler", "DebugSampler"
        };
        for (String tag : unsupported) {
            NodeList nl = doc.getElementsByTagName(tag);
            if (nl.getLength() > 0) {
                res.unsupported(0, tag + " x" + nl.getLength(),
                        "no direct GATF equivalent — translate manually (logic/timers/data-sources are out of scope for a test-case import)");
            }
        }
    }

    // ---- DOM helpers -----------------------------------------------------------------------------

    private Document parse(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(false);
        // harden the parser against XXE — this is untrusted uploaded content
        try { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); } catch (Exception ignore) {}
        try { f.setFeature("http://xml.org/sax/features/external-general-entities", false); } catch (Exception ignore) {}
        try { f.setFeature("http://xml.org/sax/features/external-parameter-entities", false); } catch (Exception ignore) {}
        f.setExpandEntityReferences(false);
        DocumentBuilder b = f.newDocumentBuilder();
        return b.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private String attr(Element e, String name) {
        String v = e.getAttribute(name);
        return v == null ? "" : v;
    }

    // value of a direct-child <stringProp name="key">value</stringProp> (or boolProp/intProp)
    private String childProp(Element parent, String name) {
        String v = propOf(parent, name);
        return v == null ? "" : v;
    }

    private String propOf(Element parent, String name) {
        NodeList kids = parent.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node k = kids.item(i);
            if (k.getNodeType() != Node.ELEMENT_NODE) continue;
            Element e = (Element) k;
            String tag = e.getTagName();
            if ((tag.equals("stringProp") || tag.equals("boolProp") || tag.equals("intProp") || tag.equals("longProp"))
                    && name.equals(e.getAttribute("name"))) {
                return text(e);
            }
        }
        return null;
    }

    private Element childElementPropByName(Element parent, String name) {
        for (Element e : childrenByTag(parent, "elementProp")) {
            if (name.equals(e.getAttribute("name"))) return e;
        }
        return null;
    }

    private Element childCollectionByName(Element parent, String name) {
        for (Element e : childrenByTag(parent, "collectionProp")) {
            if (name.equals(e.getAttribute("name"))) return e;
        }
        return null;
    }

    private Element firstChildByTag(Element parent, String tag) {
        List<Element> l = childrenByTag(parent, tag);
        return l.isEmpty() ? null : l.get(0);
    }

    private List<Element> childrenByTag(Element parent, String tag) {
        List<Element> out = new ArrayList<Element>();
        NodeList kids = parent.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node k = kids.item(i);
            if (k.getNodeType() == Node.ELEMENT_NODE && ((Element) k).getTagName().equals(tag)) {
                out.add((Element) k);
            }
        }
        return out;
    }

    // the <hashTree> immediately following an element = that element's children/config in a .jmx
    private Element followingHashTree(Element el) {
        Node n = el.getNextSibling();
        while (n != null && n.getNodeType() != Node.ELEMENT_NODE) n = n.getNextSibling();
        if (n != null && ((Element) n).getTagName().equals("hashTree")) return (Element) n;
        return null;
    }

    private String text(Node n) {
        return n == null ? null : n.getTextContent();
    }

    private String safeName(String s) {
        if (s == null || s.trim().isEmpty()) return "imported";
        return s.trim().replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
