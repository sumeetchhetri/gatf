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
// Δ163n: Test Files view state that outlives the view (it unmounts on every navigation) — the tree, the deep-linkable
// selected file (#/testfiles?path=…), per-viewer folder/list preferences and the per-file scroll position.

export type Kind = 'sel' | 'xml' | 'other';
export const kindOf = (p: string): Kind =>
  p.toLowerCase().endsWith('.sel') ? 'sel' : p.toLowerCase().endsWith('.xml') ? 'xml' : 'other';

/** A server path in '/' form. Bug 2: on Windows the listing came back as 'ui\\a.sel', so the tree had no folders. */
export const normPath = (p: string): string => p.split(/[\\/]+/).filter(Boolean).join('/');

export interface TreeNode {
  name: string;
  path: string;
  kind?: Kind;
  children?: TreeNode[];
}

export function buildTree(paths: string[]): TreeNode {
  const root: TreeNode = { name: '', path: '', children: [] };
  for (const p of paths.map(normPath).filter(Boolean).sort((a, b) => a.localeCompare(b))) {
    const parts = p.split('/');
    let cur = root;
    let acc = '';
    for (let i = 0; i < parts.length; i++) {
      const part = parts[i];
      acc = acc ? acc + '/' + part : part;
      if (i === parts.length - 1) {
        if (!cur.children!.some((c) => !c.children && c.path === p)) cur.children!.push({ name: part, path: p, kind: kindOf(p) });
      } else {
        let dir = cur.children!.find((c) => c.children && c.name === part);
        if (!dir) {
          dir = { name: part, path: acc, children: [] };
          cur.children!.push(dir);
        }
        cur = dir;
      }
    }
  }
  const sortRec = (n: TreeNode) => {
    if (!n.children) return;
    n.children.sort((a, b) => {
      const ad = !!a.children,
        bd = !!b.children;
      if (ad !== bd) return ad ? -1 : 1; // directories first
      return a.name.localeCompare(b.name);
    });
    n.children.forEach(sortRec);
  };
  sortRec(root);
  return root;
}

/** Every folder path of the tree. */
export function allDirs(n: TreeNode, out: string[] = []): string[] {
  for (const c of n.children ?? []) {
    if (c.children) {
      out.push(c.path);
      allDirs(c, out);
    }
  }
  return out;
}

/** The folders that contain {@code path}: 'ui/lib/a.sel' -> ['ui', 'ui/lib']. */
export function ancestors(path: string): string[] {
  const parts = normPath(path).split('/');
  const out: string[] = [];
  for (let i = 1; i < parts.length; i++) out.push(parts.slice(0, i).join('/'));
  return out;
}

// ---- URL state (bug 7): #/testfiles?path=ui/a.sel ----
const FILES = '#/testfiles';

/** The file a #/testfiles hash points at; null when the hash names no file (or is not a Test Files hash). */
export function pathFromHash(hash: string): string | null {
  if (hash !== FILES && !hash.startsWith(FILES + '?')) return null;
  const q = hash.indexOf('?');
  if (q === -1) return null;
  const p = new URLSearchParams(hash.slice(q + 1)).get('path');
  return p ? normPath(p) : null;
}

export function filesHash(path: string | null): string {
  return path ? FILES + '?path=' + encodeURIComponent(path).replace(/%2F/gi, '/') : FILES;
}

// ---- per-viewer preferences (localStorage; every access in try/catch: private windows / blocked storage) ----
function lsGet(k: string): string | null {
  try {
    return window.localStorage.getItem(k);
  } catch {
    return null;
  }
}
function lsSet(k: string, v: string) {
  try {
    window.localStorage.setItem(k, v);
  } catch {
    /* storage unavailable: the preference just isn't remembered */
  }
}

/** Collapsed folders (bug 2). Stored as the collapsed set so folders added later show open. */
export function loadCollapsed(cfg: string): Set<string> {
  try {
    const v = JSON.parse(lsGet('gatf.tf.collapsed:' + cfg) ?? '[]');
    return new Set(Array.isArray(v) ? v.filter((x) => typeof x === 'string') : []);
  } catch {
    return new Set();
  }
}
export function saveCollapsed(cfg: string, s: Set<string>) {
  lsSet('gatf.tf.collapsed:' + cfg, JSON.stringify([...s]));
}

/** The file list column hidden (bug 1: the editor takes the width). */
export function loadListHidden(): boolean {
  return lsGet('gatf.tf.listHidden') === '1';
}
export function saveListHidden(v: boolean) {
  lsSet('gatf.tf.listHidden', v ? '1' : '0');
}

// ---- session memory (bug 7): survives the view's unmount while the SPA stays loaded ----
export const tfMemory = {
  lastPath: new Map<string, string>(), // per config
  scroll: new Map<string, number>(), // editor scrollTop per config + file
  listScroll: new Map<string, number>(), // tree scrollTop per config
};
