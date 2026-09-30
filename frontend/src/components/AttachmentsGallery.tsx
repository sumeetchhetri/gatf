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
import { useMemo, useState } from 'react';
import { api } from '../api';
import type { A11yResult } from '../api';
import type { AttachmentKind } from '../report/attachments';
import { KIND_LABEL, diffSets, gallery } from '../report/attachments';
import DiffSlider from './DiffSlider';
import A11yPanel from './A11yPanel';

/** M10: an a11y result file, loaded and shown inline on demand. */
function A11yAttachment({ url, name, test }: { url: string; name?: string; test: string }) {
  const [res, setRes] = useState<A11yResult | null>(null);
  const [err, setErr] = useState<string | null>(null);
  return (
    <div className="ag-a11y">
      <button
        className="btn ghost small"
        title={test}
        onClick={() => {
          setErr(null);
          if (res) setRes(null);
          else
            api
              .a11yResult(url)
              .then(setRes)
              .catch((e) => setErr(e.message));
        }}
      >
        {res ? 'Hide ' : 'Show '}
        {name}
      </button>
      {err && <span className="small muted"> {err}</span>}
      {res && <A11yPanel result={res} />}
    </div>
  );
}

const ORDER: AttachmentKind[] = ['screenshot', 'diff', 'trace', 'checks', 'a11y', 'other'];

/**
 * M12: every attachment of the shown tests, grouped: screenshots and visual diffs as thumbnails, traces open in the
 * Trace Viewer, checks / accessibility / other files as links. Files outside the served report dir are listed by path.
 */
export default function AttachmentsGallery({
  tests,
  runDir,
  baseDir,
  onOpenTrace,
}: {
  tests: { title: string; attachments?: string[] }[];
  runDir?: string;
  baseDir: string;
  onOpenTrace?: (url: string) => void;
}) {
  const groups = useMemo(() => gallery(tests, runDir, baseDir), [tests, runDir, baseDir]);
  if (groups.size === 0) return <div className="view-note">No attachments in this selection.</div>;
  return (
    <div className="ag">
      {ORDER.filter((k) => groups.has(k)).map((k) => (
        <section key={k} className="ag-group">
          <h4>
            {KIND_LABEL[k]} <span className="muted small">({groups.get(k)!.length})</span>
          </h4>
          {k === 'diff' && (
            <div className="ag-diffs">
              {diffSets(groups.get(k)!).map((s) => (
                <DiffSlider key={s.key} set={s} />
              ))}
              {groups
                .get(k)!
                .filter((a) => !a.url)
                .map((a, i) => (
                  <div key={'p' + i} className="small muted mono" title={a.test}>
                    {a.path}
                  </div>
                ))}
            </div>
          )}
          {k !== 'diff' && (
          <div className={k === 'screenshot' ? 'ag-thumbs' : 'ag-list'}>
            {groups.get(k)!.map((a, i) => {
              const name = a.path.replace(/\\/g, '/').split('/').pop();
              if (!a.url)
                return (
                  <div key={i} className="small muted mono" title={a.test}>
                    {a.path}
                  </div>
                );
              if (k === 'a11y') return <A11yAttachment key={i} url={a.url} name={name} test={a.test} />;
              if (k === 'screenshot')
                return (
                  <figure key={i} className="ag-thumb" title={a.test}>
                    <a href={a.url} target="_blank" rel="noreferrer">
                      <img src={a.url} alt={'Screenshot: ' + a.test} loading="lazy" />
                    </a>
                    <figcaption className="small ellipsis">{a.test}</figcaption>
                  </figure>
                );
              if (k === 'trace' && onOpenTrace)
                return (
                  <button key={i} className="btn small" title={a.test} onClick={() => onOpenTrace(a.url!)}>
                    {'◫'} {name}
                  </button>
                );
              return (
                <a key={i} className="btn ghost small" href={a.url} target="_blank" rel="noreferrer" title={a.test}>
                  {name}
                </a>
              );
            })}
          </div>
          )}
        </section>
      ))}
    </div>
  );
}
