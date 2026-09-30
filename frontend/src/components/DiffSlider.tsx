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
import { useState } from 'react';
import type { DiffSet } from '../report/attachments';

type Mode = 'slider' | 'diff' | 'side';

/**
 * M9: a failed `screenshot compare`: expected (baseline) vs actual under a before/after slider, the diff image, or
 * side by side. Falls back to whichever images exist.
 */
export default function DiffSlider({ set }: { set: DiffSet }) {
  const canSlide = !!(set.expected && set.actual);
  const [mode, setMode] = useState<Mode>(canSlide ? 'slider' : 'diff');
  // no pair and no diff (e.g. CI with no baseline yet: only the actual image): show what exists
  const only = !canSlide && !set.diff ? (set.actual ?? set.expected) : undefined;
  const [pos, setPos] = useState(50);
  const name = set.key.split('/').pop();
  return (
    <figure className="ds" aria-label={'Visual diff ' + name}>
      <figcaption className="ds-head small">
        <span className="mono ellipsis" title={set.test}>
          {name}
        </span>
        <span className="ds-modes" role="group" aria-label="View">
          {(['slider', 'diff', 'side'] as Mode[]).map((m) => (
            <button
              key={m}
              className={'btn ghost small' + (mode === m ? ' active' : '')}
              aria-pressed={mode === m}
              disabled={m !== 'diff' ? !canSlide : !set.diff}
              onClick={() => setMode(m)}
            >
              {m === 'slider' ? 'Slider' : m === 'diff' ? 'Diff' : 'Side by side'}
            </button>
          ))}
        </span>
      </figcaption>
      {mode === 'slider' && canSlide && (
        <>
          <div className="ds-stage">
            <img src={set.expected} alt="Expected (baseline)" draggable={false} />
            <div className="ds-over" style={{ clipPath: `inset(0 0 0 ${pos}%)` }} data-testid="ds-actual">
              <img src={set.actual} alt="Actual" draggable={false} />
            </div>
            <div className="ds-bar" style={{ left: pos + '%' }} />
          </div>
          <input
            type="range"
            min={0}
            max={100}
            value={pos}
            aria-label="Expected / actual"
            onChange={(e) => setPos(Number(e.target.value))}
            style={{ width: '100%' }}
          />
          <div className="ds-legend muted small">
            <span>◀ expected</span>
            <span>actual ▶</span>
          </div>
        </>
      )}
      {mode === 'diff' && set.diff && <img className="ds-single" src={set.diff} alt="Diff (changed pixels in red)" />}
      {only && (
        <>
          <img className="ds-single" src={only} alt={set.actual ? 'Actual (no baseline yet)' : 'Expected'} />
          <div className="muted small">{set.actual ? 'No baseline to compare with yet.' : 'Baseline only.'}</div>
        </>
      )}
      {mode === 'side' && canSlide && (
        <div className="ds-side">
          <img src={set.expected} alt="Expected (baseline)" />
          <img src={set.actual} alt="Actual" />
        </div>
      )}
    </figure>
  );
}
