import react from '@vitejs/plugin-react'
// `vitest/config`'s defineConfig re-exports Vite's own, merged with the `test` option's types.
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
})
