<template>
  <div class="terminal-view">
    <!-- Neural Link Loading Overlay -->
    <Transition name="fade">
      <div v-if="connecting" class="neural-overlay">
        <div class="neural-spinner">
          <div class="spinner-ring"></div>
          <div class="spinner-ring inner"></div>
          <div class="spinner-core"></div>
        </div>
        <span class="neural-msg mono-tech">Establishing Neural Link with Trade245...</span>
      </div>
    </Transition>

    <header class="screen-header">
      <h2 class="screen-title">MetaTrader Terminal</h2>
      <span class="screen-sub">Connect your broker account</span>
    </header>

    <!-- Connection status strip -->
    <div class="status-strip">
      <div class="status-item">
        <div class="status-dot" :class="conn.server ? 'dot-green':'dot-red'" />
        <span>Server</span>
      </div>
      <div class="status-div" />
      <div class="status-item">
        <div class="status-dot" :class="conn.account ? 'dot-green':'dot-red'" />
        <span>Account</span>
      </div>
      <div class="status-div" />
      <div class="status-item">
        <div class="status-dot" :class="conn.feed ? 'dot-green':'dot-amber'" />
        <span>Data Feed</span>
      </div>
    </div>

    <div class="term-scroll">

      <!-- MT5 Icon badge -->
      <div class="mt5-badge-wrap">
        <div class="mt5-badge">
          <span class="mt5-text">MT5</span>
        </div>
        <span class="mt5-sub">MetaTrader 5 Gateway</span>
      </div>

      <!-- Form -->
      <form class="conn-form" @submit.prevent="doConnect">

        <div class="form-field">
          <label class="form-label">BROKER SERVER</label>
          <input v-model="form.server" type="text" class="field-glass" placeholder="e.g. Exness-MT5Real" autocomplete="off" />
        </div>

        <div class="form-field">
          <label class="form-label">ACCOUNT NUMBER</label>
          <input v-model="form.account" type="number" class="field-glass" placeholder="e.g. 123456789" />
        </div>

        <div class="form-field">
          <label class="form-label">PASSWORD</label>
          <div class="pwd-wrap">
            <input v-model="form.password" :type="showPwd ? 'text' : 'password'" class="field-glass" placeholder="Investor or Master password" />
            <button type="button" class="pwd-toggle" @click="showPwd=!showPwd">
              <svg v-if="!showPwd" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
              <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
            </button>
          </div>
        </div>

        <div class="form-field">
          <label class="form-label">PLATFORM</label>
          <div class="platform-row">
            <button
              v-for="p in ['MT5','MT4']"
              :key="p"
              type="button"
              class="platform-btn"
              :class="{ active: form.platform===p }"
              @click="form.platform=p"
            >{{ p }}</button>
          </div>
        </div>

        <button class="connect-btn" type="submit" :disabled="connecting">
          <span v-if="!connecting">Connect Terminal</span>
          <span v-else class="connecting-dots">SYNCING<span class="dots">...</span></span>
        </button>

        <p v-if="connError" class="conn-error">{{ connError }}</p>
        <!-- The server's own words. A broker that is still coming up says so
             here, and that is a wait rather than a failure. -->
        <p v-else-if="connNote" class="conn-note">{{ connNote }}</p>
      </form>

      <!-- The linked account, as the LICENCE reports it.
           Not a device-local list: the truth lives on the licence, so a
           reinstalled phone or an account linked from the other handset
           both read correctly here. -->
      <div class="sessions-block" v-if="link.linked">
        <span class="sessions-title">LINKED ACCOUNT</span>
        <div class="session-row">
          <div class="session-left">
            <div class="session-dot" :class="link.connecting ? 'dot-amber' : 'dot-green'"></div>
            <div class="session-info">
              <span class="session-name">{{ link.server || 'Broker account' }}</span>
              <span class="session-acc">{{ link.platform || 'MT5' }}<span v-if="connectedLabel"> · {{ connectedLabel }}</span></span>
            </div>
          </div>
          <div class="session-right">
            <!-- 'connecting' is a wait, never a green light. metacopier-execute
                 holds orders during it rather than losing them. -->
            <span class="live-pill" :class="{ 'pill-wait': link.connecting }">
              {{ link.connecting ? 'LINKING' : 'LIVE' }}
            </span>
          </div>
        </div>
        <p class="sessions-note">
          Connecting a different account replaces this one. Your broker password
          is sent once to establish the link and is never stored on this device.
        </p>
      </div>
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
 * Link state is now READ from the licence rather than remembered on the
 * device, which is why a reinstall, or an account linked from the Android
 * handset, both render correctly.
 */
import { ref, computed, onMounted } from 'vue'
import { secStorage } from '../utils/secStorage.js'
import { accent } from '../stores/accent.js'
import { session, pushEvent } from '../stores/session.js'
import { linkBroker, loadLinkState, loadAccount } from '../services/brokerService.js'
import { haptic } from '../composables/useSpeech.js'

/* `feed` starts false. It was hardcoded true, so the Data Feed dot was green
   on a screen with no broker attached at all. */
const conn = ref({ server: false, account: false, feed: false })
const form = ref({ server: '', account: '', password: '', platform: 'MT5' })
const showPwd = ref(false)
const connecting = ref(false)
const connError = ref('')
const connNote = ref('')

/** `linked: null` means the status read failed — unknown, not disconnected. */
const link = ref({ linked: false, connecting: false, server: null, platform: null, connectedAt: null })

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

async function refreshLink() {
  const state = await loadLinkState()
  link.value = state

  const up = state.linked === true && !state.connecting
  conn.value.server = state.linked === true
  conn.value.account = state.linked === true
  conn.value.feed = up

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
  if (!form.value.server || !form.value.account || !form.value.password) {
    connError.value = 'Please fill all fields.'; return
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
       purpose and there is no reason for it to survive in a reactive ref. */
    form.value = { server: '', account: '', password: '', platform: 'MT5' }

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
.terminal-view {
  position: relative; 
  width: 100%;
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  background: transparent;
}

/* Neural Link Overlay */
.neural-overlay {
  position: fixed; inset: 0;
  z-index: 1000;
  background: rgba(0, 5, 12, 0.92);
  backdrop-filter: blur(20px);
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 30px;
}
.neural-spinner {
  position: relative; width: 100px; height: 100px;
}
.spinner-ring {
  position: absolute; inset: 0; border: 2px solid transparent;
  border-top-color: var(--accent); border-radius: 50%;
  animation: spin 2s linear infinite;
}
.spinner-ring.inner {
  inset: 15px; border-top-color: transparent; border-bottom-color: var(--accent);
  animation: spin-reverse 1.5s linear infinite;
  opacity: 0.6;
}
.spinner-core {
  position: absolute; inset: 35px; border-radius: 50%;
  background: var(--accent); opacity: 0.2;
  box-shadow: 0 0 30px var(--accent);
  animation: core-pulse 1s ease-in-out infinite alternate;
}
.neural-msg {
  color: var(--accent); font-size: 0.8rem; letter-spacing: 0.15em;
  text-shadow: 0 0 10px rgba(var(--accent-rgb), 0.5);
  animation: fade-pulse 1.5s ease-in-out infinite;
}

@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
@keyframes spin-reverse { from { transform: rotate(0deg); } to { transform: rotate(-360deg); } }
@keyframes core-pulse { from { transform: scale(0.8); opacity: 0.1; } to { transform: scale(1.2); opacity: 0.4; } }
@keyframes fade-pulse { 0%,100% { opacity: 0.4; } 50% { opacity: 1; } }

.fade-enter-active, .fade-leave-active { transition: opacity 0.5s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }


.screen-header {
  padding: calc(env(safe-area-inset-top, 16px) + 16px) 20px 0;
}
.screen-title { font-size: 1.3rem; font-weight: 800; color: #E8F0FF; letter-spacing: -0.02em; margin: 0; }
.screen-sub {
  font-family: 'Courier New', monospace; font-size: 0.65rem;
  color: rgba(var(--accent-rgb), 0.5); letter-spacing: 0.1em;
  display: block; margin-top: 2px;
}

/* Status strip */
.status-strip {
  display: flex; align-items: center; justify-content: center;
  margin: 14px 16px 0;
  border-radius: 14px;
  background: rgba(255,255,255,0.04);
  border: 1px solid rgba(255,255,255,0.07);
  padding: 12px 20px;
  gap: 18px;
  position: relative; z-index: 2;
}
.status-item { display: flex; align-items: center; gap: 7px; font-family: 'Courier New', monospace; font-size: 0.72rem; color: rgba(232,240,255,0.5); }
.status-dot { width: 9px; height: 9px; border-radius: 50%; flex-shrink: 0; }
.dot-green { background: #00FF87; box-shadow: 0 0 8px #00FF87; animation: glow-pulse 1.5s ease-in-out infinite; }
.dot-red   { background: #FF3D5A; box-shadow: 0 0 8px #FF3D5A; }
.dot-amber { background: #FFB800; box-shadow: 0 0 8px #FFB800; }
@keyframes glow-pulse { 0%,100%{opacity:1} 50%{opacity:.5} }
.status-div { width: 1px; height: 18px; background: rgba(255,255,255,0.08); }

/* Scroll */
.term-scroll {
  flex: 1; overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior: contain;
  padding: 20px 16px 130px;
  display: flex; flex-direction: column; gap: 20px;
}

/* MT5 Badge */
.mt5-badge-wrap { display: flex; flex-direction: column; align-items: center; gap: 8px; }
.mt5-badge {
  width: 70px; height: 70px;
  border-radius: 18px;
  background: rgba(var(--accent-rgb), 0.1);
  border: 1.5px solid rgba(var(--accent-rgb), 0.35);
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 0 28px rgba(var(--accent-rgb), 0.2);
}
.mt5-text {
  font-family: 'Courier New', monospace;
  font-size: 1rem; font-weight: 900;
  color: var(--accent); letter-spacing: 0.04em;
}
.mt5-sub {
  font-family: 'Courier New', monospace;
  font-size: 0.65rem; color: rgba(255,255,255,0.35); letter-spacing: 0.1em;
}

/* Form */
.conn-form {
  background: rgba(255,255,255,0.03);
  border: 1px solid rgba(255,255,255,0.07);
  border-radius: 18px;
  padding: 22px 18px;
  display: flex; flex-direction: column; gap: 16px;
}
.form-field { display: flex; flex-direction: column; gap: 6px; }
.form-label {
  font-family: 'Courier New', monospace;
  font-size: 0.6rem; letter-spacing: 0.16em;
  color: rgba(var(--accent-rgb), 0.55);
}
.field-glass {
  background: rgba(255,255,255,0.04);
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 11px;
  color: #E8F0FF;
  padding: 12px 14px;
  font-size: 16px; /* 16px floor: iOS Safari zooms the viewport on focus below this, and never zooms back */
  font-family: 'Courier New', monospace;
  width: 100%; outline: none;
  transition: border-color 0.2s ease;
}
.field-glass::placeholder { color: rgba(255,255,255,0.25); }
.field-glass:focus { border-color: rgba(var(--accent-rgb), 0.5); box-shadow: 0 0 0 3px rgba(var(--accent-rgb), 0.08); }

.pwd-wrap { position: relative; }
.pwd-wrap .field-glass { padding-right: 46px; }
.pwd-toggle {
  position: absolute; right: 12px; top: 50%;
  transform: translateY(-50%);
  background: none; border: none; cursor: pointer;
  color: rgba(255,255,255,0.4); display: flex; align-items: center;
}

.platform-row { display: flex; gap: 10px; }
.platform-btn {
  flex: 1; padding: 11px;
  border-radius: 11px;
  border: 1px solid rgba(255,255,255,0.1);
  background: rgba(255,255,255,0.04);
  color: rgba(232,240,255,0.4);
  font-family: 'Courier New', monospace;
  font-weight: 700; font-size: 0.85rem;
  cursor: pointer; transition: all 0.25s ease;
  letter-spacing: 0.06em;
}
.platform-btn.active {
  border-color: var(--accent);
  color: var(--accent);
  background: rgba(var(--accent-rgb), 0.12);
  box-shadow: 0 0 16px rgba(var(--accent-rgb), 0.2);
}

.connect-btn {
  width: 100%; padding: 15px;
  border-radius: 12px;
  border: 1.5px solid rgba(var(--accent-rgb), 0.6);
  background: rgba(var(--accent-rgb), 0.1);
  color: var(--accent);
  font-family: 'Courier New', monospace;
  font-size: 0.9rem; font-weight: 900; letter-spacing: 0.1em;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 0 20px rgba(var(--accent-rgb), 0.15);
}
.connect-btn:active { transform: scale(0.97); }
.connect-btn:hover { box-shadow: 0 0 30px rgba(var(--accent-rgb), 0.3); }
.connect-btn:disabled { opacity: 0.7; cursor: not-allowed; }

.connecting-dots { display: flex; align-items: center; justify-content: center; gap: 0; }
.dots { animation: dots-anim 1.2s ease-in-out infinite; }
@keyframes dots-anim { 0%,100%{opacity:.2} 50%{opacity:1} }

.conn-error {
  color: #FF3D5A; font-size: 0.78rem; text-align: center;
  font-family: 'Courier New', monospace;
}

/* Not an error, and deliberately not green either: the common case it
   carries is "linked, broker still coming up", which is a wait. */
.conn-note {
  color: rgba(232,240,255,0.72); font-size: 0.78rem; text-align: center;
  line-height: 1.45; margin: 0;
}

/* Sessions */
.sessions-block { display: flex; flex-direction: column; gap: 10px; }
.sessions-title {
  font-family: 'Courier New', monospace;
  font-size: 0.65rem; letter-spacing: 0.18em;
  color: rgba(var(--accent-rgb), 0.45);
}
.session-row {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 16px;
  border-radius: 14px;
  background: rgba(255,255,255,0.03);
  border: 1px solid rgba(255,255,255,0.07);
}
.session-left { display: flex; align-items: center; gap: 12px; }
.session-info {}
.session-name {
  display: block; font-family: 'Courier New', monospace;
  font-size: 0.88rem; font-weight: 700; color: #E8F0FF;
}
.session-acc {
  display: block; font-family: 'Courier New', monospace;
  font-size: 0.65rem; color: rgba(var(--accent-rgb), 0.4);
}
.session-right { display: flex; align-items: center; gap: 10px; }
.live-pill {
  background: rgba(0,255,135,0.1);
  border: 1px solid rgba(0,255,135,0.35);
  color: #00FF87;
  border-radius: 100px;
  font-family: 'Courier New', monospace;
  font-size: 0.62rem; font-weight: 800; letter-spacing: 0.08em;
  padding: 4px 10px;
}

/* An account MetaCopier has registered but whose broker session is not up
   yet. Amber rather than green on purpose — orders placed now are held, not
   filled, and a green pill would promise otherwise. */
.pill-wait {
  background: rgba(255,184,0,0.1);
  border-color: rgba(255,184,0,0.38);
  color: #FFB800;
}

.sessions-note {
  color: rgba(232,240,255,0.45);
  font-size: 0.7rem; line-height: 1.5; margin: 2px 2px 0;
}
.disc-btn {
  background: rgba(255,61,90,0.08);
  border: 1px solid rgba(255,61,90,0.3);
  color: #FF3D5A;
  border-radius: 8px;
  padding: 5px 12px;
  font-family: 'Courier New', monospace;
  font-size: 0.68rem; cursor: pointer;
  transition: all 0.2s ease;
}
.disc-btn:hover { box-shadow: 0 0 12px rgba(255,61,90,0.25); }
</style>
