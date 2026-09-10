import react from '@vitejs/plugin-react'
// `vitest/config`'s defineConfig re-exports Vite's own, merged with the `test` option's types -
// plain `vite`'s defineConfig doesn't know about `test` at all.
import { defineConfig } from 'vitest/config'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Backend runs on 8080 in local dev (see docker-compose.yml / README); proxying keeps
    // the browser's requests same-origin so we don't need backend CORS configuration.
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    // AIW-88: `*.test.ts(x)` colocated next to the source file it tests - see src/test/README.md
    // for the full convention.
    css: false,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'lcov'],
      reportsDirectory: './coverage',
      include: ['src/**/*.{ts,tsx}'],
      exclude: [
        'src/main.tsx', // app bootstrap only - no branching logic to cover
        'src/vite-env.d.ts', // ambient type declaration, no runtime code
        'src/test/**', // test setup/config itself, not code under test
        'src/**/*.test.{ts,tsx}',
      ],
      // AIW-89: floor locked to the first real measurement (ci-quality-gate-policy.md's
      // ratchet strategy) - most components/API modules have no tests yet, so the ~70%/60%
      // target is a later, ticket-by-ticket climb, not a day-one requirement. Never lower
      // these; raise them as coverage grows.
      thresholds: {
        statements: 9,
        branches: 6,
        functions: 5,
        lines: 9,
      },
    },
  },
})
