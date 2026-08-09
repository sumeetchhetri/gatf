#!/usr/bin/env python3
"""
Phase 12 — credentialed/uncredentialed portal crawler → starter seleasy suite + coverage map.

Reference implementation (stdlib only) of the crawl + seleasy-emission algorithm that
com.gatf.crawler.PortalCrawler implements in Java. Kept in lock-step with the Java so the
emitted grammar can be verified against tools/smoke/sel-lint.py without a JVM/browser.

Emission grammar is exactly the FgRecorder grammar (the recorder shipped with GATF):
    open chrome / maximize / goto <url> / type "v" xpath@".." / click xpath@".." /
    select index@N xpath@'..' / sleep <seconds>
so a generated suite is a recorded-style walkthrough the user then enriches with assertions.

Usage:
    portal_crawler.py --seed http://host/ [--max-pages 25] [--max-depth 3] [--same-origin]
        [--login-url URL --username U --password P
         [--user-sel id@username --pass-sel id@password --submit-sel 'xpath@//button']]
        [--submit-forms] [--out-dir DIR] [--suite-name crawl-suite]
"""
import argparse, json, os, sys, re
from html.parser import HTMLParser
from urllib.parse import urljoin, urlparse, urldefrag
from urllib.request import Request, urlopen
from urllib.error import URLError, HTTPError

UA = "gatf-portal-crawler"

# ---- HTML parsing (links + forms + inputs), stdlib HTMLParser --------------------------------

class PageParser(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.links = []          # hrefs
        self.title = None
        self._in_title = False
        self.forms = []          # {action, method, inputs:[{tag,type,name,id,class,idx}]}
        self._form = None
        self._tag_counts = {}    # per-tag running index for positional xpath fallback

    def handle_starttag(self, tag, attrs):
        a = {k.lower(): (v or "") for k, v in attrs}
        idx = self._tag_counts.get(tag, 0) + 1
        self._tag_counts[tag] = idx
        if tag == "title":
            self._in_title = True
        elif tag == "a" and "href" in a:
            self.links.append(a["href"])
        elif tag == "form":
            self._form = {"action": a.get("action", ""), "method": (a.get("method", "get") or "get").lower(), "inputs": []}
        elif tag in ("input", "select", "textarea", "button"):
            el = {"tag": tag, "type": a.get("type", "").lower(), "name": a.get("name", ""),
                  "id": a.get("id", ""), "class": a.get("class", ""), "idx": idx}
            if self._form is not None:
                self._form["inputs"].append(el)

    def handle_endtag(self, tag):
        if tag == "title":
            self._in_title = False
        elif tag == "form" and self._form is not None:
            self.forms.append(self._form)
            self._form = None

    def handle_data(self, data):
        if self._in_title and data.strip():
            self.title = (self.title or "") + data.strip()

# ---- seleasy xpath + value mapping (shared contract with the Java) ---------------------------

def xpath_for(el):
    """id-first stable xpath, mirroring FgRecorder's id-preferring getXPath."""
    if el.get("id"):
        return "//*[@id='%s']" % el["id"]
    if el.get("name"):
        return "//%s[@name='%s']" % (el["tag"], el["name"])
    if el.get("class"):
        first = el["class"].split()[0]
        return "//%s[contains(@class,'%s')]" % (el["tag"], first)
    return "(//%s)[%d]" % (el["tag"], el["idx"])

SAMPLE_BY_TYPE = {
    "email": "user@example.com", "password": "P@ssw0rd", "number": "1", "tel": "9999999999",
    "url": "https://example.com", "date": "2020-01-01", "search": "sample", "text": "sample", "": "sample",
}

def fill_lines_for_form(form, submit_forms):
    """Emit FgRecorder-style fill lines for one form. Returns (lines, filled_count, submit_emitted)."""
    lines, filled = [], 0
    submit_el = None
    for el in form["inputs"]:
        tag, typ = el["tag"], el["type"]
        xp = xpath_for(el)
        if tag == "textarea":
            lines.append('type "sample text" xpath@"%s"' % xp); filled += 1
        elif tag == "select":
            lines.append('select index@1 xpath@"%s"' % xp); filled += 1
        elif tag == "button" or typ in ("submit",):
            submit_el = submit_el or el
        elif typ in ("checkbox", "radio"):
            lines.append('click xpath@"%s"' % xp); filled += 1
        elif typ in ("hidden", "file", "image", "reset"):
            continue
        else:
            val = SAMPLE_BY_TYPE.get(typ, "sample")
            lines.append('type "%s" xpath@"%s"' % (val, xp)); filled += 1
    submit_emitted = False
    # Submit only GET forms by default (searches/filters — non-mutating); POST needs --submit-forms.
    if submit_el is not None and (submit_forms or form["method"] == "get"):
        lines.append('click xpath@"%s"' % xpath_for(submit_el))
        lines.append("sleep 1")
        submit_emitted = True
    return lines, filled, submit_emitted

def selector_from_el(el):
    """Turn a discovered element into a seleasy selector (id@ / name@ / xpath@)."""
    if el.get("id"):
        return "id@%s" % el["id"]
    if el.get("name"):
        return "name@%s" % el["name"]
    return 'xpath@"%s"' % xpath_for(el)

# ---- fetch -----------------------------------------------------------------------------------

def fetch(url, timeout=20):
    req = Request(url, headers={"User-Agent": UA})
    try:
        with urlopen(req, timeout=timeout) as r:
            final = r.geturl()
            ctype = r.headers.get_content_type()
            body = r.read(2_000_000)
            enc = r.headers.get_content_charset() or "utf-8"
            return r.status, final, ctype, body.decode(enc, "replace")
    except HTTPError as e:
        return e.code, url, "", ""
    except (URLError, Exception) as e:
        return 0, url, "", ""

def same_origin(u, seed):
    a, b = urlparse(u), urlparse(seed)
    return (a.scheme, a.netloc) == (b.scheme, b.netloc)

def norm(url):
    return urldefrag(url)[0]

# ---- login discovery -------------------------------------------------------------------------

def discover_login(login_html):
    """Find (user_sel, pass_sel, submit_sel) from the form containing a password field."""
    p = PageParser(); p.feed(login_html)
    for form in p.forms:
        pw = next((e for e in form["inputs"] if e["type"] == "password"), None)
        if not pw:
            continue
        # user field = first text/email input before the password field
        user = None
        for e in form["inputs"]:
            if e is pw:
                break
            if e["tag"] == "input" and e["type"] in ("text", "email", "", "tel"):
                user = e
        submit = next((e for e in form["inputs"] if e["tag"] == "button" or e["type"] == "submit"), None)
        return (selector_from_el(user) if user else None,
                selector_from_el(pw),
                selector_from_el(submit) if submit else None)
    return (None, None, None)

# ---- crawl -----------------------------------------------------------------------------------

def crawl(spec):
    seed = spec["seed"]
    queue = [(seed, 0)]
    seen = set([norm(seed)])
    pages = []
    stats = {"visited": 0, "off_origin": 0, "skipped_scheme": 0, "forms": 0, "inputs": 0, "links": 0}

    while queue and len(pages) < spec["max_pages"]:
        url, depth = queue.pop(0)
        status, final, ctype, html = fetch(url)
        stats["visited"] += 1
        rec = {"url": url, "finalUrl": final, "depth": depth, "status": status,
               "contentType": ctype, "title": None, "links": [], "forms": []}
        if html and "html" in ctype:
            p = PageParser(); p.feed(html)
            rec["title"] = p.title
            forms = []
            for f in p.forms:
                inputs = [{"tag": e["tag"], "type": e["type"], "name": e["name"],
                           "id": e["id"], "xpath": xpath_for(e)} for e in f["inputs"]]
                forms.append({"action": urljoin(final, f["action"]) if f["action"] else final,
                              "method": f["method"], "inputs": inputs})
                stats["inputs"] += len(inputs)
            rec["forms"] = forms
            stats["forms"] += len(forms)
            # enqueue links
            out = []
            for href in p.links:
                absu = norm(urljoin(final, href))
                sch = urlparse(absu).scheme
                if sch not in ("http", "https"):
                    stats["skipped_scheme"] += 1
                    continue
                out.append(absu)
                if not (spec["same_origin"] and not same_origin(absu, seed)):
                    if absu not in seen and depth + 1 <= spec["max_depth"]:
                        seen.add(absu)
                        queue.append((absu, depth + 1))
                elif spec["same_origin"] and not same_origin(absu, seed):
                    stats["off_origin"] += 1
            rec["links"] = out
            stats["links"] += len(out)
        pages.append(rec)
    return pages, stats

# ---- emit seleasy ----------------------------------------------------------------------------

def emit_seleasy(spec, pages):
    L = ["// Auto-generated by GATF Phase 12 portal crawler — a starter suite; enrich with assertions.",
         "// seed: %s" % spec["seed"], "open chrome", "maximize", ""]
    forms_emitted = filled_total = submits = 0
    if spec.get("login"):
        lg = spec["login"]
        L += ["// --- login ---", "goto %s" % lg["url"]]
        if lg.get("user_sel") and lg.get("pass_sel"):
            L.append('type "%s" %s' % (lg["username"], lg["user_sel"]))
            L.append('type "%s" %s' % (lg["password"], lg["pass_sel"]))
            if lg.get("submit_sel"):
                L.append("click %s" % lg["submit_sel"])
        L += ["sleep 2", ""]
    for i, pg in enumerate(pages):
        if pg["status"] == 0:
            continue
        L.append("// page %d: %s%s" % (i + 1, pg["url"], (" — " + pg["title"]) if pg["title"] else ""))
        L.append("goto %s" % pg["url"])
        L.append("sleep 1")
        for form in pg["forms"]:
            lines, filled, submitted = fill_lines_for_form(
                {"method": form["method"],
                 "inputs": [{"tag": x["tag"], "type": x["type"], "name": x["name"], "id": x["id"], "class": "",
                             "idx": j + 1} for j, x in enumerate(form["inputs"])]},
                spec["submit_forms"])
            if lines:
                forms_emitted += 1
                filled_total += filled
                submits += 1 if submitted else 0
                L += lines
        L.append("")
    return "\n".join(L) + "\n", {"formsEmitted": forms_emitted, "inputsFilled": filled_total, "submitsEmitted": submits}

# ---- main ------------------------------------------------------------------------------------

def main():
    ap = argparse.ArgumentParser(description="GATF Phase 12 portal crawler → seleasy + coverage map")
    ap.add_argument("--seed", required=True)
    ap.add_argument("--max-pages", type=int, default=25)
    ap.add_argument("--max-depth", type=int, default=3)
    ap.add_argument("--same-origin", action="store_true", default=True)
    ap.add_argument("--allow-off-origin", dest="same_origin", action="store_false")
    ap.add_argument("--login-url"); ap.add_argument("--username"); ap.add_argument("--password")
    ap.add_argument("--user-sel"); ap.add_argument("--pass-sel"); ap.add_argument("--submit-sel")
    ap.add_argument("--submit-forms", action="store_true")
    ap.add_argument("--out-dir", default=".")
    ap.add_argument("--suite-name", default="crawl-suite")
    a = ap.parse_args()

    spec = {"seed": a.seed, "max_pages": a.max_pages, "max_depth": a.max_depth,
            "same_origin": a.same_origin, "submit_forms": a.submit_forms}
    if a.username and a.password and a.login_url:
        user_sel, pass_sel, submit_sel = a.user_sel, a.pass_sel, a.submit_sel
        if not (user_sel and pass_sel):
            _, _, _, lhtml = fetch(a.login_url)
            du, dp, ds = discover_login(lhtml)
            user_sel = user_sel or du; pass_sel = pass_sel or dp; submit_sel = submit_sel or ds
        spec["login"] = {"url": a.login_url, "username": a.username, "password": a.password,
                         "user_sel": user_sel, "pass_sel": pass_sel, "submit_sel": submit_sel}

    pages, stats = crawl(spec)
    sel, emit_stats = emit_seleasy(spec, pages)

    os.makedirs(a.out_dir, exist_ok=True)
    sel_path = os.path.join(a.out_dir, a.suite_name + ".sel")
    cov_path = os.path.join(a.out_dir, "crawl-coverage.json")
    with open(sel_path, "w", encoding="utf-8") as f:
        f.write(sel)
    coverage = {"seed": a.seed,
                "spec": {k: spec[k] for k in ("max_pages", "max_depth", "same_origin", "submit_forms")},
                "credentialed": "login" in spec,
                "stats": {**stats, **emit_stats, "pagesEmitted": sum(1 for p in pages if p["status"])},
                "pages": pages}
    with open(cov_path, "w", encoding="utf-8") as f:
        json.dump(coverage, f, indent=2)
    print("wrote %s (%d lines) and %s" % (sel_path, sel.count("\n"), cov_path))
    print("stats:", json.dumps(coverage["stats"]))

if __name__ == "__main__":
    main()
