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
// Debug Adapter (DAP) for seleasy. Bridges VS Code's debugger to a *running GATF server*'s debug
// protocol — the exact `PUT /reports?action=debug` channel the SPA online debugger uses (reverse-
// engineered in delta-031). One session per launch, keyed by a generated sessionId:
//   line=0  init      -> { c:source, l:debuggableLines, i:currentLine }
//   -1 stepIn (+sline) -2 stepOut  -3 stepOver  -4 continue  -5 pause  -6 terminate
//   -7 heartbeat      -> { r:running, p:prevLine, n:nextLine, v:state, t:currentFile }
//   b<n>/r<n>          add/remove breakpoint (server uses n+1, so we send line-1 for a 1-based line)
// Requests use Node http/https (no fetch/WebSocket dependency, so it works on the Node the VS Code
// engine ships). The WebSocket channel (delta-041) is an optional latency optimisation, not needed
// for parity — commands ride this PUT protocol either way.
import {
  LoggingDebugSession,
  InitializedEvent,
  TerminatedEvent,
  StoppedEvent,
  Thread,
  StackFrame,
  Source,
  Scope,
  Handles,
} from '@vscode/debugadapter';
import type { DebugProtocol } from '@vscode/debugprotocol';
import * as http from 'node:http';
import * as https from 'node:https';
import { URL } from 'node:url';
import * as fs from 'node:fs';
import * as path from 'node:path';

interface LaunchArgs extends DebugProtocol.LaunchRequestArguments {
  testcaseFile: string;
  testCaseName?: string;
  gatfUrl?: string;
  gatfToken?: string;
  workspaceRoot?: string;
  stopOnEntry?: boolean;
}

interface DbgResp {
  s?: boolean;
  c?: string;
  l?: unknown;
  i?: number;
  p?: number;
  n?: number;
  r?: boolean;
  v?: number;
  t?: string;
  m?: string;
  [k: string]: unknown;
}

const THREAD_ID = 1;

function putJson(base: string, params: Record<string, string>, token: string): Promise<DbgResp> {
  return new Promise((resolve) => {
    let u: URL;
    try {
      u = new URL('/reports', base);
    } catch {
      resolve({ s: false, m: `bad gatfUrl: ${base}` });
      return;
    }
    u.searchParams.set('action', 'debug');
    for (const [k, v] of Object.entries(params)) u.searchParams.set(k, v);
    if (token) u.searchParams.set('token', token);
    const mod = u.protocol === 'https:' ? https : http;
    const req = mod.request(u, { method: 'PUT', headers: { Accept: 'application/json' } }, (res) => {
      let body = '';
      res.setEncoding('utf8');
      res.on('data', (d) => (body += d));
      res.on('end', () => {
        try {
          const j = JSON.parse(body) as DbgResp;
          resolve(typeof j === 'object' && j ? j : { s: false, m: String(body) });
        } catch {
          resolve({ s: false, m: body || `HTTP ${res.statusCode}` });
        }
      });
    });
    req.on('error', (e) => resolve({ s: false, m: e.message }));
    req.end();
  });
}

// Flatten the debuggableLines map for a file into a set of 1-based line numbers.
function debugLinesFor(l: unknown, file: string): Set<number> {
  const out = new Set<number>();
  const root = l && typeof l === 'object' ? (l as Record<string, unknown>)[file] : undefined;
  const walk = (x: unknown) => {
    if (Array.isArray(x)) {
      if (typeof x[0] === 'number') out.add(x[0]);
      x.forEach(walk);
    }
  };
  walk(root);
  return out;
}

export class SeleasyDebugSession extends LoggingDebugSession {
  private gatfUrl = 'http://localhost:9080';
  private token = '';
  private sessionId = '';
  private tcFile = '';
  private tcName = '';
  private workspaceRoot = '';
  private launchedAbs = '';
  private source: string[] = [];
  private debuggable = new Set<number>();
  private curLine = 1;
  private curFile = '';
  private setBps = new Map<string, Set<number>>(); // file -> set of 1-based lines we've toggled on
  private heartbeat?: ReturnType<typeof setInterval>;
  private scopeHandles = new Handles<'debug'>();
  private terminated = false;

  constructor() {
    super('seleasy-debug.txt');
    this.setDebuggerLinesStartAt1(true);
    this.setDebuggerColumnsStartAt1(true);
  }

  protected initializeRequest(response: DebugProtocol.InitializeResponse): void {
    response.body = response.body || {};
    response.body.supportsConfigurationDoneRequest = true;
    response.body.supportsTerminateRequest = true;
    response.body.supportsStepBack = false;
    response.body.supportsEvaluateForHovers = false;
    this.sendResponse(response);
    this.sendEvent(new InitializedEvent());
  }

  protected async launchRequest(response: DebugProtocol.LaunchResponse, args: LaunchArgs): Promise<void> {
    this.gatfUrl = (args.gatfUrl || this.gatfUrl).replace(/\/+$/, '');
    this.token = args.gatfToken || '';
    this.tcFile = args.testcaseFile;
    this.tcName = args.testCaseName || '';
    this.workspaceRoot = args.workspaceRoot || '';
    this.launchedAbs = this.resolveFile(this.tcFile);
    this.curFile = this.tcFile;
    this.sessionId = 'vsc-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 8);

    const init = await this.dbg('0');
    if (init.s === false) {
      this.sendErrorResponse(response, 3000, `GATF debug start failed: ${init.m || 'no response'} (is the server at ${this.gatfUrl} running?)`);
      return;
    }
    this.source = typeof init.c === 'string' ? init.c.split(/\r?\n/) : [];
    this.debuggable = debugLinesFor(init.l, this.tcFile);
    this.curLine = typeof init.i === 'number' && init.i > 0 ? init.i : 1;
    this.sendResponse(response);

    this.startHeartbeat();
    if (args.stopOnEntry !== false) this.sendEvent(new StoppedEvent('entry', THREAD_ID));
    else this.doContinue();
  }

  protected async setBreakPointsRequest(
    response: DebugProtocol.SetBreakpointsResponse,
    args: DebugProtocol.SetBreakpointsArguments,
  ): Promise<void> {
    const file = this.gatfName(args.source.path || this.tcFile);
    const want = new Set<number>((args.breakpoints || []).map((b) => b.line));
    const have = this.setBps.get(file) || new Set<number>();
    // toggle server-side breakpoints to match the requested set (b<n>/r<n> use n+1 => send line-1)
    for (const ln of want) if (!have.has(ln)) await this.dbg('b' + (ln - 1));
    for (const ln of have) if (!want.has(ln)) await this.dbg('r' + (ln - 1));
    this.setBps.set(file, want);
    const verifiedFor = this.curFile === file && this.debuggable.size ? this.debuggable : null;
    response.body = {
      breakpoints: [...want].map((line) => ({ verified: verifiedFor ? verifiedFor.has(line) : true, line })),
    };
    this.sendResponse(response);
  }

  protected configurationDoneRequest(response: DebugProtocol.ConfigurationDoneResponse): void {
    this.sendResponse(response);
  }

  protected async continueRequest(response: DebugProtocol.ContinueResponse): Promise<void> {
    this.sendResponse(response);
    this.doContinue();
  }

  protected async nextRequest(response: DebugProtocol.NextResponse): Promise<void> {
    this.sendResponse(response);
    await this.step('-3', 'step');
  }

  protected async stepInRequest(response: DebugProtocol.StepInResponse): Promise<void> {
    this.sendResponse(response);
    await this.step('-1', 'step', this.source[this.curLine - 1] ?? '');
  }

  protected async stepOutRequest(response: DebugProtocol.StepOutResponse): Promise<void> {
    this.sendResponse(response);
    await this.step('-2', 'step');
  }

  protected async pauseRequest(response: DebugProtocol.PauseResponse): Promise<void> {
    this.sendResponse(response);
    const r = await this.dbg('-5');
    if (typeof r.p === 'number' && r.p > 0) this.curLine = r.p;
    this.sendEvent(new StoppedEvent('pause', THREAD_ID));
  }

  protected async terminateRequest(response: DebugProtocol.TerminateResponse): Promise<void> {
    await this.teardown();
    this.sendResponse(response);
  }

  protected async disconnectRequest(response: DebugProtocol.DisconnectResponse): Promise<void> {
    await this.teardown();
    this.sendResponse(response);
  }

  protected threadsRequest(response: DebugProtocol.ThreadsResponse): void {
    response.body = { threads: [new Thread(THREAD_ID, 'seleasy')] };
    this.sendResponse(response);
  }

  protected stackTraceRequest(response: DebugProtocol.StackTraceResponse): void {
    const abs = this.resolveFile(this.curFile);
    const name = path.basename(this.curFile || this.tcFile);
    const src = abs ? new Source(name, abs) : new Source(name);
    response.body = { stackFrames: [new StackFrame(1, name, src, this.curLine, 1)], totalFrames: 1 };
    this.sendResponse(response);
  }

  protected scopesRequest(response: DebugProtocol.ScopesResponse): void {
    response.body = { scopes: [new Scope('Debug', this.scopeHandles.create('debug'), false)] };
    this.sendResponse(response);
  }

  protected variablesRequest(response: DebugProtocol.VariablesResponse): void {
    // The HTTP debug channel exposes execution state, not user variables — surface what it gives.
    response.body = {
      variables: [
        { name: 'file', value: this.curFile || this.tcFile, variablesReference: 0 },
        { name: 'line', value: String(this.curLine), variablesReference: 0 },
        { name: 'session', value: this.sessionId, variablesReference: 0 },
      ],
    };
    this.sendResponse(response);
  }

  // ---- helpers ----
  private dbg(code: string, sline?: string): Promise<DbgResp> {
    const params: Record<string, string> = {
      line: code,
      testcaseFileName: this.tcFile,
      testCaseName: this.tcName,
      sessionId: this.sessionId,
    };
    if (sline != null) params.sline = sline;
    return putJson(this.gatfUrl, params, this.token);
  }

  private async step(code: string, reason: string, sline?: string): Promise<void> {
    const r = await this.dbg(code, sline);
    if (r.s === false && !r.p) {
      this.sendEvent(new TerminatedEvent());
      return;
    }
    if (typeof r.p === 'number' && r.p > 0) this.curLine = r.p;
    if (typeof r.t === 'string' && r.t) this.curFile = r.t;
    if (r.r === false) this.sendEvent(new TerminatedEvent());
    else this.sendEvent(new StoppedEvent(reason, THREAD_ID));
  }

  private async doContinue(): Promise<void> {
    const r = await this.dbg('-4');
    if (typeof r.p === 'number' && r.p > 0) this.curLine = r.p;
    if (typeof r.t === 'string' && r.t) this.curFile = r.t;
    // continue runs to the next breakpoint or the end — a heartbeat settles which.
    const hb = await this.dbg('-7');
    if (typeof hb.p === 'number' && hb.p > 0) this.curLine = hb.p;
    if (typeof hb.t === 'string' && hb.t) this.curFile = hb.t;
    if (hb.r === false) this.sendEvent(new TerminatedEvent());
    else this.sendEvent(new StoppedEvent('breakpoint', THREAD_ID));
  }

  private startHeartbeat(): void {
    this.heartbeat = setInterval(async () => {
      if (this.terminated) return;
      const hb = await this.dbg('-7');
      if (hb.r === false) {
        this.sendEvent(new TerminatedEvent());
        this.stopHeartbeat();
      } else if (typeof hb.t === 'string' && hb.t) {
        this.curFile = hb.t;
      }
    }, 1500);
  }

  private stopHeartbeat(): void {
    if (this.heartbeat) {
      clearInterval(this.heartbeat);
      this.heartbeat = undefined;
    }
  }

  private async teardown(): Promise<void> {
    if (this.terminated) return;
    this.terminated = true;
    this.stopHeartbeat();
    try {
      await this.dbg('-6');
    } catch {
      /* best effort */
    }
  }

  // GATF file name (as the protocol expects) for a VS Code source path.
  private gatfName(p: string): string {
    if (!p) return this.tcFile;
    if (this.workspaceRoot && p.startsWith(this.workspaceRoot)) {
      return p.slice(this.workspaceRoot.length).replace(/^[\\/]+/, '').replace(/\\/g, '/');
    }
    return p.replace(/\\/g, '/');
  }

  // Absolute path for a GATF-relative file name, best-effort.
  private resolveFile(name: string): string {
    if (!name) return '';
    if (path.isAbsolute(name) && fs.existsSync(name)) return name;
    const roots = [this.workspaceRoot, this.launchedAbs ? path.dirname(this.launchedAbs) : ''].filter(Boolean);
    for (const root of roots) {
      const cand = path.resolve(root, name);
      if (fs.existsSync(cand)) return cand;
      const byBase = path.resolve(root, path.basename(name));
      if (fs.existsSync(byBase)) return byBase;
    }
    return path.isAbsolute(name) ? name : '';
  }
}
