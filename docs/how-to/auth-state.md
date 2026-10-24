---
title: Log in once
sidebar_position: 11
---

# Log in once and reuse the session

Logging in through the UI in every script is slow. Save the browser's state once and start every script from it.

## In a script

```sel title="save-state.sel"
open chrome
goto https://shop.example.com/login
type "${username}" label@Email
type "${password}" label@Password
click role@button[name='Sign in']
??10 css@nav.app-menu
state save .auth/user.json
```

`state save` writes the cookies of every domain plus local and session storage, in Playwright's `storageState` format; `state load .auth/user.json` restores them in another script.

## For the whole suite

Set `loginOnce` to a script that logs in, and `storageState` to the file it should leave. GATF runs that script once before the suite (it is never run as a suite script) and loads the state into every script's browser right after `open`:

```json title="gatf-config-ui.json"
{
  "testCaseDir": "tests",
  "outFilesDir": "out",
  "isSeleniumExecutor": true,
  "seleniumScripts": ["orders.sel", "profile.sel"],
  "loginOnce": "login-once.sel",
  "storageState": ".auth/user.json"
}
```

`globalSetup` and `globalTeardown` name scripts run once before (before `loginOnce`) and once after the suite, for seeding and cleaning test data.

## Calling APIs as the logged-in user

`api` and `curl` steps in a UI script send the browser's cookies for the target URL and store `Set-Cookie` responses back in the browser (`browserApiSession`: `sync`, the default; `send`; or `off`). When the app keeps its token in web storage instead, `browserApiStorageToken` (for example `localStorage:auth#access_token`) sends it as a bearer header; it goes only to the open page's host and the hosts in `browserApiStorageTokenHosts`.

Keep `.auth/` out of version control: it holds live session cookies.
