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
import { attachmentUrl, gallery, kindOf } from './attachments';

describe('attachments', () => {
  it('classifies by name', () => {
    expect(kindOf('/o/s/fail-1.png')).toBe('screenshot');
    expect(kindOf('/o/s/home-diff.png')).toBe('diff');
    expect(kindOf('/o/s/chrome-home-expected.png')).toBe('diff');
    expect(kindOf('/o/s/chrome-home-actual.png')).toBe('diff');
    expect(kindOf('/o/s/chrome-2-a11y.json')).toBe('a11y');
    expect(kindOf('C:\\o\\s\\run-trace.zip')).toBe('trace');
    expect(kindOf('/o/s/checks.json')).toBe('checks');
    expect(kindOf('/o/s/a11y-home.json')).toBe('a11y');
    expect(kindOf('/o/s/notes.txt')).toBe('other');
  });

  it('maps paths under the run dir (and relative ones) to report URLs', () => {
    expect(attachmentUrl('/w/out/17/f.png', '/w/out', '/reports/')).toBe('/reports/17/f.png');
    expect(attachmentUrl('C:\\w\\out\\17\\f.png', 'C:/w/out/', '/reports/')).toBe('/reports/17/f.png');
    expect(attachmentUrl('shard-1-of-2/17/f.png', undefined, '/reports/merged/')).toBe('/reports/merged/shard-1-of-2/17/f.png');
    expect(attachmentUrl('/elsewhere/f.png', '/w/out', '/reports/')).toBeNull();
    expect(attachmentUrl('/w/./out/17/f.png', '/w/out', '/reports/')).toBe('/reports/17/f.png');
    expect(attachmentUrl('c:\\w\\out\\17\\f.png', 'C:\\w\\out', '/reports/')).toBe('/reports/17/f.png');
  });

  it('groups a selection', () => {
    const g = gallery(
      [
        { title: 'a', attachments: ['/w/out/1/a.png', '/w/out/1/a-trace.zip'] },
        { title: 'b', attachments: ['/w/out/2/b.png'] },
        { title: 'c' },
      ],
      '/w/out',
      '/reports/',
    );
    expect(g.get('screenshot')!.map((x) => x.url)).toEqual(['/reports/1/a.png', '/reports/2/b.png']);
    expect(g.get('trace')![0].test).toBe('a');
    expect(g.has('diff')).toBe(false);
  });
});
