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
import CodeBlock from '@theme/CodeBlock';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';

// Asset names are the ones .github/workflows/ci-release.yml uploads to every GitHub release.
export function assets(repoUrl, tag) {
  const latest = `${repoUrl}/releases/latest/download`;
  const pinned = `${repoUrl}/releases/download/${tag}`;
  return {
    releases: `${repoUrl}/releases`,
    release: `${repoUrl}/releases/tag/${tag}`,
    installSh: `${latest}/install.sh`,
    installPs1: `${latest}/install.ps1`,
    files: [
      { name: `gatf-${tag}.zip`, href: `${pinned}/gatf-${tag}.zip`, what: 'Distribution: jar, launchers and the installers, for offline installs' },
      { name: `gatf-${tag}.zip.sha256`, href: `${pinned}/gatf-${tag}.zip.sha256`, what: 'SHA-256 of the distribution zip' },
      { name: 'gatf.zip', href: `${pinned}/gatf.zip`, what: 'The same distribution zip under a stable name (also at releases/latest/download/gatf.zip)' },
      { name: 'gatf.zip.sha256', href: `${pinned}/gatf.zip.sha256`, what: 'SHA-256 of gatf.zip' },
      { name: `gatf-alldep-${tag}.jar`, href: `${pinned}/gatf-alldep-${tag}.jar`, what: 'The single shaded jar (java -jar)' },
      { name: 'install.sh', href: `${pinned}/install.sh`, what: 'Installer for macOS and Linux' },
      { name: 'install.ps1', href: `${pinned}/install.ps1`, what: 'Installer for Windows (PowerShell)' },
      { name: 'run_gatf.sh', href: `${pinned}/run_gatf.sh`, what: 'Launcher script for the jar' },
      { name: `seleasy-${tag}.vsix`, href: `${pinned}/seleasy-${tag}.vsix`, what: 'VS Code extension (Extensions → Install from VSIX…)' },
    ],
  };
}

export default function Downloads() {
  const { siteConfig } = useDocusaurusContext();
  const { repoUrl, release } = siteConfig.customFields;
  const a = assets(repoUrl, release);
  return (
    <Layout title="Downloads" description={`Download GATF ${release}: installers, jar and VS Code extension`}>
      <main className="container margin-vert--lg">
        <h1>Download GATF {release}</h1>
        <p>
          GATF needs Java 21 or later; a JRE is enough. The installers find an installed Java or offer a private
          Eclipse Temurin 21, install per user with no admin rights, and can set up the load tools (
          <code>--with-tools</code>).
        </p>

        <h2 id="install">Install</h2>
        <p>macOS / Linux:</p>
        <CodeBlock language="bash">{`curl -fsSL ${a.installSh} | bash`}</CodeBlock>
        <p>Windows (PowerShell):</p>
        <CodeBlock language="powershell">{`irm ${a.installPs1} | iex`}</CodeBlock>
        <p>
          Offline: download <code>gatf-{release}.zip</code>, then <code>unzip gatf-{release}.zip</code> and run{' '}
          <code>./gatf-{release}/install.sh</code> (Windows:{' '}
          <code>powershell -ExecutionPolicy Bypass -File .\gatf-{release}\install.ps1</code>).
        </p>

        <h2 id="assets">Release assets ({release})</h2>
        <table>
          <thead>
            <tr>
              <th>File</th>
              <th>What it is</th>
            </tr>
          </thead>
          <tbody>
            {a.files.map((f) => (
              <tr key={f.name}>
                <td>
                  <a href={f.href}>{f.name}</a>
                </td>
                <td>{f.what}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <p>
          Every release and its notes: <a href={a.release}>{release} on GitHub</a> · <a href={a.releases}>all releases</a>.
        </p>

        <h2 id="source">From source</h2>
        <CodeBlock language="bash">{`git clone ${repoUrl}.git
cd gatf
mvn -B install`}</CodeBlock>
        <p>
          The jar is then <code>alldep-jar/target/gatf-alldep-jar-{release}.jar</code>.
        </p>
      </main>
    </Layout>
  );
}
