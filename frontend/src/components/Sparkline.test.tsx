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
