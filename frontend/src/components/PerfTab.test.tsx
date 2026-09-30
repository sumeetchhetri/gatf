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
import { render } from '@testing-library/react';
import { PerfTab } from './PerfTab';
import type { Trace } from '../trace/types';

const trace = (perf: Trace['perf']) =>
  ({ perf, memory: null, t0: 0, t1: 100 } as unknown as Trace);

describe('PerfTab per-page vitals', () => {
  it('renders one row per page with the worst value marked', () => {
    const { getByText, container } = render(
      <PerfTab
        currentTime={0}
        trace={trace({
          samples: [],
          webVitals: { lcp: 900 },
          webVitalsByPage: [
            { url: 'https://a/1', ts: 10, lcp: 2600, cls: 0.25 },
            { url: 'https://a/2', ts: 20, lcp: 900, cls: 0 },
          ],
        })}
      />,
    );
    expect(getByText('Web Vitals by page')).toBeTruthy();
    const rows = container.querySelectorAll('tbody tr');
    expect(rows).toHaveLength(2);
    expect(rows[0].textContent).toContain('https://a/1');
    expect(getByText('0.250').getAttribute('style')).toContain('font-weight');
  });

  it('shows no per-page table for traces without webVitalsByPage', () => {
    const { queryByText } = render(<PerfTab currentTime={0} trace={trace({ samples: [], webVitals: { lcp: 900 } })} />);
    expect(queryByText('Web Vitals by page')).toBeNull();
  });
});
