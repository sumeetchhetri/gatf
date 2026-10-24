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
// GATF website (Docusaurus 3). Docs are read in place from ../docs ("next") plus one materialised snapshot per released
// version (see scripts/prepare.mjs). Site URL, base path and custom domain come from the environment:
//   GATF_SITE_URL   origin, default https://sumeetchhetri.github.io (the GitHub Pages project site)
//   GATF_SITE_BASE  base path, default /gatf/ (set but empty: /)
//   GATF_SITE_CNAME custom domain; prepare.mjs writes static/CNAME only when it is set
//   GATF_REPO_URL   source links, default https://github.com/sumeetchhetri/gatf
//   GATF_RELEASE_VERSION  the release the Downloads page names, default 5.0.0
// No analytics, no third-party scripts or fonts: search is local (@easyops-cn/docusaurus-search-local).
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { themes as prismThemes } from 'prism-react-renderer';
import remarkRepoLinks, { gitStat } from './plugins/remark-repo-links.mjs';
import { siteEnv } from './plugins/site-env.mjs';

const SITE = path.dirname(fileURLToPath(import.meta.url));
const REPO = path.resolve(SITE, '..');

const { siteUrl, baseUrl, repoUrl, release } = siteEnv(process.env);
const branch = 'master';

const metaFile = path.join(SITE, '.site-meta.json');
if (!fs.existsSync(metaFile)) {
  throw new Error('website/.site-meta.json is missing: run "npm run prepare-site" (npm start/build run it for you)');
}
const meta = JSON.parse(fs.readFileSync(metaFile, 'utf8'));
const versions = JSON.parse(fs.readFileSync(path.join(SITE, 'versions.json'), 'utf8'));
const latest = versions[0];

const linkRoots = [
  { dir: path.join(REPO, 'docs'), ref: branch },
  ...versions.map((v) => {
    const sha = meta.versions[v] && meta.versions[v].sha;
    // A snapshot from git checks repository paths at its own commit; a working-tree fallback checks the tree.
    return { dir: path.join(SITE, 'versioned_docs', 'version-' + v), ref: sha || branch, ...(sha && { stat: gitStat(REPO, sha) }) };
  }),
];

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'GATF',
  tagline: 'One tool for API, workflow, load, UI and RPA testing',
  favicon: 'img/favicon.svg',
  url: siteUrl,
  baseUrl,
  trailingSlash: false,
  organizationName: 'sumeetchhetri',
  projectName: 'gatf',

  onBrokenLinks: 'throw',
  onBrokenAnchors: 'throw',
  onDuplicateRoutes: 'throw',
  markdown: {
    format: 'detect', // .md is CommonMark (GitHub-renderable), .mdx is MDX
    hooks: { onBrokenMarkdownLinks: 'throw', onBrokenMarkdownImages: 'throw' },
  },
  i18n: { defaultLocale: 'en', locales: ['en'] },

  customFields: { repoUrl, release, branch },

  plugins: [
    [
      '@docusaurus/plugin-content-docs',
      {
        path: '../docs',
        routeBasePath: 'docs',
        sidebarPath: './sidebars.mjs',
        exclude: ['deltas/**', '**/_*.{js,jsx,ts,tsx,md,mdx}', '**/_*/**', '**/__tests__/**'],
        lastVersion: latest,
        versions: {
          current: { label: 'next', path: 'next', banner: 'unreleased' },
          [latest]: { label: latest, banner: 'none' },
        },
        editUrl: ({ version, docPath }) =>
          version === 'current' ? `${repoUrl}/edit/${branch}/docs/${docPath}` : undefined,
        beforeDefaultRemarkPlugins: [[remarkRepoLinks, { roots: linkRoots, repoRoot: REPO, repoUrl }]],
      },
    ],
    [
      '@docusaurus/plugin-content-blog',
      {
        path: 'blog',
        showReadingTime: true,
        onInlineAuthors: 'throw',
        onUntruncatedBlogPosts: 'ignore',
        feedOptions: { type: ['rss', 'atom'], xslt: false },
      },
    ],
    '@docusaurus/plugin-content-pages',
    '@docusaurus/plugin-sitemap',
    ['./plugins/release-notes.mjs', { dir: path.join(REPO, 'docs', 'release-notes') }],
  ],

  themes: [
    ['@docusaurus/theme-classic', { customCss: './src/css/custom.css' }],
    [
      '@easyops-cn/docusaurus-search-local',
      {
        hashed: true,
        indexDocs: true,
        indexBlog: true,
        indexPages: true,
        docsRouteBasePath: 'docs',
        docsDir: ['../docs', 'versioned_docs'],
        language: ['en'],
        highlightSearchTermsOnTargetPage: true,
      },
    ],
  ],

  themeConfig: {
    colorMode: { defaultMode: 'light', respectPrefersColorScheme: true },
    navbar: {
      title: 'GATF',
      logo: { alt: 'GATF', src: 'img/logo.svg' },
      items: [
        { type: 'docSidebar', sidebarId: 'docs', position: 'left', label: 'Docs' },
        { to: '/downloads', label: 'Downloads', position: 'left' },
        { to: '/release-notes', label: 'Release notes', position: 'left' },
        { to: '/blog', label: 'Blog', position: 'left' },
        { type: 'docsVersionDropdown', position: 'right' },
        { href: repoUrl, label: 'GitHub', position: 'right' },
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: 'Use',
          items: [
            { label: 'Downloads', to: '/downloads' },
            { label: 'Release notes', to: '/release-notes' },
          ],
        },
        {
          title: 'Project',
          items: [
            { label: 'Source', href: repoUrl },
            { label: 'Issues', href: repoUrl + '/issues' },
            { label: 'Releases', href: repoUrl + '/releases' },
            { label: 'Blog', to: '/blog' },
          ],
        },
      ],
      copyright: 'Copyright © 2013-2026 Sumeet Chhetri. Apache License 2.0.',
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
      additionalLanguages: ['java', 'bash', 'powershell', 'properties', 'groovy', 'ini', 'csv'],
    },
  },
};

export default config;
