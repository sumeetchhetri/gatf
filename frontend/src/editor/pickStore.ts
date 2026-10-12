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
// Δ148: the last picked locator line (from the Trace Viewer's snapshot picker), waiting to be pasted into the editor.
// The line is exactly what the server built (PickLocator / LocatorRanking); nothing here formats seleasy.
import { useSyncExternalStore } from 'react';

export interface Picked {
  line: string;
  /** where it was picked, e.g. "trace snapshot #12" */
  origin: string;
}

let current: Picked | null = null;
const subs = new Set<() => void>();

export const pickStore = {
  get: (): Picked | null => current,
  set(p: Picked | null): void {
    current = p && typeof p.line === 'string' && p.line && !/[\r\n]/.test(p.line) ? { ...p } : null;
    for (const f of Array.from(subs)) f();
  },
  subscribe(f: () => void): () => void {
    subs.add(f);
    return () => { subs.delete(f); };
  },
};

export function usePicked(): Picked | null {
  return useSyncExternalStore(pickStore.subscribe, pickStore.get, pickStore.get);
}
