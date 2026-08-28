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
// Registers @testing-library/jest-dom matchers (toBeInTheDocument, etc.) on vitest's expect.
// The /vitest entrypoint binds to vitest's expect (needed with test.globals=false).
import '@testing-library/jest-dom/vitest';

// Unmount React trees after each test. With globals:false, RTL's automatic afterEach cleanup
// isn't wired, so renders would otherwise accumulate in the DOM across tests in a file.
import { afterEach } from 'vitest';
import { cleanup } from '@testing-library/react';
afterEach(() => {
  cleanup();
});
