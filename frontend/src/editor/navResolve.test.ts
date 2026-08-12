import { describe, it, expect, vi } from 'vitest';
import { stripEolComment, navTarget, navigateForLine, type NavResolver, type NavHooks } from './navResolve';

describe('stripEolComment', () => {
  it('strips a whitespace-preceded trailing // comment', () => {
    expect(stripEolComment('config app.properties //note')).toBe('config app.properties');
  });
  it('preserves :// inside a URL', () => {
    expect(stripEolComment('goto https://example.com/path')).toBe('goto https://example.com/path');
  });
  it('preserves // inside a quoted string', () => {
    expect(stripEolComment('execjs "a//b"')).toBe('execjs "a//b"');
  });
  it('leaves ordinary lines untouched', () => {
    expect(stripEolComment('open chrome')).toBe('open chrome');
  });
});

describe('navTarget', () => {
  it('classifies reference keywords', () => {
    expect(navTarget('include setup.sel')).toEqual({ kind: 'ref', arg: 'setup.sel' });
    expect(navTarget('config app.properties')).toEqual({ kind: 'ref', arg: 'app.properties' });
  });
  it('passes the whole @call line as a subtest', () => {
    expect(navTarget('@call "doLogin"')).toEqual({ kind: 'subtest', arg: '@call "doLogin"' });
  });
  it('extracts a goto URL', () => {
    expect(navTarget('goto https://example.com')).toEqual({ kind: 'goto', arg: 'https://example.com' });
  });
  it('returns null for ordinary code', () => {
    expect(navTarget('click id@ submit')).toBeNull();
  });
});

describe('navigateForLine', () => {
  const resolver: NavResolver = {
    resolveRef: async (_p, ref) => (ref === 'setup.sel' ? ['tests/ui/setup.sel'] : []),
    resolveSubtest: async () => ['tests/ui/lib/auth.sel', 12],
  };
  it('drives onNavigate for a resolved reference', async () => {
    const onNavigate = vi.fn();
    const hooks: NavHooks = { getPath: () => 'tests/ui/login.sel', onNavigate };
    const handled = await navigateForLine('include setup.sel', resolver, hooks);
    expect(handled).toBe(true);
    expect(onNavigate).toHaveBeenCalledWith('tests/ui/setup.sel');
  });
  it('reports an error when a reference does not resolve', async () => {
    const onError = vi.fn();
    const hooks: NavHooks = { getPath: () => 'x.sel', onNavigate: vi.fn(), onError };
    await navigateForLine('include missing.sel', resolver, hooks);
    expect(onError).toHaveBeenCalled();
  });
  it('opens a goto URL via the injected opener (no real window needed)', async () => {
    const openUrl = vi.fn();
    const hooks: NavHooks = { getPath: () => 'x.sel', onNavigate: vi.fn(), openUrl };
    const handled = await navigateForLine('goto https://example.com', resolver, hooks);
    expect(handled).toBe(true);
    expect(openUrl).toHaveBeenCalledWith('https://example.com');
  });
  it('returns false for ordinary code', async () => {
    const hooks: NavHooks = { getPath: () => 'x.sel', onNavigate: vi.fn() };
    expect(await navigateForLine('click id@ submit', resolver, hooks)).toBe(false);
  });
});
