<template>
  <!-- A spoken proposal waiting for a tap. Never acts on its own: a trade
       idea dies at its expiry, the kill switch waits for Confirm. -->
  <Transition name="vac">
    <div v-if="pending" class="vac" :style="{ '--a': accent }" role="dialog" aria-live="assertive">
      <template v-if="pending.type === 'trade_idea'">
        <div class="vac-top">
          <span class="vac-tag" :class="idea.side === 'BUY' ? 'buy' : 'sell'">{{ idea.side }}</span>
          <span class="vac-pair">{{ idea.pair }}</span>
          <span class="vac-time">{{ left }}s</span>
        </div>
        <div class="vac-row">
          <span>Lot <b>{{ idea.volume ?? 'Your plan' }}</b></span>
          <span>SL <b>{{ idea.sl ?? '—' }}</b></span>
          <span>TP <b>{{ idea.tp ?? '—' }}</b></span>
        </div>
        <div v-if="idea.reason" class="vac-why">{{ idea.reason }}</div>
        <div class="vac-bar"><div :style="{ width: (left / total * 100) + '%' }"></div></div>
      </template>
      <template v-else>
        <div class="vac-top"><span class="ms vac-warn">warning</span><span class="vac-pair">Close every open trade?</span></div>
        <div class="vac-why">This closes all positions on your account, including ones you opened yourself.</div>
      </template>
      <div class="vac-btns">
        <button class="vac-no" @click="$emit('dismiss')">Cancel</button>
        <button class="vac-yes" :class="{ danger: pending.type === 'kill_switch' }" :disabled="busy || left <= 0" @click="$emit('confirm')">
          {{ busy ? 'Sending…' : pending.type === 'kill_switch' ? 'Close all' : 'Confirm trade' }}
        </button>
      </div>
    </div>
  </Transition>
</template>

<script setup>
import { ref, computed, watch, onUnmounted } from 'vue'

const props = defineProps({ pending: Object, busy: Boolean, accent: { type: String, default: '#F2B53A' } })
const emit = defineEmits(['confirm', 'dismiss'])

const idea = computed(() => props.pending?.idea || {})
const total = ref(30)
const left = ref(30)
let t = 0
watch(() => props.pending, (p) => {
  clearInterval(t)
  if (p?.type !== 'trade_idea') { left.value = 1; return }
  const end = new Date(p.idea.expires_at).getTime()
  total.value = Math.max(1, Math.round((end - Date.now()) / 1000))
  const tick = () => {
    left.value = Math.max(0, Math.round((end - Date.now()) / 1000))
    if (left.value <= 0) { clearInterval(t); setTimeout(() => emit('dismiss'), 600) }
  }
  tick(); t = setInterval(tick, 250)
}, { immediate: true })
onUnmounted(() => clearInterval(t))
</script>

<style scoped>
.ms { font-family: 'Material Symbols Outlined'; font-weight: 400; font-style: normal; line-height: 1; letter-spacing: normal; font-feature-settings: 'liga'; }
.vac { position: fixed; left: 16px; right: 16px; bottom: calc(24px + env(safe-area-inset-bottom)); z-index: 60; max-width: 420px; margin: 0 auto; padding: 16px; border-radius: 24px; background: rgba(14,14,19,.94); border: 1px solid color-mix(in srgb, var(--a) 45%, transparent); backdrop-filter: blur(18px); -webkit-backdrop-filter: blur(18px); color: #E6E6EA; box-shadow: 0 18px 50px rgba(0,0,0,.5); }
.vac-top { display: flex; align-items: center; gap: 10px; }
.vac-tag { font: 700 12px/1 system-ui; padding: 6px 10px; border-radius: 9999px; }
.vac-tag.buy { background: rgba(52,199,123,.18); color: #4FD99A; }
.vac-tag.sell { background: rgba(255,93,93,.18); color: #FF8A8A; }
.vac-pair { font: 600 17px/1.2 system-ui; flex: 1; }
.vac-time { font: 600 14px/1 ui-monospace, monospace; color: var(--a); }
.vac-warn { color: #FF8A8A; font-size: 22px; }
.vac-row { display: flex; gap: 16px; margin-top: 12px; font-size: 13px; color: #A6A6B0; }
.vac-row b { color: #E6E6EA; font-weight: 600; }
.vac-why { margin-top: 8px; font-size: 13px; line-height: 18px; color: #A6A6B0; }
.vac-bar { margin-top: 12px; height: 3px; border-radius: 9999px; background: rgba(255,255,255,.08); overflow: hidden; }
.vac-bar div { height: 100%; background: var(--a); transition: width .25s linear; }
.vac-btns { display: flex; gap: 10px; margin-top: 14px; }
.vac-btns button { flex: 1; height: 46px; border-radius: 9999px; border: 0; font: 600 15px/1 system-ui; }
.vac-no { background: rgba(255,255,255,.08); color: #E6E6EA; }
.vac-yes { background: var(--a); color: #0B0B0E; }
.vac-yes.danger { background: #FF5D5D; color: #fff; }
.vac-yes:disabled { opacity: .5; }
.vac-enter-active, .vac-leave-active { transition: transform .3s cubic-bezier(.2,.9,.3,1.2), opacity .25s; }
.vac-enter-from, .vac-leave-to { transform: translateY(30px); opacity: 0; }
</style>
