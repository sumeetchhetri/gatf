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

  it('generator schema is a non-empty object', () => {
    expect(generatorSchema.type).toBe('object');
    expect(Object.keys(generatorSchema.properties ?? {}).length).toBeGreaterThan(0);
  });

  it('testcase schema is a non-empty object', () => {
    expect(testcaseSchema.type).toBe('object');
    expect(Object.keys(testcaseSchema.properties ?? {}).length).toBeGreaterThan(10);
  });
});
