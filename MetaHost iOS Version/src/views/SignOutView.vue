<template>
  <!--
    The doc's danger screen.

    Its rule, in the doc's own words: "It lists exactly what gets erased,
    then asks twice. No red button mid-scroll." So this is a route rather
    than a row in Settings — the list has to be readable before anything
    is armed.

    Ported honestly rather than literally. The doc's screen is "Remove
    broker & wipe data" and claims to erase broker credentials, trade
    history and every setting in one action. On iOS signing out clears
    the licence and session and nothing else, so the two lists are split:
    what this actually clears, and what deliberately stays. Erasing the
    rest is a separate, opt-in switch.
  -->
  <div
    class="cmd danger"
    :data-scheme="resolvedScheme"
    :data-card="theme.cardStyle"
    :data-motion="theme.motion"
  >
    <header class="dg-head">
      <button class="dg-back" aria-label="Back" @click="$router.back()">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <polyline points="15 18 9 12 15 6" />
        </svg>
      </button>
      <h1 class="cc-heading">Sign out</h1>
      <span class="dg-spacer"></span>
    </header>

    <div class="dg-body">
      <div class="dg-warn">
        <span class="dg-warn-icon" aria-hidden="true">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor"
               stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z" />
            <line x1="12" y1="9" x2="12" y2="13" /><line x1="12" y1="17" x2="12" y2="17" />
          </svg>
        </span>
        <div class="dg-warn-copy">
          <span class="dg-warn-title">This cannot be undone</span>
          <p class="cc-caption dg-warn-body">
            Signing out unbinds this device from your licence. You will need your
            licence key to get back in.
          </p>
        </div>
      </div>

      <span class="cc-label dg-section">What gets erased</span>
      <div class="dg-panel cc-card">
        <div v-for="item in erased" :key="item" class="dg-item">
          <span class="dg-mark dg-mark-out" aria-hidden="true">—</span>
          <span class="dg-item-text">{{ item }}</span>
        </div>
        <div v-if="alsoWipe" class="dg-item">
          <span class="dg-mark dg-mark-out" aria-hidden="true">—</span>
          <span class="dg-item-text">Every theme, layout and widget setting</span>
        </div>
        <div v-if="alsoWipe" class="dg-item">
          <span class="dg-mark dg-mark-out" aria-hidden="true">—</span>
          <span class="dg-item-text">Saved MetaAPI and ElevenLabs credentials</span>
        </div>
      </div>

      <span class="cc-label dg-section">What stays</span>
      <div class="dg-panel cc-card">
        <div v-for="item in kept" :key="item" class="dg-item">
          <span class="dg-mark dg-mark-keep" aria-hidden="true">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="20 6 9 17 4 12" />
            </svg>
          </span>
          <span class="dg-item-text">{{ item }}</span>
        </div>
      </div>

      <!-- The doc erases everything in one action. Here it is opt-in,
           because settings and credentials survive an ordinary sign-out
           and someone signing back in on the same handset expects their
           screen to look the way they left it. -->
      <div class="dg-panel cc-card dg-opt">
        <div class="dg-row">
          <span class="dg-copy">
            <span class="dg-name">Also erase settings and credentials</span>
            <span class="cc-caption">Wipes this device back to a fresh install</span>
          </span>
          <button
            class="dg-switch"
            :class="{ 'is-on': alsoWipe }"
            role="switch"
            :aria-checked="alsoWipe"
            aria-label="Also erase settings and credentials"
            @click="toggleWipe"
          ><span class="dg-knob"></span></button>
        </div>
      </div>

      <!-- Asks twice. The second tap is a different label on the same
           control rather than an alert, so what is about to happen stays
           on screen next to the list describing it. -->
      <button class="dg-go" :class="{ 'is-armed': armed }" @click="press">
        {{ armed ? 'Tap again to sign out' : 'Sign out of this device' }}
      </button>
      <p v-if="armed" class="cc-caption dg-cancel-hint">
        <button class="dg-cancel" @click="armed = false">Cancel</button>
      </p>

      <p class="cc-caption dg-foot">
        Your licence key stays valid — you can activate this device again at any
        time.
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref, onBeforeUnmount } from 'vue'
import { theme, resolvedScheme, clearPreferences } from '../stores/theme.js'
import { logout } from '../services/authService.js'
import { haptic } from '../composables/useSpeech.js'

const armed = ref(false)
const alsoWipe = ref(false)

/* The exact set logout() clears, written out rather than summarised —
   the doc's point is that the screen says what happens, and "your
   session" does not tell anyone whether their licence key is gone. */
const erased = [
  'Your licence key and its activation on this device',
  'The verified email and session token stored here',
  /* Named separately because it is a capability, not bookkeeping: until the
     app next reaches the server it will not open at all, where before signing
     out it would have run offline for up to five more days. */
  'Your offline access — the app will need a connection to unlock again',
]

const kept = [
  'Your licence itself — it stays valid on the server',
  'This install’s device ID, so signing back in is not a device move',
]

/* Credentials are plain localStorage, not secStorage: they are written
   by Settings with localStorage directly, so they have to be named here.
   The theme keys are NOT listed — stores/theme.js owns that list and
   clears it itself, because a copy here would go stale the first time a
   setting is added and the erase would silently miss it. */
const CREDENTIAL_KEYS = ['METAAPI_TOKEN', 'METAAPI_ACCOUNT_ID', 'ELEVENLABS_API_KEY']

/* Disarms when the terms change. Arming, then switching the wipe on,
   then tapping again would otherwise run a bigger erase than the one
   that was confirmed. */
function toggleWipe() {
  alsoWipe.value = !alsoWipe.value
  armed.value = false
  haptic(20)
}

/* Arming expires. A screen left open in a pocket should not still be one
   tap from a wipe when it comes back out. */
let timer = null
function press() {
  if (!armed.value) {
    armed.value = true
    haptic(30)
    clearTimeout(timer)
    timer = setTimeout(() => { armed.value = false }, 8000)
    return
  }
  clearTimeout(timer)
  haptic(60)
  signOut()
}
onBeforeUnmount(() => clearTimeout(timer))

function signOut() {
  logout()

  if (alsoWipe.value) {
    clearPreferences()
    for (const k of CREDENTIAL_KEYS) {
      try { localStorage.removeItem(k) } catch (err) { console.warn('Clear credential:', err) }
    }
  }

  /* A hard reload, not router.replace. Every preference is held in a
     reactive store whose watchers re-persist on change, so a running app
     would write some of what was just erased straight back. Reloading is
     the only way the next screen is genuinely a fresh start.

     assign(), not replace(): a wipe should not also destroy the back
     button's history for the tab. */
  window.location.assign(`${window.location.pathname}#/unlock`)
  window.location.reload()
}
</script>

<style scoped>
.danger {
  width: 100%;
  height: 100dvh;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  background: var(--cc-canvas);
}

.dg-head {
  position: sticky;
  top: 0;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: calc(var(--cc-safe-top) + 12px) 16px 12px;
  background: var(--cc-canvas);
  border-bottom: 1px solid var(--cc-border-soft);
}
.dg-back {
  display: grid;
  place-items: center;
  width: 36px; height: 36px;
  flex: none;
  border: none;
  border-radius: 50%;
  background: var(--cc-card-2);
  color: var(--cc-ink-2);
  cursor: pointer;
}
.dg-head h1 { margin: 0; flex: 1 }
/* Balances the back button so the title sits optically centred. */
.dg-spacer { width: 36px; flex: none }

.dg-body {
  padding: 16px 16px calc(var(--cc-safe-bottom) + 40px);
  display: flex;
  flex-direction: column;
}

.dg-section { margin: 20px 6px 9px }

/* ── Warning ─────────────────────────────────────────────────────────
   Tinted with the sell token rather than a bespoke red: it is already
   measured in both schemes, and a second red would be a second thing to
   keep in step. */
/* Deliberately NOT .cc-card. Those rules are written as
   `.cmd[data-card="glass"] .cc-card` — (0,3,0) — and a scoped class here
   is only (0,2,0), so the glass fill and its white rim won this element
   outright and the alert rendered as an ordinary white panel with no red
   on it at all. It is an alert, not a data card: it carries its own
   surface and only borrows the elevation. */
.dg-warn {
  display: flex;
  gap: 13px;
  padding: 15px 16px;
  border-radius: var(--cc-r-card-lg);
  background: var(--cc-sell-dim);
  border: 1px solid var(--cc-sell);
  box-shadow: var(--cc-shadow-card);
}
.dg-warn-icon { flex: none; color: var(--cc-sell-text); display: grid; place-items: center; padding-top: 1px }
.dg-warn-copy { min-width: 0 }
.dg-warn-title { display: block; font-size: 15px; font-weight: 600; color: var(--cc-ink) }
.dg-warn-body { margin: 3px 0 0 }

/* ── Lists ───────────────────────────────────────────────────────── */
.dg-panel {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: var(--cc-r-card-lg);
}
.dg-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 13px 16px;
}
.dg-item + .dg-item { border-top: 1px solid var(--cc-border-soft) }
.dg-item-text { font-size: 14px; line-height: 20px; color: var(--cc-ink-2) }

.dg-mark {
  flex: none;
  display: grid;
  place-items: center;
  width: 20px; height: 20px;
  font-size: 15px;
}
.dg-mark-out  { color: var(--cc-sell-text) }
.dg-mark-keep { color: var(--cc-buy) }

/* ── Opt-in wipe ─────────────────────────────────────────────────── */
.dg-opt { margin-top: 18px }
.dg-row { display: flex; align-items: center; gap: 12px; padding: 14px 16px }
.dg-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px }
.dg-name { font-size: 15px; font-weight: 500; color: var(--cc-ink) }

.dg-switch {
  flex: none;
  position: relative;
  width: 46px; height: 28px;
  padding: 0;
  border: 1px solid var(--cc-border);
  border-radius: var(--cc-r-chip);
  background: var(--cc-track);
  cursor: pointer;
}
.dg-switch.is-on { background: var(--cc-sell); border-color: var(--cc-sell) }
.dg-knob {
  position: absolute;
  top: 2px; left: 2px;
  width: 22px; height: 22px;
  border-radius: 50%;
  background: var(--cc-ink);
  /* transform only — a colour transition here reads a scheme token and
     would hold the old scheme's value after a swap. */
  transition: transform 200ms var(--cc-spring);
}
.dg-switch.is-on .dg-knob { transform: translateX(18px); background: var(--cc-sell-ink) }

/* ── Action ──────────────────────────────────────────────────────── */
.dg-go {
  margin-top: 24px;
  width: 100%;
  padding: 16px;
  border: 1px solid var(--cc-sell);
  border-radius: var(--cc-r-chip);
  background: var(--cc-sell-dim);
  color: var(--cc-sell-text);
  font-family: var(--cc-font);
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.02em;
  cursor: pointer;
  transition: background 200ms var(--cc-ease), color 200ms var(--cc-ease);
}
/* Armed is the filled state: the second tap is the destructive one and
   should not look identical to the first. The ink is --cc-sell-ink, not
   white — measured, white on the dark sell hue is only 3.79:1, so dark
   takes black-ish ink and light takes white. */
.dg-go.is-armed {
  background: var(--cc-sell);
  color: var(--cc-sell-ink);
}

.dg-cancel-hint { margin: 12px 0 0; text-align: center }
.dg-cancel {
  border: none;
  background: none;
  font-family: var(--cc-font);
  font-size: 13px;
  color: var(--cc-ink-3);
  text-decoration: underline;
  cursor: pointer;
}

.dg-foot { margin: 20px 4px 0; text-align: center }
</style>
