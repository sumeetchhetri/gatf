---
title: Organise UI suites
sidebar_position: 14
---

# Organise UI suites: subtests, hooks, markers, retries

## Subtests and hooks

```sel title="cart.sel"
// Cart behaviour @cart
open chrome

beforeEach
{
	goto https://shop.example.com
}

subtest "add an item"
{
	click role@button[name='Add to cart']
	?? testid@cart-count text ="1"
}

skip subtest "export the cart"
{
	click id@export
}

subtest "empty cart message"
{
	click role@link[name=Cart]
	??~ css@.empty text ="Your cart is empty"
}

afterAll
{
	exec @print("cart suite done")
}
```

- Each `subtest` is reported on its own. `@call "name"` runs a subtest from elsewhere, also from an `import`ed file. Subtests bound to a browser session can take arguments; see [Subtest definition](../seleasy/REFERENCE.md#subtest-definition).
- `beforeAll` / `beforeEach` / `afterEach` / `afterAll` run around the script and each subtest; the `after` hooks also run after a failure.
- `skip subtest` and `fixme subtest` skip it; `only subtest` skips all others in the file. They are reported as skipped with the reason. `gatf test --forbid-only` (or `forbidOnly` in the config) turns a leftover `only` into an error, for CI.
- `??~` is a soft assertion: the subtest goes on and fails at the end, listing every soft failure.

## Suite-level setup

`globalSetup` and `globalTeardown` in the config run a script once before and after the whole UI suite (teardown also after a failure); a failing setup stops the run. `loginOnce` logs in once for everyone: see [Log in once](auth-state.md).

## Retries and flaky tests

`seleniumScriptRetryCount` (or `gatf test --retries N`) re-runs failed scripts. A script that fails and then passes is reported as **flaky** (yellow): it does not fail the run, and `gatf-junit.xml` marks it with `<flakyFailure>`. With `traceMode: on-first-retry`, traces are recorded only in the first retry round.

## Waits that don't flake

Switch on `autoWait` and `webFirstAssertions` in the config. For a single step, `click! …` or a `nowait` prefix opts out of auto-wait. `??N` bounds how long a line waits for its element.
