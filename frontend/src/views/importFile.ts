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
import { unzipSync, strFromU8 } from 'fflate';

// Δ163n (bug 8): the Flow → load importer says its source is "a UI run's network.har (inside its trace zip)", but the
// upload read every file as text, so a picked trace.zip became binary garbage. A .zip now yields its network.har.
export function harFromZip(bytes: Uint8Array): string {
  let files: Record<string, Uint8Array>;
  try {
    files = unzipSync(bytes, { filter: (f) => /(^|\/)network\.har$/.test(f.name) });
  } catch {
    throw new Error('Not a readable zip file.');
  }
  const name = Object.keys(files).sort((a, b) => a.length - b.length)[0];
  if (!name) throw new Error('No network.har in this zip (record the run with tracing and traceNetwork on).');
  return strFromU8(files[name]);
}

/** The text an importer gets from an uploaded file (a trace .zip -> its network.har for the HAR / flow importers). */
export async function importText(kind: string, f: File): Promise<string> {
  if ((kind === 'flow' || kind === 'har') && /\.zip$/i.test(f.name)) {
    return harFromZip(new Uint8Array(await f.arrayBuffer()));
  }
  return f.text();
}
