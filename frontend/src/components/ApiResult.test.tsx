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
import { render, screen } from '@testing-library/react';
import ApiResult, { contractDetail } from './ApiResult';
import type { TestCaseReport } from '../api';

const detail = 'contract getPet (GET /pet/{id}):\n  /name: wrong type (expected string, actual integer 1)';

describe('contract detail', () => {
  it('is found whatever the failureReason', () => {
    expect(contractDetail({ failureReason: 'ContractViolation', errorText: detail })).toBe(detail);
    expect(contractDetail({ failureReason: 'InvalidStatusCode', errorText: 'status 500\n' + detail })).toBe(detail);
    expect(contractDetail({ failureReason: 'InvalidStatusCode', errorText: 'stack trace' })).toBeNull();
    expect(contractDetail({})).toBeNull();
  });

  it('renders the per-pointer lines when the case failed on its status first', () => {
    const r = { status: 'Failed', failureReason: 'InvalidStatusCode', error: 'bad status; contract ...', errorText: detail } as TestCaseReport;
    render(<ApiResult r={r} />);
    expect(screen.getByText(/\/name: wrong type/)).toBeInTheDocument();
  });
});
