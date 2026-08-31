# Phase 12 — Portal crawler → seleasy starter suite + coverage map

BFS-walks a portal and emits a **starter seleasy suite** (a recorded-style walkthrough you then
enrich with assertions) plus a **coverage map** (`crawl-coverage.json`) of every page/form/input seen.

Implementation: `com.gatf.crawler.PortalCrawler` (`+ CrawlSpec`) — HTTP + lightweight HTML scan, no
jsoup. (The Python reference was retired in delta 074.)

## Emitted grammar (FgRecorder-compatible)

```
open chrome
maximize
goto <url>
type "value" xpath@"<xpath>"
click xpath@"<xpath>"
select index@N xpath@"<xpath>"
sleep <milliseconds>
```

XPath is id-first (`//*[@id='..']` → `//tag[@name='..']` → `//tag[contains(@class,'..')]` →
`(//tag)[N]`). Values are chosen by input `type` (email→`user@example.com`, tel→`9999999999`,
date→`2020-01-01`, textarea→`sample text`, select→`index@1`, checkbox/radio→`click`; hidden/file
skipped). GET forms are submitted by default; POST forms only with `--submit-forms`.

## Run

```
java -cp gatf.jar com.gatf.crawler.PortalCrawler \
  --seed http://host/ [--max-pages 25] [--max-depth 3] [--allow-off-origin] [--submit-forms] \
  [--login-url URL --username U --password P [--user-sel id@u --pass-sel id@p --submit-sel 'xpath@//button']] \
  [--out-dir DIR] [--suite-name crawl-suite]
```

## Credentialed crawl

Pass `--login-url/--username/--password`. If `--user-sel/--pass-sel` are omitted, the login form is
auto-discovered (the form containing a `type=password` field; the preceding text/email input is the
username). The emitted suite performs the login block first, then walks the pages.

## JavaScript-rendered apps (SPA) — use the browser crawler

The HTTP crawler above only sees server-rendered HTML. For React/Angular/Vue apps use
`com.gatf.crawler.SpaCrawler` — the **Crawl** view in /app (More → Crawl) or `POST /crawl?action=start`.
It walks the *rendered* DOM in a real Chrome (headless by default; honours `SELENIUM_REMOTE_URL` and
`GATF_CHROME_BINARY=headless-shell`), follows `a[href]`, hash routes (`#/…`) and `routerlink`, and captures
forms, inputs outside `<form>` and action buttons. It emits a starter `.sel` plus a prioritized
`<name>-coverage.json`. Logout/delete-style links are never followed (override with `exclude`), POST forms
are only submitted with `submitForms`, and credentials appear in the suite only as `${username}` /
`${password}`.

## Verify the emitted suite

```
python3 tools/smoke/sel-lint.py <out-dir>/crawl-suite.sel
```
