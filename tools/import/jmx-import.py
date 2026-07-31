#!/usr/bin/env python3
"""
jmx-import.py  --  Best-effort JMeter (.jmx) -> GATF importer  [Phase 11].

Parses a JMeter test plan and emits:
  * <out>.xml                 a GATF <TestCases> suite (one TestCase per HTTP sampler)
  * <out>.gatf-config.xml     an executor config snippet seeded from the Thread Group
                              (concurrentUserSimulationNum / rampUp / loadTestingTime)
  * <out>.unsupported.txt     an honest report of everything NOT converted

This is a *best-effort transpiler*, not a lossless converter -- the goal is a
running GATF starting point. Unsupported JMeter constructs (JSR223 scripting,
BeanShell, most timers, controllers, CSV data sets, correlation/regex
extractors, etc.) are reported rather than silently dropped.

Dependency-free (stdlib xml.etree only). Usage:
    python3 tools/import/jmx-import.py PLAN.jmx [-o OUTBASE]
Default OUTBASE is the input path without .jmx.
"""
import argparse
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from xml.sax.saxutils import escape

# ---- JMeter property helpers ---------------------------------------------

def prop(elem, name):
    """First <*Prop name="name"> text under elem (direct children only)."""
    for c in list(elem):
        if c.get("name") == name:
            return (c.text or "").strip()
    return None


def coll(elem, name):
    """A <collectionProp name="name"> element under elem, or None."""
    for c in list(elem):
        if c.tag == "collectionProp" and c.get("name") == name:
            return c
    return None


def elemprop(elem, name):
    for c in list(elem):
        if c.tag == "elementProp" and c.get("name") == name:
            return c
    return None


# ---- extractors -----------------------------------------------------------

def parse_thread_group(tg):
    """-> dict of load knobs (best-effort)."""
    num = prop(tg, "ThreadGroup.num_threads")
    ramp = prop(tg, "ThreadGroup.ramp_time")
    loops_e = elemprop(tg, "ThreadGroup.main_controller")
    loops = prop(loops_e, "LoopController.loops") if loops_e is not None else prop(tg, "LoopController.loops")
    duration = prop(tg, "ThreadGroup.duration")
    scheduler = prop(tg, "ThreadGroup.scheduler")
    return {
        "name": tg.get("testname", "ThreadGroup"),
        "threads": _int(num, 1),
        "rampSec": _int(ramp, 0),
        "loops": loops,
        "durationSec": _int(duration, 0),
        "scheduler": scheduler == "true",
    }


def parse_http_sampler(s, headers_inherited, report):
    domain = prop(s, "HTTPSampler.domain") or ""
    port = prop(s, "HTTPSampler.port") or ""
    protocol = (prop(s, "HTTPSampler.protocol") or "").strip()
    path = prop(s, "HTTPSampler.path") or "/"
    method = (prop(s, "HTTPSampler.method") or "GET").upper()
    name = s.get("testname", method + " " + path)

    # base url
    base = ""
    if domain:
        proto = protocol or "http"
        base = proto + "://" + domain + ((":" + port) if port else "")

    # body vs query args
    post_raw = prop(s, "HTTPSampler.postBodyRaw") == "true"
    body = None
    query = []
    args_ep = elemprop(s, "HTTPsampler.Arguments")
    if args_ep is None:
        args_ep = elemprop(s, "arguments")
    if args_ep is not None:
        argcoll = coll(args_ep, "Arguments.arguments")
        if argcoll is not None:
            for a in list(argcoll):
                if a.tag != "elementProp":
                    continue
                val = prop(a, "Argument.value")
                key = prop(a, "Argument.name")
                if post_raw or not key:
                    body = (body or "") + (val or "")
                else:
                    query.append((key, val or ""))
    if query and method == "GET":
        qs = "&".join("%s=%s" % (k, v) for k, v in query)
        path = path + ("&" if "?" in path else "?") + qs
    elif query and body is None:
        # form-encoded body
        body = "&".join("%s=%s" % (k, v) for k, v in query)

    tc = {
        "name": name,
        "baseUrl": base,
        "url": path,
        "method": method,
        "headers": dict(headers_inherited),
        "content": body,
        "expectedResCode": None,
        "expectedNodes": [],
    }
    return tc


def parse_header_manager(hm):
    out = {}
    hcoll = coll(hm, "HeaderManager.headers")
    if hcoll is None:
        return out
    for h in list(hcoll):
        if h.tag != "elementProp":
            continue
        n = prop(h, "Header.name")
        v = prop(h, "Header.value")
        if n:
            out[n] = v or ""
    return out


def apply_assertion(tc, asn, report):
    """Map a ResponseAssertion onto the TestCase (best-effort)."""
    field = prop(asn, "Assertion.test_field") or ""
    tcoll = coll(asn, "Asserion.test_strings")
    if tcoll is None:
        tcoll = coll(asn, "Assertion.test_strings")
    strings = []
    if tcoll is not None:
        for st in list(tcoll):
            if (st.text or "").strip():
                strings.append(st.text.strip())
    if not strings:
        return
    if field == "Assertion.response_code":
        # first numeric assertion -> expected response code
        for v in strings:
            if v.isdigit():
                tc["expectedResCode"] = v
                return
    elif field in ("Assertion.response_data", "Assertion.response_message", ""):
        # substring-in-body assertions -> GATF node checks are JSONPath/XPath, not
        # substrings, so we can't map faithfully. Report it.
        report.append("ResponseAssertion on '%s' for '%s' (substring match: %s) "
                      "-- GATF asserts JSONPath/XPath nodes, not raw substrings; not converted"
                      % (field or "response_data", tc["name"], ", ".join(strings[:3])))
    else:
        report.append("ResponseAssertion field '%s' on '%s' not converted" % (field, tc["name"]))


# ---- tree walk ------------------------------------------------------------

SUPPORTED_SAMPLERS = {"HTTPSamplerProxy", "HTTPSampler2"}

def walk(node, ctx, testcases, load, report):
    """
    node is a <hashTree>. JMeter alternates: <Element/> then its <hashTree/>.
    ctx carries inherited headers + current thread-group name.
    """
    children = list(node)
    i = 0
    while i < len(children):
        elem = children[i]
        subtree = children[i + 1] if i + 1 < len(children) and children[i + 1].tag == "hashTree" else None
        i += 2 if subtree is not None else 1

        tag = elem.tag
        enabled = elem.get("enabled", "true") != "false"

        if tag == "hashTree":
            walk(elem, ctx, testcases, load, report)
            continue

        if not enabled:
            report.append("disabled %s '%s' skipped" % (tag, elem.get("testname", "")))
            continue

        if tag == "TestPlan":
            if subtree is not None:
                walk(subtree, ctx, testcases, load, report)

        elif tag in ("ThreadGroup", "SetupThreadGroup", "PostThreadGroup", "kg.apc.jmeter.threads.SteppingThreadGroup"):
            tg = parse_thread_group(elem)
            if load["threads"] == 0:
                load.update(tg)  # first thread group seeds the load config
            newctx = dict(ctx)
            newctx["tg"] = tg["name"]
            if subtree is not None:
                walk(subtree, newctx, testcases, load, report)

        elif tag == "HeaderManager":
            # header manager at this level -> inherited by following siblings
            ctx.setdefault("headers", {}).update(parse_header_manager(elem))

        elif tag in SUPPORTED_SAMPLERS:
            tc = parse_http_sampler(elem, ctx.get("headers", {}), report)
            # sampler-local children (header manager, assertions, timers)
            if subtree is not None:
                local_children = list(subtree)
                j = 0
                while j < len(local_children):
                    ch = local_children[j]
                    j += 1
                    if ch.tag == "HeaderManager":
                        tc["headers"].update(parse_header_manager(ch))
                    elif ch.tag == "ResponseAssertion":
                        apply_assertion(tc, ch, report)
                    elif ch.tag in ("ConstantTimer", "UniformRandomTimer", "GaussianRandomTimer"):
                        report.append("timer %s on '%s' -> not converted (add preWaitMs manually if needed)"
                                      % (ch.tag, tc["name"]))
                    elif ch.tag == "hashTree":
                        pass
                    else:
                        report.append("child %s of sampler '%s' not converted" % (ch.tag, tc["name"]))
            testcases.append(tc)

        elif tag in ("JSR223Sampler", "BeanShellSampler", "DebugSampler", "TestAction",
                     "TCPSampler", "JDBCSampler", "FTPSampler", "JavaSampler"):
            report.append("sampler %s '%s' not supported (non-HTTP or scripted)" % (tag, elem.get("testname", "")))
            if subtree is not None:
                walk(subtree, ctx, testcases, load, report)

        elif tag in ("CSVDataSet",):
            report.append("CSVDataSet '%s' -> map to a GATF data provider manually (globalVariables/dataSourceList)"
                          % elem.get("testname", ""))
        elif tag.endswith("Controller"):
            report.append("controller %s '%s' -> flattened (logic controllers are not modelled)"
                          % (tag, elem.get("testname", "")))
            if subtree is not None:
                walk(subtree, ctx, testcases, load, report)
        elif tag in ("ResultCollector", "Arguments", "ConfigTestElement", "CookieManager", "CacheManager"):
            if tag == "ConfigTestElement":
                # HTTP Request Defaults: pick up a default domain/protocol as fallback base
                d = prop(elem, "HTTPSampler.domain")
                if d:
                    proto = prop(elem, "HTTPSampler.protocol") or "http"
                    p = prop(elem, "HTTPSampler.port")
                    ctx["defaultBase"] = proto + "://" + d + ((":" + p) if p else "")
                    report.append("HTTP Request Defaults -> used as fallback baseUrl (%s)" % ctx["defaultBase"])
            else:
                report.append("%s '%s' not converted" % (tag, elem.get("testname", "")))
            if subtree is not None:
                walk(subtree, ctx, testcases, load, report)
        else:
            report.append("element %s '%s' not converted" % (tag, elem.get("testname", "")))
            if subtree is not None:
                walk(subtree, ctx, testcases, load, report)


# ---- emit -----------------------------------------------------------------

def _int(v, d):
    try:
        return int(float(v))
    except (TypeError, ValueError):
        return d


def emit_suite(testcases, ctx):
    out = ['<TestCases>']
    out.append("  <!-- Imported from JMeter by tools/import/jmx-import.py (best-effort). -->")
    for n, tc in enumerate(testcases, 1):
        base = tc["baseUrl"] or ctx.get("defaultBase", "")
        out.append("  <TestCase>")
        if base:
            out.append("    <baseUrl>%s</baseUrl>" % escape(base))
        out.append("    <url>%s</url>" % escape(tc["url"]))
        out.append("    <name>%s</name>" % escape(tc["name"]))
        out.append("    <method>%s</method>" % escape(tc["method"]))
        out.append("    <secure>false</secure>")
        if tc["headers"]:
            out.append("    <headers>")
            for k, v in tc["headers"].items():
                out.append("      <%s><![CDATA[%s]]></%s>" % (_xtag(k), v, _xtag(k)))
            out.append("    </headers>")
        if tc["content"]:
            out.append("    <content><![CDATA[%s]]></content>" % tc["content"])
        if tc["expectedResCode"]:
            out.append("    <expectedResCode>%s</expectedResCode>" % escape(tc["expectedResCode"]))
        out.append("    <sequence>%d</sequence>" % n)
        out.append("  </TestCase>")
    out.append("</TestCases>")
    return "\n".join(out) + "\n"


def _xtag(name):
    """Header names become XML element names; sanitise (keep it simple/safe)."""
    safe = "".join(c if (c.isalnum() or c == "-") else "-" for c in name)
    if not safe or not (safe[0].isalpha() or safe[0] == "_"):
        safe = "H-" + safe
    return safe


def emit_config(load):
    threads = max(load.get("threads", 1), 1)
    ramp_ms = load.get("rampSec", 0) * 1000
    dur = load.get("durationSec", 0)
    load_ms = dur * 1000 if dur > 0 else 30000
    if load_ms <= 10000:
        load_ms = 30000  # GATF only load-tests when loadTestingTime > 10000ms
    return (
        "<gatf-execute-config>\n"
        "  <!-- Load knobs seeded from JMeter Thread Group '%s' (best-effort). Fill in the paths. -->\n"
        "  <testCasesBasePath>.</testCasesBasePath>\n"
        "  <testCaseDir>tests</testCaseDir>\n"
        "  <outFilesBasePath>.</outFilesBasePath>\n"
        "  <outFilesDir>out</outFilesDir>\n"
        "  <isSeleniumExecutor>false</isSeleniumExecutor>\n"
        "  <authEnabled>false</authEnabled>\n"
        "  <soapAuthEnabled>false</soapAuthEnabled>\n"
        "  <httpConnectionTimeout>10000</httpConnectionTimeout>\n"
        "  <httpRequestTimeout>10000</httpRequestTimeout>\n"
        "  <loadTestingEnabled>true</loadTestingEnabled>\n"
        "  <loadTestingTime>%d</loadTestingTime>\n"
        "  <concurrentUserSimulationNum>%d</concurrentUserSimulationNum>\n"
        "  <concurrentUserRampUpTime>%d</concurrentUserRampUpTime>\n"
        "  <loadTestingReportSamples>6</loadTestingReportSamples>\n"
        "  <numConcurrentExecutions>%d</numConcurrentExecutions>\n"
        "  <isOrderByFileName>true</isOrderByFileName>\n"
        "  <enabled>true</enabled>\n"
        "</gatf-execute-config>\n"
        % (escape(load.get("name", "ThreadGroup")), load_ms, threads, ramp_ms, threads)
    )


# ---- main -----------------------------------------------------------------

def main():
    ap = argparse.ArgumentParser(description="Best-effort JMeter .jmx -> GATF importer")
    ap.add_argument("jmx", help="input .jmx plan")
    ap.add_argument("-o", "--out", help="output base path (default: input without .jmx)")
    args = ap.parse_args()

    src = Path(args.jmx)
    if not src.exists():
        sys.exit("error: no such file: %s" % src)
    outbase = Path(args.out) if args.out else src.with_suffix("")

    try:
        root = ET.parse(str(src)).getroot()
    except ET.ParseError as e:
        sys.exit("error: malformed .jmx: %s" % e)

    # top-level: <jmeterTestPlan><hashTree>...</hashTree></jmeterTestPlan>
    top = root.find("hashTree")
    if top is None:
        sys.exit("error: not a JMeter plan (no top-level hashTree)")

    testcases, report = [], []
    load = {"threads": 0}
    ctx = {"headers": {}}
    walk(top, ctx, testcases, load, report)

    if not testcases:
        report.insert(0, "NOTE: no HTTP samplers found -> empty suite emitted")

    suite_path = Path(str(outbase) + ".xml")
    cfg_path = Path(str(outbase) + ".gatf-config.xml")
    rep_path = Path(str(outbase) + ".unsupported.txt")

    suite_path.write_text(emit_suite(testcases, ctx), encoding="utf-8")
    cfg_path.write_text(emit_config(load), encoding="utf-8")
    rep_lines = ["JMeter -> GATF import report for %s" % src.name,
                 "converted HTTP samplers: %d" % len(testcases),
                 "thread group: %s (threads=%s ramp=%ss)" % (load.get("name", "?"),
                                                              load.get("threads"), load.get("rampSec")),
                 "",
                 "UNSUPPORTED / APPROXIMATED (%d):" % len(report)]
    rep_lines += ["  - " + r for r in report] if report else ["  (none)"]
    rep_path.write_text("\n".join(rep_lines) + "\n", encoding="utf-8")

    print("wrote %s (%d testcases)" % (suite_path, len(testcases)))
    print("wrote %s" % cfg_path)
    print("wrote %s (%d notes)" % (rep_path, len(report)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
