<template>
  <!--
    BROKER TERMINAL.

    Redesigned against the Android screen (MetaTraderConnectScreen.kt), which
    is the design of record for this flow: hero mark, title, platform toggle,
    one credentials card, one full-width commit. What is NOT ported is the
    Android build's hardcoded yellow — every accent here comes from the
    Command Center tokens, so the screen wears the mentor's colour like the
    rest of the app rather than a second brand.

    The old screen's decorations are gone with it. The three-dot Server /
    Account / Data Feed strip restated one fact three times and none of it
    louder than the badge at the top; the full-screen "Establishing Neural
    Link" overlay blanked the page for what can be a two-minute wait and could
    only ever say "working", with the outcome arriving later in a message the
    user had stopped watching for. The result now lands under the button that
    started it.
  -->
  <div
    class="cmd mtv"
    :data-scheme="resolvedScheme"
    :data-card="theme.cardStyle"
    :data-motion="theme.motion"
  >
    <div class="mtv-wash" aria-hidden="true"></div>

    <div class="mtv-inner">
      <!-- ── Chrome ─────────────────────────────────────────────── -->
      <div class="mtv-top">
        <button class="mtv-back" aria-label="Back" @click="$router.push('/home')">
          <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor"
               stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <polyline points="15 18 9 12 15 6" />
          </svg>
        </button>

        <span class="mtv-badge cc-thin" :data-state="badge.state">
          <span class="cc-dot" :class="badge.state === 'live' ? 'cc-dot-live' : ''"></span>
          {{ badge.text }}
        </span>
      </div>

      <!-- ── Hero ───────────────────────────────────────────────── -->
      <div class="mtv-hero">
        <span class="mtv-hero-glow" aria-hidden="true"></span>
        <img src="/mt_logo.png" class="mtv-hero-img" alt="MetaTrader" decoding="async" />
      </div>

      <h1 class="mtv-title">Broker Terminal</h1>
      <p class="mtv-sub">Direct bridge to your broker's execution nodes.</p>

      <!-- ── Platform ───────────────────────────────────────────── -->
      <div class="mtv-seg" role="tablist" aria-label="Platform">
        <button
          v-for="p in PLATFORMS"
          :key="p"
          class="mtv-seg-btn"
          :class="{ 'is-on': form.platform === p }"
          role="tab"
          :aria-selected="form.platform === p"
          @click="pickPlatform(p)"
        >{{ p }}</button>
      </div>

      <!-- ── Credentials ────────────────────────────────────────── -->
      <form class="mtv-card cc-card" @submit.prevent="doConnect">
        <label class="mtv-field">
          <span class="cc-label">Broker server</span>
          <input
            v-model="form.server"
            type="text"
            class="mtv-input cc-recess"
            placeholder="Exness-MT5Real8"
            autocomplete="off"
            autocapitalize="off"
            spellcheck="false"
          />
          <span class="cc-caption">Exactly as shown in MetaTrader</span>
        </label>

        <label class="mtv-field">
          <span class="cc-label">Login</span>
          <input
            v-model="form.account"
            type="text"
            inputmode="numeric"
            class="mtv-input cc-recess"
            placeholder="Account number"
            autocomplete="off"
          />
        </label>

        <label class="mtv-field">
          <span class="cc-label">Password</span>
          <span class="mtv-pwd">
            <input
              v-model="form.password"
              :type="showPwd ? 'text' : 'password'"
              class="mtv-input cc-recess"
              placeholder="••••••••"
              autocomplete="current-password"
            />
            <button
              type="button"
              class="mtv-eye"
              :aria-label="showPwd ? 'Hide password' : 'Show password'"
              @click="showPwd = !showPwd"
            >
              <svg v-if="!showPwd" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                   stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" /><circle cx="12" cy="12" r="3" />
              </svg>
              <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                   stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24" />
                <line x1="1" y1="1" x2="23" y2="23" />
              </svg>
            </button>
          </span>
          <span class="cc-caption">From your broker email or {{ form.platform }} terminal settings</span>
        </label>
      </form>

      <!-- ── Commit ─────────────────────────────────────────────── -->
      <button class="mtv-connect" :disabled="connecting || !canSubmit" @click="doConnect">
        <svg v-if="!connecting" width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71" />
          <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71" />
        </svg>
        <span v-else class="mtv-spin" aria-hidden="true"></span>
        <span>{{ connecting ? 'Connecting' : link.linked ? 'Reconnect account' : 'Connect account' }}</span>
      </button>

      <!--
        The whole attempt, in the one place the user is already looking. It
        replaces a full-screen overlay plus a toast that arrived after they
        had stopped watching for it.
      -->
      <p v-if="connError" class="mtv-msg is-error">{{ connError }}</p>
      <p v-else-if="connNote" class="mtv-msg is-note">{{ connNote }}</p>
      <p v-else-if="connecting" class="mtv-msg">
        Handing your login to the broker. This can take up to two minutes.
      </p>

      <!-- ── Linked account ─────────────────────────────────────── -->
      <div v-if="link.linked" class="mtv-linked cc-card">
        <div class="mtv-linked-head">
          <span class="cc-label">Linked account</span>
          <span class="cc-chip" :class="link.connecting ? '' : 'cc-chip-accent'">
            {{ link.connecting ? 'Linking' : 'Live' }}
          </span>
        </div>
        <div class="mtv-linked-row">
          <span class="cc-dot" :class="link.connecting ? '' : 'cc-dot-live'"></span>
          <span class="mtv-linked-name">{{ link.server || 'Broker account' }}</span>
          <span class="mtv-linked-meta cc-mono">
            {{ link.platform || form.platform }}<template v-if="connectedLabel"> · {{ connectedLabel }}</template>
          </span>
        </div>
        <p class="cc-caption mtv-linked-note">
          Connecting a different account replaces this one. Your broker password
          is sent once to establish the link and is never stored on this device.
        </p>
      </div>

      <p v-else class="cc-caption mtv-foot">
        Your password is sent once, to your broker, to establish the link. It is
        never written to this device.
      </p>
    </div>
  </div>
</template>

<script setup>
/**
 * Broker linking.
 *
 * ## What changed, and why none of it was cosmetic
 *
 * This screen used to POST to `test-broker-connection`. That endpoint is a
 * tombstone returning 410 — "Broker linking runs through metacopier-connect"
 * — so **every connect on iOS failed**, and had since the MetaAPI bridge was
 * removed. The three faults that travelled with it:
 *
 *   1. No `license_key` was sent. The link is recorded on the LICENCE, so
 *      there was nothing to bind to even had the endpoint lived.
 *   2. The broker **password** was written to device storage as
 *      `MH_TERMINAL_CONFIG`, and nothing ever read it back. It is now handed
 *      to the edge function and goes out of scope with the call. The old key
 *      is actively erased below, because a fix that leaves the password on
 *      disk for every existing install is not a fix.
 *   3. `sessions` was seeded with a hardcoded row — `Exness-MT5Real` /
 *      `84912347` — so a user who had connected nothing saw a green LIVE pill
 *      against somebody else's account number.
 *
 * Link state is READ from the licence rather than remembered on the device,
 * which is why a reinstall, or an account linked from the Android handset,
 * both render correctly.
 */
import { ref, computed, onMounted } from 'vue'
import { secStorage } from '../utils/secStorage.js'
import { theme, resolvedScheme } from '../stores/theme.js'
import { accent } from '../stores/accent.js'
import { session, pushEvent } from '../stores/session.js'
import { linkBroker, loadLinkState, loadAccount } from '../services/brokerService.js'
import { haptic } from '../composables/useSpeech.js'

const PLATFORMS = ['MT4', 'MT5']

const form = ref({ server: '', account: '', password: '', platform: 'MT5' })
const showPwd = ref(false)
const connecting = ref(false)
const connError = ref('')
const connNote = ref('')

/** `linked: null` means the status read failed — unknown, not disconnected. */
const link = ref({ linked: false, connecting: false, server: null, platform: null, connectedAt: null })

const canSubmit = computed(() =>
  Boolean(form.value.server.trim() && String(form.value.account).trim() && form.value.password)
)

/**
 * The one status readout on the screen.
 *
 * Four states, not two. A broker MetaCopier is still dialling is neither
 * connected nor disconnected, and calling it either one is a claim the user
 * acts on — they either start trading against a session that is not up, or
 * they re-enter credentials that were already accepted. A failed status READ
 * is a fourth thing again, and saying "not connected" because we could not
 * ask would be the worst of the four.
 */
const badge = computed(() => {
  if (link.value.linked === null) return { state: 'unknown', text: 'Status unknown' }
  if (!link.value.linked)         return { state: 'off',     text: 'Not connected' }
  if (link.value.connecting)      return { state: 'wait',    text: 'Linking' }
  return { state: 'live', text: 'Connected' }
})

const connectedLabel = computed(() => {
  if (!link.value.connectedAt) return ''
  const d = new Date(link.value.connectedAt)
  return Number.isNaN(d.getTime())
    ? ''
    : d.toLocaleDateString(undefined, { day: 'numeric', month: 'short' })
})

/**
 * One-time cleanup of the credential the old code left behind.
 *
 * Every install that ever completed the old flow still has a broker password
 * sitting in localStorage under the app's prefix. Removing the write without
 * removing the stored value would leave it there indefinitely, so this runs
 * on mount regardless of whether the user connects anything today.
 */
function purgeLegacyCredentials() {
  if (secStorage.getItem('MH_TERMINAL_CONFIG')) {
    secStorage.removeItem('MH_TERMINAL_CONFIG')
    console.info('[terminal] removed legacy stored broker credentials')
  }
  // The fake seeded session lived here too.
  secStorage.removeItem('MH_SESSIONS')
}

function pickPlatform(p) {
  if (form.value.platform === p) return
  form.value.platform = p
  haptic(15)
}

async function refreshLink() {
  const state = await loadLinkState()
  link.value = state

  const up = state.linked === true && !state.connecting

  accent.brokerConnected = up
  session.brokerConnected = up
  session.brokerServer = state.server || null

  /* The balance is only worth asking for once an account is actually
     attached, and only the linked branch can answer. */
  if (up) {
    const acct = await loadAccount()
    if (acct.ok && acct.balance != null) {
      accent.brokerBalance = acct.balance
      /* Currency travels WITH the figure. `session.brokerCurrency` defaults to
         'USD', and PairsView seeds its risk calculator from both — so an
         account holding R596 whose currency never arrives gets sized as
         dollars, about eighteen times too large. Writing it is not optional. */
      if (acct.currency) session.brokerCurrency = acct.currency
    }
  }
}

async function doConnect() {
  if (!canSubmit.value) {
    connError.value = 'Fill in the server, login and password.'
    return
  }
  connError.value = ''
  connNote.value = ''
  connecting.value = true
  haptic(20)

  try {
    const res = await linkBroker({
      accountNumber: form.value.account,
      password: form.value.password,
      server: form.value.server,
      platform: form.value.platform,
    })

    if (!res.ok) {
      connError.value = res.message
      pushEvent('Broker link refused', 'error')
      return
    }

    /* Cleared immediately on success — the password has served its only
       purpose and there is no reason for it to survive in a reactive ref.
       The platform choice survives: it is a preference, not a credential. */
    const platform = form.value.platform
    form.value = { server: '', account: '', password: '', platform }

    // A still-connecting broker is reported as the wait it is, not as success.
    connNote.value = res.message
    pushEvent(res.connecting ? 'Broker linking…' : 'Broker link up', res.connecting ? 'info' : 'ok')

    await refreshLink()

    if (Notification.permission === 'default') {
      Notification.requestPermission()
    }
  } catch (err) {
    connError.value = err?.message || 'Connection failed.'
  } finally {
    connecting.value = false
  }
}

onMounted(() => {
  purgeLegacyCredentials()
  refreshLink()
})
</script>

<style scoped>
/* height, NOT min-height. `.app-shell` is `height: 100dvh; overflow: hidden`,
   so it CLIPS this view; with min-height the element grows past the shell
   instead of scrolling inside it and everything below the fold becomes
   unreachable. Installed to the Home screen there is no collapsing URL bar to
   jostle it into view, so the page simply freezes. overflow-y: auto is what
   makes this element the scroll container. */
.mtv {
  position: relative;
  width: 100%;
  height: 100dvh;
  overflow-y: auto;
  overflow-x: hidden;
  -webkit-overflow-scrolling: touch;
  background: var(--cc-canvas);
}

/* One soft accent bloom behind the hero. Fixed rather than absolute so it
   does not travel with the scroll and re-rasterise the glass above it. */
.mtv-wash {
  position: fixed;
  top: -14%;
  left: 50%;
  width: 130%;
  height: 46%;
  transform: translateX(-50%);
  background: radial-gradient(ellipse at 50% 40%,
    rgba(var(--cc-accent-rgb), 0.22) 0%,
    rgba(var(--cc-accent-rgb), 0.06) 46%,
    transparent 72%);
  filter: blur(40px);
  pointer-events: none;
  z-index: 0;
}

.mtv-inner {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  padding: calc(var(--cc-safe-top) + 10px) 22px calc(var(--cc-safe-bottom) + 118px);
}

/* ── Chrome ──────────────────────────────────────────────────────── */
.mtv-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  height: 44px;
}
.mtv-back {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border: none;
  background: none;
  border-radius: var(--cc-r-chip);
  color: var(--cc-ink-3);
  cursor: pointer;
  transition: transform 180ms var(--cc-spring);
}
.mtv-back:active { transform: scale(0.92) }

.mtv-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 32px;
  padding: 0 14px;
  border-radius: var(--cc-r-chip);
  font-family: var(--cc-font);
  font-size: 12px;
  font-weight: 600;
  color: var(--cc-ink-2);
  white-space: nowrap;
}
/* Only the states the user has to act on take a colour of their own.
   `--cc-sell` is already this app's "not good" and `--cc-warn` its "wait". */
.mtv-badge[data-state="off"]  .cc-dot { background: var(--cc-sell) }
.mtv-badge[data-state="wait"] .cc-dot { background: var(--cc-warn) }

/* ── Hero ────────────────────────────────────────────────────────── */
.mtv-hero {
  position: relative;
  align-self: center;
  display: grid;
  place-items: center;
  width: 96px;
  height: 96px;
  margin-top: 26px;
}
.mtv-hero-glow {
  position: absolute;
  inset: 6px;
  border-radius: 26px;
  background: rgba(var(--cc-accent-rgb), 0.18);
  filter: blur(14px);
}
.mtv-hero-img {
  position: relative;
  width: 68px;
  height: 68px;
  object-fit: contain;
  border-radius: 16px;
}

.mtv-title {
  margin: 22px 0 0;
  text-align: center;
  font-family: var(--cc-font);
  font-size: 26px;
  font-weight: 800;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--cc-ink);
}
.mtv-sub {
  margin: 9px 0 0;
  text-align: center;
  font-size: 14px;
  line-height: 20px;
  color: var(--cc-ink-4);
  text-wrap: pretty;
}

/* ── Platform ────────────────────────────────────────────────────── */
.mtv-seg {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 4px;
  margin-top: 34px;
  padding: 4px;
  border-radius: var(--cc-r-card);
  background: var(--cc-track);
  border: 1px solid var(--cc-border-soft);
}
.mtv-seg-btn {
  padding: 13px 4px;
  border: none;
  border-radius: 14px;
  background: none;
  font-family: var(--cc-font);
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.02em;
  color: var(--cc-ink-4);
  cursor: pointer;
  transition: transform 180ms var(--cc-spring);
}
.mtv-seg-btn:active { transform: scale(0.97) }
/* The selected platform is the one place on this screen the accent FILLS, so
   it takes the accent's measured ink rather than white. */
.mtv-seg-btn.is-on {
  color: var(--cc-accent-ink);
  background: var(--cc-accent);
  box-shadow: var(--cc-glow-edge);
}

/* ── Credentials ─────────────────────────────────────────────────── */
.mtv-card {
  margin-top: 26px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.mtv-field { display: flex; flex-direction: column; gap: 7px }

.mtv-input {
  width: 100%;
  height: 52px;
  padding: 0 15px;
  font-family: var(--cc-font);
  font-size: 16px;              /* 16px exactly — iOS zooms a focused field below it */
  font-weight: 500;
  color: var(--cc-ink);
  outline: none;
  transition: border-color 180ms var(--cc-ease), box-shadow 180ms var(--cc-ease);
}
.mtv-input::placeholder { color: var(--cc-ink-5) }
/* The accent doubles as the focus indicator, the same rule the Android
   build's NovaTextField follows. */
.mtv-input:focus {
  border-color: var(--cc-accent-line);
  box-shadow: var(--cc-glow-edge);
}

.mtv-pwd { position: relative; display: block }
.mtv-pwd .mtv-input { padding-right: 48px }
.mtv-eye {
  position: absolute;
  top: 0;
  right: 0;
  display: grid;
  place-items: center;
  width: 46px;
  height: 52px;
  border: none;
  background: none;
  color: var(--cc-ink-4);
  cursor: pointer;
}

/* ── Commit ──────────────────────────────────────────────────────── */
/* Outlined rather than filled, and deliberately: the filled accent is
   already spent on the platform toggle above, and two solid accent blocks
   stacked read as one control that moved. The rim plus the halo makes this
   the loudest thing on the screen without repeating that fill. */
.mtv-connect {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  width: 100%;
  height: 56px;
  margin-top: 30px;
  border: 1.5px solid var(--cc-accent);
  border-radius: var(--cc-r-chip);
  background: rgba(var(--cc-accent-rgb), 0.10);
  font-family: var(--cc-font);
  font-size: 14px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--cc-accent-text);
  cursor: pointer;
  box-shadow: var(--cc-glow-halo);
  transition: transform 200ms var(--cc-spring), opacity 200ms var(--cc-ease);
}
.mtv-connect:active { transform: scale(0.975) }
.mtv-connect:disabled {
  opacity: 0.45;
  cursor: not-allowed;
  box-shadow: none;
}

.mtv-spin {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  border: 2px solid rgba(var(--cc-accent-rgb), 0.28);
  border-top-color: var(--cc-accent);
  animation: mtvSpin 780ms linear infinite;
}
@keyframes mtvSpin { to { transform: rotate(360deg) } }
@media (prefers-reduced-motion: reduce) {
  .mtv-spin { animation-duration: 2.4s }
}
.cmd[data-motion="reduced"] .mtv-spin { animation-duration: 2.4s }

/* ── Messages ────────────────────────────────────────────────────── */
.mtv-msg {
  margin: 14px 2px 0;
  font-size: 13px;
  line-height: 19px;
  color: var(--cc-ink-3);
  text-wrap: pretty;
}
.mtv-msg.is-error { color: var(--cc-sell-text) }
.mtv-msg.is-note  { color: var(--cc-accent-text) }

/* ── Linked account ──────────────────────────────────────────────── */
.mtv-linked {
  margin-top: 22px;
  padding: 16px 18px 18px;
}
.mtv-linked-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.mtv-linked-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
  min-width: 0;
}
.mtv-linked-name {
  flex: 1;
  min-width: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--cc-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mtv-linked-meta { flex: none; font-size: 11px; color: var(--cc-ink-4) }
.mtv-linked-note { margin: 12px 0 0 }

.mtv-foot { margin: 18px 2px 0; text-align: center }
</style>
