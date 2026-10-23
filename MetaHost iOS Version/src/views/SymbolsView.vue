<template>
  <!--
    TRADING SYMBOLS — step two of the connection flow.

    The Broker Terminal links the account; this screen sets what that account
    trades, under which broker names, at what risk. Opened from the Trading
    symbols button on Home. With no linked account there is nothing to match
    against, so it sends the user to link one first.
  -->
  <div
    class="cmd syv"
    :data-scheme="resolvedScheme"
    :data-card="theme.cardStyle"
    :data-motion="theme.motion"
  >
    <div class="syv-inner">
      <!-- The wizard carries its own back and progress bar; this header is
           only for the states around it. -->
      <div v-if="!showSetup" class="syv-top">
        <button class="syv-back" aria-label="Back" @click="$router.push('/home')">
          <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor"
               stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <polyline points="15 18 9 12 15 6" />
          </svg>
        </button>
        <span class="cc-label">Trading symbols</span>
        <span class="syv-spacer" aria-hidden="true"></span>
      </div>

      <div v-if="!checked && !mockSetup" class="syv-wait"><span class="syv-spin"></span>Checking your broker link…</div>

      <SymbolSetup v-else-if="showSetup" :mock="mockSetup" @relink="$router.push('/terminal')" />

      <div v-else class="syv-empty cc-card">
        <span class="cc-label">Step 1 first</span>
        <h1 class="syv-h">Connect your broker account</h1>
        <p class="cc-caption">
          Symbols are matched against your broker's own list, so the account has
          to be linked before they can be set up.
        </p>
        <button class="syv-cta" @click="$router.push('/terminal')">Connect account</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { theme, resolvedScheme } from '../stores/theme.js'
import { loadLinkState } from '../services/brokerService.js'
import SymbolSetup from '../components/terminal/symbols/SymbolSetup.vue'

/* `?symbols=mock` (dev only) shows the setup against three made-up accounts,
   so every state can be reviewed without a linked broker. */
const route = useRoute()
const mockSetup = import.meta.env.DEV && route.query.symbols === 'mock'

/* Tri-state: null is "couldn't tell", which is not the same as unlinked —
   only a definite `false` sends the user back to step one. SymbolSetup has
   its own disconnected and load-failed states for the rest. */
const linked = ref(null)
const checked = ref(false)
const showSetup = computed(() => mockSetup || (checked.value && linked.value !== false))

onMounted(async () => {
  try {
    const s = await loadLinkState()
    linked.value = s?.linked ?? null
  } catch {
    linked.value = null
  } finally {
    checked.value = true
  }
})
</script>

<style scoped>
/* height, NOT min-height: the app shell clips, this element must scroll. */
.syv {
  position: relative;
  width: 100%;
  height: 100dvh;
  overflow-y: auto;
  overflow-x: hidden;
  -webkit-overflow-scrolling: touch;
  background: var(--cc-canvas);
}
.syv-inner {
  display: flex;
  flex-direction: column;
  padding: calc(var(--cc-safe-top) + 10px) 22px calc(var(--cc-safe-bottom) + 118px);
}
.syv-top { display: flex; align-items: center; justify-content: space-between; gap: 12px; height: 44px; }
.syv-back {
  display: grid; place-items: center; width: 38px; height: 38px; border: none; background: none;
  border-radius: var(--cc-r-chip); color: var(--cc-ink-3); cursor: pointer;
  transition: transform 180ms var(--cc-spring);
}
.syv-back:active { transform: scale(0.92) }
.syv-spacer { width: 38px; }

.syv-wait { display: flex; align-items: center; justify-content: center; gap: 10px; padding: 60px 0; font-size: 14px; color: var(--cc-ink-3); }
.syv-spin { width: 16px; height: 16px; border-radius: 50%; border: 2px solid rgba(255,255,255,.15); border-top-color: var(--cc-accent); animation: syvSpin .8s linear infinite; }
@keyframes syvSpin { to { transform: rotate(360deg) } }

.syv-empty { margin-top: 28px; padding: 22px 20px; display: flex; flex-direction: column; gap: 10px; }
.syv-h { margin: 0; font-family: var(--cc-font); font-size: 22px; font-weight: 600; color: var(--cc-ink); }
.syv-cta {
  margin-top: 10px; height: 52px; border: 1.5px solid var(--cc-accent); border-radius: var(--cc-r-chip); cursor: pointer;
  background: rgba(var(--cc-accent-rgb), 0.10); color: var(--cc-accent-text); box-shadow: var(--cc-glow-halo);
  font-family: var(--cc-font); font-size: 14px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase;
  transition: transform 200ms var(--cc-spring);
}
.syv-cta:active { transform: scale(.98) }
</style>
