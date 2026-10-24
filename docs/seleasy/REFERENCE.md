# seleasy language reference

seleasy is GATF's UI / RPA automation language. A `.sel` script is compiled in-process (bundled ECJ) into a Selenium Java test, so a JRE is enough. The web console editor and the VS Code extension give autocomplete, diagnostics and go-to-definition for everything below.

## Essentials

```sel
open chrome                                   // start a browser session (one per 'open')
goto https://example.com                      // navigate
??10 id@username type "user"                  // wait up to 10s for the element, then act on it
?? css@"h1" text ="Welcome"                   // wait (global timeout) and assert the element text
??+ id@spinner                                // wait until no longer found;  ??- until found
var who "alice"                               // variables: reference as $v{who} (or @who in exec code)
// a subtest is a reusable, individually reported block (no comment after the subtest line)
subtest "login"
{
    ??10 id@password type "secret"
}
```

- **Find expression:** `<by>@<selector>` where `<by>` is `id`, `name`, `class`, `css`/`cssselector`, `xpath`, `tag`, `text`, `linkText`, `partialLinkText`, `jq`/`$`/`jquery` (jQuery selector), `active`, `this`/`current`. Optional prefixes scope the search (`eval`, `browser-scope`, `session-scope`, `relative`). See [Find Expression](#find-expression).
- **Waits:** `??N` waits up to N seconds (default: the global `timeout`) for the element, then applies the action or assertion on the same line; `??+` (word form `wait till visible`) waits until the element is no longer found, e.g. a spinner has gone; `??-` (word form `wait for`) waits until it is found, like `??`. Neither checks visibility, whatever the word form says; assert the `visible` property for that.
- **Auto-wait:** with `autoWait` set in the executor config, element actions (click, type, select, hover, clear, submit, upload, double-click, `actions … draganddrop`/`moveto`/`clickandhold`) also wait (up to `actionTimeout` ms, default 5000) until the element is visible, enabled, stable and not covered by another element; An element clipped by a scrolling container is scrolled into view first. `type` fails only on a readonly text field (keys still go to other focusable elements); `clear` needs an editable field (upload and submit only need it attached and enabled). A step that times out fails with the reason, e.g. `covered by <div.modal>`. So an explicit `??10 x` before `click x` is no longer needed.
- **Web-first assertions:** with `webFirstAssertions` set in the executor config, an assertion whose element is found but whose condition is false (`?? css@h1 text ="Welcome"`, `?? id@count text != "0"`) is retried until `assertTimeout` ms (default 5000). That retry budget applies regardless of `??N`, where N only bounds the search for the element. Without the setting the assertion fails at once, as before.
- **Soft assertions:** `??~` (optionally `??~N`) asserts like `??` but a failure is recorded instead of stopping the script; the script or subtest fails at the end, listing every soft failure with its file and line.
- **Opting out:** add `!` to the verb (`click! css@#fast-btn`, `type! 'abc' id@q`) or prefix the line with `nowait` (`nowait click id@x`, `nowait ??10 id@x click`, `nowait actions draganddrop id@a id@b`). The opt-out applies to that line only.
- **Semantic locators:** `role@button[name=Save]` (implicit or explicit ARIA role plus accessible name; also `[level=N]`, `[checked]`, `[disabled]`, `[expanded]`, `[pressed]`, `[selected]`, `[include-hidden]`), `label@Email`, `placeholder@Search`, `testid@save-btn` (attribute from `testIdAttribute`, default `data-testid`), `alt@Logo`, `title@Close`, and `text@=Exact text` / `text@/regex/i` (plain `text@x` still means "contains"). Values: plain = whole text, case-insensitive; `~x` = contains; `=x` = exact; `/re/flags` = regex; quote values with spaces (`label@'Email address'`). They pierce open shadow roots and skip hidden elements (append `[include-hidden]` to any value, e.g. `text@=Saved[include-hidden]`); empty values and invalid regexes fail when the script is compiled. With `strictLocators` in the config a semantic locator matching more than one element fails and lists the candidates (loops `#`/`##` and `#transient-provider` may match many). A semantic locator can also be the anchor: `relative tag@input rightof label@Email`.
- **Assertions:** a find expression followed by a property (`text`, `tagname`, `attr@x`, `cssvalue@x`, `selected`, `enabled`, `visible`, `title`, `currentUrl`, ...) and `=value` or `=[list]` (value must be one of the list).
- **Comments:** `// line` and `/* block */`; `/* … */` may sit on one line. `*/` closes only an open block comment, so `route **/api/x` and `xpath@//div/*/a` are ordinary statements.

The catalog of statement keywords used by the editor and linter is generated from the parser: [`commands.json`](commands.json).

## Contents

- [Browsers, sessions & settings](#browsers-sessions--settings)
- [Navigation, windows, tabs & frames](#navigation-windows-tabs--frames)
- [Finding elements, values & waiting](#finding-elements-values--waiting)
- [Element actions](#element-actions)
- [Dialogs](#dialogs)
- [Control flow](#control-flow)
- [Variables, data & files](#variables-data--files)
- [Structure & reuse](#structure--reuse)
- [Embedded code (Java, JS, Groovy, Ruby, Python)](#embedded-code-java-js-groovy-ruby-python)
- [Screenshots, OCR, QR/barcode & PDF](#screenshots-ocr-qrbarcode--pdf)
- [Mail & HTTP simulator checks](#mail--http-simulator-checks)
- [Mobile (Appium)](#mobile-appium)
- [Plugins](#plugins)
- [Word forms and short forms](#word-forms-and-short-forms)

## Browsers, sessions & settings

### Define test mode

```sel
	mode {normal|integration} {true|false}
Examples :-
	mode normal
	mode integration
```

### Maximize window

```sel
	maximize
```

### Define Screen No-activity layers

```sel
	layer {find-expr}
Examples :-
	layer id@"loader-icon"
	layer id@"overlay-div"
```

### Open Browser

```sel
	open {chrome|firefox|edge|safari|ie|appium-android|appium-ios..} {optional session-name}
		Legacy suffixes -hdl / -dkr / -rec are accepted and resolve to the same browser; headless, Grid
		and chrome-headless-shell are chosen by configuration (see docs/browser-modes.md)
Examples :-
	open chrome
	open firefox "my-ff-sess"
```

### Add Settings

```sel
	setting {width|height|posx|posy|clk_focus} {value} {value}?
Examples :-
	setting width 100
	setting height 100
	setting posx 100
	setting posy 100
	setting clk_focus true
```

### Wait Till Browser/Dowument is ready, called after application level logouts or reloads

```sel
	waitready
```

### Define Global timeout

```sel
	timeout {timeout-secs} {if-timeout-secs}} {sleep-granularity-millis}
Examples :-
	timeout 60 1 1000
```

### Emulate a device, location, locale, time zone, permissions, colour scheme or network (CDP / WebDriver BiDi)

```sel
	emulate device {device-name} {optional landscape}
	emulate viewport {width} {height} {optional scale}
	emulate geolocation {latitude} {longitude} {optional accuracy}
	emulate locale {BCP 47 tag}
	emulate timezone {IANA time zone}
	emulate permissions {name,name,...}|clear
	emulate colorscheme dark|light|no-preference
	emulate offline|online
	emulate network slow3g|slow4g|fast4g|off|{down kbit/s} {up kbit/s} {latency ms}
	emulate reset
Examples :-
	emulate device "iPhone 13"
	emulate device "Pixel 7" landscape
	emulate viewport 1024 768
	emulate geolocation 51.5072 -0.1276
	emulate locale de-DE
	emulate timezone Europe/Berlin
	emulate permissions geolocation,notifications
	emulate colorscheme dark
	emulate offline
	emulate network slow3g
	emulate reset
```
- **Devices:** a mobile viewport without Appium: viewport, device scale factor, mobile layout, touch and user agent from the bundled descriptors. They include iPhone SE / 13 / 13 Mini / 14 / 14 Pro Max / 15 / 15 Pro Max, iPad Mini / Pro 11, Pixel 5 / 7, Galaxy S9+ / S23 / Tab S4, and Desktop Chrome (HiDPI, 1920x1080). Add or override devices with a `devices.json` in the test-case dir, in the same shape as `gatf-devices/devices.json`. Emulate before `goto`, so the first page load already sees the user agent.
- **Settings:** `geolocation` also grants the geolocation permission. The names `permissions` accepts are `geolocation`, `notifications`, `camera`, `microphone`, `clipboard-read`, `clipboard-write`, `midi`, `midi-sysex`, `background-sync`, `persistent-storage`, `sensors`, `idle-detection` and `storage-access`. Grants accumulate until `permissions clear` or `reset`. `network` presets follow Chrome DevTools: `slow3g`, `slow4g` (DevTools' former "Fast 3G", also accepted as `fast3g`) and `fast4g`. Custom values are kbit/s down, kbit/s up and ms latency. `online` also ends throttling.
- **Browsers:** Chromium uses CDP and supports everything. Firefox and Grid sessions use WebDriver BiDi, which covers viewport, user agent, geolocation, locale, time zone, permission grants and offline. Colour scheme, throttling and `permissions clear` need CDP and fail with a message saying so. Touch / mobile flags on BiDi are applied as viewport + user agent, with a note. A BiDi command the browser does not implement yet fails naming it. Scripts that use `emulate` ask for the BiDi socket (`webSocketUrl`) automatically.

### Control the page's clock (fake Date, timers and animation frames)

```sel
	clock install {optional time: ISO date-time, date or epoch ms}
	clock set {time}
	clock fastforward {duration: ms, 30s, 5m, 1h30m or mm:ss}
	clock pause|resume
	clock uninstall
Examples :-
	clock install 2026-01-01T09:00:00Z
	clock set 2026-12-31T23:59:50Z
	clock fastforward 10s
	clock fastforward 01:30
	clock resume
	clock pause
	clock uninstall
```
- **`install`:** replaces `Date`, `performance.now`, `setTimeout` / `setInterval` (and their clears) and `requestAnimationFrame` in the page. Time then stands still (paused) at the given time (default: now) until the script moves it, so countdowns, session timeouts, carousels and "x minutes ago" labels are testable deterministically. Timers that are already due (0 ms deferrals such as ready handlers) still run on their own, so pages keep initialising.
- **`fastforward`:** advances time and runs every timer that falls due, in order. Promise reactions (`await`, `.then`) run between timers as in the real event loop, so `await sleep(1000)` loops advance once per second of fake time. Intervals repeat, animation frames fire every 16 ms, and timers scheduled by timers run if they fall due too. A throwing timer is reported, not fatal. A timer that re-arms itself at 0 ms more than 10,000 times without time moving fails the step as a runaway. If a timer navigates away (a session-timeout redirect, say), the step ends there and the next page continues at the time reached.
- **`resume` / `pause`:** `resume` lets fake time follow the real clock (like Playwright's default) and `pause` freezes it again.
- **`set`:** jumps the wall clock (`Date`) without running timers.
- **`uninstall`:** restores the real clock.
- **Navigation:** the clock is installed on every new document of the tab (CDP / WebDriver BiDi preload script) and keeps its time across same-origin navigations. A cross-origin navigation restarts it at the install time. Without CDP or a BiDi socket it covers the current document only, and the step says so.
- **Not covered:** iframes get their own paused clock that `fastforward` does not move; CSS animations, `Event.timeStamp`, Web Workers and new windows are not covered either. GATF's own injected scripts (auto-wait, web vitals) keep using the real timers. `a11y check` on a page whose frames do not answer axe may wait for the script timeout while the clock is installed.

## Navigation, windows, tabs & frames

### Open URL in window

```sel
	goto {url}
Examples :-
	goto http://abc.com/testpage.html
```

### Capture network API calls (netapix)

```sel
	netapix on {api-method} {api-url}
	netapix off {status|header|json} {header-name|json-path}?
Examples :-
	netapix on GET http://abc.com/api/person
	netapix off status --> creates context parameter by name ${apiStatus}=200
	netapix off header token --> creates context parameter by name ${apiHeader}=some-token
	netapix off json $.store.book[0].title --> creates context parameter by name ${apiJson}=some-value
```

### Save / load the browser's authenticated state (cookies + local/session storage)

```sel
	state save {file}
	state load {file}
Examples :-
	state save .auth/admin.json
	state load .auth/admin.json
```

The file is Playwright's `storageState` JSON (plus `sessionStorage`), relative to the test-case directory. `save` writes every cookie of the browser (any domain, HttpOnly included, via CDP or BiDi) and the open page's local/session storage, merged into an existing file. `load` sets the cookies and applies the storage to its origin now and on later navigations (once per tab). Project-wide: `storageState` in the config is loaded by every UI script as its browser opens, and `loginOnce` names a script that runs first and saves it (when it ends, or at a plain `close` of its last window). `browserApiStorageToken` is sent only to the open page's host (plus the hosts in `browserApiStorageTokenHosts`, e.g. `*.api.test` for a sibling API host), never from an https page to http.

### Mock network requests (route)

```sel
	route {METHOD}? {url-glob} fulfill {status} {'body'|body-file}? {type content-type}? {header 'Name: value'}*
	route {METHOD}? {url-glob} abort {reason}?
	route {METHOD}? {url-glob} continue {header 'Name: value'}*
	route from har {har-file} {url url-glob}? {notfound abort|fallback}?
	unroute {url-glob|all}
	url-glob: ** = anything, * = anything but '/', {a,b} = alternatives, \ = next char literal; the first matching route wins
	          the whole URL must match: **/api/users does not match ?page=2 (write **/api/users*)
Examples :-
	route **/api/users fulfill 200 users.json
	route POST **/api/orders fulfill 201 '{"id": 7}'
	route **/*.{png,jpg} abort
	route **/api/** continue header 'X-Env: test'
	route from har checkout.har url **/api/** notfound abort
	unroute all
```

Routes apply to the current browser session from that line on: a quoted body is sent as-is, an unquoted one is a file next to the script (its Content-Type comes from the extension unless `type` is given). `abort` reasons: failed (default), aborted, timedout, accessdenied, connectionrefused, namenotresolved, internetdisconnected, blockedbyclient, … (under BiDi every abort is a plain network error). A repeated `header` name (e.g. two `Set-Cookie`) is sent twice; for Content-Type the last one wins. `route from har` replays a recording by method + URL (exact URL first, then the URL without its query, preferring the entry that shares the most query parameters; between otherwise equal entries the one whose recorded POST body equals the request's wins under CDP, else the first), keeping repeated response headers; unrecorded requests go through, or fail with `notfound abort`. Chromium intercepts over CDP `Fetch`; Firefox and other browsers over WebDriver BiDi (`network.addIntercept`) — a script that uses `route` asks non-Chromium sessions for a BiDi socket automatically. CDP intercepts only the page's own target — popups and cross-origin (out-of-process) iframes are not mocked; `-Dgatf.routeProtocol=bidi` (or `traceProtocol: bidi`) routes Chromium over BiDi instead, which covers every browsing context.

### Scroll [Javascript based]

```sel
	scroll {up|down|pageup|pagedown|top|bottom|left|right|find-expr}
Examples :-
	scroll up
	scroll id@dsdsd
```

### Navigate Forward/Next

```sel
	forward
```

### Refresh window

```sel
	refresh
```

### Select frame or use a scoped frame block

```sel
	frame main|parent|1..N|{some-name}
Examples :-
	frame main
	frame parent
	frame 2
	frame "my-frame"
	frame
	{...
	}
```

### Save URL passed to window.open

```sel
	wopensave {on|off} {num-window-opens|filepath}? {text}? {openPos}?
Examples :-
	wopensave on
	wopensave on 2
	wopensave off '/path/to/file.txt' text
	wopensave off '/path/to/file.txt' text 2
	wopensave off '/path/to/file.txt' text 2 |
```

### Navigate Back/Previous

```sel
	back
```

### Close window

```sel
	close {tab-index|others}?
Examples :-
	close
	close 1
	close others
```

### Select a tab or use a scoped tab block

```sel
	tab main|0..N|{some-name}
Examples :-
	tab main
	tab 0
	tab 2
	tab "my-frame"
	tab
	{...
	}
```

### Select/Open Window

```sel
	window {optional main|0}
Examples :-
	window
	window 0
	window main
```

## Finding elements, values & waiting

### Find Expression

```sel
	{eval|browser-scope|session-scope|relative}? {id|name|class|xpath|tag|cssselector|css|text|partialLinkText|linkText|jq|$|jquery|role|label|placeholder|testid|alt|title|active|this|current}(@selector) (title|currentUrl|pageSource|width|height|xpos|ypos|alerttext) {matching-value|matching-value-in-list}
	semantic: role@button[name=Save][level=N][checked] label@Email placeholder@Search testid@id alt@Logo title@Close text@=exact text@/regex/i
	value: plain = whole text, case-insensitive | ~x = contains | =x = exact | /re/flags = regex
Examples :-
	click role@button[name=Save]
	type 'me@example.com' label@'Email address'
	?? text@'=Order placed'
```

### Value

```sel
	{primtive-value}
Examples :-
	'abc'
	123
	true
```

### Value List

```sel
	[{primtive-value},...,{primtive-value}]
Examples :-
	['abc', 'sss']
	[123, 234]
	[true, false]
```

### Wait for element to be found / no longer found

```sel
	??(+|-) {find-expr}
	'+' - wait till the element is no longer found (same as: wait till visible {find-expr})
	'-' - wait till the element is found, like ?? (same as: wait for {find-expr}); neither checks visibility
	??~ {find-expr} {assertion} - soft assertion: a failure is recorded, the script continues and fails at the end
Examples :-
	??+ id@'eleid'
	??- id@'eleid'
	??~ css@'h1' text ='Welcome'
```

### Sleep for milliseconds

```sel
	sleep {time-in-ms}
Examples :-
	sleep 10000
```

## Element actions

### Send keys using Robot

```sel
	robot keydown|keyup|keypress {key-code1} ... keydown|keyup|keypress|keyrelease|key {key-codeN}
	scrollup
	scrolldown
	scrollpageup
	scrollpagedown
Examples :-
	robot keydown 1
	robot keyup 1
	robot keypress 1
	robot keypress 1 keydown 2 keyup 
	scrollup
	scrolldown
	scrollpageup
	scrollpagedown
```

### Submit element

```sel
	submit {find-expr}
Examples :-
	submit id@'ele1'
```

### Select value from dropdown element

```sel
	select {text|index|value|first|last}@{value} {find-expr}
Examples :-
	select text@'first' id@'abc'
	select index@2 id@'abc'
	select value@'second' id@'abc'
	select first id@'abc'
	select last id@'abc'
```

### Double Click element

```sel
	doubleclick {find-expr}
	dblclick {find-expr}
Examples :-
	doubleclick id@'ele1'
```

### Click element

```sel
	click(fo|nf) {find-expr}
Examples :-
	click id@'ele1'
	clickfo id@'ele1'
	clicknf id@'ele1'
```

### Type Value in input/textarea elements

```sel
	(type|sendkeys|chord|randomize)(bl|ch|bk|cl|fo) {text} {find-expr}
	where 
		bl->Trigger Blur
		ch->Trigger Change
		bk->Trigger Backspace
		cl->Trigger Click
		fo->Trigger Focus
Examples :-
	type 'abc' id@'ele1'
	sendkeys 'abc' id@'ele1'
	typebl 'abc' id@'ele1'
	sendkeyscl 'abc' id@'ele1'
```

### Type UTF-8 or normal ASCII characters in input/textarea elements

```sel
	chord(bl|ch|bk|cl|fo) {utf-8 character1}{utf-8 character2}...{utf-8 characterN} {find-expr}
Examples :-
	chord \u0048\u0065\u006c\u006c\u006f\u0020\u0057\u006f\u0072\u006c\u0064 id@'abc'
```

### Hover over an element

```sel
	hover {find-expr}
Examples :-
	hover id@'abc'
```

### Type random values in input/textarea elements

```sel
	randomize(bl|ch|bk|cl|fo) {find-expr} alphanumeric|alpha|alphanumericlc|alphalc|alphanumericuc|alphauc|numeric|value|range|prefixed|prefixed_|fuzzyn|fuzzya|fuzzyauc|fuzzyalc|fuzzyan|fuzzyanuc|fuzzyanlc {optional character count|range start} {count of space separated random words(for eg, name of person)|range end}
Examples :-
	randomize id@'ele1' alphanumeric 12
	randomize id@'ele1' alpha 8 3 (first-name middle-name last-name)
	randomize id@'ele1' numeric 5
	randomize id@'ele1' range 9999 99999
	randomize id@'ele1' value 'abcd'
	randomize id@'ele1' fuzzyn _@3:4:5:6
	randomize id@'ele1' fuzzya _@3:4:5:6
	randomize id@'ele1' fuzzyan _@3:4:5:6
```

### Clear element value

```sel
	clear {find-expr}
Examples :-
	clear id@'ele1'
```

### Upload file

```sel
	upload {filepath} {find-expr}
Examples :-
	upload '/path/to/file.txt' id@'ele1'
```

### Hover over an element and click some other element

```sel
	hoverclick {find-expr} {find-expr}
Examples :-
	hoverclick id@'hoverele' id@'clickele'
```

`hoverclick` waits for both elements itself; it can't follow a `??` wait (`?? id@a hoverclick` is a parse error).

### Multiple Chained Actions

```sel
	actions movetoelement|moveto {find-expr} ({click|clickandhold|release|dblclick|doubleclick|contextclick|clickhold|rightclick} {find-expr}?|{keydown|keyup|sendkeys|type {value}}|{movetoelement|moveto {find-expr}}|{draganddrop|dragdrop|draganddrop1|dragdrop1 {find-expr} {find-expr}}|randomize {alphanumeric|alpha|alphanumericlc|alphalc|alphanumericuc|alphauc|numeric|value|range|prefixed|prefixed_|fuzzyn|fuzzya|fuzzyauc|fuzzyalc|fuzzyan|fuzzyanuc|fuzzyanlc} {arg1} {arg2} {arg3}?}|{movebyoffset|moveby {x-offset} {y-offset}}) ... movetoelement|moveto {find-expr} ... ({click|clickan...
Examples :-
	actions movetoelement id@'ele' click moveto id@'ele2' clickandhold moveto id@'ele3' release type '123'
	actions movetoelement id@'ele' sendkeys 'abc'
	actions draganddrop id@'source' id@'target'
	actions moveto id@'handle' clickandhold moveby 120 0 release
```
`draganddrop` / `dragdrop` emit clickAndHold(source) → moveToElement(target) → release(target); `draganddrop1` / `dragdrop1` call Selenium's `dragAndDrop(source, target)`. Pages differ in which gesture they respond to, so try the other if one has no effect.

### Draw a circle on a canvas element

```sel
	canvas {find-expr}
Examples :-
	canvas xpath@"asds"
```

## Dialogs

### Show alert with message

```sel
	alert {value}
Examples :-
	alert
	alert 'Hello'
```

### Handle Confirm Dialog

```sel
	confirm ok|cancel|yes|no {optional button-text-to-check}
Examples :-
	confirm ok
	confirm yes
	confirm cancel
	confirm no
	confirm yes 'Confirm'
```

## Control flow

### If block

```sel
	? {find-expr} & {find-expr}?
	{
		code
	}
	? eval {template-expr} & {template-expr}?
	{
		code
	}
	? browser-scope {browser-name}?
	{
		code
	}
	? session-scope {browser-name}?
	{
		code
	}
Examples :-
	? xpath@"ddd"
	{
		exec @print("if")
	}
	? eval "a"=="a" & eval ${bvar}!="b"
	{
		exec @print("if")
	}
	? browser-scope "chrome"
	{
		exec @print("if")
	}
	? s-scope "one"
	{
		exec @print("if")
	}
```

### Java control command, block statements

```sel
	#j{if|try|catch|finally|else|else if|while|for|continue|break|\{|\}|synchronized} {java statement}
Examples :-
	#jif(1==1) {} else {}
	#jfor(int i=0;i<10;i++){}
```

### Loop block

```sel
	## {find-expr}
	{
		code
	}
Examples :-
	## class@"ddd"
	{
		exec @print(@index)
		click xpath@"ddd-@index"
	}
```

### Else block, needs to be superseded by an If or Else-If block

```sel
	:
	{
		code
	}
Examples :-
	:
	{
		exec @print("else")
	}
```

### Provider Loop block

```sel
	#provider {find-expr}
	{
		code
	}
	#p {find-expr}
	{
		code
	}
	#counter {start-index} {end-index}
	{
		code
	}
	#counter {end-index}
	{
		code
	}
	#c {start-index} {end-index}
	{
		code
	}
	#c {end-index}
	{
		code
	}
	#sql {dsn} {query} {var1,var2,..}
	{
		code
	}
	#mongo {dsn} {collection-name} {query} {col1,col2,..} {var1,var2,..}
	{
		code
	}
	#file {filepath} {var1,var2,..}
	{
		code
	}
Examples :-
	#provider "provider-name"
	{
		exec @print(@index)
		click xpath@'$provider-variable-1'
	}
	#provider "provider-name" 0 3
	{
		exec @print(@index)
		click xpath@'$provider-variable-1'
	}
	#provider "provider-name" 2
	{
		exec @print(@index)
		click xpath@'$provider-variable-1'
	}
	#p "provider-name"
	{
		exec @print(@index)
		click xpath@'$provider-variable-1'
	}
	#p "provider-name" 0 3
	{
		exec @print(@index)
		click xpath@'$provider-variable-1'
	}
	#p "provider-name" 2
	{
		exec @print(@index)
		click xpath@'$provider-variable-1'
	}
	#counter 0 5
	{
		exec @print(@index)
	}
	#counter 5
	{
		exec @print(@index)
	}
	#c 0 5
	{
		exec @print(@index)
	}
	#c 5
	{
		exec @print(@index)
	}
	#sql sqldsn "select col1,col2 from some_table" "var1,var2"
	{
		exec @print(@index)
		exec @print(@var1)
		click xpath@'$var2'
	}
	#file "/tmp/path/file.txt" "var1,var2"
	{
		exec @print(@index)
		exec @print(@var1)
		click xpath@'$var2'
	}
```

### Else-If block, needs to be superseded by an If block

```sel
	:? {find-expr}
	{
		code
	}
Examples :-
	:? xpath@"ddd"
	{
		exec @print("else-if")
	}
```

### Continue in loop

```sel
	continue
```

### Break from loop

```sel
	break
```

## Variables, data & files

> **Two or more data providers in scope:** a value is templated once per provider loop, so `#[[…]]#` (how the recorder and crawler carry a literal `$`) protects it for one pass only. Don't nest recorded or crawled lines inside two provider loops.

### Data Source Query definition

```sel
	#dsq {dsn} {query}
Examples :-
	#dsq sqldb "update some_table set cola = 'value' where colb = 1"
```

### File Variable definition

```sel
	filevar {var1,var2,..} {regex} {var1-pattern,var2-pattern..}
Examples :-
	filevar v1,@v2,@v3 "(.*)|(.*),(.*)" "$1,ABC_$2,BCD$3EFG"
	filevar v1 "([a-z]+)" "$1"
```

### Transient Variable definition

```sel
	#transient-variable {variable-name} {find-expr} {sub-selector}
Examples :-
	#transient-variable var1 id@'abc' text
	#transient-variable var1 id@'abc' html
	#transient-variable var1 id@'abc' attr@data-prop
	#tv var1 id@'abc' text
	#tv var1 id@'abc' html
	#tv var1 id@'abc' attr@data-prop
```

### Transient Provider definition

```sel
	#transient-provider {provider-name} {variableName1,...,variableNameN} {find-expr} {sub-selector1,...,sub-selectorN} {lazy}?
Examples :-
	#transient-provider prov1 var1,var2 id@'abc' text,attr@abc
	#tp prov1 var1,var2 id@'abc' text,attr@abc
```

### Import dynamic (code vars) properties file, dynamic variables provide support for configurable code

```sel
	dynprops {file-path}
Examples :-
	dynprops a/b/c/t1.props
	dynprops t2.props
```

### Import config properties file

```sel
	config {file-path}
Examples :-
	config a/b/c/t1.props
	config t2.props
```

### Js Variable definition

```sel
	jsvar {javascript statement returning value}
Examples :-
	jsvar var1 'return "123"'
	jsvar var1 'return $("#elid").val()'
```

### Variable definition

```sel
	var name @{another-variable-name}|plugin ...|{primitive-value}
Examples :-
	var var0 "Some text"
	var var1 @var0
	var var1 plugin jsonpath $v{myvar} out.x.y.z
	var var1 123455
	var var1 123.455
	var var1 true
	var var1 new java.util.Date()
```

### Read File line by line

```sel
	#readfile {file-path} {binary}?
	{
		code
	}
Examples :-
	#readfile "/path/to/file.txt"
	{
		exec @print(@index)
		exec @print(@line)
		click xpath@'$provider-variable-1'
	}
	#readfile "/path/to/file.bin" "binary"
	{
		exec @print(@index)
		exec @print(@line)
		click xpath@'$provider-variable-1'
	}
```

In GATF 5.0.0 the statement is written `readfile`, without the `#`: `readfile "/path/to/file.txt"` compiles, while the `#readfile` spelling above fails with `No func/subtest with that name found - #readfile`.

## Structure & reuse

### Block level comment

```sel
	/*
	 Some multi line comment
	 Some multi line comment 2
	*/
```

### Fail test/sub-test and continue

```sel
	warn {error string}
Examples :-
	warn "Test failed"
	warn "Sub-Test failed"
```

### Subtest definition

```sel
	subtest "name" session-name|@session-id (args)
	{
		code
	}
where
	session-name - the browser session name for which to run this sub test
	session-id - the browser session id prefixed with @ for which to run this sub test
	args - the arguments passed to the subtest, space separated names for args
Examples :-
	subtest "sb1" "bs1"
	{
		select index@4 id@"Location"
	}
	subtest "sb1" @1 (arg1 arg2)
	{
		select index@$v{arg1} id@"$v{arg2}"
	}
```

### Function definition

```sel
	func "name" (args)
	{
		code
	}
where
	args - the arguments passed to the func
Examples :-
	func "cmd1"
	{
		select index@4 id@"Location"
	}
	func "cmd2" @1 (arg1 arg2)
	{
		select index@$v{arg1} id@"$v{arg2}"
	}
```

### Hooks

```sel
	beforeAll|beforeEach|afterEach|afterAll
	{
		code
	}
where
	beforeAll - runs once before the script's first statement, afterAll once at its end (also when it failed)
	beforeEach / afterEach - run around every subtest; afterEach also runs when the subtest failed
Examples :-
	beforeEach
	{
		goto ${baseUrl}/login
	}
	afterAll
	{
		state save .auth/after.json
	}
```

A failing `beforeAll` fails the script; a failing `beforeEach` fails that subtest (its body is not run). When the script or subtest has already failed, a failing `afterEach` / `afterAll` is logged and the first failure is reported. Hooks run like funcs and are defined at the top level, one of each kind per script; an `import`ed file lends its subtests and funcs but not its hooks. Suite-wide setup is `globalSetup` / `globalTeardown` in the config: scripts run once before the UI suite (before `loginOnce`) and once after its last round, also when scripts failed (also after a failing `globalSetup`); a failing `globalSetup` stops the run. With `loginOnce` set, `globalSetup` does not load `storageState` (loginOnce is about to replace it).

With `browserContexts: subtest` in the config, every top-level subtest (and its `beforeEach` / `afterEach`) runs in a fresh browser context in the same browser and starts on `about:blank`: no cookies, storage, cache, routes, emulation or fake clock from the script or other subtests, plus the project's `storageState` when set. `beforeAll` / `afterAll` and the script body use the browser's default context. Inside it, `tab` / `window` / `close others` see only the context's tabs, and only the browser current at the subtest's start gets a context. The context is disposed when the subtest ends (pass, fail, timeout or stop). See docs/browser-modes.md for which browsers support it.

### Subtest markers

```sel
	only|skip|fixme subtest "name" ...
where
	skip / fixme - the subtest is not run and is reported as skipped
	only - when any subtest in the file is marked only, the others are reported as skipped
Examples :-
	skip subtest "export"
	{
		click id@export
	}
	fixme subtest "checkout"
	{
		click testid@pay
	}
```

Skipped subtests pass the script and show as skipped in the console (with the reason), the report and JUnit XML (`<skipped message=…>`); hooks do not run for them. `only` is per script; `forbidOnly: true` in the config (or `gatf test --forbid-only`) makes an `only` marker a transpile error.

### Include other seleasy scripts inline

```sel
	include {script-path}
Examples :-
	include a/b/c/t1.sel
	include t2.sel
```

### Pass test/sub-test

```sel
	pass {some string}?
Examples :-
	pass
	pass "Test passed"
	pass "Sub-Test passed"
```

### Fail test/sub-test

```sel
	fail {error string}
Examples :-
	fail "Test failed"
	fail "Sub-Test failed"
```

### Import only subtests/functions from other seleasy scripts

```sel
	import {script-path}
Examples :-
	import a/b/c/t1.sel
	import t2.sel
```

### Subtest/Function execution

```sel
	@call "name" session-name|@session-id (args)
where
	session-name - the browser session name for which to run this sub test
	session-id - the browser session id prefixed with @ for which to run this sub test
	args - the arguments passed to the subtest, space separated pairs of colon separated name/value
Examples :-
	@call "func1" @1 (arg1:1 arg2:abc)
	{
		select index@$v{arg1} id@"$v{arg2}"
	}
```

### Single line comment

```sel
	//...
```

## Embedded code (Java, JS, Groovy, Ruby, Python)

### Execute java code

```sel
	exec {java statement}
Available variables in context -
	1. @driver - WebDriver instance
	2. @window - WebDriver instance
	3. @element - Currently selected WebElement instance
	4. @sc - Currently selected SearchContext instance
	5. @printProvJson - Print Provider data as json
	6. @printProv - Print Provider data
	7. @print - System.out.println
	8. @index - Current provider index under iteration
Examples :-
	exec @driver.refresh()
	exec @window.back()
	exec @element.click()
	exec @sc.findElement(org.openqa.selenium.By.id("id")
	exec @printProvJson("provider-name")
	exec @printProv("provider-name")
	exec @print("something-to-console")
	exec @print(@index)
	exec System.out.println("Hello World!")
```

### Specify Java Imports

```sel
	require fqcn|[{fqcn1},..{fqcnN}]
Examples :-
	require java.util.Date
	require [java.util.List, java.math.BigDecimal]
```

### Execute embedded code in java/js/ruby/groovy/python

```sel
	<<<(java|js|ruby|groovy|python) a,b,c
	{code}
	>>>
Arguments: a bare name (`a`) passes that script variable into the block (for js as a page-script argument); `a=expr`
binds `a` to a Java expression (for js, a JavaScript expression evaluated in the page).
Available variables in context -
	1. @driver - WebDriver instance (Java only)
	2. @window - WebDriver instance (Java only)
	3. @element - Currently selected WebElement instance (Java only)
	4. @sc - Currently selected SearchContext instance (Java only)
	5. @printProvJson - Print Provider data as json (Java only)
	6. @printProv - Print Provider data (Java only)
	7. @print - System.out.println (Java only)
	8. @index - Current provider index under iteration (Java only)
	9. @cntxtParam - Add string variable to current context
	9. @cntxtVar - Add variable to current context
Examples :-
	<<<(java) a,b,c
	System.out.println(a);
	>>>
	<<<(js) a,b,c
	console.log(a);
	>>>
	<<<(groovy) a,b,c
	println a
	>>>
	<<<(ruby) a,b,c
	puts a
	>>>
	<<<(python) a,b,c
	print(a)
	>>>
```

Java blocks and `#j` statements assert with `GatfAssert` (JUnit 4's method names and messages: `GatfAssert.assertTrue("msg", cond)`, `assertEquals`, `assertNotNull`, `fail`, …). `Assert.` still works as an alias, so older scripts compile unchanged. `GatfAssert` also has `assertArrayEquals` (object, primitive and nested arrays), `assertNotEquals`, `assertSame`, `assertThrows`, and float/double `assertEquals` with a delta. JUnit itself is no longer on the runtime classpath, so `org.junit.*` imports and Hamcrest `assertThat` are not available in scripts.

### Execute javascript code from file in the browser

```sel
	execjsfile {javascript file path}
Examples :-
	execjsfile 'file.js'
```

### Execute javascript code in the browser

```sel
	execjs {javascript statement}
Examples :-
	execjs 'console.log("Hello");'
	execjs '$("#elid").click();'
```

## Screenshots, OCR, QR/barcode & PDF

### Save element image to file

```sel
	imgsave {element-selector} {optional image-file-path-to-save-screenshot-to}
Examples :-
	imgsave id@'eleid'
	imgsave id@'eleid' '/path/to/image/file/file.png'
```

### Print the page to a PDF file

```sel
	printpdf {pdf-filepath} {text?} {col-separator}?
Examples :-
	printpdf '/path/to/page.pdf'
	printpdf '/path/to/page.pdf' text
	printpdf '/path/to/page.pdf' text ;
```
- **Browser support:** Chromium uses CDP `Page.printToPDF`, Firefox uses BiDi `browsingContext.print`, and anything else falls back to W3C print. It works locally and on a Grid, and the step fails if the session cannot print.
- **PDF already open:** if the page is showing a PDF blob, the document itself is saved, not a print of the viewer.
- **`text`:** also writes the extracted text and tables to `<file>.txt`, using the column separator (default `|`). Embedded code can read the tables with `pdfTable("<file>")`.
- For a document the page opens in a new window, use [`wopensave`](#save-url-passed-to-windowopen).

### Take screenshot

```sel
	screenshot {image-file-path-to-save-screenshot-to}
Examples :-
	screenshot
	screenshot "/path/to/image/file/file.png"
```

### Read text from an element image using OCR (Captcha)

```sel
	readimgtext {element-selector} {varname} {optional image-file-path-to-save-screenshot-to}
	ocr {element-selector} {varname} {optional image-file-path-to-save-screenshot-to}
Examples :-
	readimgtext id@'eleid' somevar
	ocr id@'eleid' somevar
	readimgtext id@'eleid' captchatext '/path/to/image/file/file.png'
```

### Read text from a QR code or a Bar code

```sel
	readqrbar {element-selector} {varname} {optional image-file-path-to-save-screenshot-to}
	qrcode {element-selector} {varname}  {optional image-file-path-to-save-screenshot-to}
	barcode {element-selector} {varname} {optional image-file-path-to-save-screenshot-to}
Examples :-
	readqrbar id@'eleid' somevar
	qrcode id@'eleid' somevar
	barcode id@'eleid' somevar
	readqrbar id@'eleid' captchatext '/path/to/image/file/file.png'
```

### Take element screenshot

```sel
	ele-screenshot {element-selector} {optional image-file-path-to-save-screenshot-to}
Examples :-
	ele-screenshot id@'eleid'
	ele-screenshot id@'eleid' '/path/to/image/file/file.png'
```

### Compare the page with its baseline screenshot (visual regression)

```sel
	screenshot compare {name} {optional threshold 0..1} {optional maxdiff N|N%} {optional mask {locator}}...
Examples :-
	screenshot compare home
	screenshot compare "checkout step 2" 0.1 maxdiff 0.5%
	screenshot compare dashboard mask id@clock mask css@'.ad-banner'
```
- **Baseline:** `<testCaseDir>/__screenshots__/<browser>/<script>/<name>.png` (`<script>` = the .sel path without `.sel`, e.g. `ui/login`), a full-page capture in CSS pixels. Commit the baselines. Take it from the top-level document (not inside `frame …`): capture size and mask positions are page coordinates.
- **First run:** with no baseline, the capture becomes the baseline and the step passes. Under `CI=true` it fails instead and writes `<out>/<browser>-<script>-<name>-actual.png`, so a forgotten baseline never passes in CI.
- **Comparison:** `threshold` is the per-pixel colour tolerance (perceptual YIQ distance; default 0.2). Anti-aliased pixels are not counted. `maxdiff` is the number (`25`) or share (`0.5%`) of differing pixels allowed (default none). `mask` elements (repeatable) are ignored; a mask that is not on the page masks nothing.
- **Mismatch:** the step fails and writes `-expected.png`, `-actual.png` and `-diff.png` (red = changed pixels) to the run's out dir. They are attached to the test in the report and in `gatf-results.json`.
- **Accepting changes:** `gatf test --update-snapshots` (or config `updateSnapshots`) rewrites every compared baseline.

### Check the page for accessibility violations (axe-core)

```sel
	a11y check {optional include {css@…|id@…}}... {optional exclude {css@…|id@…}}... {optional impact>=minor|moderate|serious|critical} {optional report}
Examples :-
	a11y check
	a11y check impact>=serious
	a11y check include id@main exclude css@'.ad-banner' impact>=critical
	a11y check report
```
- **Engine:** runs the bundled [axe-core](https://github.com/dequelabs/axe-core) 4.13.0 (unmodified, MPL-2.0) in the current page. It is injected into the page and its frames (up to 3 levels deep), replacing any other axe version the page ships; frame results come back with ` >>> ` joined selectors (best effort: a frame that refuses scripts shows under "need review"). The async-script timeout is raised to 120 s for the check.
- **Scope:** `include` / `exclude` (repeatable) take CSS (`css@…`, `id@…` or a plain selector); by default the whole document is checked.
- **Result:** violations at or above `impact>=` (default: every violation) fail the step. The message lists each rule with its first selector, e.g. `[critical] image-alt: Images must have alternative text at img.logo (+1)`. `report` records them without failing.
- **Where results go:** `<out>/<browser>-<script>-<n>-a11y.json` (attached to the test), the report's **Checks** tab (every violation with its selectors, HTML and fix summary), the trace's `checks.json` and a trace event.

## Mail & HTTP simulator checks

### Check whether an http request was received from a given sender, for a given receiver and matching the given content with timeout period

```sel
	http {name}  {from} {to} {content} {timeout}?
Examples :-
	http 'http request message for sms' 'from' 'to' 'How are you doing today'
	http 'http request message for sms' 'from' 'to' 'How are you doing today (.*)'
	http 'http request message for sms' 'from' 'to' 'How are you doing today (.*)' 50000
```

### Check whether email was received from a given sender, for a given receiver, with a given subject and matching the given content with timeout period

```sel
	mail {name}  {from} {to} {subject} {content}? {timeout}?
Examples :-
	mail 'mail for appointment' 'abc@a.com' 'abc@b.com' 'Hello There'
	mail 'mail for appointment' 'abc@a.com' 'abc@b.com' 'Hello There' 'How are you doing today'
	mail 'mail for appointment' 'abc@a.com' 'abc@b.com' 'Hello There' 'How are you doing today (.*)'
	mail 'mail for appointment' 'abc@a.com' 'abc@b.com' 'Hello There' 'How are you doing today (.*)' 50000
```

## Mobile (Appium)

Appium 2: `open appium-android|appium-ios` starts a UiAutomator2 / XCUITest session on a local Appium server (`GATF_APPIUM_URL`, the driver config's `url`, or `http://127.0.0.1:4723`; config capabilities get the `appium:` prefix) or, with `GATF_CLOUD` set, on a real cloud device (see the README's device clouds). `tap`, `swipe`, `zoom`, `pinch` and `touch` are sent as W3C touch actions; `tap` without a duration holds 100 ms, and `tap {find-expr} x y` is x y from the element's top-left. `hidekeypad` is `mobile: hideKeyboard`, `shake` is `mobile: shake` (iOS only), `rotate` toggles portrait/landscape. On a desktop browser session, or when the device refuses, each is a step error naming the command and its line.

### Mobile shake

```sel
	shake
```

### Mobile Tap

```sel
	tap ({find-expr}|{find-expr} {duration}|{x-co-ordinate} {y-co-ordinate}|{x-co-ordinate} {y-co-ordinate} {duration}|{find-expr} {x-co-ordinate} {y-co-ordinate}|{find-expr} {x-co-ordinate} {y-co-ordinate} {duration})
Examples :-
	tap id@'ele'
	tap id@'ele' 2000
	tap id@'ele' 123 234
	tap id@'ele' 123 234 2000
	tap 123 234
	tap 123 234 2000
```

### Mobile hide keypad

```sel
	hidekeypad
```

### Mobile rotate

```sel
	rotate
```

### Mobile Zoom

```sel
	zoom ({x-co-ordinate} {y-co-ordinate}|{find-expr})
Examples :-
	zoom 123 234
	zoom id@'ele'
```

### Mobile Touch

```sel
	touch ({press|moveto|tap {find-expr}|{find-expr} {x-co-ordinate} {y-co-ordinate}|{x-co-ordinate} {y-co-ordinate}}|longpress|{longpress {find-expr} {x-co-ordinate} {y-co-ordinate} {duration}|{find-expr} {x-co-ordinate} {y-co-ordinate}|{find-expr} {duration}|{x-co-ordinate} {y-co-ordinate} {duration}|{x-co-ordinate} {y-co-ordinate}}|{wait {duration}}|release) ... ({press|moveto|tap {find-ex...
Examples :-
	touch moveto id@'ele' longpress moveto id@'ele2' wait 1000 release
	touch moveto id@'ele' longpress id@'ele1' moveto id@'ele2' wait 1000 release
```

### Mobile Swipe

```sel
	swipe {start-x-co-ordinate} {start-y-co-ordinate} {end-x-co-ordinate} {end-y-co-ordinate}
Examples :-
	swipe 123 234 200 300
```

### Mobile Pinch

```sel
	pinch ({x-co-ordinate} {y-co-ordinate}|{find-expr})
Examples :-
	pinch 123 234
	pinch id@'ele'
```

## Plugins

Built-in plugins are statements, or values via `var x plugin <name> ...`. Your own are registered in `plugins.txt` as
`name=com.acme.MyPlugin@method`, where the method is `public static Object method(Object[] args)` or
`public static Object method(String name, Object[] args)`. Entries that don't match are skipped with a warning in the log.

**Plugin SDK v2 (jars, no rebuild).**
1. Implement `com.gatf.selenium.plugins.sdk.GatfStepPlugin`:
   - `name()`: the keyword, `[a-z][a-z0-9_-]*`.
   - `execute(Call)`: `call.args` (inline, expanded like built-ins), `call.block` (block lines), `call.index`, `call.context`. The return value is what `var x plugin <name> …` stores.
   - optional `info()`: summary, args and examples.
   - optional `init(context)`.
2. List the class in `META-INF/services/com.gatf.selenium.plugins.sdk.GatfStepPlugin` and drop the jar into `plugins/`.
   - The folder is found like `plugins.txt`: under the test-case dir, the base path, or the working dir.
   - It is re-scanned whenever a script is compiled, so a new or changed jar works without a rebuild or restart.

How names and classes resolve:
- **Precedence:** built-ins first, then `plugins.txt`, then jars; the first definition of a name wins.
- **Legacy plugins in jars:** `plugins.txt` classes may also live in a `plugins/` jar.
- **Editor help:** `GET /api/plugins` lists every plugin with its docs. The web editor and the VS Code extension (`seleasy.gatfUrl`) use it for completion.
- **Distributed runs:** jars are not shipped to distributed nodes; copy `plugins/` there too.

- `jsonpath` / `xmlpath` take the document and the path in either order. The document may be text or a value from
  `jsonread` / `xmlread`.
- `jsonwrite` / `xmlwrite` take the file and the object in either order.
- `curl` headers may be written `Name=value` or `Name: value`. `POST`/`PUT`/`PATCH`/`DELETE` send the body block.
  The executor's `httpRequestTimeout` applies.
- A script's leading `//` comment lines may carry `@budget lcp<2000 requests<50` (performance budgets, checked from
  the script's trace; see the README).
- `api` and `curl` share the browser session (config `browserApiSession`: `sync` default, `send`, `off`): the
  browser's cookies for the URL are sent (an explicit `Cookie` header wins by name) and Set-Cookies are stored back.
  Chromium does this over CDP for any domain; other browsers only for the domain of the page currently open.
- `curl` also takes a raw cURL command line on one line (from dev tools "Copy as cURL" or a GATF report):
  `-X`, `-H`, `-d`/`--data*`, `--json`, `-u`, `-b`, `-A`, `-e`, `-G` are converted when the script is compiled.
  Multipart (`-F`), body files (`@file`) and options GATF does not reproduce (`-x`, `-T`, ...) fail the parse.

### XML Plugin

```sel
	xmlread {xml-text}
	xmlwrite {optional-path-to-file} {xml-object-or-map-list-set}
	xmlpath {xml-text} {xpath-string}
Examples :-
	xmlread '<o><a>abc</a><b>1</b></o>'
	xmlwrite '/path/to/file.xml' $xmlObjectVar
	xmlpath '<o><a>abc</a><b>1</b></o>' '/o/a'
```

### Curl Plugin

```sel
	curl get|put|post|delete {url}
	{
		[
			header=value
		]
		content-body
	}
Examples :-
	curl get http://abc.com
	curl post http://abc.com
	{
		[
			Content-Type=application/xml
		]
		<abc>abc</abc>
	}
	curl 'http://abc.com/api' -H 'Content-Type: application/json' --data-raw '{"a":1}'    (raw cURL command line, single line)
```

### JSON Plugin

```sel
	jsonread {json-text}
	jsonwrite {optional-path-to-file} {jackson-annotated-json-object-or-map-list-set}
	jsonpath {json-text} {json-path-string}
Examples :-
	jsonread '{"a": "abc", "b": 1}'
	jsonwrite '/path/to/file.json' $jsonObjectVar
	jsonpath '{"a": "abc", "b": 1}' '$.a'
```

### API Plugin

```sel
	api {optional test-case-file-name}@{test-name}
Examples :-
	api api-name
	api test-case-file-path@api-name
```

## Word forms and short forms

The parser also accepts these spellings. Each one behaves exactly like the statement it stands for.

| Write | Same as | Notes |
|---|---|---|
| `if {find-expr}` | `? {find-expr}` | If block |
| `ifnot {find-expr}`, `?! {find-expr}` | `? {find-expr}`, negated | The block runs when the element is **not** found |
| `else if {find-expr}` | `:? {find-expr}` | Else-If block |
| `else ifnot {find-expr}`, `:?! {find-expr}` | `:? {find-expr}`, negated | |
| `else` | `:` | Else block |
| `loop over {find-expr}` | `## {find-expr}` | Loop over the matching elements |
| `begin code in {lang} {args}?` … `end code` | `<<<({lang}) {args}?` … `>>>` | Embedded code; `{lang}` is `java`, `js`, `groovy`, `ruby` or `python` |
| `ds query {dsn} {query}`, `dsq {dsn} {query}` | `#dsq {dsn} {query}` | The query is one argument: quote it |
| `#p-sf {provider}`, `#provider-sf {provider}` | `#p {provider}` | Stateful provider loop |
| `#o {dynprop}`, `#optional {dynprop}` | none | The block runs only when the `dynprops` variable is `yes`, `true`, `1`, `enabled` or `on` |
| `^{dynprop} {statement}` | none | One statement that runs only when the `dynprops` variable is `yes`, `true`, `1`, `enabled` or `on`; otherwise the line is skipped |

`dynprops` variables come from a [dynprops file](#import-dynamic-code-vars-properties-file-dynamic-variables-provide-support-for-configurable-code).

```sel
open chrome
goto https://example.com
if id@banner
{
	exec @print("banner shown")
}
else ifnot css@.cookie-notice
{
	exec @print("no cookie notice")
}
else
{
	exec @print("neither")
}
loop over css@a
{
	exec @print(@index)
}
begin code in java
System.out.println("from embedded java");
end code
#o runSmoke
{
	exec @print("smoke checks on")
}
^runSmoke goto https://example.com/smoke
```
