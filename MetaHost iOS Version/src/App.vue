<template>
  <div class="app-shell" :class="{ 'app-shell-light': isLightRoute }">

    <!-- Persistent background loop (subtle, blended) -->
    <video
      v-if="!isLightRoute && !isCommandRoute"
      autoplay
      muted
      loop
      playsinline
      webkit-playsinline
      class="bg-video-container"
    >
      <source :src="'/bg_loop.mp4'" type="video/mp4">
    </video>

    <!-- Router View with a cross-fade transition.
         NOT `mode="out-in"` — that mounts the incoming view only after
         the outgoing one's leave transition ends, and Vue waits on
         requestAnimationFrame for that. In any context where rAF is
         throttled (a backgrounded tab, an occluded webview) the leave
         never resolves, the enter never fires, and the app is left
         blank with no view mounted at all. The default overlapping mode
         mounts the new view immediately; the old one fades out over it. -->
    <RouterView v-slot="{ Component, route }">
      <Transition name="page">
        <component :is="Component" :key="route.name" />
      </Transition>
    </RouterView>

    <!-- FloatingPulseHUD: removed.

         It was the last prop left on the screen. Its bot was hardcoded
         "GhostRider X", its ONLINE/OFFLINE pill was a local ref a tap
         flipped, and it carried a "SIM TRADE" button that faked an
         execution with a shake animation — on a product whose entire
         claim is that it places real orders. It rendered on Terminal and
         Support, so a user two taps from Home could press SIM TRADE and
         watch the app pretend. -->

    <!-- Floating Dock — hidden on splash, onboarding and the gate.
         Wrapped in a `.cmd` context because the dock now uses command
         tokens and those are declared on `.cmd`, not :root — only the
         five runtime tokens (accent, glow) are global. The wrapper is a
         plain div on purpose: a transform or filter here would become
         the containing block for the dock's `position: fixed`.

         Scheme follows the user only on command routes. The other five
         views the dock appears on are obsidian and dark-only, so a light
         dock would be the single pale object on a black screen. -->
    <div v-if="showDock" class="cmd dock-host" :data-scheme="dockScheme" :data-motion="theme.motion">
      <FloatingDock />
    </div>

    <!-- iOS Install Card — onboarding teaches this as its own step, so the
         floating card would be a duplicate prompt over the top of it. -->
    <iOSInstallCard v-if="showInstall && !isLightRoute" @dismiss="showInstall = false" />

    <!-- SVG Filters for Gooey & Glitch -->
    <svg style="width:0;height:0;position:absolute;" aria-hidden="true" focusable="false">
      <defs>
        <filter id="goo">
          <feGaussianBlur in="SourceGraphic" stdDeviation="10" result="blur" />
          <feColorMatrix in="blur" mode="matrix" values="1 0 0 0 0  0 1 0 0 0  0 0 1 0 0  0 0 0 25 -10" result="goo" />
          <feComposite in="SourceGraphic" in2="goo" operator="atop"/>
        </filter>
        <filter id="chromatic-aberration">
          <feOffset dx="2" dy="0" in="SourceGraphic" result="red-shift"/>
          <feOffset dx="-2" dy="0" in="SourceGraphic" result="blue-shift"/>
          <feMerge>
            <feMergeNode in="red-shift"/>
            <feMergeNode in="SourceGraphic"/>
            <feMergeNode in="blue-shift"/>
          </feMerge>
        </filter>
      </defs>
    </svg>
  </div>
</template>

<script setup>
import { ref, computed, watchEffect } from 'vue'
import { useRoute } from 'vue-router'
import FloatingDock from './components/FloatingDock.vue'
import iOSInstallCard from './components/iOSInstallCard.vue'
import { accent } from './stores/accent.js'
import { resolvedScheme, theme } from './stores/theme.js'

const route   = useRoute()

// Hide the dock on splash, onboarding, the licence-key gate and the payment
// screen. `payment-return` is in here because it is reached from PayFast in a
// browser that has usually never seen this app: the dock would offer navigation
// into an unverified session, and its robot avatar reads as a second, competing
// subject on a screen that has one thing to say.
const HIDE_DOCK = ['splash', 'onboarding', 'unlock', 'payment', 'payment-return']

// Home layouts that render chrome-free.
//
// Full-Bleed Art and Focus Engine are the two art-forward compositions —
// Full-Bleed IS the marketing screen, and Focus Engine strips the screen to
// one control on purpose. A floating pill across the bottom of either is the
// one element that stops them being the thing they were designed to be.
//
// Neither becomes a dead end. HomeTopBar's layers button opens the Interface
// Style sheet, whose header carries an "All settings" route to the hub — and
// the hub reaches every destination the dock offers. That route is what makes
// this safe: the top bar used to hold a SECOND button going straight to
// Settings, and it was removed as a duplicate of the layers one. On a layout
// that also hides the dock, removing it without the sheet's route would have
// left no way off the screen at all. This only applies on Home — the layout
// preference says nothing about Scanner or Terminal.
const DOCKLESS_LAYOUTS = ['bleed', 'focus']

const showDock = computed(() => {
  if (HIDE_DOCK.includes(route.name)) return false
  if (route.name === 'home' && DOCKLESS_LAYOUTS.includes(theme.layout)) return false
  return true
})

// Routes on the light Nova system rather than obsidian glass. The shell's
// black gradient and the bg video would otherwise sit behind — and blur
// through — the frosted Nova materials on onboarding, the gate and the
// PayFast landing screen.
const LIGHT_ROUTES = ['onboarding', 'unlock', 'payment', 'payment-return']
const isLightRoute = computed(() => LIGHT_ROUTES.includes(route.name))

// Routes on the command.css system. They paint their own opaque canvas
// and their own backdrop, so the shell's bg_loop video is pure waste
// behind them — and worse, it would be blurred through by every glass
// card on the screen, which is not the material HomeBackdrop composes.
// `pairs` and `scanner` joined with their rewrites: both are command-system
// screens now, and `sign-out` and `trades` followed.
//
// (This list used to carry a second job: keeping the old FloatingPulseHUD's
// z-index 9999 robot portrait off screens it was landing on top of. That
// component is gone — see the note in the template — so what is left here is
// only the bg_loop / glass-blur reasoning above.)
const COMMAND_ROUTES = ['home', 'settings', 'arrange-widgets', 'pairs', 'scanner', 'sign-out', 'trades']
const isCommandRoute = computed(() => COMMAND_ROUTES.includes(route.name))

const dockScheme = computed(() => (isCommandRoute.value ? resolvedScheme.value : 'dark'))

// Show iOS install hint on first visit (after 3s)
const showInstall = ref(false)
const isIOS        = /iphone|ipad|ipod/i.test(navigator.userAgent)
const isStandalone = window.matchMedia('(display-mode: standalone)').matches
if (isIOS && !isStandalone) {
  setTimeout(() => { showInstall.value = true }, 3000)
}

// ── Bottom nav glow tracks robot accent reactively ─────────────────────────
// The FloatingDock already reads --accent CSS vars, the accent store watch()
// keeps those CSS vars in sync, so no extra work needed here. The dock glow
// automatically updates the moment accent.current changes (e.g. on license
// activation or settings change).
</script>

<style>
.bg-video-container {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  object-fit: cover;
  opacity: 0.25;
  z-index: -1;
  pointer-events: none;
  mix-blend-mode: screen;
}

.app-shell {
  position: relative; /* containing block for the leaving view below */
  width: 100%;
  height: 100dvh;
  overflow: hidden;
  background: #000000;
  background-image:
    radial-gradient(ellipse 80% 80% at 20% -20%, #0A0A0A 0%, transparent 100%),
    radial-gradient(ellipse 80% 120% at 80% 120%, #001122 0%, transparent 100%);
  background-size: 200% 200%;
  animation: bg-drift 20s ease-in-out infinite alternate;
}

@keyframes bg-drift {
  0%   { background-position: 0% 0%; }
  50%  { background-position: 100% 100%; }
  100% { background-position: 0% 100%; }
}

/* The dock's token context, not a surface. `.cmd` paints
   `background: var(--cc-canvas)`, which on a wrapper whose only child is
   `position: fixed` would be a zero-height no-op today — but would become
   a full-bleed canvas the moment anything else was put inside it. */
.dock-host {
  background: none;
  height: 0;
}

/* Light routes opt out of the obsidian shell entirely. */
.app-shell-light {
  background: #F4F7F9;
  background-image: none;
  animation: none;
}

/* ── Page transition ───────────────────────────────────────────────────────
   Enter and leave overlap (no `mode="out-in"`). The leaving view is taken
   out of flow and laid over the top so the two do not stack vertically
   during the ~220ms crossover; it fades and scales away to reveal the
   entering view beneath it. */
.page-enter-active,
.page-leave-active {
  transition: opacity 0.22s ease, transform 0.22s ease;
}
.page-leave-active {
  position: absolute;
  inset: 0;
  z-index: 2;
}
.page-enter-from {
  opacity: 0;
  transform: scale(0.97) translateY(8px);
}
.page-leave-to {
  opacity: 0;
  transform: scale(1.02);
}
</style>
