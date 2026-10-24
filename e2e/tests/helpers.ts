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
import { expect, type Locator, type Page } from '@playwright/test';

/** Δ163n: selenium-backed specs run with a Grid (GATF_SELENIUM_GRID) or a local chromedriver (GATF_E2E_CHROMEDRIVER). */
export const HAS_BROWSER = !!(process.env.GATF_SELENIUM_GRID || process.env.GATF_E2E_CHROMEDRIVER);

/** Δ163n: a minimal STORED (uncompressed) zip, so upload specs need no zip dependency. */
export function storedZip(files: Record<string, string>): Buffer {
  const crcTable = Array.from({ length: 256 }, (_, n) => {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    return c >>> 0;
  });
  const crc32 = (b: Buffer) => {
    let c = 0xffffffff;
    for (const x of b) c = crcTable[(c ^ x) & 0xff] ^ (c >>> 8);
    return (c ^ 0xffffffff) >>> 0;
  };
  const locals: Buffer[] = [];
  const centrals: Buffer[] = [];
  let off = 0;
  for (const [name, text] of Object.entries(files)) {
    const nb = Buffer.from(name, 'utf8');
    const data = Buffer.from(text, 'utf8');
    const crc = crc32(data);
    const lh = Buffer.alloc(30);
    lh.writeUInt32LE(0x04034b50, 0); lh.writeUInt16LE(20, 4); lh.writeUInt32LE(crc, 14);
    lh.writeUInt32LE(data.length, 18); lh.writeUInt32LE(data.length, 22); lh.writeUInt16LE(nb.length, 26);
    const ch = Buffer.alloc(46);
    ch.writeUInt32LE(0x02014b50, 0); ch.writeUInt16LE(20, 4); ch.writeUInt16LE(20, 6); ch.writeUInt32LE(crc, 16);
    ch.writeUInt32LE(data.length, 20); ch.writeUInt32LE(data.length, 24); ch.writeUInt16LE(nb.length, 28); ch.writeUInt32LE(off, 42);
    locals.push(lh, nb, data);
    centrals.push(ch, nb);
    off += 30 + nb.length + data.length;
  }
  const cd = Buffer.concat(centrals);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0); end.writeUInt16LE(Object.keys(files).length, 8); end.writeUInt16LE(Object.keys(files).length, 10);
  end.writeUInt32LE(cd.length, 12); end.writeUInt32LE(off, 16);
  return Buffer.concat([...locals, cd, end]);
}

/**
 * Open the Test Files view and return the first file node (optionally containing `text`).
 * Waits for the tree to finish loading first. Folders render EXPANDED by default (delta-045), so only
 * collapsed folders (caret ▸) are clicked. Clicking an expanded folder would collapse it, hide its
 * children, and leave a later click waiting on a hidden node until the test timeout.
 */
export async function fileNode(page: Page, text?: string): Promise<Locator> {
  await page.getByRole('button', { name: 'Test Files' }).click();
  await expect(page.locator('.tf-node').first()).toBeVisible();          // tree rendered (not "Loading…")
  const files = text ? page.locator('.tf-node.tf-file', { hasText: text }) : page.locator('.tf-node.tf-file');
  const collapsed = page.locator('.tf-node.tf-dir', { has: page.locator('.tf-caret', { hasText: '\u25b8' }) });
  for (let i = 0; i < 50 && (await files.count()) === 0 && (await collapsed.count()) > 0; i++) {
    await collapsed.first().click();
  }
  return files.first();
}

/**
 * Δ163p: delete the files the bug-fix specs create (b<N>-*, b8/*, st-* / hc-* at any depth), so the suite can run
 * again on a reused server (no CI: reuseExistingServer) without tripping over its own leftovers.
 */
export async function clearFixtures(request: import('@playwright/test').APIRequestContext) {
  const r = await request.get('/testcasefiles');
  if (!r.ok()) return;
  const files = (await r.json()) as [string, string][];
  for (const [p] of files) {
    const n = p.replace(/\\/g, '/');
    if (/^b8\//.test(n) || /(^|\/)(b\d+-|st-|hc-)[^/]*$/.test(n)) {
      await request.delete('/testcasefiles?testcaseFileName=' + encodeURIComponent(n));
    }
  }
}
