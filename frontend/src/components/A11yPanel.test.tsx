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
import { render, screen, within } from '@testing-library/react';
import A11yPanel from './A11yPanel';
import TraceChecksPanel from './TraceChecksPanel';
import type { A11yResult } from '../api';

// the shape A11yReport maps axe-core's result to (see A11yReportTest's canned run)
const result: A11yResult = {
  url: 'https://shop.test/',
  browser: 'chrome',
  minImpact: 'minor',
  failed: true,
  counts: { critical: 2, serious: 1, moderate: 0, minor: 0 },
  passes: 15,
  incomplete: 3,
  violations: [
    {
      id: 'image-alt',
      impact: 'critical',
      help: 'Images must have alternative text',
      helpUrl: 'https://dequeuniversity.com/rules/axe/4.13/image-alt',
      nodes: [
        { target: 'img[src$="logo.png"]', html: '<img src="logo.png">', summary: 'Fix any of the following:\n  Element does not have an alt attribute' },
        { target: 'iframe#pay >>> img.card', html: '<img class="card">', summary: null },
      ],
    },
    { id: 'button-name', impact: 'critical', help: 'Buttons must have discernible text', nodes: [{ target: '#buy', html: '<button id="buy"></button>', summary: null }] },
    { id: 'link-name', impact: 'serious', help: 'Links must have discernible text', nodes: [{ target: 'a[href$="x"]', html: null, summary: null }] },
  ],
};

describe('A11yPanel', () => {
  it('lists every violation with its selectors', () => {
    render(<A11yPanel result={result} />);
    const t = screen.getByRole('table', { name: 'Accessibility violations' });
    expect(within(t).getAllByRole('row')).toHaveLength(3);
    expect(screen.getByText('img[src$="logo.png"]')).toBeInTheDocument();
    expect(screen.getByText('iframe#pay >>> img.card')).toBeInTheDocument();
    expect(screen.getByText('#buy')).toBeInTheDocument();
    expect(screen.getByText('image-alt').closest('a')).toHaveAttribute('href', 'https://dequeuniversity.com/rules/axe/4.13/image-alt');
    expect(screen.getByText('3 violation(s)')).toBeInTheDocument();
    expect(screen.getByText('2 critical')).toBeInTheDocument();
  });

  it('says so when there are none', () => {
    render(<A11yPanel result={{ ...result, failed: false, violations: [], counts: {} }} />);
    expect(screen.getByText('no violations')).toBeInTheDocument();
    expect(screen.queryByRole('table')).toBeNull();
  });

  it('appears in the report checks tab', () => {
    render(<TraceChecksPanel checks={{ budgets: [], security: null, failure: null, a11y: [result] }} />);
    expect(screen.getByText('Accessibility')).toBeInTheDocument();
    expect(screen.getByText('#buy')).toBeInTheDocument();
  });
});
