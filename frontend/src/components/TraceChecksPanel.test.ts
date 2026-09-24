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
import { describe, expect, it } from 'vitest';
import { groupFindings } from './TraceChecksPanel';

describe('groupFindings', () => {
  it('groups by page in first-seen order, most severe first', () => {
    const f = (page: string, severity: string, rule: string) => ({ page, severity, rule, url: null, message: rule });
    const g = groupFindings([
      f('https://a/', 'low', 'missing-nosniff'),
      f('https://b/', 'medium', 'missing-csp'),
      f('https://a/', 'high', 'mixed-content'),
      f('https://a/', 'medium', 'missing-hsts'),
    ]);
    expect(g.map(([p]) => p)).toEqual(['https://a/', 'https://b/']);
    expect(g[0][1].map((x) => x.rule)).toEqual(['mixed-content', 'missing-hsts', 'missing-nosniff']);
  });
});
