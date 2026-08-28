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
// seleasy VS Code extension entry point.
//  • Starts the language server (server.ts) over Node IPC → highlighting is TextMate (static),
//    diagnostics/completion/hover come from the LSP (shares the generated command catalog).
//  • Registers an inline Debug Adapter (seleasyDebug.ts) that bridges F5 to a running GATF server,
//    plus a config resolver that fills in gatfUrl / token / workspace root and defaults the file.
import * as path from 'node:path';
import * as vscode from 'vscode';
import { LanguageClient, TransportKind, type LanguageClientOptions, type ServerOptions } from 'vscode-languageclient/node';
import { SeleasyDebugSession } from './seleasyDebug';

let client: LanguageClient | undefined;

export function activate(context: vscode.ExtensionContext): void {
  // ---- language server ----
  const serverModule = context.asAbsolutePath(path.join('dist', 'server.js'));
  const serverOptions: ServerOptions = {
    run: { module: serverModule, transport: TransportKind.ipc },
    debug: { module: serverModule, transport: TransportKind.ipc, options: { execArgv: ['--nolazy', '--inspect=6011'] } },
  };
  const clientOptions: LanguageClientOptions = {
    documentSelector: [{ scheme: 'file', language: 'seleasy' }],
    synchronize: { configurationSection: 'seleasy' },
  };
  client = new LanguageClient('seleasy', 'seleasy Language Server', serverOptions, clientOptions);
  client.start();

  // ---- debug adapter (inline) ----
  context.subscriptions.push(
    vscode.debug.registerDebugAdapterDescriptorFactory('seleasy', {
      createDebugAdapterDescriptor: () => new vscode.DebugAdapterInlineImplementation(new SeleasyDebugSession()),
    }),
    vscode.debug.registerDebugConfigurationProvider('seleasy', {
      resolveDebugConfiguration: (folder, config) => {
        // F5 with no launch.json → synthesize a config for the active .sel file.
        if (!config.type && !config.request && !config.name) {
          const ed = vscode.window.activeTextEditor;
          if (!ed || ed.document.languageId !== 'seleasy') return undefined;
          config.type = 'seleasy';
          config.request = 'launch';
          config.name = 'Debug seleasy (current file)';
          config.stopOnEntry = true;
        }
        const cfg = vscode.workspace.getConfiguration('seleasy');
        const root = folder?.uri.fsPath ?? vscode.workspace.workspaceFolders?.[0]?.uri.fsPath ?? '';
        const active = vscode.window.activeTextEditor?.document.uri.fsPath ?? '';
        if (!config.testcaseFile) {
          config.testcaseFile = root && active.startsWith(root) ? path.relative(root, active).replace(/\\/g, '/') : active;
        }
        config.gatfUrl = config.gatfUrl || cfg.get<string>('gatfUrl') || 'http://localhost:9080';
        config.gatfToken = config.gatfToken || cfg.get<string>('gatfToken') || '';
        config.workspaceRoot = root;
        return config;
      },
    }),
  );
}

export function deactivate(): Thenable<void> | undefined {
  return client?.stop();
}
