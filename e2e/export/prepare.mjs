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
// Δ160: copies the export goldens (alldep-jar/src/test/resources/export-corpus/playwright/*.spec.ts) into
// e2e/export/.generated/, where @playwright/test resolves from e2e/node_modules. Used by test:export and typecheck:export.
import { cpSync, mkdirSync, readdirSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = fileURLToPath(new URL('.', import.meta.url));
const corpus = join(here, '..', '..', 'alldep-jar', 'src', 'test', 'resources', 'export-corpus', 'playwright');
const out = join(here, '.generated');
rmSync(out, { recursive: true, force: true });
mkdirSync(out, { recursive: true });
const specs = readdirSync(corpus).filter((f) => f.endsWith('.spec.ts'));
for (const f of specs) cpSync(join(corpus, f), join(out, f));
console.log(`copied ${specs.length} exported spec(s) to ${out}`);
