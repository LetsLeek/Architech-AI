import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

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
})
