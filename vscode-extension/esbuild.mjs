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
// Bundle the extension (client) and the language server into dist/ as CommonJS for the VS Code
// extension host. `vscode` is provided by the host, so it stays external.
import { build, context } from 'esbuild';

const watch = process.argv.includes('--watch');
const common = {
  bundle: true,
  platform: 'node',
  format: 'cjs',
  target: 'node16',
  sourcemap: true,
  logLevel: 'info',
};
const configs = [
  { ...common, entryPoints: ['src/extension.ts'], outfile: 'dist/extension.js', external: ['vscode'] },
  { ...common, entryPoints: ['src/server.ts'], outfile: 'dist/server.js', external: [] },
];

if (watch) {
  for (const c of configs) {
    const ctx = await context(c);
    await ctx.watch();
  }
  console.log('esbuild watching…');
} else {
  await Promise.all(configs.map((c) => build(c)));
  console.log('esbuild done');
}
