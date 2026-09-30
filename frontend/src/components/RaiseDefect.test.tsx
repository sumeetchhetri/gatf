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
import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import RaiseDefect, { issueRef } from './RaiseDefect';
import { api, type TestCaseReport } from '../api';

const failed: TestCaseReport = { status: 'Failed', workflowName: 'login', url: 'http://app/login', error: 'Expected 200 got 500' };
const json = (v: unknown, status = 200) => new Response(JSON.stringify(v), { status, headers: { 'Content-Type': 'application/json' } });

afterEach(() => vi.unstubAllGlobals());

describe('<RaiseDefect>', () => {
  it('renders the issue API templates for the failure, then creates the issue and shows its id/link', async () => {
    const calls: { url: string; method: string; body?: string }[] = [];
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      calls.push({ url, method: init?.method || 'GET', body: init?.body as string | undefined });
      if (url.startsWith('/testcases?')) return json([{ name: 'authapi' }, { name: 'jira' }]);
      if (url.includes('action=getContent')) return json({ status: null, url: 'https://j/rest/api/2/issue', requestContent: '{"summary":"login failed"}' });
      return json({ status: 'Success', responseContent: '{"key":"PROJ-9","self":"https://j/rest/api/2/issue/10009"}' });
    });
    vi.stubGlobal('fetch', fetchMock);
    render(<RaiseDefect report={failed} />);
    fireEvent.click(screen.getByRole('button', { name: 'Raise defect' }));
    const link = await screen.findByRole('link', { name: 'Created PROJ-9' });
    expect(link.getAttribute('href')).toBe('https://j/rest/api/2/issue/10009');

    expect(calls[0].url).toBe('/testcases?testcaseFileName=gatf-issuetracking-api-int.xml&configType=issuetrackerapi');
    const q = '&isExternalLogsApi=true&testcaseFileName=gatf-issuetracking-api-int.xml&testCaseName=jira';
    expect(calls[1]).toMatchObject({ url: '/reports?action=getContent' + q, method: 'PUT' });
    expect(JSON.parse(calls[1].body!)).toEqual(failed);
    expect(calls[2]).toMatchObject({ url: '/reports?action=createIssue' + q, method: 'PUT' });
    expect(JSON.parse(calls[2].body!)).toEqual({ url: 'https://j/rest/api/2/issue', requestContent: '{"summary":"login failed"}' });
  });

  it('says so when no issue-tracker API is configured', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => json([{ name: 'authapi' }])));
    render(<RaiseDefect report={failed} />);
    fireEvent.click(screen.getByRole('button', { name: 'Raise defect' }));
    expect(await screen.findByText(/No issue-tracker API configured/)).toBeInTheDocument();
  });

  it('does not call createIssue when getContent already ran the API (no body template)', async () => {
    const fetchMock = vi.fn(async (url: string) =>
      url.startsWith('/testcases?') ? json([{ name: 'gh' }]) : json({ status: 'Failed', error: 'HTTP 401', responseStatusCode: 401 }),
    );
    vi.stubGlobal('fetch', fetchMock);
    render(<RaiseDefect report={failed} />);
    fireEvent.click(screen.getByRole('button', { name: 'Raise defect' }));
    expect(await screen.findByText('Issue API failed: HTTP 401')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });
});

describe('issueRef', () => {
  it('reads Jira and GitHub style responses', () => {
    expect(issueRef({ status: 'Success', responseContent: '{"number":42,"html_url":"https://gh/i/42","url":"https://api/i/42"}' })).toEqual({
      id: '42',
      link: 'https://gh/i/42',
    });
    expect(issueRef({ status: 'Success', responseContent: 'created' })).toEqual({ id: '', link: '' });
  });
});

describe('api.downloadTestDocs', () => {
  it('GETs /misc?action=testdocs and saves the attachment under its server name', async () => {
    const fetchMock = vi.fn(
      async () => new Response(new Blob(['PK']), { status: 200, headers: { 'Content-Disposition': 'attachment; filename="gatf-testcases.xlsx"' } }),
    );
    vi.stubGlobal('fetch', fetchMock);
    const created = vi.fn(() => 'blob:x');
    URL.createObjectURL = created as unknown as typeof URL.createObjectURL;
    URL.revokeObjectURL = vi.fn();
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    await expect(api.downloadTestDocs('xlsx')).resolves.toBe('gatf-testcases.xlsx');
    expect(fetchMock.mock.calls[0]).toEqual(['/misc?action=testdocs&format=xlsx']);
    expect(created).toHaveBeenCalled();
    expect(click).toHaveBeenCalled();
    click.mockRestore();
  });

  it('surfaces the server error text', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('No test cases or scripts found for this config', { status: 400 })));
    await expect(api.downloadTestDocs('csv')).rejects.toThrow('No test cases or scripts found');
  });
});

