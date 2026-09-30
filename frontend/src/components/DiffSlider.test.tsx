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
import { render, screen, fireEvent } from '@testing-library/react';
import DiffSlider from './DiffSlider';
import AttachmentsGallery from './AttachmentsGallery';
import { diffSets, gallery } from '../report/attachments';

const set = {
  key: '/w/out/17/chrome-home',
  test: '[chrome] › ui/home.sel',
  diff: '/reports/17/chrome-home-diff.png',
  expected: '/reports/17/chrome-home-expected.png',
  actual: '/reports/17/chrome-home-actual.png',
};

describe('DiffSlider', () => {
  it('slides between expected and actual', () => {
    render(<DiffSlider set={set} />);
    expect(screen.getByAltText('Expected (baseline)')).toHaveAttribute('src', set.expected);
    expect(screen.getByTestId('ds-actual').style.clipPath).toBe('inset(0 0 0 50%)');
    fireEvent.change(screen.getByLabelText('Expected / actual'), { target: { value: '80' } });
    expect(screen.getByTestId('ds-actual').style.clipPath).toBe('inset(0 0 0 80%)');
  });

  it('switches to the diff image and side by side', () => {
    render(<DiffSlider set={set} />);
    fireEvent.click(screen.getByText('Diff'));
    expect(screen.getByAltText('Diff (changed pixels in red)')).toHaveAttribute('src', set.diff);
    fireEvent.click(screen.getByText('Side by side'));
    expect(screen.getAllByRole('img')).toHaveLength(2);
  });

  it('shows only the diff when the baseline pair is missing', () => {
    render(<DiffSlider set={{ key: 'x', test: 't', diff: '/d-diff.png' }} />);
    expect(screen.getByText('Slider')).toBeDisabled();
    expect(screen.getByAltText('Diff (changed pixels in red)')).toBeInTheDocument();
  });

  it('shows the actual image when there is no baseline yet', () => {
    render(<DiffSlider set={{ key: 'x', test: 't', actual: '/x-actual.png' }} />);
    expect(screen.getByAltText('Actual (no baseline yet)')).toHaveAttribute('src', '/x-actual.png');
  });

  it('pairs the three files of a comparison in the gallery', () => {
    const tests = [{ title: set.test, attachments: ['/w/out/17/chrome-home-diff.png', '/w/out/17/chrome-home-expected.png', '/w/out/17/chrome-home-actual.png'] }];
    const g = gallery(tests, '/w/out', '/reports/');
    const sets = diffSets(g.get('diff')!);
    expect(sets).toHaveLength(1);
    expect(sets[0]).toMatchObject({ diff: set.diff, expected: set.expected, actual: set.actual });
    render(<AttachmentsGallery tests={tests} runDir="/w/out" baseDir="/reports/" />);
    expect(screen.getByLabelText('Visual diff chrome-home')).toBeInTheDocument();
  });
});
