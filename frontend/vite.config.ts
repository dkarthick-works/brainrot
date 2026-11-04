import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import { VitePWA } from 'vite-plugin-pwa'
import pkg from './package.json' with { type: 'json' }

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate', // a new deploy takes over on the next launch, no "update" prompt
      includeAssets: ['icon.svg', 'apple-touch-icon.png'],
      manifest: {
        name: 'brainrot',
        short_name: 'brainrot',
        description: 'Flashcards with spaced repetition',
        theme_color: '#f4f1ea',
        background_color: '#f4f1ea',
        display: 'standalone',
        orientation: 'portrait',
        start_url: '/',
        icons: [
          { src: 'icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: 'icon-512.png', sizes: '512x512', type: 'image/png' },
          { src: 'icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      // App shell only. API calls go to another origin and are never cached: data needs the network.
      workbox: {
        globPatterns: ['**/*.{js,css,html,svg,png,woff2}'],
        navigateFallback: '/index.html',
      },
    }),
  ],
  // 8089 is the backend's default CORS origin (ALLOWED_ORIGINS)
  server: { port: 8089, strictPort: true },
  define: { __APP_VERSION__: JSON.stringify(pkg.version) },
})
