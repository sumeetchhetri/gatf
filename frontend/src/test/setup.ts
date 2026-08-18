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
