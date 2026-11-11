import { defineConfig } from '@playwright/test'
export default defineConfig({
  testDir: './e2e',
  use: { baseURL: 'http://127.0.0.1:8091', browserName: 'chromium' },
  webServer: { command: 'npx vite --config e2e/vite.config.ts', url: 'http://127.0.0.1:8091', reuseExistingServer: false },
})
