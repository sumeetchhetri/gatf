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
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import type { ImportResultDto } from '../api';

const importSource = vi.fn();
vi.mock('../api', async (orig) => {
  const real = await orig<typeof import('../api')>();
  return { ...real, api: { ...real.api, importSource: (...a: unknown[]) => importSource(...a) } };
});

import ImportView from './ImportView';

const ok = (over: Partial<ImportResultDto> = {}): ImportResultDto => ({
  type: 'openapi',
  fileName: 'petstore.xml',
  kind: 'xml',
  content: '<testCases/>',
  unsupported: [],
  stats: {},
  ...over,
});

// jsdom's File has no text(): give each test file one
const file = (name: string, content: string) => Object.assign(new File([content], name), { text: () => Promise.resolve(content) });

describe('<ImportView>', () => {
  beforeEach(() => importSource.mockReset());

  it('shows the negative-cases toggle only for OpenAPI and passes it through', async () => {
    importSource.mockResolvedValue(ok());
    const { container } = render(<ImportView onOpenTestFiles={() => {}} />);
    expect(screen.queryByText(/negative cases/)).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'OpenAPI' }));
    fireEvent.click(screen.getByRole('checkbox'));
    fireEvent.change(container.querySelector('textarea')!, { target: { value: 'openapi: 3.0.0' } });
    fireEvent.click(screen.getByRole('button', { name: 'Preview import' }));
    await waitFor(() => expect(importSource).toHaveBeenCalled());
    expect(importSource.mock.calls[0]).toEqual(['openapi', '', false, 'openapi: 3.0.0', undefined, true, undefined]);
    fireEvent.click(screen.getByRole('button', { name: 'Postman' }));
    expect(screen.queryByText(/negative cases/)).toBeNull();
  });

  it('renders drift (+ added, ~ changed, - removed) and "no drift"', async () => {
    importSource.mockResolvedValueOnce(ok({ drift: { added: ['GET /a'], removed: ['DELETE /c'], changed: ['POST /b'] } }));
    const { container } = render(<ImportView onOpenTestFiles={() => {}} />);
    fireEvent.click(screen.getByRole('button', { name: 'OpenAPI' }));
    fireEvent.change(container.querySelector('textarea')!, { target: { value: 'openapi: 3.0.0' } });
    fireEvent.click(screen.getByRole('button', { name: 'Preview import' }));
    await screen.findByText('+ GET /a');
    expect(screen.getByText('~ POST /b')).toBeInTheDocument();
    expect(screen.getByText('- DELETE /c')).toBeInTheDocument();
    expect(screen.getByText(/drift from the existing petstore.xml/)).toBeInTheDocument();

    importSource.mockResolvedValueOnce(ok({ drift: { added: [], removed: [], changed: [] } }));
    fireEvent.click(screen.getByRole('button', { name: 'Preview import' }));
    await screen.findByText('no drift from the existing petstore.xml');
    expect(screen.queryByText('+ GET /a')).toBeNull();
  });

  it('uploads an OpenAPI multi-file bundle: main spec detected, others by relative path', async () => {
    importSource.mockResolvedValue(ok());
    const { container } = render(<ImportView onOpenTestFiles={() => {}} />);
    fireEvent.click(screen.getByRole('button', { name: 'OpenAPI' }));
    const input = screen.getByTestId('import-file') as HTMLInputElement;
    expect(input.multiple).toBe(true);
    const pet = file('pet.yaml', 'type: object');
    const main = file('petstore.openapi.yaml', "openapi: 3.0.0\npaths: {}\ncomponents: {schemas: {Pet: {$ref: './pet.yaml'}}}");
    fireEvent.change(input, { target: { files: [pet, main] } });
    await screen.findByText(/referenced file/);
    expect((container.querySelector('textarea') as HTMLTextAreaElement).value).toContain('openapi: 3.0.0');
    expect((screen.getByPlaceholderText(/output file name/) as HTMLInputElement).value).toBe('petstore.xml'); // .openapi stripped
    fireEvent.click(screen.getByRole('button', { name: 'Save to test files' }));
    await waitFor(() => expect(importSource).toHaveBeenCalled());
    const [type, name, save, , , , bundle] = importSource.mock.calls[0];
    expect([type, name, save]).toEqual(['openapi', 'petstore.xml', true]);
    expect(bundle).toEqual({ mainName: 'petstore.openapi.yaml', files: { 'pet.yaml': 'type: object' } });
  });

  it('a single OpenAPI file posts no bundle', async () => {
    importSource.mockResolvedValue(ok());
    render(<ImportView onOpenTestFiles={() => {}} />);
    fireEvent.click(screen.getByRole('button', { name: 'OpenAPI' }));
    fireEvent.change(screen.getByTestId('import-file'), { target: { files: [file('api.json', '{"openapi":"3.1.0"}')] } });
    await waitFor(() => expect((screen.getByPlaceholderText(/output file name/) as HTMLInputElement).value).toBe('api.xml'));
    fireEvent.click(screen.getByRole('button', { name: 'Preview import' }));
    await waitFor(() => expect(importSource).toHaveBeenCalled());
    expect(importSource.mock.calls[0][6]).toBeUndefined();
    expect(screen.queryByText(/referenced file/)).toBeNull();
  });
});

describe('api.importSource request shape', () => {
  const fetchMock = vi.fn();
  beforeEach(() => {
    fetchMock.mockReset();
    fetchMock.mockResolvedValue({ ok: true, json: () => Promise.resolve({}) });
    vi.stubGlobal('fetch', fetchMock);
  });
  afterEach(() => vi.unstubAllGlobals());
  const realApi = async () => (await vi.importActual<typeof import('../api')>('../api')).api;

  it('posts {main, mainName, files} as JSON with bundle=true', async () => {
    await (await realApi()).importSource('openapi', 'p.xml', true, 'openapi: 3.0.0', undefined, false, {
      mainName: 'openapi.yaml',
      files: { 'schemas/pet.yaml': 'type: object' },
    });
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toContain('&bundle=true');
    expect(init.headers['Content-Type']).toBe('application/json');
    expect(JSON.parse(init.body)).toEqual({ main: 'openapi: 3.0.0', mainName: 'openapi.yaml', files: { 'schemas/pet.yaml': 'type: object' } });
  });

  it('keeps the single-body path for one file', async () => {
    await (await realApi()).importSource('openapi', 'p.xml', false, 'openapi: 3.0.0', undefined, true);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).not.toContain('bundle');
    expect(url).toContain('&negative=true');
    expect(init.headers['Content-Type']).toBe('text/plain; charset=utf-8');
    expect(init.body).toBe('openapi: 3.0.0');
  });
});
