// Registers @testing-library/jest-dom matchers (toBeInTheDocument, etc.) on vitest's expect.
// The /vitest entrypoint binds to vitest's expect (needed with test.globals=false).
import '@testing-library/jest-dom/vitest';
