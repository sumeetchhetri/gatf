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
import { describe, it, expect } from 'vitest';
import { executorSchema, generatorSchema } from './gatfConfigSchema';
import { testcaseSchema } from './testcaseSchema';

describe('config schemas (extracted verbatim from the legacy config-tool)', () => {
  it('executor schema is an object with the known top-level fields', () => {
    expect(executorSchema.type).toBe('object');
    const props = executorSchema.properties!;
    for (const k of ['baseUrl', 'testCasesBasePath', 'outFilesBasePath', 'outFilesDir']) {
      expect(k in props, k).toBe(true);
    }
    expect(Object.keys(props).length).toBeGreaterThan(40);
  });

  it('executor schema exposes traceMode with exactly the values the backend accepts', () => {
    // must match GatfExecutorConfig.TRACE_MODES; the backend rejects anything else when the config is saved
    expect(executorSchema.properties!.traceMode.options).toEqual(['off', 'on', 'retain-on-failure', 'on-first-retry']);
  });

  it('executor schema exposes browserContexts (AUDIT-T3 F8) with the ContextMode values', () => {
    // must match com.gatf.selenium.context.ContextMode OFF / SUBTEST / WORKER (anything else reads as off)
    expect(executorSchema.properties!.browserContexts.options).toEqual(['off', 'subtest', 'worker']);
  });

  it('executor schema exposes browserApiSession with exactly the values the backend accepts', () => {
    // must match SessionBridge.MODES; the backend rejects anything else when the config is saved
    expect(executorSchema.properties!.browserApiSession.options).toEqual(['sync', 'send', 'off']);
  });

  it('executor schema exposes securityChecks with exactly the values the backend accepts', () => {
    // must match TraceChecks.SECURITY_MODES
    expect(executorSchema.properties!.securityChecks.options).toEqual(['report', 'fail', 'off']);
  });

  it('executor schema exposes the M1 auto-wait settings', () => {
    // GatfExecutorConfig.autoWait (Boolean) + actionTimeout (Long, ms)
    expect(executorSchema.properties!.autoWait.type).toBe('boolean');
    expect(executorSchema.properties!.actionTimeout.type).toBe('integer');
  });

  it('executor schema exposes the M2 web-first assertion settings', () => {
    // GatfExecutorConfig.webFirstAssertions (Boolean) + assertTimeout (Long, ms)
    expect(executorSchema.properties!.webFirstAssertions.type).toBe('boolean');
    expect(executorSchema.properties!.assertTimeout.type).toBe('integer');
  });

  it('executor schema exposes the M3 semantic locator settings', () => {
    expect(executorSchema.properties!.testIdAttribute.type).toBe('string');
    expect(executorSchema.properties!.strictLocators.type).toBe('boolean');
  });

  it('executor schema exposes the M5 auth-state settings', () => {
    for (const k of ['storageState', 'loginOnce', 'browserApiStorageToken']) expect(executorSchema.properties![k].type).toBe('string');
  });

  it('executor schema exposes the M6 global setup / teardown scripts', () => {
    for (const k of ['globalSetup', 'globalTeardown']) expect(executorSchema.properties![k].type).toBe('string');
  });

  it('executor schema exposes the token host allow-list and forbidOnly', () => {
    expect(executorSchema.properties!.browserApiStorageTokenHosts.type).toBe('array');
    expect(executorSchema.properties!.browserApiStorageTokenHosts.items!.type).toBe('string');
    expect(executorSchema.properties!.forbidOnly.type).toBe('boolean');
  });

  it('generator schema is a non-empty object', () => {
    expect(generatorSchema.type).toBe('object');
    expect(Object.keys(generatorSchema.properties ?? {}).length).toBeGreaterThan(0);
  });

  it('testcase schema is a non-empty object', () => {
    expect(testcaseSchema.type).toBe('object');
    expect(Object.keys(testcaseSchema.properties ?? {}).length).toBeGreaterThan(10);
  });
});
