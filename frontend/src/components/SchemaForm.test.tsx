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
