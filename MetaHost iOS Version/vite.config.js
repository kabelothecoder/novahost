import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { VitePWA } from 'vite-plugin-pwa'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))

/**
 * Local HTTPS, only if certificates are present.
 *
 * Testing this app on a real iPhone over the LAN needs a secure context,
 * and a LAN IP does not get the free pass `localhost` does. Without it
 * the service worker never registers, so Add to Home Screen cannot be
 * tested — and that flow is load-bearing rather than cosmetic: iOS
 * evicts storage for sites unused for ~7 days unless they are installed,
 * and losing that storage costs the user their device binding.
 *
 * A merely self-signed certificate is not enough either. Safari will let
 * you click through the interstitial but still refuses to register a
 * service worker behind an untrusted chain, which is the one thing this
 * exists to enable. So `certs/` holds a small local CA whose root is
 * installed and trusted on the test device once.
 *
 * OPT-IN, via `npm run dev:https`. It is not the default because an
 * HTTPS dev server breaks any tooling that proxies the port over plain
 * HTTP — including the editor's own preview pane, which silently stops
 * loading. Phone testing wants TLS; everyday work does not.
 *
 * Optional by design: `certs/` is gitignored (it contains private keys),
 * so a fresh clone and CI both fall back to plain HTTP rather than
 * failing to boot. Regenerate per machine with openssl — the leaf needs
 * a subjectAltName carrying the LAN IP, extendedKeyUsage=serverAuth and
 * a lifetime under 398 days, or iOS rejects it whatever you trust.
 */
function localHttps(mode) {
  if (mode !== 'https') return undefined
  const dir = path.join(here, 'certs')
  const key = path.join(dir, 'dev-key.pem')
  const cert = path.join(dir, 'dev-cert.pem')
  if (!fs.existsSync(key) || !fs.existsSync(cert)) {
    console.warn('[vite] --mode https requested but certs/ is missing; serving HTTP.')
    return undefined
  }
  return { key: fs.readFileSync(key), cert: fs.readFileSync(cert) }
}

export default defineConfig(({ mode }) => ({
  server: {
    host: true,
    allowedHosts: true, // Allow ngrok / localtunnel connections
    https: localHttps(mode),
  },
  assetsInclude: ['**/*.mp4'],
  plugins: [
    vue(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['app_logo.png', 'robot_avatar.png', 'bg_loop.mp4'],
      manifest: {
        name: 'NovaHost',
        short_name: 'NovaHost',
        description: 'Premium AI-Powered Trading Platform',
        display: 'standalone',
        orientation: 'portrait',
        /**
         * The app's DEFAULT command canvas, not a generic near-black.
         *
         * These two paint the launch splash and whatever chrome the OS draws
         * around the web view, and `#0A0A0A` was neither of the app's two
         * grounds — so an installed handset showed a dark grey band meeting
         * the app's own canvas at a visible seam, on top of the in-page one
         * fixed by `--app-ground` in glass.css.
         *
         * A manifest carries one colour and the background tone is a user
         * setting, so this is the Obsidian default every install starts on.
         * Changing it only takes effect on a FRESH install: iOS reads the
         * manifest when the icon is added to the home screen and never again.
         */
        background_color: '#0E0E11',
        theme_color: '#0E0E11',
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
        // Only run the service worker on the dev server that actually needs
        // it: `dev:https`, for testing Add to Home Screen on a real iPhone
        // (see localHttps above). On plain `npm run dev` an autoUpdate SW just
        // pre-caches the dev bundle and races HMR — a half-applied update can
        // then serve a stale module graph and wedge navigation until a hard
        // reload. Production builds are unaffected; they always ship the SW.
        enabled: mode === 'https'
      }
    })
  ]
}))
