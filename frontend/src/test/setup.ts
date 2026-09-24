// Extends Vitest's `expect` with jest-dom matchers (toBeInTheDocument, toBeDisabled, ...) and
// their TypeScript types - the `/vitest` subpath (rather than plain `@testing-library/jest-dom`)
// is what wires the type augmentation up for Vitest specifically. Loaded once via
// vite.config.ts's `test.setupFiles`, never imported directly by individual test files.
import '@testing-library/jest-dom/vitest'
