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
import React from 'react';
import Layout from '@theme/Layout';
import Link from '@docusaurus/Link';
import { usePluginData } from '@docusaurus/useGlobalData';
import { useVersions } from '@docusaurus/plugin-content-docs/client';

// Lists docs/release-notes/*.md (plugins/release-notes.mjs). Each entry links to the note in the newest released docs
// version that has it, else to "next"; the notes stay ordinary docs pages.
export default function ReleaseNotes() {
  const { notes } = usePluginData('gatf-release-notes');
  const versions = useVersions(); // newest first; "current" (next) is first
  const ordered = [...versions.filter((v) => v.name !== 'current'), ...versions.filter((v) => v.name === 'current')];
  const find = (id) => {
    for (const v of ordered) {
      const d = v.docs.find((x) => x.id === id);
      if (d) return { path: d.path, version: v.label };
    }
    return null;
  };
  return (
    <Layout title="Release notes" description="GATF release notes">
      <main className="container margin-vert--lg">
        <h1>Release notes</h1>
        <p>
          Release notes live in the docs (<code>docs/release-notes/</code>). Binaries are on the{' '}
          <Link to="/downloads">Downloads</Link> page.
        </p>
        {notes.length === 0 && <p>No release notes yet.</p>}
        <ul>
          {notes.map((n) => {
            const hit = find(n.id);
            if (!hit) throw new Error(`release note ${n.id} is not a docs page in any version`);
            return (
              <li key={n.id} className="margin-bottom--md">
                <Link to={hit.path}>
                  <strong>{n.title}</strong>
                </Link>
                {n.summary && <div>{n.summary}</div>}
              </li>
            );
          })}
        </ul>
      </main>
    </Layout>
  );
}
