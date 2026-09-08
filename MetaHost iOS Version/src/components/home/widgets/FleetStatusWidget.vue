<template>
  <WidgetCard label="Fleet Status">
    <div class="fleet">
      <div v-for="s in stats" :key="s.k" class="fleet-stat">
        <span class="fleet-k cc-label">{{ s.k }}</span>
        <span class="fleet-v cc-figure" :class="s.tone">{{ s.v }}</span>
      </div>
    </div>
  </WidgetCard>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import WidgetCard from '../WidgetCard.vue'
import { session, uptimeMs, formatUptime, beatState } from '../../../stores/session.js'

/* Uptime is derived from `startedAt`, so it needs a clock to re-render
   against — without this the figure freezes at whatever it was when the
   widget mounted. One second is the smallest unit the format shows. */
const now = ref(Date.now())
let timer = null
onMounted(() => { timer = setInterval(() => { now.value = Date.now() }, 1000) })
onUnmounted(() => clearInterval(timer))

/**
 * Every figure reports what is actually known. Where nothing has been
 * measured the tile shows an em dash rather than a zero — "0ms" and
 * "0/0" read as measurements, and a screen that invents telemetry is
 * worse than one that admits it has none.
 *
 * The doc's four tiles are UPTIME / PING / BOTS / BEAT. BOTS is replaced
 * by BROKER here: a device count needs `device_activations` counted
 * server-side, which nothing on this platform can reach without also
 * executing signals (see services/fleetService.js), so it would have
 * been a permanent em dash. The broker link IS known, and matters more
 * to someone deciding whether to hit START.
 *
 * BEAT is kept and stays unknown for the same reason — it is the one
 * figure that means "the server knows this device is alive", and
 * claiming to know that without the server-side record would be the
 * exact lie this widget exists to avoid.
 */
const stats = computed(() => {
  // Referenced so the interval above invalidates this computed.
  void now.value

  const beat = beatState()

  return [
    {
      k: 'Uptime',
      v: session.isRunning ? formatUptime(uptimeMs()) : '—',
      tone: session.isRunning ? 'is-live' : '',
    },
    {
      k: 'Ping',
      v: session.pingMs == null ? '—' : `${session.pingMs}ms`,
      tone: session.pingMs != null && session.pingMs > 400 ? 'is-warn' : '',
    },
    {
      k: 'Broker',
      v: session.brokerConnected ? 'Linked' : 'None',
      tone: session.brokerConnected ? 'is-live' : '',
    },
    {
      k: 'Beat',
      v: beat === 'ok' ? 'OK' : beat === 'stale' ? 'STALE' : '—',
      tone: beat === 'ok' ? 'is-live' : beat === 'stale' ? 'is-warn' : '',
    },
  ]
})
</script>

<style scoped>
.fleet {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}

.fleet-stat {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
}

.fleet-k {
  font-size: 9px;
  letter-spacing: 0.14em;
}

.fleet-v {
  font-size: 15px;
  font-weight: 600;
  color: var(--cc-ink);
  /* Four figures on a 375pt phone; a long uptime must shrink rather
     than push the grid wider than the card. */
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.fleet-v.is-live { color: var(--cc-live) }
.fleet-v.is-warn { color: var(--cc-warn) }
</style>
