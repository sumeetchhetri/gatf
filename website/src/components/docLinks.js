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
import { useLatestVersion } from '@docusaurus/plugin-content-docs/client';

/** URL of a doc in the latest released version by doc id (honours slugs), or the docs home when it is not there. */
export function useDocPath() {
  const v = useLatestVersion();
  const home = (v.docs.find((d) => d.id === v.mainDocId) || v.docs[0] || { path: v.path }).path;
  return (id) => {
    if (!id) return home;
    const d = v.docs.find((x) => x.id === id);
    return d ? d.path : home;
  };
}
