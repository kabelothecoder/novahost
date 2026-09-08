<template>
  <WidgetCard label="Voice Console">
    <template #action>
      <span class="vc-state cc-mono">{{ stateLabel }}</span>
    </template>

    <button class="vc-body" :aria-label="stateLabel" @click="push">
      <canvas ref="canvasEl" class="vc-canvas"></canvas>
      <span class="vc-hint cc-mono">{{ hint }}</span>
    </button>
  </WidgetCard>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import WidgetCard from '../WidgetCard.vue'
import { accent } from '../../../stores/accent.js'
import { session } from '../../../stores/session.js'
import { haptic } from '../../../composables/useSpeech.js'

const emit = defineEmits(['toast'])

/* Voice state deliberately still lives in accent.js. VoiceView.vue owns
   it and is outside this redesign's scope — duplicating it into a new
   store would mean two objects disagreeing about whether the mic is
   open. */
const canvasEl = ref(null)
let raf = null

const stateLabel = computed(() => {
  if (accent.isSpeaking)  return 'Speaking'
  if (accent.isListening) return 'Listening'
  return session.isRunning ? 'Armed' : 'Idle'
})

const hint = computed(() =>
  accent.isListening ? 'Say "start", "stop" or "pairs"' : 'Tap to talk'
)

function draw() {
  const canvas = canvasEl.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  if (!ctx) return

  /* Backing store is sized to the device pixel ratio, then the context
     is scaled — the original drew at CSS pixels, which is why the bars
     looked soft on a 3x phone. Capped at 2 because a 3x backing store
     for a decorative waveform is wasted fill rate. */
  const dpr = Math.min(window.devicePixelRatio || 1, 2)
  const w = canvas.offsetWidth
  const h = canvas.offsetHeight
  if (canvas.width !== w * dpr || canvas.height !== h * dpr) {
    canvas.width  = w * dpr
    canvas.height = h * dpr
  }
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  ctx.clearRect(0, 0, w, h)

  const live = accent.isListening || accent.isSpeaking || session.isRunning
  const amp  = live ? 1 + (accent.micVolume || 0.35) * 4 : 1

  const styles = getComputedStyle(canvas)
  const hex = styles.getPropertyValue('--cc-accent').trim() || '#5C9CE6'

  const bars = 48
  const gap  = 1
  const barW = Math.max(1, w / bars - gap)
  const t    = Date.now() / 200

  for (let i = 0; i < bars; i++) {
    const noise = live
      ? Math.abs(Math.sin(t + i * 0.5)) * amp * 9 + Math.random() * amp * 3
      : 1.5
    const barH = Math.max(1.5, noise)
    const x = i * (barW + gap)
    const y = (h - barH) / 2
    // Centre bars brighter than the edges, so the waveform reads as one
    // shape rather than 48 separate ticks.
    const fade = 1 - Math.abs(i - bars / 2) / (bars / 2)
    ctx.globalAlpha = 0.35 + fade * 0.5
    ctx.fillStyle = hex
    ctx.fillRect(x, y, barW, barH)
  }
  ctx.globalAlpha = 1

  raf = requestAnimationFrame(draw)
}

function push() {
  haptic(30)
  emit('toast', accent.isListening ? 'Already listening' : 'Say "start", "stop" or "pairs"')
}

onMounted(() => { raf = requestAnimationFrame(draw) })
onUnmounted(() => { if (raf) cancelAnimationFrame(raf) })
</script>

<style scoped>
.vc-state {
  font-size: 9px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--cc-accent-text);
}

.vc-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 0;
  border: none;
  background: none;
  cursor: pointer;
}

.vc-canvas {
  width: 100%;
  height: 44px;
  display: block;
}

.vc-hint {
  font-size: 10px;
  letter-spacing: 0.08em;
  color: var(--cc-ink-4);
}
</style>
