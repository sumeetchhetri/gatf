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
import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  allDirs,
  ancestors,
  buildTree,
  filesHash,
  loadCollapsed,
  loadListHidden,
  normPath,
  pathFromHash,
  saveCollapsed,
  saveListHidden,
} from './testFilesState';
import { viewOfHash } from '../Shell';
import { harFromZip } from './importFile';
import { strToU8, zipSync } from 'fflate';

describe('Test Files tree (Δ163n bug 2)', () => {
  it('builds folders from Windows-separated server paths', () => {
    // base: the listing said 'ui\\login.sel' on Windows and the tree split on '/' only -> a flat list, nothing to collapse
    const t = buildTree(['ui\\login.sel', 'ui\\lib\\auth.sel', 'api/users.xml', 'top.sel']);
    expect(t.children!.map((c) => c.name)).toEqual(['api', 'ui', 'top.sel']);
    const ui = t.children!.find((c) => c.name === 'ui')!;
    expect(ui.children!.map((c) => c.path)).toEqual(['ui/lib', 'ui/login.sel']);
    expect(allDirs(t)).toEqual(['api', 'ui', 'ui/lib']);
  });
  it('normalises paths and lists the folders above a file', () => {
    expect(normPath('ui\\\\lib//a.sel')).toBe('ui/lib/a.sel');
    expect(ancestors('ui/lib/a.sel')).toEqual(['ui', 'ui/lib']);
    expect(ancestors('a.sel')).toEqual([]);
  });
  it('does not duplicate a file listed twice', () => {
    expect(buildTree(['a/x.sel', 'a\\x.sel']).children![0].children).toHaveLength(1);
  });
});

describe('URL state (Δ163n bug 7)', () => {
  it('round-trips the selected file through the hash, keeping / readable', () => {
    expect(filesHash('ui/lib/a b.sel')).toBe('#/testfiles?path=ui/lib/a%20b.sel');
    expect(pathFromHash(filesHash('ui/lib/a b.sel'))).toBe('ui/lib/a b.sel');
    expect(pathFromHash('#/testfiles')).toBeNull();
    expect(pathFromHash('#/testfiles?other=1')).toBeNull();
    expect(pathFromHash('#/reports?path=x.sel')).toBeNull();
    expect(pathFromHash('#/testfilesX?path=x.sel')).toBeNull();
    expect(filesHash(null)).toBe('#/testfiles');
  });
  it('routes a view hash that carries state', () => {
    // base: Shell looked the whole hash up, so #/testfiles?path=… fell back to Reports
    expect(viewOfHash('#/testfiles?path=ui/a.sel')).toBe('testfiles');
    expect(viewOfHash('#/testfiles')).toBe('testfiles');
    expect(viewOfHash('#/execute')).toBe('execute');
    expect(viewOfHash('#/nope?x')).toBe('reports');
    expect(viewOfHash('')).toBe('reports');
  });
});

describe('per-viewer preferences (Δ163n bugs 1, 2)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    window.localStorage.clear();
  });
  it('remembers collapsed folders per config and the hidden list', () => {
    saveCollapsed('a', new Set(['ui', 'ui/lib']));
    expect([...loadCollapsed('a')].sort()).toEqual(['ui', 'ui/lib']);
    expect(loadCollapsed('b').size).toBe(0);
    saveListHidden(true);
    expect(loadListHidden()).toBe(true);
    saveListHidden(false);
    expect(loadListHidden()).toBe(false);
  });
  it('survives blocked or corrupt storage', () => {
    window.localStorage.setItem('gatf.tf.collapsed:x', '{not json');
    expect(loadCollapsed('x').size).toBe(0);
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('denied');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('denied');
    });
    expect(() => saveCollapsed('x', new Set(['a']))).not.toThrow();
    expect(loadCollapsed('x').size).toBe(0);
    expect(loadListHidden()).toBe(false);
  });
});

describe('trace.zip upload for the flow importer (Δ163n bug 8)', () => {
  it('takes network.har out of a trace zip', () => {
    const z = zipSync({ 'trace.json': strToU8('{}'), 'network.har': strToU8('{"log":{"entries":[]}}') });
    expect(harFromZip(z)).toBe('{"log":{"entries":[]}}');
  });
  it('explains a zip without a HAR, and a non-zip', () => {
    expect(() => harFromZip(zipSync({ 'a.txt': strToU8('x') }))).toThrow(/No network.har/);
    expect(() => harFromZip(strToU8('not a zip'))).toThrow(/Not a readable zip/);
  });
});
