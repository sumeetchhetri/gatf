import { describe, it, expect } from 'vitest';
import { summarizeCoverage } from './coverage';
import type { Coverage } from './types';

describe('summarizeCoverage', () => {
  it('returns null for empty/absent coverage', () => {
    expect(summarizeCoverage(null)).toBeNull();
    expect(summarizeCoverage({})).toBeNull();
  });

  it('computes JS used/total from executed ranges', () => {
    const cov: Coverage = {
      js: [{
        scriptId: '1', url: 'http://x/app.js',
        functions: [{
          functionName: 'f', isBlockCoverage: true,
          ranges: [
            { startOffset: 0, endOffset: 40, count: 1 },   // executed
            { startOffset: 40, endOffset: 100, count: 0 },  // total extends to 100, not covered
          ],
        }],
      }],
    };
    const s = summarizeCoverage(cov)!;
    expect(s.js).toHaveLength(1);
    expect(s.js[0].url).toBe('app.js');
    expect(s.js[0].totalBytes).toBe(100);
    expect(s.js[0].usedBytes).toBe(40);
    expect(s.js[0].pct).toBeCloseTo(40);
    expect(s.jsTotals).toEqual({ used: 40, total: 100, pct: 40 });
  });

  it('merges overlapping executed ranges without double counting', () => {
    const cov: Coverage = {
      js: [{
        scriptId: '1', url: 'http://x/a.js',
        functions: [{
          functionName: 'f', isBlockCoverage: true,
          ranges: [
            { startOffset: 0, endOffset: 50, count: 1 },
            { startOffset: 25, endOffset: 100, count: 2 },  // overlaps -> union [0,100]
          ],
        }],
      }],
    };
    const s = summarizeCoverage(cov)!;
    expect(s.js[0].totalBytes).toBe(100);
    expect(s.js[0].usedBytes).toBe(100);
  });

  it('computes CSS used/total per stylesheet', () => {
    const cov: Coverage = {
      css: [
        { styleSheetId: 's1', startOffset: 0, endOffset: 50, used: true },
        { styleSheetId: 's1', startOffset: 50, endOffset: 100, used: false },
      ],
    };
    const s = summarizeCoverage(cov)!;
    expect(s.css).toHaveLength(1);
    expect(s.cssTotals).toEqual({ used: 50, total: 100, pct: 50 });
  });
});
