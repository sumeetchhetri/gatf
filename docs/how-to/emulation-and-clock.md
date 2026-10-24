---
title: Emulate devices and control time
sidebar_position: 12
---

# Emulate devices, locales and network, and control the clock

## Emulation

```sel title="mobile-checkout.sel"
open chrome
emulate device "iPhone 13"
emulate locale de-DE
emulate timezone Europe/Berlin
emulate geolocation 52.52 13.405
emulate network slow3g
goto https://shop.example.com
?? css@.mobile-menu visible = true
emulate reset
```

| Statement | Effect |
|---|---|
| `emulate device "<name>" [landscape]` | Viewport, pixel ratio, touch and user agent of a bundled device descriptor (or one from your `devices.json`) |
| `emulate viewport <w> <h> [scale]` | Just the viewport |
| `emulate geolocation <lat> <lon> [accuracy]` | Position reported to the page |
| `emulate locale <BCP 47 tag>`, `emulate timezone <IANA zone>` | Language and time zone |
| `emulate permissions geolocation,notifications` or `clear` | Granted permissions |
| `emulate colorscheme dark` | `prefers-color-scheme` (`light`, `no-preference`) |
| `emulate offline` / `online`, `emulate network slow3g` | Connectivity and throttling (`slow4g`, `fast4g`, `off`, or kbit/s and latency) |
| `emulate reset` | Undo all of it |

It runs over CDP on Chromium and WebDriver BiDi elsewhere; without either the step fails and says so. For real devices, see [mobile and device clouds](mobile-and-device-clouds.md).

## Clock

```sel title="session-timeout.sel"
open chrome
clock install 2026-01-01T09:00:00Z
goto https://shop.example.com
clock fastforward 30m
?? text@'Your session has expired' visible = true
clock uninstall
```

`clock install` fakes `Date`, timers and animation frames in the page and in every new document; `clock set`, `clock fastforward` (ms, `30s`, `5m`, `1h30m` or `mm:ss`), `clock pause` and `clock resume` move it. Without CDP or BiDi it works on the current document only, and the log says so.

Syntax: [emulate](../seleasy/REFERENCE.md#emulate-a-device-location-locale-time-zone-permissions-colour-scheme-or-network-cdp--webdriver-bidi), [clock](../seleasy/REFERENCE.md#control-the-pages-clock-fake-date-timers-and-animation-frames).
