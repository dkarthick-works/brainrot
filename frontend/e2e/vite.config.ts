import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { fileURLToPath } from 'node:url'

// Test-only entry and auth adapter. Never used by the production build.
export default defineConfig({
  root: fileURLToPath(new URL('.', import.meta.url)),
  plugins: [react()],
  resolve: { alias: { '@clerk/react': fileURLToPath(new URL('./clerk.tsx', import.meta.url)) } },
  server: { host: '127.0.0.1', port: 8091, strictPort: true },
  define: { __APP_VERSION__: JSON.stringify('browser-test'), 'import.meta.env.VITE_API_URL': JSON.stringify('/api') },
})
