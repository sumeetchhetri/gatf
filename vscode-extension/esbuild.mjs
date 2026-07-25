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
