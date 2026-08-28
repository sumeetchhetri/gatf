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
import { Sparkline } from './Sparkline';

describe('<Sparkline>', () => {
  it('shows a placeholder when there are no samples', () => {
    const { getByText } = render(<Sparkline series={[]} />);
    expect(getByText('no samples')).toBeInTheDocument();
  });
  it('renders an SVG path for a series', () => {
    const { container } = render(
      <Sparkline series={[{ points: [{ x: 0, y: 1 }, { x: 1, y: 3 }], color: '#f00' }]} />,
    );
    const svg = container.querySelector('svg.spark');
    expect(svg).not.toBeNull();
    expect(svg!.querySelector('path')).not.toBeNull();
  });
});
