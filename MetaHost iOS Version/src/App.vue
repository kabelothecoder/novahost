<template>
  <div class="app-shell">

    <!-- Persistent background loop (subtle, blended) -->
    <video
      autoplay
      muted
      loop
      playsinline
      webkit-playsinline
      class="bg-video-container"
    >
      <source :src="'/bg_loop.mp4'" type="video/mp4">
    </video>

    <!-- Router View with slide-scale spring transition -->
    <RouterView v-slot="{ Component, route }">
      <Transition name="page" mode="out-in">
        <component :is="Component" :key="route.name" />
      </Transition>
    </RouterView>

    <!-- Floating Pulse HUD — hidden on splash & activate -->
    <FloatingPulseHUD v-if="showDock" />

    <!-- Floating Dock — hidden on splash & activate -->
    <FloatingDock v-if="showDock" />

    <!-- iOS Install Card -->
    <iOSInstallCard v-if="showInstall" @dismiss="showInstall = false" />

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
import FloatingPulseHUD from './components/FloatingPulseHUD.vue'
import iOSInstallCard from './components/iOSInstallCard.vue'
import { accent } from './stores/accent.js'

const route   = useRoute()

// Hide the dock on splash, onboarding AND the activation gate
const showDock = computed(() =>
  route.name !== 'splash' && route.name !== 'activate' && route.name !== 'onboarding'
)

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

/* ── Page transition ─────────────────────────────────────────────────────── */
.page-enter-active,
.page-leave-active {
  transition: opacity 0.22s ease, transform 0.22s ease;
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
