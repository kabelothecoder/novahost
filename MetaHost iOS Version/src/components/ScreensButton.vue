<template>
  <!-- Replaces the old four-tab dock on every screen but Home. Each screen
       has its own back arrow to Home; this one button opens the same
       Screens tray Home uses, so there is one way to move between screens. -->
  <button class="sb" :class="{ 'is-open': open }" :aria-label="open ? 'Close screens' : 'Other screens'" @click="toggle">
    <span class="ms">{{ open ? 'close' : 'apps' }}</span>
  </button>
  <ScreensStrip v-if="open" fixed :current="route.path" @close="open = false" @open="go" />
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { haptic } from '../composables/useSpeech.js'
import ScreensStrip from './home/v4/ScreensStrip.vue'

const route = useRoute()
const router = useRouter()
const open = ref(false)

function toggle() { haptic(15); open.value = !open.value }
function go(c) {
  open.value = false
  if (c.to !== route.path) router.push(c.to)
}
watch(() => route.path, () => { open.value = false })
</script>

<style scoped>
.ms { font-family: 'Material Symbols Outlined'; font-weight: 400; font-style: normal; line-height: 1; letter-spacing: normal; font-feature-settings: 'liga'; font-size: 22px; }
.sb {
  position: fixed; z-index: 9001;
  right: 18px; bottom: calc(env(safe-area-inset-bottom, 0px) + 20px);
  width: 54px; height: 54px; border-radius: 50%;
  display: grid; place-items: center; cursor: pointer;
  color: #F0F0F3; background: rgba(14,14,19,.62);
  -webkit-backdrop-filter: blur(16px) saturate(1.3); backdrop-filter: blur(16px) saturate(1.3);
  border: 1px solid rgba(255,255,255,.14);
  box-shadow: 0 12px 28px -10px rgba(0,0,0,.8), 0 0 0 1px rgba(var(--cc-accent-rgb, 224,49,63), .0);
  transition: transform .15s ease, border-color .2s ease, box-shadow .2s ease;
}
.sb:active { transform: scale(.92); }
.sb.is-open { border-color: rgba(var(--cc-accent-rgb, 224,49,63), .6); box-shadow: 0 0 18px -2px rgba(var(--cc-accent-rgb, 224,49,63), .55); }
</style>
