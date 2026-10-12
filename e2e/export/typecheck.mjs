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
// Δ160: typechecks every export golden against @playwright/test with the TypeScript compiler pinned by
// frontend/package-lock.json (no global tsc, no npx download). Run from e2e/: npm run typecheck:export
import { existsSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = fileURLToPath(new URL('.', import.meta.url));
const tsc = join(here, '..', '..', 'frontend', 'node_modules', 'typescript', 'bin', 'tsc');
if (!existsSync(tsc)) {
  console.error('run npm ci in frontend first');
  process.exit(1);
}
const prep = spawnSync(process.execPath, [join(here, 'prepare.mjs')], { stdio: 'inherit' });
if (prep.status !== 0) process.exit(prep.status ?? 1);
const run = spawnSync(process.execPath, [tsc, '-p', join(here, 'tsconfig.json'), '--noEmit', ...process.argv.slice(2)], { stdio: 'inherit' });
process.exit(run.status ?? 1);
