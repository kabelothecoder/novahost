<template>
  <!-- The Screens tray: three cards, centred, with whatever is behind it (the
       home video, or the current screen) still playing through a light scrim.
       Opened by swipe-up / the grid button on Home and by ScreensButton
       everywhere else. Each card is a small looping scene, not a screenshot. -->
  <div class="ss-layer" :class="{ 'is-fixed': fixed }" @click.self="$emit('close')">
    <div class="ss" @click.self="$emit('close')">
      <div class="ss-label">SCREENS</div>
      <div class="ss-row">
        <div
          v-for="c in CARDS"
          :key="c.id"
          role="button"
          class="ss-card"
          :class="{ 'is-here': c.to === current }"
          :aria-label="c.to === current ? c.name + ', current screen' : 'Open ' + c.name"
          @click="$emit('open', c)"
        >
          <div class="ss-art" aria-hidden="true">
            <!-- MT5: phone and broker server, a trade pulse travelling the link. -->
            <svg v-if="c.id === 'terminal'" viewBox="0 0 100 100">
              <rect class="ln" x="10" y="30" width="22" height="40" rx="5" />
              <line class="ln" x1="17" y1="63" x2="25" y2="63" />
              <line class="link" x1="34" y1="50" x2="64" y2="50" />
              <circle class="pulse" cx="34" cy="50" r="3.2" />
              <rect class="ln" x="66" y="28" width="26" height="13" rx="3" />
              <rect class="ln" x="66" y="44" width="26" height="13" rx="3" />
              <rect class="ln" x="66" y="60" width="26" height="13" rx="3" />
              <circle class="led l1" cx="86" cy="34.5" r="1.8" />
              <circle class="led l2" cx="86" cy="50.5" r="1.8" />
              <circle class="led l3" cx="86" cy="66.5" r="1.8" />
            </svg>

            <!-- Chart Scanner: candles inside a viewfinder, a scan line sweeping them. -->
            <svg v-else-if="c.id === 'scanner'" viewBox="0 0 100 100">
              <path class="ln acc" d="M14 26v-8h8M78 18h8v8M86 74v8h-8M22 82h-8v-8" />
              <g v-for="(k, i) in CANDLES" :key="i" :class="k.up ? 'up' : 'dn'">
                <line :x1="k.x" :x2="k.x" :y1="k.hi" :y2="k.lo" class="wick" />
                <rect :x="k.x - 3.5" :y="k.top" width="7" :height="k.h" rx="1" />
              </g>
              <g class="sweep">
                <rect x="16" y="22" width="68" height="10" class="sweep-glow" />
                <line x1="16" y1="32" x2="84" y2="32" class="sweep-line" />
              </g>
            </svg>

            <!-- Settings: three sliders easing between positions, out of step. -->
            <svg v-else viewBox="0 0 100 100">
              <g v-for="(r, i) in [32, 50, 68]" :key="i">
                <line class="ln dim" x1="18" :y1="r" x2="82" :y2="r" />
                <circle class="knob" :class="'k' + i" cx="30" :cy="r" r="5.5" />
              </g>
            </svg>
          </div>
          <div class="ss-name">{{ c.name }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  /* Fixed to the viewport (ScreensButton) rather than the Home stage. */
  fixed: Boolean,
  /* Route of the screen the tray was opened from, marked as "here". */
  current: { type: String, default: '' },
})
defineEmits(['close', 'open'])

/* Home look lives in Settings; the tray is only these three. */
const CARDS = [
  { id: 'terminal', name: 'MT5',           to: '/terminal' },
  { id: 'scanner',  name: 'Chart Scanner', to: '/scanner' },
  { id: 'settings', name: 'Settings',      to: '/settings' },
]

const CANDLES = [
  { x: 26, up: true,  hi: 52, top: 56, h: 12, lo: 72 },
  { x: 38, up: false, hi: 44, top: 48, h: 14, lo: 66 },
  { x: 50, up: true,  hi: 40, top: 46, h: 16, lo: 68 },
  { x: 62, up: false, hi: 46, top: 50, h: 10, lo: 64 },
  { x: 74, up: true,  hi: 30, top: 34, h: 22, lo: 60 },
]
</script>

<style scoped>
.ss-layer {
  --ac: var(--a, var(--cc-accent, #E0313F));
  position: absolute; inset: 0; z-index: 14;
  display: flex; align-items: center; justify-content: center;
  /* Light enough that the home video keeps playing through it. */
  background: radial-gradient(ellipse at 50% 50%, rgba(4,4,6,.55), rgba(4,4,6,.25) 75%);
  animation: ssFade .25s ease;
}
.ss-layer.is-fixed { position: fixed; z-index: 9000; }
@keyframes ssFade { from { opacity: 0 } to { opacity: 1 } }

.ss { width: 100%; max-width: 460px; padding: 0 14px; display: flex; flex-direction: column; align-items: center; gap: 14px; animation: ssUp .38s cubic-bezier(.2,.8,.2,1); }
@keyframes ssUp { from { transform: translateY(28px) scale(.97); opacity: 0 } to { transform: none; opacity: 1 } }
.ss-label { font-size: 11px; font-weight: 600; letter-spacing: .18em; color: #D6D6DD; text-shadow: 0 1px 8px rgba(0,0,0,.6); }
.ss-row { width: 100%; display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }

.ss-card {
  position: relative; min-width: 0; height: 216px; border-radius: 22px; cursor: pointer;
  display: flex; flex-direction: column; align-items: center; justify-content: space-between; padding: 16px 8px 18px;
  background: rgba(14,14,19,.55); -webkit-backdrop-filter: blur(16px) saturate(1.3); backdrop-filter: blur(16px) saturate(1.3);
  border: 1px solid rgba(255,255,255,.12); box-shadow: 0 18px 36px -16px rgba(0,0,0,.85);
  transition: transform .15s ease, border-color .2s ease;
}
.ss-card:active { transform: scale(.96); }
.ss-card.is-here { border-color: color-mix(in srgb, var(--ac) 70%, transparent); box-shadow: 0 0 0 1px color-mix(in srgb, var(--ac) 35%, transparent), 0 18px 36px -16px rgba(0,0,0,.85); }

.ss-art { flex: 1; width: 100%; display: grid; place-items: center; }
.ss-art svg { width: 100%; max-width: 118px; overflow: visible; }
.ss-name { font-size: 13.5px; font-weight: 600; color: #F0F0F3; white-space: nowrap; letter-spacing: .01em; }

/* Shared strokes */
.ln { fill: none; stroke: rgba(255,255,255,.55); stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; }
.ln.acc { stroke: var(--ac); }
.ln.dim { stroke: rgba(255,255,255,.18); stroke-width: 3; }

/* MT5 */
.link { stroke: color-mix(in srgb, var(--ac) 55%, transparent); stroke-width: 2; stroke-dasharray: 3 4; animation: dash 1.2s linear infinite; }
@keyframes dash { to { stroke-dashoffset: -14 } }
.pulse { fill: var(--ac); filter: drop-shadow(0 0 4px var(--ac)); animation: travel 1.8s cubic-bezier(.5,0,.5,1) infinite; }
@keyframes travel { 0% { transform: translateX(0); opacity: 0 } 15% { opacity: 1 } 85% { opacity: 1 } 100% { transform: translateX(30px); opacity: 0 } }
.led { fill: #4CD787; animation: blink 1.8s ease-in-out infinite; }
.l2 { animation-delay: .3s } .l3 { animation-delay: .6s }
@keyframes blink { 0%, 100% { opacity: .25 } 50% { opacity: 1 } }

/* Scanner */
.up rect { fill: #4CD787; } .dn rect { fill: #FF5D5D; }
.wick { stroke: rgba(255,255,255,.45); stroke-width: 1.4; }
.sweep { animation: sweep 2.4s ease-in-out infinite; }
.sweep-line { stroke: var(--ac); stroke-width: 1.6; filter: drop-shadow(0 0 3px var(--ac)); }
.sweep-glow { fill: color-mix(in srgb, var(--ac) 22%, transparent); }
@keyframes sweep { 0% { transform: translateY(0) } 50% { transform: translateY(46px) } 100% { transform: translateY(0) } }

/* Settings */
.knob { fill: #15151B; stroke: var(--ac); stroke-width: 2.4; animation: slide 3s ease-in-out infinite; }
.k1 { animation-delay: -1s; animation-duration: 3.6s; }
.k2 { animation-delay: -2.2s; animation-duration: 2.8s; }
@keyframes slide { 0%, 100% { transform: translateX(0) } 50% { transform: translateX(40px) } }

@media (prefers-reduced-motion: reduce) {
  .ss, .ss-layer, .link, .pulse, .led, .sweep, .knob { animation: none; }
  .pulse { transform: translateX(15px); }
}
</style>
