/*
    Copyright 2013-2026, Sumeet Chhetri

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/
import { describe, it, expect } from 'vitest';
import { buildExecRows, visibleRows, dirState } from './execTree';

describe('execTree (folder execution)', () => {
  const paths = ['ui/lib/auth.sel', 'root.sel', 'ui/login.sel', 'ui/lib/nav.sel', 'api/x.sel'];

  it('interleaves folders before their descendants, each folder once', () => {
    const rows = buildExecRows(paths);
    expect(rows.map((r) => (r.kind === 'dir' ? 'D:' : '') + r.path)).toEqual([
      'D:api', 'api/x.sel', 'root.sel', 'D:ui', 'D:ui/lib', 'ui/lib/auth.sel', 'ui/lib/nav.sel', 'ui/login.sel',
    ]);
    const ui = rows.find((r) => r.path === 'ui');
    expect(ui).toMatchObject({ kind: 'dir', depth: 0, files: ['ui/lib/auth.sel', 'ui/lib/nav.sel', 'ui/login.sel'] });
    expect(rows.find((r) => r.path === 'ui/lib/nav.sel')).toMatchObject({ depth: 2, name: 'nav.sel' });
  });

  it('collapsing hides descendants but keeps the folder row', () => {
    const v = visibleRows(buildExecRows(paths), new Set(['ui']));
    expect(v.map((r) => r.path)).toEqual(['api', 'api/x.sel', 'root.sel', 'ui']);
  });

  it('folder tri-state', () => {
    const files = ['a/1.sel', 'a/2.sel'];
    expect(dirState(files, new Set())).toBe('none');
    expect(dirState(files, new Set(['a/1.sel']))).toBe('some');
    expect(dirState(files, new Set(files))).toBe('all');
  });
});
