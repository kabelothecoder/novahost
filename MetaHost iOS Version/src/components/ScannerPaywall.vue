<template>
  <div class="sp">

    <!-- ═══ 1 · THE PITCH ═══════════════════════════════════════════
         A badge, not a barrier.

         This screen used to open with a 68px padlock inside a dashed
         empty box captioned SCANNER LOCKED. That is an accurate
         description of the state and a terrible thing to sell with: it
         tells the reader what they cannot do before it has told them
         what the thing is. None of the reference paywalls worth copying
         show a lock at all — they lead with the capability and let the
         price be the only friction.

         So the state is demoted to a PRO chip in the corner and the
         screen leads with what the scanner DOES. -->
    <header class="sp-hero">
      <span class="sp-glow" aria-hidden="true"></span>

      <div class="sp-hero-top">
        <span class="sp-eyebrow cc-label">AI Chart Scanner</span>
        <span class="sp-pro">PRO</span>
      </div>

      <h2 class="sp-hl">Read any chart<br />in seconds.</h2>

      <p class="sp-sub">
        Send a screenshot of any chart and get the setup read back —
        direction, confidence, entry, stop loss and take profit.
      </p>
    </header>

    <!-- ═══ 2 · WHAT YOU GET ════════════════════════════════════════
         One card per capability, each with its own coloured icon tile.
         Three cards rather than three list rows because a row of 13px
         text with a tick beside it is a terms-and-conditions block; a
         card with a coloured glyph is a feature. -->
    <ul class="sp-feats">
      <li v-for="f in FEATURES" :key="f.title" class="sp-feat cc-card">
        <span class="sp-feat-icon" :class="`is-${f.tone}`" v-html="f.icon"></span>
        <span class="sp-feat-copy">
          <span class="sp-feat-t">{{ f.title }}</span>
          <span class="sp-feat-d cc-caption">{{ f.desc }}</span>
        </span>
      </li>
    </ul>

    <!-- ═══ 3 · THE PRICE ═══════════════════════════════════════════ -->
    <!-- Your plan carries one free scan a month; this is for the rest. -->
    <div class="sp-price cc-card">
      <span class="sp-price-copy">
        <span class="sp-price-k cc-label">20 scans · never expire</span>
        <span class="sp-price-s cc-caption">R7.50 a scan · 1 free scan with your plan each month</span>
      </span>
      <span class="sp-price-v cc-figure">R150</span>
    </div>

    <Transition name="sp-note">
      <p v-if="notice" class="sp-notice" :class="{ 'sp-notice-bad': noticeBad }" role="status">
        {{ notice }}
      </p>
    </Transition>

    <!-- ═══ 4 · CTA ═════════════════════════════════════════════════ -->
    <button type="button" class="sp-cta" :disabled="busy || !email" @click="startCheckout">
      <template v-if="!busy">
        <span>Buy 20 scans</span>
        <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="m13 3 9 9-9 9" /><path d="M22 12H3" />
        </svg>
      </template>
      <template v-else>
        <span class="sp-spinner" aria-hidden="true"></span>
        <span>Opening checkout</span>
      </template>
    </button>

    <p class="sp-secure cc-caption">
      Payment is handled by PayFast. NovaHost never sees your card details.
    </p>

  </div>
</template>

<script setup>
/**
 * @description ScannerPaywall — scan packs (20 for R150), sold where the
 * scanner lives rather than at the front door. Shown once this month's free
 * scan and any bought credits are spent; the old R349 unlimited unlock still
 * bypasses it for anyone who bought one.
 *
 * It used to be a second plan card on PaymentView, the screen the sign-in gate
 * hands an unpaid visitor. That put a price on a feature the reader had never
 * seen, next to the app purchase they were actually there for. This screen has
 * the pitch the other one could not make: it is standing where the scanner
 * would be.
 *
 * ── Restyled onto the command system ──────────────────────────────────
 * Everything visual here was written against the OLD glass vocabulary —
 * `rgba(var(--accent-rgb), …)`, `'Courier New'`, hand-rolled rgba whites.
 * ScannerView moved onto command.css and this did not, so the one screen a
 * user is asked to spend money on was the one screen that looked like a
 * different app: different type, different card material, an accent that did
 * not follow the mentor's swatch, and no light scheme at all.
 *
 * The checkout is the same one PaymentView runs — `generate-payfast-checkout`
 * with `product: 'SCANNER'` — and the same rules apply to reading its answer:
 * a refusal is a 200 carrying a `route`, never an error, so the routes are
 * switched on explicitly rather than collapsed into one failure message.
 *
 * There is no callback to wait on. PayFast's return_url points at the
 * `payment-redirect` function and iOS breaks a standalone PWA out to Safari
 * for a cross-origin navigation anyway, so entitlement is re-checked whenever
 * this component becomes visible again — which is what actually happens when
 * the user switches back.
 */
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { supabase } from '../utils/supabaseClient'
import { authState, verifySubscription } from '../services/authService'
import { getDeviceId } from '../utils/deviceId'
import { secStorage } from '../utils/secStorage'

const emit = defineEmits(['unlocked'])

/* Was three bare strings. Each is now a titled card, because the middle
   one ("Entry, stop loss and take profit levels") is the whole product
   and it was reading as the second of three equal ticks. Tones are
   semantic tokens, not literals, so they follow the scheme. */
const FEATURES = [
  {
    tone: 'buy',
    title: 'Direction and confidence',
    desc: 'Read straight off your chart — including the levels you drew yourself.',
    icon: `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="22 7 13.5 15.5 8.5 10.5 2 17"/><polyline points="16 7 22 7 22 13"/></svg>`,
  },
  {
    tone: 'accent',
    title: 'Entry, stop loss, take profit',
    desc: 'A full plan with real price levels, not a direction and a shrug.',
    icon: `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="3" y1="6.5" x2="21" y2="6.5"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="17.5" x2="21" y2="17.5"/><circle cx="8" cy="6.5" r="2" fill="currentColor" stroke="none"/><circle cx="16" cy="12" r="2" fill="currentColor" stroke="none"/><circle cx="11" cy="17.5" r="2" fill="currentColor" stroke="none"/></svg>`,
  },
  {
    tone: 'warn',
    title: 'Straight to your terminal',
    desc: 'Send the reading to your broker as a staged order, or just keep it.',
    icon: `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m21.5 2.5-10 10"/><path d="M21.5 2.5 15 21l-3.5-8.5L3 9z"/></svg>`,
  },
]

const email = computed(() => authState.email || '')

const busy = ref(false)
const notice = ref('')
const noticeBad = ref(false)

function say (message, bad) {
  notice.value = message
  noticeBad.value = !!bad
}

let redirecting = false

async function startCheckout () {
  if (busy.value || !email.value) return
  redirecting = false
  busy.value = true
  say('', false)

  try {
    const { data, error } = await supabase.functions.invoke('generate-payfast-checkout', {
      body: {
        email: email.value,
        deviceId: getDeviceId(),
        // A pack of 20, added to scan_credits. The old R349 unlimited unlock
        // is no longer sold on iOS; anyone who bought it keeps it.
        product: 'SCANS20',
        license_key: secStorage.getItem('LICENSE_KEY') || undefined,
        // Sends the payer to a page rather than the android `metahost://`
        // deep link, which in Safari is an invalid-address error.
        platform: 'ios',
      },
    })

    if (error || !data) {
      return say('That purchase could not be started. Try again in a moment.', true)
    }

    if (data.error) return say(data.error, true)

    if (data.checkout_url) {
      // Assigned rather than window.open: this runs after an await, and iOS
      // blocks a popup that is no longer attributable to the tap.
      redirecting = true
      window.location.href = data.checkout_url
      return
    }

    switch (data.route) {
      case 'ACTIVE_NEW_DEVICE_BLOCKED':
        // Buying here would leave them holding a scanner behind an app lock.
        // The function's own wording names the move, so it is shown as written.
        return say(data.message || 'This email is active on another device. Move it across first.', true)

      default:
        return say(data.message || 'That purchase could not be started right now.', true)
    }
  } catch (err) {
    console.error('Scanner checkout error:', err)
    say('That purchase could not be started. Check your connection and try again.', true)
  } finally {
    // Held on the redirect path: navigation is already under way, and snapping
    // the button back to ready before the page unloads reads as a failed tap.
    if (!redirecting) busy.value = false
  }
}

/**
 * Re-asks the server and lifts the lock once there is something to scan with.
 *
 * `verifySubscription` reports on app access, not scans, so its return value
 * is not the answer here — the scan fields it refreshes on the way through
 * are what this reads.
 */
async function refresh (fallbackMessage) {
  if (!email.value) return
  await verifySubscription(email.value)
  if (authState.hasScanner || authState.scanCredits > 0 || authState.freeScanAvailable) {
    emit('unlocked')
    return true
  }
  if (fallbackMessage) say(fallbackMessage, false)
  return false
}

function onVisible () {
  // The ITN can lag the redirect by a few seconds, so a miss here is not
  // final. Nothing is said on failure, to avoid shouting "not paid" at someone
  // whose payment is still settling.
  if (document.visibilityState === 'visible' && !busy.value) refresh(null)
}

onMounted(() => document.addEventListener('visibilitychange', onVisible))
onUnmounted(() => document.removeEventListener('visibilitychange', onVisible))
</script>

<style scoped>
.sp {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* ── 1 · Hero ─────────────────────────────────────────────────────── */
.sp-hero {
  position: relative;
  padding: 4px 2px 2px;
  overflow: visible;
}

/* Replaces the dashed empty box. A single soft accent bloom behind the
   headline gives the screen a subject without pretending to show a scan
   that has not happened. */
.sp-glow {
  position: absolute;
  left: -14%;
  top: -46px;
  width: 78%;
  height: 190px;
  border-radius: 50%;
  pointer-events: none;
  background: radial-gradient(closest-side,
    rgba(var(--cc-accent-rgb), calc(0.30 * var(--cc-glow))),
    rgba(var(--cc-accent-rgb), 0) 72%);
}

.sp-hero-top {
  position: relative;
  display: flex;
  align-items: center;
  gap: 9px;
  margin-bottom: 10px;
}

.sp-eyebrow { color: var(--cc-ink-3) }

/* The demoted lock. It still says the scanner is a paid tier — it just
   says it as a badge on the product rather than as a barrier across it. */
.sp-pro {
  display: inline-flex;
  align-items: center;
  height: 17px;
  padding: 0 7px;
  border-radius: 4px;
  background: var(--cc-accent-fill, var(--cc-accent));
  color: var(--cc-accent-fill-ink, var(--cc-accent-ink));
  font-family: var(--cc-font);
  font-size: 9px;
  font-weight: 900;
  letter-spacing: 0.14em;
}

.sp-hl {
  position: relative;
  margin: 0;
  font-family: var(--cc-font);
  font-size: 27px;
  font-weight: 800;
  line-height: 1.12;
  letter-spacing: -0.03em;
  color: var(--cc-ink);
}

.sp-sub {
  position: relative;
  margin: 10px 0 0;
  font-size: 13.5px;
  line-height: 1.5;
  color: var(--cc-ink-3);
  text-wrap: pretty;
}

/* ── 2 · Features ─────────────────────────────────────────────────── */
.sp-feats {
  list-style: none;
  margin: 4px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.sp-feat {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 13px 14px;
}

.sp-feat-icon {
  flex: none;
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: var(--cc-r-tile);
}
/* Tinted tiles rather than bare glyphs: at 20px a stroke icon on a card
   has no presence, and the three then read as one grey block. */
.sp-feat-icon.is-buy    { background: var(--cc-buy-dim);  color: var(--cc-buy) }
.sp-feat-icon.is-warn   { background: rgba(232, 163, 61, 0.14); color: var(--cc-warn) }
.sp-feat-icon.is-accent {
  background: rgba(var(--cc-accent-rgb), 0.14);
  color: var(--cc-accent-text);
}

.sp-feat-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.sp-feat-t {
  font-family: var(--cc-font);
  font-size: 13.5px;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--cc-ink);
}
.sp-feat-d { line-height: 1.45; text-wrap: pretty }

/* ── 3 · Price ────────────────────────────────────────────────────── */
.sp-price {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 13px 16px;
}
.sp-price-copy { display: flex; flex-direction: column; gap: 2px; min-width: 0 }
.sp-price-k { color: var(--cc-ink-3) }
.sp-price-v {
  flex: none;
  font-size: 26px;
  font-weight: 800;
  letter-spacing: -0.03em;
  color: var(--cc-accent-text);
}

/* ── Notice ───────────────────────────────────────────────────────── */
.sp-notice {
  margin: 0;
  padding: 12px 14px;
  border-radius: var(--cc-r-tile);
  background: rgba(var(--cc-accent-rgb), 0.09);
  border: 1px solid rgba(var(--cc-accent-rgb), 0.20);
  font-size: 13px;
  line-height: 1.45;
  color: var(--cc-ink-2);
  text-wrap: pretty;
}
.sp-notice-bad {
  background: var(--cc-sell-dim);
  border-color: rgba(224, 84, 78, 0.30);
  color: var(--cc-sell-text);
}

.sp-note-enter-active,
.sp-note-leave-active { transition: opacity 220ms linear, transform 220ms var(--cc-ease-out) }
.sp-note-enter-from,
.sp-note-leave-to { opacity: 0; transform: translateY(-5px) }

/* ── 4 · CTA ──────────────────────────────────────────────────────── */
.sp-cta {
  width: 100%;
  min-height: 54px;
  margin-top: 2px;
  padding: 0 20px;
  border: none;
  border-radius: var(--cc-r-chip);
  /* The accent FILL, so a user on a gradient gets the gradient here —
     and its measured ink, which is why the ramp carries its own. The old
     button was a 12% accent wash with accent-coloured text on it, which
     on the low-luminance swatches (Rust, Forest) was accent-on-accent. */
  background: var(--cc-accent-fill, var(--cc-accent));
  color: var(--cc-accent-fill-ink, var(--cc-accent-ink));
  font-family: var(--cc-font);
  font-size: 15px;
  font-weight: 800;
  letter-spacing: 0.01em;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  cursor: pointer;
  box-shadow: 0 0 calc(30px * var(--cc-glow)) rgba(var(--cc-accent-rgb), calc(0.42 * var(--cc-glow)));
  transition: transform 200ms var(--cc-spring), box-shadow 240ms var(--cc-ease);
}
.sp-cta:active {
  transform: scale(0.975);
  box-shadow: 0 0 calc(42px * var(--cc-glow)) rgba(var(--cc-accent-rgb), calc(0.6 * var(--cc-glow)));
}
.sp-cta:disabled {
  opacity: 0.5;
  cursor: default;
  box-shadow: none;
  transform: none;
}

.sp-spinner {
  width: 15px;
  height: 15px;
  border-radius: 50%;
  border: 2px solid rgba(0, 0, 0, 0.22);
  border-top-color: currentColor;
  animation: spSpin 800ms linear infinite;
}
@keyframes spSpin { to { transform: rotate(360deg) } }
@media (prefers-reduced-motion: reduce) {
  .sp-spinner { animation-duration: 2.4s }
}

.sp-secure {
  margin: 0;
  text-align: center;
  line-height: 1.45;
  text-wrap: pretty;
}
</style>
