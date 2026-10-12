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
import { asCoverageIndex, scriptRows, untestedRoutes } from './coverageIndex';

describe('coverage index helpers', () => {
  it('accepts only gatf-coverage-index v1 and fills missing sections', () => {
    expect(asCoverageIndex(null)).toBeNull();
    expect(asCoverageIndex([])).toBeNull();
    expect(asCoverageIndex({ js: [] })).toBeNull(); // a raw trace coverage.json is not an index
    expect(asCoverageIndex({ format: 'gatf-coverage-index', version: 0, status: 'ok', counts: {} })).toBeNull();
    const i = asCoverageIndex({ format: 'gatf-coverage-index', version: 1, status: 'partial', counts: {} })!;
    expect(i.untested.routes).toBeNull();
    expect(i.untested.scripts).toEqual([]);
    expect(i.scripts).toEqual({});
    expect(i.css.totalBytes).toBe(0);
    expect(i.skipped).toEqual([]);
  });

  it('orders untested routes by crawl priority and scripts by size', () => {
    const i = asCoverageIndex({
      format: 'gatf-coverage-index',
      version: 1,
      status: 'ok',
      counts: {},
      untested: { routes: [{ route: 'b', priority: null }, { route: 'c', priority: 'low' }, { route: 'a', priority: 'high' }], scripts: [] },
      scripts: { 'x.js': { len: 10, usedBytes: 5, functionsHit: 1, functionsTotal: 2, tests: ['t'], bodies: [] },
        'y.js': { len: 0, usedBytes: 0, functionsHit: 0, functionsTotal: 0, tests: [], bodies: [] } },
    })!;
    expect(untestedRoutes(i)!.map((r) => r.route)).toEqual(['a', 'c', 'b']);
    const rows = scriptRows(i);
    expect(rows.map((r) => [r.url, r.pct, r.functions])).toEqual([['x.js', 50, '1/2'], ['y.js', 0, '0/0']]);
  });
});
