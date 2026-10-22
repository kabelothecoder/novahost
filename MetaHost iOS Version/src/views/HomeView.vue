<template>
  <!--
    Home is now a HOST, not a screen.

    It owns the design-system root (`.cmd` plus the data attributes
    command.css keys its token sets off), the backdrop, and the session
    lifecycle. What the user actually sees is one of six layout
    components, swapped by `theme.layout`.

    `data-name-font` and `data-name-color` are here rather than among the
    :root runtime tokens theme.js writes, because their values reference
    --cc-font, --cc-ink and --cc-ink-3, which are declared on `.cmd` and
    do not exist at :root.

    Nothing that must survive a layout change lives here: run state is in
    stores/session.js, robot selection in stores/robot.js. That is the
    whole reason for the split — swapping layout unmounts and remounts
    the layout component, and anything held here would go with it.
  -->
  <div
    class="cmd home-host"
    :data-scheme="resolvedScheme"
    :data-card="theme.cardStyle"
    :data-motion="theme.motion"
    :data-name-font="theme.nameFont"
    :data-name-color="theme.nameColor"
  >
    <!--
      Home v4 (2026-10-22). Four layouts: Classic Core, Focus Engine and
      Full-Bleed share RobotHome; Command Core is its own HUD. Each paints
      its own full-screen ground, so the old HomeBackdrop is gone.

      Deliberately NOT wrapped in a <Transition>: an out-in transition is
      driven by rAF, which a backgrounded page throttles to nothing, and a
      layout switch at the wrong moment left Home blank. Swapping instantly
      cannot strand anyone.
    -->
    <CommandCore
      v-if="theme.layout === 'core'"
      key="core"
      @look="openInterface"
      @go="go"
      @toast="showToast"
    />
    <RobotHome
      v-else
      :key="theme.layout"
      :layout="v4Layout"
      @look="openInterface"
      @go="go"
      @toast="showToast"
    />

    <Transition name="toast">
      <div v-if="toast.visible" class="home-toast cc-thin" role="status" aria-live="polite">
        <span>{{ toast.text }}</span>
      </div>
    </Transition>

    <!-- Opened in place rather than by routing to Settings: every
         control in it changes what is on the screen behind it, so
         navigating away would hide the thing being adjusted. -->
    <InterfaceStyleSheet v-if="sheetOpen" @close="sheetOpen = false" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'

import { supabase } from '../utils/supabaseClient.ts'
import { theme, resolvedScheme } from '../stores/theme.js'
import { robot, ensureSeeded, refreshFleet } from '../stores/robot.js'
import { session, startSession, stopSession, pushEvent } from '../stores/session.js'
import { useSpeech } from '../composables/useSpeech.js'
import { startFleetPolling, stopFleetPolling } from '../services/fleetService.js'

import InterfaceStyleSheet from '../components/settings/InterfaceStyleSheet.vue'
import RobotHome from '../components/home/v4/RobotHome.vue'
import CommandCore from '../components/home/v4/CommandCore.vue'
import { startPositionsPolling, stopPositionsPolling } from '../stores/trades.js'

const router = useRouter()
const { speak } = useSpeech()

/* Statically imported: the layout is chosen before first paint from a
   stored preference. `bleed` is the stored id for Full-Bleed. */
const v4Layout = computed(() =>
  theme.layout === 'focus' ? 'focus' : theme.layout === 'classic' ? 'classic' : 'full')

/* ── Toast ───────────────────────────────────────────────────────── */
const toast = reactive({ visible: false, text: '' })
let toastTimer = null

function showToast(text, ms = 2600) {
  toast.text = text
  toast.visible = true
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => { toast.visible = false }, ms)
}

/* ── Navigation out of the layouts ───────────────────────────────── */
/* Home look opens over Home rather than routing away: every control in it
   changes what is on the screen behind it. Robot switching and the symbol
   status are sheets inside the layouts themselves. */
const sheetOpen = ref(false)
function openInterface() { sheetOpen.value = true }
function go(path) { if (path) router.push(path) }

/* ── Voice: removed ───────────────────────────────────────────────────
   `useVoiceCommands` opened a continuous SpeechRecognition session on
   every Home mount — it is what put the SAY "START", "STOP" OR "PAIRS"
   pill on the screen and what kept the orange mic indicator lit in the
   status bar for the whole session.

   The whole voice stack is out of the build until the rewritten version
   lands: this composable, the Voice Console widget, and /voice. The
   session actions it drove are unaffected — `startSession` / `stopSession`
   are still what the ignition control calls, and they were never voice's
   to own. Text-to-speech (`speak`) stays: it is the robot's confirmation
   of an action the user took by hand, not a microphone. */

/* ── MetaAPI: removed ─────────────────────────────────────────────────
   `setupMetaApi()` opened a streaming connection to MetaAPI on every Home
   mount, reading `METAAPI_TOKEN` / `METAAPI_ACCOUNT_ID` out of localStorage.

   MetaAPI is not this product's broker bridge and has not been since the
   MetaCopier migration — `test-broker-connection`, the endpoint that
   accompanied it, is a deployed tombstone whose own note reads "it depended
   on METAAPI_TOKEN, an integration this product no longer uses". Nothing
   wrote those two keys either, so the function returned at its own guard on
   every run.

   It was not free for being dead. The `import MetaApi from 'metaapi.cloud-sdk'`
   at the top of this file pulled the entire SDK into the Home chunk: **1,336 kB
   minified, 373 kB gzipped**, downloaded by every user on a phone, to reach a
   guard clause. Removing the import takes the chunk to a fraction of that.

   Broker state now comes from `license-status` via `startFleetPolling`, and
   the balance from the `broker-account` edge function. */

/* ── Session ─────────────────────────────────────────────────────────
   `displayName` is only used by the welcome toast — the doc's Home
   shows the ROBOT's name, not the user's, so it no longer appears in
   any layout.

   The old version assigned to `userAvatar`, which was declared as a
   `computed` and is therefore readonly: Vue warned and dropped every
   write. The profile avatar is not used by any layout, so the read is
   gone rather than repaired into something nothing displays. */
const displayName = ref('there')

async function checkUserSession() {
  try {
    const { data: { session: authSession } } = await supabase.auth.getSession()
    const user = authSession?.user
    if (!user) return
    displayName.value =
      user.user_metadata?.full_name || user.email?.split('@')[0] || 'there'
  } catch (err) {
    console.error('Check user session error:', err)
  }
}

/* ── Realtime ────────────────────────────────────────────────────────
   `broker_accounts` had a channel here too. That table is empty and no
   longer written — balance comes from the `broker-account` edge
   function — so the subscription could never fire and has been dropped
   rather than left as dead reassurance.

   The signals channel stays a NUDGE only: delivery is a claim-based
   poll (`claim-signals` + `signal_deliveries`), and acting on the
   realtime row directly would double-trade against the poll. */
let tradeChannel = null
let signalsChannel = null

function setupRealtime() {
  tradeChannel = supabase
    .channel('trade_logs_channel')
    .on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'trade_logs' }, () => {
      onTrade()
    })
    .subscribe()

  signalsChannel = supabase
    .channel('signals_push')
    .on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'signals' }, (payload) => {
      const s = payload.new
      if (Notification.permission === 'granted') {
        new Notification(`Signal · ${s.pair}`, {
          body: `${s.side || s.type} @ ${s.price ?? 'market'} | SL ${s.sl} | TP ${s.tp}`,
          icon: robot.avatarUrl || '/robot_avatar.png',
        })
      }
      pushEvent(`Signal in · ${s.side || s.type} ${s.pair}`, 'ok')
      onTrade()
      speak(`New signal on ${s.pair}.`)
    })
    .subscribe()
}

function onTrade() {
  if (navigator.vibrate) navigator.vibrate([100, 50, 100])
  showToast('TRADE EXECUTED')
}

/* ── Lifecycle ───────────────────────────────────────────────────── */
onMounted(() => {
  /* Seed first so the first paint has a row, then ask the server. The
     seed is a single locally-derived robot; refreshFleet replaces it with
     every licence this device holds, and keeps the seed if the read fails
     or comes back empty. */
  ensureSeeded()
  refreshFleet()
  checkUserSession()
  setupRealtime()
  startFleetPolling()
  /* Open-trade count feeds the status sheet and Command Core's last-action
     panel. It was started by the Open Trades widget, which v4 no longer has. */
  startPositionsPolling()

  const fresh = sessionStorage.getItem('MH_FRESH_ACTIVATION')
  if (fresh) {
    sessionStorage.removeItem('MH_FRESH_ACTIVATION')
    setTimeout(() => {
      if (navigator.vibrate) navigator.vibrate([30, 40, 60])
      showToast(`${robot.name.toUpperCase()} IS ONLINE`, 4000)
      speak(`Welcome ${displayName.value}. ${robot.name} is online.`)
    }, 600)
  }
  /* No "ROBOT · ARMED" toast on ordinary visits any more: it showed even
     while the robot was stopped, and the v4 status dot and power control
     already say whether it is running. */
})

onUnmounted(() => {
  clearTimeout(toastTimer)
  stopFleetPolling()
  stopPositionsPolling()
  if (tradeChannel)   supabase.removeChannel(tradeChannel)
  if (signalsChannel) supabase.removeChannel(signalsChannel)
})
</script>

<style scoped>
.home-host {
  position: relative;
  width: 100%;
  height: 100dvh;
  overflow-y: auto;
  overflow-x: hidden;
  -webkit-overflow-scrolling: touch;
}

/* ── Toast ───────────────────────────────────────────────────────── */
.home-toast {
  position: fixed;
  left: 50%;
  transform: translateX(-50%);
  /* Clears the top bar rather than guessing: 8px layout padding + the
     bar's own 44px + 12px breathing room. At 54px it overlapped the
     robot switcher on any device reporting a safe-area inset. */
  top: calc(var(--cc-safe-top) + 64px);
  z-index: 60;
  max-width: min(88vw, 380px);
  padding: 10px 18px;
  border-radius: var(--cc-r-chip);
  font-family: var(--cc-mono);
  font-size: 11px;
  letter-spacing: 0.10em;
  text-transform: uppercase;
  color: var(--cc-ink);
  text-align: center;
}

.toast-enter-active,
.toast-leave-active { transition: opacity 240ms var(--cc-ease), transform 240ms var(--cc-ease) }
.toast-enter-from,
.toast-leave-to     { opacity: 0; transform: translateX(-50%) translateY(-10px) }

/* The layout swap has no transition — see the note in the template. */
</style>
