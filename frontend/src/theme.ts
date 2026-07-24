// Light/dark theme, persisted per browser session in sessionStorage. Falls back to the OS preference,
// then dark. The theme is applied as data-theme on <html>; styles.css defines both palettes.
const KEY = 'theme';
export type Theme = 'dark' | 'light';

export function getTheme(): Theme {
  try {
    const s = sessionStorage.getItem(KEY);
    if (s === 'dark' || s === 'light') return s;
  } catch {
    /* sessionStorage unavailable */
  }
  return window.matchMedia && window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
}

export function applyTheme(t: Theme) {
  document.documentElement.setAttribute('data-theme', t);
}

export function setTheme(t: Theme) {
  try {
    sessionStorage.setItem(KEY, t);
  } catch {
    /* ignore */
  }
  applyTheme(t);
}

export function toggleTheme(): Theme {
  const next: Theme = getTheme() === 'dark' ? 'light' : 'dark';
  setTheme(next);
  return next;
}
