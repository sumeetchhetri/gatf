# Phase 11 — Playwright / JMeter test importer

UI-enabled, Java-backed import of external tests into GATF. Open **Import** (under the *More* menu in
the /app console), choose Playwright or JMeter, paste or upload the source, optionally name the output
file, then **Preview** (translate only) or **Save to test files** (write into the test-case directory).

## Endpoint

```
POST /import?type=playwright|jmeter[&fileName=<name>][&save=true]
  body = raw Playwright .spec source  (type=playwright)  → a .sel suite
       | raw JMeter .jmx XML          (type=jmeter)      → a GATF API .xml test file
  ->   { type, fileName, kind, content, unsupported:[{line,construct,reason}], stats, saved, savedPath?, validation? }
```

With `save=true` the file is written into the test-case dir (same place `/testcasefiles` create uses),
so it appears in the test tree. JMeter cases are serialized with GATF's own XmlMapper
(`WorkflowContextHandler.XOM`) — the exact mapper GATF uses to read/write test XML — so the output
round-trips and opens in the structured XML case editor; imported `.sel` is additionally
parse-validated (`Command.validateSel`) and the result returned.

Java: `com.gatf.imports.{PlaywrightImporter,JMeterImporter,ImportResult}` + handler
`com.gatf.ui.GatfImportHandler`. Both importers are **best-effort** — constructs with no GATF
equivalent are reported (never dropped silently). Covered by `ImportersTest` (JUnit) against the
fixtures in `alldep-jar/src/test/resources/imports/`. (The earlier Python references were retired in
delta 074.)

## Playwright → seleasy

Each `test('name', …)` becomes a file-scope `subtest "name" { … }` (auto-runs, per `sample/test.sel`).

| Playwright | seleasy |
|---|---|
| `page.goto(url)` | `goto url` |
| `page.fill(sel,v)` / `page.type(sel,v)` / `locator(sel).fill(v)` | `type "v" <sel>` |
| `page.click(sel)` / `locator(sel).click()` | `click <sel>` |
| `page.dblclick(sel)` | `doubleclick <sel>` |
| `page.hover(sel)` | `hover <sel>` |
| `page.check/uncheck(sel)` | `click <sel>` |
| `page.selectOption(sel, v)` | `select value@"v" <sel>` (`{label}`→text@, `{index}`→index@) |
| `page.press(sel, key)` | `sendkeys "key" <sel>` |
| `page.setInputFiles(sel, file)` | `upload "file" <sel>` |
| `page.waitForTimeout(ms)` | `sleep <ms>` (seleasy sleep is milliseconds) |
| `page.goBack/goForward/reload()` | `back` / `forward` / `refresh` |
| `page.waitForSelector(sel)` | `??+ <sel>` |
| `expect(loc).toBeVisible()` | `??+ <sel>` |
| `expect(loc).toHaveText(t)` | `?? <sel> text ="t"` |
| `expect(loc).toContainText(t)` | `??+ text@"t"` |
| `expect(loc).toHaveValue(v)` | `?? <sel> attr@value ="v"` |
| `expect(loc).toBeChecked()` | `?? <sel> attr@checked ="true"` |
| `expect(loc).toBeHidden()` | `??- <sel>` |

Hooks: `test.beforeEach/afterEach` are inlined at the start/end of every test's subtest;
`test.beforeAll/afterAll` become a first/last subtest.

Selectors: `#id`→`id@`, CSS→`css@`, `text=`/`getByText`→`text@`, `xpath=`/`//…`→`xpath@`,
`getByRole/Label/Placeholder/TestId`→best-effort `xpath@`. Not translated (reported): `toHaveURL`,
`toHaveTitle`, `toHaveCount`, arbitrary JS in the test body. One action per source line.

## JMeter → GATF API test case

Each `HTTPSamplerProxy` → a `<TestCase>`.

| JMeter | GATF TestCase |
|---|---|
| protocol + domain + port (else in-scope HTTP Request Defaults) | `baseUrl` |
| path (+ GET form args as query) | `url` |
| method | `method` |
| raw post body / form args | `content` |
| `HeaderManager` at any enclosing level + sibling (nearest wins) | `headers` |
| `ResponseAssertion` (response_code) | `expectedResCode` |
| `ResponseAssertion` (response_data/substring) | `expectedNodes` |
| `JSONPostProcessor` / `RegexExtractor` | `workflowContextParameterMap` (captures) |

Disabled elements (and everything under them) are skipped and reported. The first enabled Thread
Group is returned as `loadConfig` — `{loadTestingEnabled, concurrentUserSimulationNum,
concurrentUserRampUpTime(ms), loadTestingTime(ms)}` to paste into gatf-config (duration < 10 s ⇒ 30 s,
since GATF only load-tests above 10 s).

`${var}` passes through unchanged (JMeter and GATF share the syntax). Reported (no direct mapping):
script samplers (JSR223/BeanShell), controllers (If/Loop/ForEach), CSV Data Sets, timers; user-defined variables are noted for moving into `gatf-config` globalVariables.
