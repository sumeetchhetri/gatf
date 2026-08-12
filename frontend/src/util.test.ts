import { describe, it, expect } from 'vitest';
import { fmtBytes, fmtMs, fmtOffset, clamp, reportSidecarUrl, reportHtmlUrl } from './util';

describe('fmtBytes', () => {
  it('nullish / non-finite -> dash', () => {
    expect(fmtBytes(null)).toBe('–');
    expect(fmtBytes(undefined)).toBe('–');
    expect(fmtBytes(NaN)).toBe('–');
  });
  it('scales units', () => {
    expect(fmtBytes(512)).toBe('512 B');
    expect(fmtBytes(1024)).toBe('1.0 KB');
    expect(fmtBytes(1024 * 1024)).toBe('1.00 MB');
    expect(fmtBytes(1024 * 1024 * 1024)).toBe('1.00 GB');
  });
});

describe('fmtMs', () => {
  it('negative / nullish -> dash', () => {
    expect(fmtMs(-1)).toBe('–');
    expect(fmtMs(null)).toBe('–');
  });
  it('ms then seconds', () => {
    expect(fmtMs(0)).toBe('0 ms');
    expect(fmtMs(999)).toBe('999 ms');
    expect(fmtMs(1500)).toBe('1.50 s');
  });
});

describe('fmtOffset', () => {
  it('mm:ss.mmm from trace start', () => {
    expect(fmtOffset(0)).toBe('00:00.000');
    expect(fmtOffset(1500)).toBe('00:01.500');
    expect(fmtOffset(65432)).toBe('01:05.432');
    expect(fmtOffset(-5)).toBe('00:00.000');
  });
});

describe('clamp', () => {
  it('bounds a value', () => {
    expect(clamp(5, 0, 10)).toBe(5);
    expect(clamp(-1, 0, 10)).toBe(0);
    expect(clamp(11, 0, 10)).toBe(10);
  });
});

describe('report URL resolution', () => {
  it('sidecar: absolute path swaps extension', () => {
    expect(reportSidecarUrl('/reports/r1/index.html', '/reports/r1/')).toBe('/reports/r1/index.data.json');
  });
  it('sidecar: relative resolved against baseDir', () => {
    expect(reportSidecarUrl('./sub/run.html', '/reports/r1/')).toBe('/reports/r1/sub/run.data.json');
    expect(reportSidecarUrl('run.html', '/reports/r1/')).toBe('/reports/r1/run.data.json');
  });
  it('html: relative resolved, absolute untouched', () => {
    expect(reportHtmlUrl('run.html', '/reports/r1/')).toBe('/reports/r1/run.html');
    expect(reportHtmlUrl('/x/y.html', '/reports/r1/')).toBe('/x/y.html');
  });
});
