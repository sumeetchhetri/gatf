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
