import { describe, it, expect } from 'vitest';
import { SELEASY } from './seleasyLang';

describe('SELEASY keyword map', () => {
  it('includes core seleasy commands and locators', () => {
    for (const kw of ['open', 'chrome', 'click', 'goto', 'subtest', '@call', 'include', 'config', 'type', 'screenshot', 'execjs', 'css', 'xpath']) {
      expect(kw in SELEASY, `missing keyword: ${kw}`).toBe(true);
    }
  });

  it('classifies commands (1) vs locators/values (3) vs operators (4)', () => {
    expect(SELEASY['open']).toBe(1);
    expect(SELEASY['subtest']).toBe(1);
    expect(SELEASY['@call']).toBe(1);
    expect(SELEASY['css']).toBe(3);
    expect(SELEASY['xpath']).toBe(3);
    expect(SELEASY['chrome']).toBe(3);
    expect(SELEASY['==']).toBe(4);
    expect(SELEASY['??']).toBe(1);          // conditional wait
    expect(SELEASY['if']).toEqual([1, 1]);  // block keyword -> tuple
  });

  it('has the full command vocabulary', () => {
    expect(Object.keys(SELEASY).length).toBeGreaterThan(150);
  });
});
