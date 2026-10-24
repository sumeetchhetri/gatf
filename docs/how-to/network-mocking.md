---
title: Mock network requests
sidebar_position: 10
---

# Mock network requests in UI tests

`route` intercepts the browser's own requests: answer them with a fixed response, block them, add headers, or replay a recording. Routes apply to the script's session from the line they appear on; the first matching route wins.

```sel title="mocked-users.sel"
open chrome
route **/api/users* fulfill 200 '[{"id": 1, "name": "alice"}]' type application/json
route POST **/api/orders fulfill 201 '{"id": 7}'
route **/*.{png,jpg} abort
route **/api/** continue header 'X-Env: test'
goto https://shop.example.com/users
?? text@=alice visible = true
unroute all
```

- **URL globs**: `**` matches anything, `*` anything but `/`, `{a,b}` alternatives. The whole URL must match, so `**/api/users` does not match `…/api/users?page=2`; write `**/api/users*`.
- **`fulfill`** takes a status, then a body (quoted inline, or a file name relative to the script), `type <content-type>` and `header 'Name: value'`.
- **`abort`** fails the request; **`continue`** lets it through with extra headers.
- **`route from har checkout.har`** replays a recorded HAR by method and URL (`notfound abort|fallback` decides what happens to requests the HAR does not have).
- **`unroute <glob>`** or `unroute all` removes routes.

Interception uses CDP `Fetch` on Chromium and WebDriver BiDi elsewhere. On a browser with neither (Safari without a BiDi socket) the step fails with `route: this browser session cannot intercept requests …`. `-Dgatf.routeProtocol=bidi` forces BiDi.

Syntax: [language reference](../seleasy/REFERENCE.md#mock-network-requests-route).
