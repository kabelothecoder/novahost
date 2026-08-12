import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig({
  server: {
    host: true,
    allowedHosts: true, // Allow ngrok / localtunnel connections
  },
  assetsInclude: ['**/*.mp4'],
  plugins: [
    vue(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['app_logo.png', 'robot_avatar.png', 'bg_loop.mp4'],
      manifest: {
        name: 'Nova Edge',
        short_name: 'Nova Edge',
        description: 'Premium AI-Powered Trading Platform',
        display: 'standalone',
        orientation: 'portrait',
        background_color: '#0A0A0A',
        theme_color: '#0A0A0A',
        start_url: '/',
        scope: '/',
        icons: [
          {
            src: '/app_logo.png',
            sizes: '180x180',
            type: 'image/png'
          },
          {
            src: '/app_logo.png',
            sizes: '192x192',
            type: 'image/png',
            purpose: 'any'
          },
          {
            src: '/app_logo.png',
            sizes: '384x384',
            type: 'image/png'
          },
          {
            src: '/app_logo.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'any maskable'
          }
        ]
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,ico,png,svg,mp4,woff2}'],
        runtimeCaching: [
          {
            urlPattern: /\.mp4$/,
            handler: 'CacheFirst',
            options: {
              cacheName: 'video-cache',
              expiration: { maxEntries: 5, maxAgeSeconds: 60 * 60 * 24 * 30 }
            }
          }
        ]
      },
      devOptions: {
        enabled: true
      }
    })
  ]
})
