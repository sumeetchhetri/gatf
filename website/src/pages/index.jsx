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
import Link from '@docusaurus/Link';
import Layout from '@theme/Layout';
import CodeBlock from '@theme/CodeBlock';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import { useDocPath } from '../components/docLinks';
import styles from './index.module.css';

// Lines as written in docs/seleasy/REFERENCE.md (Essentials and Semantic locators) and the README.
const SAMPLE = `open chrome
goto https://example.com
??10 id@username type "user"
??10 id@password type "secret"
click role@button[name=Save]
?? css@"h1" text ="Welcome"
screenshot compare home`;

// Each item states what GATF 5.0 does; the docs page holds the details.
const FEATURES = [
  {
    title: 'API tests as data',
    doc: 'configuration',
    body: 'Test cases in XML, JSON or CSV: REST over HTTP/1.1 and HTTP/2, SOAP, multipart. Assert on status, headers and body with JSONPath, XPath and Velocity expressions; log in once and reuse the token.',
  },
  {
    title: 'Workflows and data',
    doc: 'configuration',
    body: 'Chain cases by capturing response values into variables, repeat them per scenario, and feed them from JDBC, MongoDB or file providers. The same providers drive seleasy loops.',
  },
  {
    title: 'UI and RPA with seleasy',
    doc: 'seleasy/REFERENCE',
    body: 'A terse language compiled to Selenium Java: semantic locators (role@, label@, testid@), auto-wait, web-first and soft assertions, network routes, auth state, emulation, clock control, visual and accessibility checks.',
  },
  {
    title: 'Load, concurrency and distribution',
    doc: 'load-tools',
    body: 'Run a case N times with percentiles, simulate concurrent users on virtual threads, replay a suite for a time box, or delegate to wperfk (the default), vegeta, autocannon or wrk/wrk2. Agents on other machines report back live.',
  },
  {
    title: 'Browsers your way',
    doc: 'browser-modes',
    body: 'Local browsers through Selenium Manager, the lean chrome-headless-shell, or any Selenium Grid. CDP on Chromium and WebDriver BiDi elsewhere sit behind one browser-control layer.',
  },
  {
    title: 'Traces, debugger and console',
    doc: null,
    body: 'A trace viewer with DOM time travel, a step debugger for seleasy, a React web console (gatf ui) and a VS Code extension with diagnostics, completion and debugging.',
  },
];

// Factual, sourced statements only: what each project's own documentation describes (checked October 2026).
const COMPARE = {
  tools: ['GATF', 'Playwright', 'Postman', 'Apache JMeter'],
  rows: [
    {
      label: 'Described by its project as',
      cells: [
        'One tool for API, workflow, performance, load, distributed, comparative and UI/RPA testing',
        'A framework for reliable end-to-end testing of modern web apps',
        'An API platform: requests and collections, with scripted tests',
        'Designed to load test functional behavior and measure performance',
      ],
    },
    {
      label: 'API testing',
      cells: [
        'Test cases as XML, JSON or CSV data; REST and SOAP',
        'Through APIRequestContext, from test code',
        'Collections with JavaScript test scripts',
        'HTTP(S), SOAP/REST samplers, plus JDBC, LDAP, JMS, FTP, mail and TCP',
      ],
    },
    {
      label: 'Browser UI testing',
      cells: [
        'seleasy scripts on Selenium (CDP / WebDriver BiDi)',
        'Its core purpose: Chromium, Firefox and WebKit',
        '—',
        'Through the third-party WebDriver Sampler plugin',
      ],
    },
    {
      label: 'Load and performance',
      cells: [
        'Built-in engine, concurrent users, distributed agents; wperfk/vegeta/autocannon/wrk',
        '—',
        'Performance tests with virtual users in the Collection Runner',
        'Its core purpose',
      ],
    },
    {
      label: 'How tests are written',
      cells: [
        'Data files (API) and seleasy scripts (UI); Java and other languages for hooks and plugins',
        'Code: JavaScript/TypeScript, Python, Java or .NET',
        'Requests in the app; tests in JavaScript',
        'Test plans (.jmx), usually built in the GUI',
      ],
    },
  ],
  sources: [
    ['Playwright', 'https://playwright.dev/'],
    ['Playwright: API testing', 'https://playwright.dev/docs/api-testing'],
    ['Postman', 'https://www.postman.com/'],
    ['Postman: performance test configuration', 'https://learning.postman.com/docs/collections/performance-testing/performance-test-configuration/'],
    ['Apache JMeter', 'https://jmeter.apache.org/'],
    ['JMeter WebDriver Sampler', 'https://jmeter-plugins.org/wiki/WebDriverSampler/'],
  ],
};

function Hero() {
  const docPath = useDocPath();
  return (
    <header className={styles.hero}>
      <div className="container">
        <h1 className={styles.title}>GATF</h1>
        <p className={styles.lead}>
          One tool for API, workflow, performance, load, distributed, comparative and UI/RPA testing: a single Java 21
          jar with a web console, a trace viewer, a step debugger and a VS Code extension.
        </p>
        <div className={styles.actions}>
          <Link className="button button--primary button--lg" to={docPath(null)}>
            Read the docs
          </Link>
          <Link className="button button--secondary button--lg" to="/downloads">
            Download
          </Link>
        </div>
      </div>
    </header>
  );
}

function Features() {
  const docPath = useDocPath();
  return (
    <section className={styles.section} aria-labelledby="tour">
      <div className="container">
        <h2 id="tour">Feature tour</h2>
        <div className={styles.grid}>
          {FEATURES.map((f) => (
            <article key={f.title} className={styles.card}>
              <h3>{f.title}</h3>
              <p>{f.body}</p>
              <Link to={docPath(f.doc)}>Learn more →</Link>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}

function Sample() {
  return (
    <section className={styles.section} aria-labelledby="sample">
      <div className="container">
        <h2 id="sample">What a seleasy test looks like</h2>
        <CodeBlock language="seleasy" title="login.sel">
          {SAMPLE}
        </CodeBlock>
      </div>
    </section>
  );
}

function Compare() {
  return (
    <section className={styles.section} aria-labelledby="compare">
      <div className="container">
        <h2 id="compare">How GATF compares</h2>
        <p>
          What each tool covers, as its own documentation describes it. "—" means the project's documentation does not
          describe the capability (add-ons may exist). Each tool does more than one row can say; follow the sources.
        </p>
        <div className={styles.tableWrap}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th scope="col"></th>
                {COMPARE.tools.map((t) => (
                  <th key={t} scope="col">
                    {t}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {COMPARE.rows.map((r) => (
                <tr key={r.label}>
                  <th scope="row">{r.label}</th>
                  {r.cells.map((c, i) => (
                    <td key={i}>{c}</td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <p className={styles.sources}>
          Sources:{' '}
          {COMPARE.sources.map(([label, href], i) => (
            <React.Fragment key={href}>
              {i > 0 && ' · '}
              <a href={href} rel="noopener noreferrer">
                {label}
              </a>
            </React.Fragment>
          ))}
        </p>
      </div>
    </section>
  );
}

export default function Home() {
  const { siteConfig } = useDocusaurusContext();
  return (
    <Layout title="GATF" description={siteConfig.tagline}>
      <Hero />
      <main>
        <Features />
        <Sample />
        <Compare />
      </main>
    </Layout>
  );
}
