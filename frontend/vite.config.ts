import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';

// Dev server proxies /api to the backend so the browser uses a single origin.
// In production the built assets are served by nginx, which proxies /api (see Dockerfile/nginx.conf).
export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      manifest: {
        name: 'Life Admin',
        short_name: 'Life Admin',
        description: 'Never forget an important document, deadline, warranty, or renewal again.',
        theme_color: '#1e5eff',
        background_color: '#f4f6fb',
        display: 'standalone',
        start_url: '/',
        icons: [
          { src: '/icon.svg', sizes: 'any', type: 'image/svg+xml', purpose: 'any' },
        ],
      },
    }),
  ],
  server: {
    port: 3000,
    proxy: {
      // Dev-only: proxy API calls to the local backend so the browser uses a single origin.
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
