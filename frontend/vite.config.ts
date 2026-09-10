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
  },
})
