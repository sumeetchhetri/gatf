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
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import SchemaForm from './SchemaForm';
import type { CfgSchema } from '../config/gatfConfigSchema';

const schema: CfgSchema = {
  type: 'object',
  properties: { baseUrl: { type: 'string' }, enabled: { type: 'boolean' } },
};
const value = { baseUrl: 'http://x', enabled: false };

describe('<SchemaForm>', () => {
  it('renders inputs bound to the value', () => {
    render(<SchemaForm schema={schema} value={value} onChange={() => {}} enums={{}} />);
    expect((screen.getByRole('textbox') as HTMLInputElement).value).toBe('http://x');
    expect((screen.getByRole('checkbox') as HTMLInputElement).checked).toBe(false);
  });

  it('calls onChange with the updated object when a string field is edited', () => {
    const onChange = vi.fn();
    render(<SchemaForm schema={schema} value={value} onChange={onChange} enums={{}} />);
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'http://y' } });
    expect(onChange).toHaveBeenCalled();
    expect(onChange.mock.calls.at(-1)![0]).toMatchObject({ baseUrl: 'http://y' });
  });

  it('calls onChange when a boolean is toggled', () => {
    const onChange = vi.fn();
    render(<SchemaForm schema={schema} value={value} onChange={onChange} enums={{}} />);
    fireEvent.click(screen.getByRole('checkbox'));
    expect(onChange).toHaveBeenCalled();
    expect(onChange.mock.calls.at(-1)![0]).toMatchObject({ enabled: true });
  });
});
