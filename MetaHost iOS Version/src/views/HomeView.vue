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
    <HomeBackdrop />

    <!--
      Deliberately NOT wrapped in a <Transition>.

      It was `mode="out-in"`, which mounts the incoming layout only once
      the outgoing one's leave has finished. Vue drives that with
      requestAnimationFrame, and rAF is throttled to nothing in a
      backgrounded or occluded page — so a layout switch made at the
      wrong moment leaves BOTH layouts unmounted and Home renders as an
      empty screen over the backdrop. Observed exactly that.

      A layout change is a deliberate action taken from the Interface
      Style sheet, and swapping instantly cannot strand anyone. The
      backdrop persists across the change, which carries the continuity
      a cross-fade would have.
    -->
    <component
      :is="LayoutComponent"
      :key="theme.layout"
      @interface="openInterface"
      @robots="openRobots"
      @assets="openAssets"
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
import { robot, ensureSeeded } from '../stores/robot.js'
import { session, startSession, stopSession, pushEvent } from '../stores/session.js'
import { useSpeech } from '../composables/useSpeech.js'
import { useVoiceCommands } from '../composables/useVoiceCommands.js'
import { startFleetPolling, stopFleetPolling } from '../services/fleetService.js'

import HomeBackdrop from '../components/home/HomeBackdrop.vue'
import InterfaceStyleSheet from '../components/settings/InterfaceStyleSheet.vue'
import ClassicCore from '../components/home/layouts/ClassicCore.vue'
import FocusEngine from '../components/home/layouts/FocusEngine.vue'
import FullBleedArt from '../components/home/layouts/FullBleedArt.vue'
import NeonFrame from '../components/home/layouts/NeonFrame.vue'
import SignalFeed from '../components/home/layouts/SignalFeed.vue'
import CommandDeck from '../components/home/layouts/CommandDeck.vue'

const router = useRouter()
const { speak } = useSpeech()

/* ── Layout registry ──────────────────────────────────────────────────
   Statically imported, not async: the layout is chosen before first
   paint from a stored preference, so lazy-loading it would show an
   empty Home on every cold start. The widgets inside them ARE async —
   that is where the weight is.

   An unknown id falls back rather than rendering nothing; a blank Home
   is indistinguishable from a crash. */
const LAYOUTS = {
  classic: ClassicCore,
  focus:   FocusEngine,
  bleed:   FullBleedArt,
  neon:    NeonFrame,
  feed:    SignalFeed,
  deck:    CommandDeck,
}
const LayoutComponent = computed(() => LAYOUTS[theme.layout] || FullBleedArt)

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
const sheetOpen = ref(false)
function openInterface() { sheetOpen.value = true }
function openRobots()    { showToast('Robot switching arrives with the Robot List widget') }
// The real Asset Hub is a per-symbol sheet over `license_symbol_config`
// and lands with the data wiring. Quotes is the honest destination for
// now — TradingSymbols.vue is mock data, not a hub.
function openAssets()    { router.push('/pairs') }

/* ── Voice commands — Home-level, independent of the widget ──────── */
useVoiceCommands({
  onStart: () => { if (!session.isRunning) { startSession(); showToast('Order received · START'); speak('Order received. Starting.') } },
  onStop:  () => { if (session.isRunning)  { stopSession();  showToast('Order received · STOP');  speak('Order received. Stopping.') } },
  onPairs: () => router.push('/pairs'),
})

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
  ensureSeeded()
  checkUserSession()
  setupRealtime()
  startFleetPolling()

  const fresh = sessionStorage.getItem('MH_FRESH_ACTIVATION')
  if (fresh) {
    sessionStorage.removeItem('MH_FRESH_ACTIVATION')
    setTimeout(() => {
      if (navigator.vibrate) navigator.vibrate([30, 40, 60])
      showToast(`${robot.name.toUpperCase()} IS ONLINE`, 4000)
      speak(`Welcome ${displayName.value}. ${robot.name} is online.`)
    }, 600)
  } else {
    setTimeout(() => {
      if (navigator.vibrate) navigator.vibrate(20)
      showToast(`${robot.name.toUpperCase()} · ARMED`, 2800)
    }, 500)
  }
})

onUnmounted(() => {
  clearTimeout(toastTimer)
  stopFleetPolling()
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
