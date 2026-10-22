<template>
  <div v-if="item" class="di-host" :data-motion="theme.motion" aria-live="polite">
    <button
      class="di"
      :class="['is-' + phase, 'k-' + item.kind, item.side ? 's-' + item.side : '']"
      :aria-label="item.title + '. ' + item.body"
      @click="open"
    >
      <span class="di-avatar">
        <img :src="avatar" alt="" />
        <span class="di-ring"></span>
      </span>

      <span class="di-text">
        <span class="di-title">{{ item.title }}</span>
        <span v-if="item.body" class="di-body">{{ item.body }}</span>
      </span>

      <span class="di-end">
        <span v-if="item.kind === 'wake'" class="di-cta">Turn on</span>
        <span v-else-if="item.side" class="di-side">{{ item.side.toUpperCase() }}</span>
        <span v-else class="ms di-icon">{{ icon }}</span>
      </span>

      <!-- Collapsed state: the two little dots iOS shows for a live activity. -->
      <span class="di-mini">
        <i class="di-dot"></i>
        <i class="di-wave"><b></b><b></b><b></b></i>
      </span>
    </button>
  </div>
</template>

<script setup>
/**
 * Dynamic-Island-style alert for the open app. See stores/island.js for why
 * this is drawn by the page rather than by iOS.
 *
 * Choreography, timed to feel like the hardware: the pill appears at the
 * island's own size over the status bar, springs open into the alert, holds,
 * then shrinks back to a pill and fades. A tap opens what the alert is about.
 */
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { island, showIsland, nextIsland } from '../stores/island.js'
import { robot, FALLBACK_AVATAR } from '../stores/robot.js'
import { theme } from '../stores/theme.js'
import { haptic } from '../composables/useSpeech.js'

const router = useRouter()
const item = computed(() => island.current)
const phase = ref('pill')     // pill → open → closing
const avatar = computed(() => robot.avatarUrl || FALLBACK_AVATAR)
const icon = computed(() => ({ placed: 'check_circle', refused: 'error', target: 'flag', test: 'notifications_active' })[item.value?.kind] || 'smart_toy')

let timers = []
const later = (ms, fn) => timers.push(setTimeout(fn, ms))
const clear = () => { timers.forEach(clearTimeout); timers = [] }

watch(item, (it) => {
  clear()
  if (!it) return
  const reduced = theme.motion === 'reduced'
  phase.value = 'pill'
  haptic([12, 40, 18])
  later(reduced ? 0 : 60, () => { phase.value = 'open' })
  later(it.kind === 'wake' ? 7000 : 5200, collapse)
}, { immediate: true })

function collapse() {
  clear()
  phase.value = 'closing'
  later(theme.motion === 'reduced' ? 0 : 520, nextIsland)
}

function open() {
  const url = item.value?.url
  collapse()
  if (url && router.currentRoute.value.path !== url) router.push(url).catch(() => {})
}

/* The service worker forwards every push to open windows (public/push-sw.js),
   and tells the app where to go when a banner is tapped. */
function onMessage(e) {
  const m = e.data
  if (!m || typeof m !== 'object') return
  if (m.type === 'novahost-push') showIsland(m)
  else if (m.type === 'novahost-open' && m.url) router.push(m.url).catch(() => {})
}

onMounted(() => {
  navigator.serviceWorker?.addEventListener('message', onMessage)
  // A preview hook for design review on a desktop browser, where push cannot run.
  window.__novaIsland = showIsland
})
onBeforeUnmount(() => {
  clear()
  navigator.serviceWorker?.removeEventListener('message', onMessage)
})
</script>

<style scoped>
.di-host {
  position: fixed;
  inset: 0 0 auto 0;
  z-index: 10000;
  display: flex;
  justify-content: center;
  pointer-events: none;
  /* Where the hardware island sits on 14 Pro and later. On older phones and
     in a browser there is no safe area, so it floats just below the top. */
  padding-top: max(11px, calc(env(safe-area-inset-top) - 48px));
}

.di {
  --w: 126px;
  --h: 37px;
  --r: 19px;
  pointer-events: auto;
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  width: var(--w);
  height: var(--h);
  padding: 0 10px;
  border: 0;
  border-radius: var(--r);
  background: #000;
  color: #fff;
  overflow: hidden;
  text-align: left;
  font: inherit;
  box-shadow: 0 0 0 0 rgba(0, 0, 0, 0);
  transform: scale(.9);
  opacity: 0;
  transition:
    width .55s cubic-bezier(.2, 1.25, .35, 1),
    height .55s cubic-bezier(.2, 1.25, .35, 1),
    border-radius .55s cubic-bezier(.2, 1.25, .35, 1),
    padding .45s ease,
    transform .45s cubic-bezier(.2, 1.3, .4, 1),
    opacity .25s ease,
    box-shadow .5s ease;
  -webkit-tap-highlight-color: transparent;
}

.di.is-pill { opacity: 1; transform: scale(1); }

.di.is-open {
  --w: min(calc(100vw - 20px), 384px);
  --h: 96px;
  --r: 36px;
  padding: 0 14px 0 12px;
  opacity: 1;
  transform: scale(1);
  box-shadow:
    0 14px 40px rgba(0, 0, 0, .55),
    0 0 0 1px rgba(255, 255, 255, .06),
    0 0 28px -6px rgba(var(--accent-rgb, 92, 156, 230), .55);
}

.di.is-closing {
  --w: 126px;
  --h: 37px;
  --r: 19px;
  opacity: 0;
  transform: scale(.92);
  transition-delay: 0s, 0s, 0s, 0s, 0s, .32s, 0s;
}

/* Content only once there is room for it; otherwise it would squeeze. */
.di-avatar, .di-text, .di-end {
  opacity: 0;
  transform: scale(.7);
  transition: opacity .2s ease, transform .35s cubic-bezier(.2, 1.3, .4, 1);
}
.di.is-open .di-avatar, .di.is-open .di-text, .di.is-open .di-end {
  opacity: 1;
  transform: none;
  transition-delay: .16s;
}

.di-avatar {
  position: relative;
  flex: 0 0 52px;
  width: 52px;
  height: 52px;
}
.di-avatar img {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
}
.di-ring {
  position: absolute;
  inset: -3px;
  border-radius: 50%;
  border: 2px solid var(--accent, #5C9CE6);
  animation: di-ring 1.6s ease-out infinite;
}

.di-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
  transform-origin: left center;
}
.di-title {
  font-size: 15px;
  font-weight: 650;
  line-height: 1.2;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.di-body {
  font-size: 13px;
  line-height: 1.25;
  color: rgba(235, 235, 245, .62);
  /* Two lines, not an ellipsis: on a signal this is entry, TP and SL, and a
     cut-off stop loss is worse than a taller island. */
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-variant-numeric: tabular-nums;
}

.di-end { flex: 0 0 auto; }
.di-side, .di-cta {
  display: inline-block;
  padding: 7px 12px;
  border-radius: 9999px;
  font-size: 12px;
  font-weight: 750;
  letter-spacing: .4px;
}
.di-side { background: rgba(48, 209, 88, .18); color: #30D158; }
.s-sell .di-side { background: rgba(255, 69, 58, .18); color: #FF453A; }
.di-cta {
  background: var(--accent, #5C9CE6);
  color: #000;
  animation: di-breathe 1.4s ease-in-out infinite;
}
.di-icon { font-size: 26px; color: var(--accent, #5C9CE6); }
.k-refused .di-icon { color: #FF9F0A; }
.k-placed .di-icon { color: #30D158; }

/* Collapsed pill: a glowing dot left, a tiny live waveform right. */
.di-mini {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  opacity: 1;
  transition: opacity .15s ease;
  pointer-events: none;
}
.di.is-open .di-mini { opacity: 0; }
.di-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: var(--accent, #5C9CE6);
  box-shadow: 0 0 10px var(--accent, #5C9CE6);
}
.s-sell .di-dot { background: #FF453A; box-shadow: 0 0 10px #FF453A; }
.s-buy .di-dot { background: #30D158; box-shadow: 0 0 10px #30D158; }
.di-wave { display: flex; gap: 2px; align-items: center; height: 12px; }
.di-wave b {
  width: 2px;
  height: 100%;
  border-radius: 1px;
  background: var(--accent, #5C9CE6);
  animation: di-wave .9s ease-in-out infinite;
}
.di-wave b:nth-child(2) { animation-delay: .15s; }
.di-wave b:nth-child(3) { animation-delay: .3s; }

@keyframes di-ring {
  0% { transform: scale(1); opacity: .9; }
  100% { transform: scale(1.25); opacity: 0; }
}
@keyframes di-breathe {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.06); }
}
@keyframes di-wave {
  0%, 100% { transform: scaleY(.35); }
  50% { transform: scaleY(1); }
}

[data-motion='reduced'] .di,
[data-motion='reduced'] .di * {
  transition-duration: .01s !important;
  animation: none !important;
}
</style>
