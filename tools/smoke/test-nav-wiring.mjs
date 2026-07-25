#!/usr/bin/env node
// Headless regression test for the seleasy editor's cross-file navigation WIRING
// (frontend/src/editor/navResolve.ts). C2.1 only tested navTarget() classification (8/8) yet the
// in-browser click did nothing — the gap was the click -> resolve -> navigate path. That path is
// now pure (navResolve.ts, injectable resolver + hooks) so it is exercised here without a browser.
//
// The module is dependency-free TypeScript; we bundle it with the local esbuild to an ESM file and
// import it. Run: node tools/smoke/test-nav-wiring.mjs   (or via tools/smoke/run-smoke.sh)
import { execFileSync } from 'node:child_process';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repo = resolve(here, '../..');
const src = join(repo, 'frontend/src/editor/navResolve.ts');
const esbuild = join(repo, 'frontend/node_modules/.bin/esbuild');
const out = join(mkdtempSync(join(tmpdir(), 'navtest-')), 'navResolve.mjs');

execFileSync(esbuild, [src, '--bundle', '--format=esm', '--platform=neutral', `--outfile=${out}`], {
  stdio: ['ignore', 'ignore', 'inherit'],
});
const { navTarget, stripEolComment, navigateForLine } = await import(pathToFileURL(out).href);

let pass = 0,
  fail = 0;
const eq = (a, b) => JSON.stringify(a) === JSON.stringify(b);
function ok(name, cond) {
  if (cond) {
    pass++;
    console.log(`  ok   ${name}`);
  } else {
    fail++;
    console.log(`  FAIL ${name}`);
  }
}

// ---- classification (with trailing-comment stripping) ----
ok('navTarget config -> ref', eq(navTarget('config app.properties'), { kind: 'ref', arg: 'app.properties' }));
ok('navTarget include+comment -> ref (stripped)', eq(navTarget('include lib/nav.sel  // helpers'), { kind: 'ref', arg: 'lib/nav.sel' }));
ok('navTarget import -> ref', eq(navTarget('import lib/auth.sel'), { kind: 'ref', arg: 'lib/auth.sel' }));
ok('navTarget dynprops -> ref', eq(navTarget('dynprops dynamic.props'), { kind: 'ref', arg: 'dynamic.props' }));
ok('navTarget @call -> subtest (whole line)', eq(navTarget('@call "doLogin"'), { kind: 'subtest', arg: '@call "doLogin"' }));
ok('navTarget goto url -> goto', eq(navTarget('goto https://example.com'), { kind: 'goto', arg: 'https://example.com' }));
ok('navTarget goto url+comment', eq(navTarget('goto https://example.com //site'), { kind: 'goto', arg: 'https://example.com' }));
ok('navTarget plain command -> null', navTarget('click id@save') === null);
ok('stripEolComment keeps :// url', stripEolComment('goto https://x //c') === 'goto https://x');
ok('stripEolComment keeps // in string', stripEolComment('type u "a // b"') === 'type u "a // b"');

// ---- wiring: ref -> resolveRef -> onNavigate(target) ----
{
  const calls = { ref: [], sub: [] };
  const nav = [];
  const resolver = {
    resolveRef: async (p, r) => (calls.ref.push([p, r]), ['ui/props/app.properties']),
    resolveSubtest: async () => ['x', 0],
  };
  const handled = await navigateForLine('config app.properties //notes', resolver, {
    getPath: () => 'ui/login.sel',
    onNavigate: (p, l) => nav.push([p, l]),
  });
  ok('ref: handled=true', handled === true);
  ok('ref: resolveRef called with STRIPPED arg', eq(calls.ref, [['ui/login.sel', 'app.properties']]));
  ok('ref: onNavigate got resolved target', eq(nav, [['ui/props/app.properties', undefined]]));
}

// ---- wiring: subtest -> resolveSubtest -> onNavigate(target, line) ----
{
  const nav = [];
  const resolver = {
    resolveRef: async () => [],
    resolveSubtest: async (p, c) => (p === 'ui/login.sel' && c === '@call "doLogin"' ? ['ui/lib/auth.sel', 12] : ['', 0]),
  };
  await navigateForLine('@call "doLogin"', resolver, { getPath: () => 'ui/login.sel', onNavigate: (p, l) => nav.push([p, l]) });
  ok('subtest: onNavigate got [file, line]', eq(nav, [['ui/lib/auth.sel', 12]]));
}

// ---- wiring: goto -> openUrl, no resolver call ----
{
  let opened = null;
  let refCalls = 0;
  const resolver = { resolveRef: async () => (refCalls++, []), resolveSubtest: async () => ['', 0] };
  const handled = await navigateForLine('goto https://example.com', resolver, {
    getPath: () => 'ui/login.sel',
    onNavigate: () => {},
    openUrl: (u) => (opened = u),
  });
  ok('goto: handled=true', handled === true);
  ok('goto: openUrl called with url', opened === 'https://example.com');
  ok('goto: resolver NOT called', refCalls === 0);
}

// ---- wiring: non-nav line -> false, nothing fires ----
{
  let touched = 0;
  const resolver = { resolveRef: async () => (touched++, []), resolveSubtest: async () => (touched++, ['', 0]) };
  const handled = await navigateForLine('click id@save', resolver, { getPath: () => 'x.sel', onNavigate: () => touched++ });
  ok('plain: handled=false, no side effects', handled === false && touched === 0);
}

// ---- wiring: unresolved ref -> onError, onNavigate NOT called ----
{
  const nav = [];
  let err = null;
  await navigateForLine('config missing.props', { resolveRef: async () => [], resolveSubtest: async () => ['', 0] }, {
    getPath: () => 'x.sel',
    onNavigate: (p) => nav.push(p),
    onError: (m) => (err = m),
  });
  ok('unresolved: onError fired', typeof err === 'string' && err.length > 0);
  ok('unresolved: onNavigate NOT called', nav.length === 0);
}

// ---- wiring: resolver throws -> onError, no throw ----
{
  let err = null;
  const handled = await navigateForLine('config x.props', {
    resolveRef: async () => {
      throw new Error('HTTP 500');
    },
    resolveSubtest: async () => ['', 0],
  }, { getPath: () => 'x.sel', onNavigate: () => {}, onError: (m) => (err = m) });
  ok('throw: still returns true', handled === true);
  ok('throw: onError got the message', typeof err === 'string' && /500/.test(err));
}

rmSync(dirname(out), { recursive: true, force: true });
console.log(`\nnav-wiring: ${pass} passed, ${fail} failed`);
process.exit(fail ? 1 : 0);
