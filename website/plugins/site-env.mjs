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
// Pure: the site settings that come from the environment (see docusaurus.config.mjs for the variables).

export function normaliseBase(b) {
  const s = (b || '/').trim().replace(/^\/+|\/+$/g, '');
  return s ? '/' + s + '/' : '/';
}

export function normaliseOrigin(u) {
  const s = u.trim().replace(/\/+$/, '');
  if (!/^https?:\/\/[^/\s]+$/i.test(s)) throw new Error(`GATF_SITE_URL must be an origin like https://host (no path): ${u}`);
  return s;
}

export function siteEnv(env) {
  return {
    siteUrl: normaliseOrigin(env.GATF_SITE_URL || 'https://sumeetchhetri.github.io'),
    baseUrl: normaliseBase(env.GATF_SITE_BASE ?? '/gatf/'), // set but empty = the root
    repoUrl: (env.GATF_REPO_URL || 'https://github.com/sumeetchhetri/gatf').trim().replace(/\/+$/, ''),
    release: (env.GATF_RELEASE_VERSION || '5.0.0').trim(),
  };
}
