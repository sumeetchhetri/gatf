import { describe, it, expect } from 'vitest';
import { lineDiff } from './CompareFiles';

describe('lineDiff (LCS line alignment)', () => {
  it('marks identical files as all same', () => {
    const rows = lineDiff(['a', 'b', 'c'], ['a', 'b', 'c']);
    expect(rows).toHaveLength(3);
    expect(rows.every((r) => r.t === 'same')).toBe(true);
    expect(rows[0]).toMatchObject({ t: 'same', a: 'a', b: 'a', an: 1, bn: 1 });
  });

  it('detects a deleted line', () => {
    const rows = lineDiff(['a', 'b', 'c'], ['a', 'c']);
    expect(rows.map((r) => r.t)).toEqual(['same', 'del', 'same']);
    expect(rows[1]).toMatchObject({ t: 'del', a: 'b', an: 2 });
  });

  it('detects an added line', () => {
    const rows = lineDiff(['a', 'c'], ['a', 'b', 'c']);
    expect(rows.map((r) => r.t)).toEqual(['same', 'add', 'same']);
    expect(rows[1]).toMatchObject({ t: 'add', b: 'b', bn: 2 });
  });

  it('handles empty inputs on either side', () => {
    expect(lineDiff([], ['x'])).toEqual([{ t: 'add', b: 'x', bn: 1 }]);
    expect(lineDiff(['x'], [])).toEqual([{ t: 'del', a: 'x', an: 1 }]);
    expect(lineDiff([], [])).toEqual([]);
  });
});
