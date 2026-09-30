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
// M13: plugin names + docs from the running GATF server (GET /api/plugins) for completion and diagnostics.
// Built-ins, plugins.txt entries and plugins/*.jar SDK plugins all appear; a server that is not running just
// leaves the static keyword catalog.

export interface PluginInfo {
  name: string;
  kind?: string;
  summary?: string | null;
  usage?: string[];
  args?: { name: string; doc?: string | null; required?: boolean }[];
  examples?: string[];
  source?: string | null;
}

export interface PluginItem {
  label: string;
  detail: string;
  insertText: string;
  documentation: string;
}

type FetchLike = (url: string, init?: { signal?: unknown }) => Promise<{ ok: boolean; status: number; json(): Promise<unknown> }>;

/** `${base}/api/plugins` (+ `?token=` when the server has auth on). */
export function pluginsUrl(base: string, token?: string): string {
  const b = (base || 'http://localhost:9080').replace(/\/+$/, '');
  return b + '/api/plugins' + (token ? '?token=' + encodeURIComponent(token) : '');
}

/** Completion entries, one per valid plugin name the catalog does not already cover. */
export function pluginItems(list: unknown, known: Set<string>): PluginItem[] {
  if (!Array.isArray(list)) return [];
  const out: PluginItem[] = [];
  for (const p of list as PluginInfo[]) {
    if (!p || typeof p.name !== 'string' || !/^[a-z][\w-]*$/i.test(p.name) || known.has(p.name.toLowerCase())) continue;
    const args = (p.args || []).map((a) => (a.required ? a.name : '[' + a.name + ']')).join(' ');
    const doc = [
      p.summary || '',
      args ? 'args: ' + args : '',
      ...(p.usage || []),
      ...(p.examples || []).map((e) => 'e.g. `' + e + '`'),
      p.source ? '(' + (p.kind || 'plugin') + ': ' + p.source + ')' : '',
    ].filter(Boolean);
    out.push({ label: p.name, detail: 'plugin (' + (p.kind || 'sdk') + ')', insertText: p.name + ' ', documentation: doc.join('\n\n') });
  }
  return out;
}

/** Fetches the server's plugin list; [] on any failure (server down, auth, older server) within `timeoutMs`. */
export async function fetchPlugins(base: string, token: string | undefined, fetchImpl: FetchLike, timeoutMs = 3000): Promise<PluginInfo[]> {
  const timer = new Promise<PluginInfo[]>((resolve) => setTimeout(() => resolve([]), timeoutMs));
  const req = fetchImpl(pluginsUrl(base, token))
    .then((r) => (r.ok ? r.json() : []))
    .then((j) => (Array.isArray(j) ? (j as PluginInfo[]) : []))
    .catch(() => [] as PluginInfo[]);
  return Promise.race([req, timer]);
}
