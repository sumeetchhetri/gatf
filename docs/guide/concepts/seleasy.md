---
title: seleasy
sidebar_position: 3
---

# seleasy

seleasy is GATF's language for UI tests and robotic process automation. A script is a `.sel` file of one statement per line. GATF compiles each script into a Java Selenium test in-process (no JDK needed) and runs it in the browser the script opens. The web console's editor and the VS Code extension give completion and diagnostics for every statement.

## A script

```sel title="checkout.sel"
// Checkout happy path @smoke @checkout
open chrome
goto https://shop.example.com

click role@link[name='Sign in']
type "alice@example.com" label@Email
type "secret" label@Password
click role@button[name='Sign in']

?? css@h1 text ="Welcome, Alice"
??10 testid@cart-count text != "0"
```

- **Find expressions** name an element as `by@selector`: `id@`, `name@`, `css@`, `xpath@`, `text@`, `class@`, and the semantic locators `role@`, `label@`, `placeholder@`, `testid@`, `alt@`, `title@`.
- **Actions**: `click`, `type`, `select`, `hover`, `clear`, `upload`, and more. With `autoWait` in the config each action waits until its element is visible, enabled, stable and not covered.
- **Waits and assertions**: `??` finds the element (waiting up to the global timeout, `??N` for N seconds) and optionally checks a property: `text`, `visible`, `enabled`, `attr@name`, `title`, `currentUrl`, … With `webFirstAssertions` a failing check is retried for `assertTimeout` ms. `??~` is a soft assertion: it records the failure and the script goes on.
- **Tags** go in the `//` comment lines at the top: `gatf test --tag smoke` selects this script.

## Reuse

A `subtest` is a named, separately reported block; `@call` runs it. Put shared subtests in a library file and `import` it; `include` pastes another script in place (setup boilerplate). `config` loads a properties file as `${name}` variables.

```properties title="app.props"
baseUrl=https://shop.example.com
username=alice@example.com
```

```sel title="lib/auth.sel"
// Shared login subtests
subtest "doLogin"
{
	goto ${baseUrl}/login
	type "${username}" label@Email
	click role@button[name='Sign in']
	??10 css@nav.app-menu
}
```

```sel title="orders.sel"
open chrome
config app.props
import lib/auth.sel
@call "doLogin"
click role@link[name=Orders]
?? css@h1 text ="Your orders"
```

Paths in `import`, `include` and `config` are relative to the file that uses them.

## Data and control flow

- `var name "value"` sets a variable, used as `$v{name}`; `${name}` reads config, global and provider variables.
- `? find-expr` … `:` is an if/else on whether an element is found; `##` loops over every matching element.
- `#provider name` loops over the rows of a [data provider](data-providers.md), with the row's columns as variables.
- `exec`, `execjs` and `<<<(java)` … `>>>` blocks run Java, browser JavaScript or other embedded code.
- `api`, `curl`, `jsonpath` and other plugins call APIs from a UI script and check the result.

## How a script runs

1. `Command` parses the `.sel` file and reports syntax errors with file and line (`gatf -validate-sel` does only this step and the compile).
2. The script becomes a Java class, compiled by the bundled ECJ compiler.
3. The test runs: each `open` starts a browser session (locally through Selenium Manager, or on a Grid when `SELENIUM_REMOTE_URL` is set), every step is logged and, with tracing on, recorded into a [trace](traces.md).

## Reference

- [seleasy language reference](../../seleasy/REFERENCE.md): every statement with syntax and examples.
- [seleasy keywords](../../reference/seleasy-keywords.md): the keyword index generated from the parser.
- [Browser modes](../../browser-modes.md): local browser, chrome-headless-shell, or a Selenium Grid.
