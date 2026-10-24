---
title: Mobile and device clouds
sidebar_position: 16
---

# Mobile apps and device clouds

## Device clouds

Set `GATF_CLOUD` to `browserstack`, `saucelabs` or `lambdatest` and the vendor's credentials in the environment (`BROWSERSTACK_USERNAME`/`BROWSERSTACK_ACCESS_KEY`, `SAUCE_USERNAME`/`SAUCE_ACCESS_KEY`, `LT_USERNAME`/`LT_ACCESS_KEY`). The same `.sel` files (`open chrome`, `firefox`, `safari` or `ie`, plain or `-dkr`) then run on the cloud:

```bash
export GATF_CLOUD=browserstack
export BROWSERSTACK_USERNAME=… BROWSERSTACK_ACCESS_KEY=…
export GATF_CLOUD_PLATFORM="Windows 11" GATF_CLOUD_BUILD=nightly
gatf test gatf-config-ui.json
```

| Variable | Default | Meaning |
|---|---|---|
| `GATF_CLOUD_BROWSER_VERSION` | `latest` | Browser version |
| `GATF_CLOUD_PLATFORM` | `Windows 11` | Operating system (required for Safari) |
| `GATF_CLOUD_BUILD` | | Build name on the dashboard |
| `SAUCE_REGION` | `us-west-1` | Sauce Labs data centre |
| `GATF_CLOUD_DEVICE` | | A `devices.json` name, as Chrome mobile emulation |
| `GATF_CLOUD_OPTIONS` | | JSON merged into the vendor options, for example `'{"local":true}'`; credential keys are refused |

Credentials go as HTTP basic auth, never in the URL, capabilities, logs or reports, and each result is reported to the vendor dashboard. An explicit `SELENIUM_REMOTE_URL` wins. `open edge` cannot reach the clouds in 5.0.0. Trace and DevTools features depend on the vendor: BrowserStack enables CDP and BiDi; Sauce Labs needs a BiDi session (`traceProtocol: bidi`, `-Dgatf.routeProtocol=bidi`); on LambdaTest BiDi is documented for Chrome, Firefox and Edge.

## Real devices with Appium 2

```sel title="app-login.sel"
open appium-android
??10 id@com.example:id/username type "alice"
??10 id@com.example:id/password type "secret"
click id@com.example:id/login
```

`open appium-android` and `open appium-ios` drive Appium 2 (UiAutomator2, XCUITest) at `GATF_APPIUM_URL`, the driver config's `url`, or `http://127.0.0.1:4723`. With `GATF_CLOUD` they run on BrowserStack App Automate, Sauce Labs or LambdaTest real devices: set `GATF_CLOUD_DEVICE` (the provider's device name), `GATF_CLOUD_APP` (your uploaded app: `bs://…`, `storage:filename=…`, `lt://…`; not needed for mobile web) and optionally `GATF_CLOUD_OS_VERSION`. Local-only capabilities (app path, udid, ports) are not sent to the cloud.

Touch commands (tap, swipe, pinch, zoom, rotate, shake, touch sequences) become W3C touch actions; see [Mobile (Appium)](../seleasy/REFERENCE.md#mobile-appium) in the language reference.
