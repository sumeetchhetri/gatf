export function fmtBytes(n: number | null | undefined): string {
  if (n == null || !Number.isFinite(n)) return '–';
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  if (n < 1024 * 1024 * 1024) return `${(n / (1024 * 1024)).toFixed(2)} MB`;
  return `${(n / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}

export function fmtMs(n: number | null | undefined): string {
  if (n == null || !Number.isFinite(n) || n < 0) return '–';
  if (n < 1000) return `${Math.round(n)} ms`;
  return `${(n / 1000).toFixed(2)} s`;
}

export function fmtClock(ms: number): string {
  const d = new Date(ms);
  const p = (x: number, w = 2) => String(x).padStart(w, '0');
  return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}.${p(d.getMilliseconds(), 3)}`;
}

// offset from trace start, mm:ss.mmm
export function fmtOffset(ms: number): string {
  if (!Number.isFinite(ms) || ms < 0) ms = 0;
  const totalMs = Math.floor(ms);
  const m = Math.floor(totalMs / 60000);
  const s = Math.floor((totalMs % 60000) / 1000);
  const mmm = totalMs % 1000;
  const p = (x: number, w = 2) => String(x).padStart(w, '0');
  return `${p(m)}:${p(s)}.${p(mmm, 3)}`;
}

export function clamp(x: number, lo: number, hi: number): number {
  return x < lo ? lo : x > hi ? hi : x;
}
