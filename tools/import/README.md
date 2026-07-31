# Test importers (Phase 11)

Best-effort converters that give you a **running GATF starting point** from an
existing suite. Neither claims lossless conversion — both emit an honest
`*.unsupported.txt` report listing what they could not translate. Dependency-free
Python (stdlib only).

## Playwright → seleasy

```bash
python3 tools/import/pw-import.py path/to/login.spec.ts   # -> login.sel + login.unsupported.txt
python3 tools/import/pw-import.py spec.ts -o out/login     # choose the output base
```

Recognised: `page.goto/goBack/goForward/reload`, `page.click/fill/type/press/
hover/check/dblclick/selectOption/setInputFiles/waitForSelector`, locator chains
(`page.locator(...).click()`), the `getBy*` engines
(`getByRole({name})`/`getByText`/`getByLabel`/`getByPlaceholder`/`getByTestId`/
`getByTitle`/`getByAltText`), and `expect(...)` matchers (`toBeVisible`,
`toHaveText`, `toContainText`, `toHaveValue`, …). `test()` blocks and
`test.beforeEach` are inlined in order.

Selector mapping: `#id` → `id@`, `text=…`/`getByText` → `text@`, `xpath=`/`//…`
→ `xpath@`, everything else → `css@`. The generated `.sel` is validated by
`tools/smoke/sel-lint.py`.

Not translated (reported, emitted as `// [unsupported]`): loops/conditionals,
custom helpers and fixtures beyond `page`, `expect(page).toHaveURL/toHaveTitle`
(GATF has no page-URL assertion), and negative-visibility matchers. Playwright's
per-test context isolation is **not** reproduced — the script runs sequentially
in one browser (`open chrome … close`).

## JMeter → GATF

```bash
python3 tools/import/jmx-import.py plan.jmx    # -> plan.xml + plan.gatf-config.xml + plan.unsupported.txt
```

Emits a GATF `<TestCases>` suite (one TestCase per HTTP sampler — domain/port/
protocol/path/method, header managers incl. inherited ones, raw or form bodies,
GET query args folded into the URL, a `response_code` assertion → `expectedResCode`)
plus a `*.gatf-config.xml` load config seeded from the first Thread Group
(`num_threads` → `concurrentUserSimulationNum`, `ramp_time` → `concurrentUserRampUpTime`,
`duration` → `loadTestingTime`; clamped to > 10000 ms so the load engine activates).

Not translated (reported): JSR223/BeanShell and non-HTTP samplers, timers, logic
controllers (flattened), CSV data sets and correlation/regex extractors, and
`response_data` substring assertions (GATF asserts JSONPath/XPath nodes, not raw
substrings). HTTP Request Defaults are used as a fallback base URL.

## Test

```bash
bash tools/import/test-importers.sh   # regenerates from fixtures/, lints + validates, asserts
```

`fixtures/` holds one input of each kind (`login.spec.ts`, `sample-plan.jmx`);
outputs are reproducible, so they are not checked in.
