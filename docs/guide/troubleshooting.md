---
title: Troubleshooting and FAQ
sidebar_position: 4
---

# Troubleshooting and FAQ

Start with `logs/gatf.log` in the folder you ran `gatf` in: the console shows only results and warnings, the log has every request, step and stack trace. `GATF_JAVA_OPTS="-Dgatf.log.level=debug"` adds more detail.

## Install and start-up

### `gatf: command not found` after installing

The launcher is in `~/.local/bin`, which the installer adds to your shell profile. Open a new terminal, or run the `export PATH=…` line the installer printed. On Windows, open a new PowerShell window.

### `No config given and no gatf-config.json / gatf-config.xml in …`

`gatf test` without a file name looks for `gatf-config.json`, then `gatf-config.xml`, in the current folder. Run it from the project folder or pass the config: `gatf test path/to/config.json`.

### Output shows `?` instead of arrows or check marks

The terminal has no UTF-8 locale. Set one, for example `export LANG=C.UTF-8`.

### The console port is taken

`gatf ui 9090` uses another port; `gatf ui 9090 path/to/project` also picks the project folder. `GATF_UI_HOST=0.0.0.0` makes it reachable from other machines (it binds to `127.0.0.1` by default).

## API tests

### A test fails with `Expected status code 200 but got 404`

Check the request GATF built: the failing line in `logs/gatf.log` shows the URL. Common causes: `url` is appended to `baseUrl`, so check for a missing or doubled `/`; a `${variable}` that did not resolve is sent literally (see the next entry).

### A `${variable}` is sent literally, or a value is `env.NAME`

- Global variables written `env.NAME` take the environment variable's value only when it is set; otherwise the literal text `env.NAME` is used. Export the variable in the shell or CI job that runs `gatf`.
- A captured workflow variable exists only after the capturing case ran: check `sequence` and file order ([Workflows](concepts/workflows.md#order)).
- A data-provider variable exists only inside a case that names the provider.

### A data provider seems to be ignored

The log says `Provider <name> is Disabled...`. In GATF 5.0.0 a provider needs `"enabled": true` (or `<enabled>true</enabled>`) explicitly. See [Data providers](concepts/data-providers.md).

### `logicalValidations` never fail

In GATF 5.0.0 a false `logicalValidations` expression does not fail a plain test case (one without scenarios). Assert with `expectedNodes` instead.

### `Invalid Config file, please provide either an xml or a json config file`

The config file name must end in `.xml` or `.json`.

## UI tests

### The UI run prints `No tests were executed.`

The browser did not start. GATF 5.0.0 then prints `No tests were executed.` and exits with code `0`, so check `logs/gatf.log` for `Could not start a new session` and the reason after it:

- **`Running as root without --no-sandbox is not supported`** (Linux containers running as root): Chrome needs `--no-sandbox`. A local `open chrome` in 5.0.0 passes the config's `arguments` to Chrome as one switch, so `"arguments": "--headless=new --no-sandbox"` does not work. Run as a non-root user, or run the browser on a Selenium Grid (`export SELENIUM_REMOTE_URL=http://localhost:4444`; see [Browser modes](../browser-modes.md)).
- **`This version of ChromeDriver only supports Chrome version …`**: a chromedriver on `PATH` or in the driver config's `path` does not match Chrome. Remove it and let Selenium Manager pick the right one, or point `path` at a matching driver.
- **Downloads blocked**: Selenium Manager downloads Chrome for Testing and chromedriver from Google's servers. Behind a proxy or offline, install Chrome and a matching chromedriver yourself.

`GATF_CHROME_BINARY` selects another Chrome binary for `open chrome-dkr` and Grid-style sessions; a plain local `open chrome` in 5.0.0 uses the Chrome that Selenium Manager finds.

### `Run aborted: Please provide Selenium scripts for execution`

A UI config (`isSeleniumExecutor: true`) needs `seleniumScripts` (paths relative to `testCaseDir`) or `seleniumScript`.

### `Element not found by selector … at line number N`

The element did not appear within the wait. `??N` sets the wait for that line (the default is 60 seconds). Open the trace (the failure prints its path) and look at the DOM snapshot of that step; with `strictLocators` a semantic locator matching several elements also fails and lists them.

### A step fails with `covered by <div…>`

With `autoWait`, an action waits until nothing covers the element. A modal, cookie banner or spinner is in the way: close it, or wait for it to go (`??+ css@.spinner`).

### Checking a script without running it

`gatf -validate-sel /full/path/to/script.sel gatf-config.xml /full/path/to/project` parses and compiles it and prints `SUCCESS` or `FAILURE`. Give full paths: a bare file name is looked up under `/workdir/`. It exits with `0` either way, so read the output.

### `No func/subtest with that name found - #readfile`

The statement is `readfile` without the `#` in 5.0.0.

## Load tests

### `NOTICE:` at the top of a load result, or `"tool":"vegeta"` when you asked for wperfk or wrk

The requested engine could not be found or downloaded, and GATF fell back to vegeta. Run `gatf tools install wperfk` (or set `wperfkPath`). Offline machines: see [wperfk offline install](../load-tools.md#offline--air-gapped-install).

### A suite with `loadTestingEnabled` runs only once

Load testing needs `loadTestingTime` above `10000` (ms) and comparison mode off.

## Web console

### The console asks for a login

The folder you started `gatf ui` from (its working directory, not the project folder argument) has an `auth.props` with users. Remove it (or its entries) for anonymous mode.

### The console changed my `gatf-config.json`

When the console loads the project (open it in a browser) and when you save in the Config view, it writes the full config back with every default filled in. Keep a copy if you want a minimal file.

### A run of another config does not show in Reports

The console lists the reports of the config it serves (`gatf-config.json` in its folder). Open other runs' traces with **◫ Trace Viewer** and the file picker.

## Still stuck?

Open an issue at [github.com/sumeetchhetri/gatf/issues](https://github.com/sumeetchhetri/gatf/issues) with the command, the config (without secrets) and the relevant part of `logs/gatf.log`.
