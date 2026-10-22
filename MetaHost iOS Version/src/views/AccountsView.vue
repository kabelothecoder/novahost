<template>
  <!--
    MY ACCOUNTS.

    Phase 1 of the connection-area redesign: the stack switcher and every card
    state, against the MOCK accountsService. Nothing here touches `session` or
    `accent`, so the rest of the app is unaffected until the facade lands
    (design-briefs/connection-area-plan.md, section 6).

    Review the awkward states with `#/accounts?scene=` empty | pending | failed
    | ready | max.
  -->
  <div class="cmd acv" :data-scheme="resolvedScheme" :data-card="theme.cardStyle" :data-motion="theme.motion">
    <div class="acv-wash" aria-hidden="true"></div>

    <div class="acv-inner">
      <div class="acv-top">
        <button class="acv-back" aria-label="Back" @click="$router.push('/home')">
          <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polyline points="15 18 9 12 15 6" /></svg>
        </button>
        <span class="acv-chip cc-thin">
          <span class="cc-dot" :class="session.isRunning ? 'cc-dot-live' : ''"></span>
          {{ session.isRunning ? 'Robot running' : 'Robot stopped' }}
        </span>
      </div>

      <h1 class="acv-title">My accounts</h1>
      <p class="acv-sub">{{ subtitle }}</p>

      <div class="acv-seg" role="tablist" aria-label="Accounts layout">
        <button
          v-for="l in ACCOUNT_LAYOUTS"
          :key="l.id"
          class="acv-seg-btn"
          :class="{ 'is-on': accountPrefs.layout === l.id }"
          role="tab"
          :aria-selected="accountPrefs.layout === l.id"
          :title="l.blurb"
          @click="pickLayout(l.id)"
        >{{ l.name }}</button>
      </div>

      <div v-if="!accounts.loaded" class="acv-skel" aria-busy="true">
        <span v-for="n in 3" :key="n" class="acv-skel-bar"></span>
      </div>

      <template v-else>
        <component
          :is="SWITCHERS[accountPrefs.layout]"
          :key="accountPrefs.layout"
          :items="accounts.items"
          :active-id="accounts.activeId"
          :included-id="includedAccountId"
          :running="session.isRunning"
          :busy="accounts.busy"
          :robot-name="robotName"
          @make="requestMake"
          @delink="requestDelink"
          @add="onAdd"
          @slot-act="onSlot"
        >
          <div v-if="summary" class="acv-sum">
            <span>Billing</span>
            <span class="acv-sum-r">{{ summary }}</span>
          </div>
        </component>
      </template>

      <p v-if="accounts.error" class="acv-err" role="alert">{{ accounts.error }}</p>
    </div>

    <Transition name="acv-toast">
      <div v-if="toast" class="acv-toast cc-thin" role="status">{{ toast }}</div>
    </Transition>

    <UpsellSheet v-if="sheet === 'upsell'" :robot-name="robotName" @close="closeSheet" @continue="sheet = 'payfast'" />
    <PayfastSheet v-if="sheet === 'payfast'" :paying="paying" @close="closeSheet" @pay="onPay" />
    <UnlockCelebration v-if="sheet === 'unlock'" @link="onLinkSlot" @later="closeSheet" />
    <ConfirmSwitchSheet
      v-if="sheet === 'confirm' && activeAccount && targetAccount"
      :from="activeAccount" :to="targetAccount" :robot-name="robotName" :busy="accounts.busy"
      @close="closeSheet" @confirm="doSwitch"
    />
    <DelinkSheet
      v-if="sheet === 'delink' && targetAccount"
      :account="targetAccount" :included="targetAccount.id === includedAccountId" :robot-name="robotName" :busy="accounts.busy"
      @close="closeSheet" @confirm="doDelink"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { theme, resolvedScheme } from '../stores/theme.js'
import { session } from '../stores/session.js'
import { robot } from '../stores/robot.js'
import {
  accounts, accountList, activeAccount, extraCount, includedAccountId, loadAccounts, makeActive, delink,
  buySlot, recheckSlot, ACCOUNT_LAYOUTS, accountPrefs, setAccountLayout,
} from '../stores/accounts.js'
import { ACCOUNT_ADDON, maskLogin } from '../services/accountsService.js'
import { haptic } from '../composables/useSpeech.js'
import StackSwitcher from '../components/accounts/StackSwitcher.vue'
import CarouselSwitcher from '../components/accounts/CarouselSwitcher.vue'
import ListSwitcher from '../components/accounts/ListSwitcher.vue'
import UpsellSheet from '../components/accounts/sheets/UpsellSheet.vue'
import PayfastSheet from '../components/accounts/sheets/PayfastSheet.vue'
import UnlockCelebration from '../components/accounts/sheets/UnlockCelebration.vue'
import ConfirmSwitchSheet from '../components/accounts/sheets/ConfirmSwitchSheet.vue'
import DelinkSheet from '../components/accounts/sheets/DelinkSheet.vue'

const SWITCHERS = { stack: StackSwitcher, carousel: CarouselSwitcher, list: ListSwitcher }

function pickLayout(id) {
  if (accountPrefs.layout === id) return
  haptic(10)
  setAccountLayout(id)
}

const route = useRoute()
const toast = ref('')
let toastTimer = null

function flash(text) {
  clearTimeout(toastTimer)
  toast.value = text
  toastTimer = setTimeout(() => { toast.value = '' }, 2800)
}
const notYet = (text) => flash(text)

const subtitle = computed(() => {
  if (!accounts.loaded) return 'Reading your accounts…'
  const n = accountList.value.length
  return n === 0
    ? 'No broker accounts linked yet'
    : `${n} of ${ACCOUNT_ADDON.maxAccounts} linked · your robot trades the active one`
})

const summary = computed(() => {
  if (!accounts.loaded || accountList.value.length === 0) return ''
  const x = extraCount.value
  const cost = x * ACCOUNT_ADDON.priceMonthly
  return `1 included${x ? ` · ${x} extra · +${ACCOUNT_ADDON.currencySymbol}${cost}/mo` : ''}`
})

/* ── Sheets ───────────────────────────────────────────────────────────
   One at a time. `target` is the account a confirm/delink sheet is about;
   `slotFor` is the slot a payment or link is for (null = a brand-new one). */
const sheet = ref(null)
const target = ref(null)
const slotFor = ref(null)
const paying = ref(false)
const robotName = computed(() => robot.name)
const targetAccount = computed(() => accountList.value.find((a) => a.id === target.value) || null)

function closeSheet() {
  if (paying.value) return          // never abandon a payment mid-flight
  sheet.value = null
  target.value = null
}

/* Only the active account trades. While the robot is running, a switch is a
   decision about live money, so it asks; while stopped it is just a pointer. */
async function requestMake(id) {
  if (id === accounts.activeId) return
  if (session.isRunning && accounts.activeId) {
    target.value = id
    sheet.value = 'confirm'
    return
  }
  await doSwitch(id)
}

async function doSwitch(id = target.value) {
  haptic(15)
  const res = await makeActive(id)
  sheet.value = null
  target.value = null
  if (!res.ok) return
  const a = activeAccount.value
  if (a) flash(session.isRunning ? `${robotName.value} is now trading ${a.broker} ${maskLogin(a)}` : `${a.broker} ${maskLogin(a)} is now active`)
}

function requestDelink(id) {
  target.value = id
  sheet.value = 'delink'
}

async function doDelink() {
  haptic(15)
  const gone = targetAccount.value
  const wasExtra = gone && gone.id !== includedAccountId.value
  const res = await delink(target.value)
  sheet.value = null
  target.value = null
  if (res.ok && gone) flash(wasExtra ? `${gone.broker} delinked · billing stops at the end of the period` : `${gone.broker} delinked`)
}

/* The "+" card. The first account is included in the plan, so it skips payment
   and goes straight to linking; every later one is a paid slot. */
function onAdd() {
  slotFor.value = null
  if (accountList.value.length === 0) return startLink()
  sheet.value = 'upsell'
}

async function onSlot(slot) {
  if (slot.state === 'pending') {
    flash('Checking with PayFast…')
    const res = await recheckSlot(slot.id)
    if (res.ok) flash('Payment confirmed · your slot is ready')
  } else if (slot.state === 'failed') {
    slotFor.value = slot.id
    sheet.value = 'payfast'
  } else {
    slotFor.value = slot.id
    startLink()
  }
}

async function onPay() {
  if (paying.value) return
  paying.value = true
  const res = await buySlot(slotFor.value)
  paying.value = false
  if (!res.ok) { sheet.value = null; return }
  slotFor.value = res.slotId
  sheet.value = 'unlock'
}

function onLinkSlot() { startLink() }

/* The connect flow is the next phase. Until it lands this is the one honest
   dead end: the slot stays paid and READY, nothing is lost. */
function startLink() {
  sheet.value = null
  flash('Linking an account arrives in the next step.')
}

onMounted(() => {
  /* `?scene=` is a review aid for the mock and means nothing in production. */
  const scene = import.meta.env.DEV ? String(route.query.scene || '') : ''
  loadAccounts(scene ? { scene } : undefined)
})
onUnmounted(() => clearTimeout(toastTimer))
</script>

<style scoped>
/* height, NOT min-height: the shell clips, and only an overflow container
   scrolls once the app is installed. See project_novahost_ios_scroll_container. */
.acv {
  position: relative; width: 100%; height: 100dvh;
  overflow-y: auto; overflow-x: hidden; -webkit-overflow-scrolling: touch;
  background: var(--cc-canvas);
}
.acv-wash {
  position: fixed; top: -14%; left: 50%; width: 130%; height: 46%;
  transform: translateX(-50%);
  background: radial-gradient(ellipse at 50% 40%, rgba(var(--cc-accent-rgb), 0.22) 0%, rgba(var(--cc-accent-rgb), 0.06) 46%, transparent 72%);
  filter: blur(40px); pointer-events: none; z-index: 0;
}
.acv-inner {
  position: relative; z-index: 1;
  display: flex; flex-direction: column;
  padding: calc(var(--cc-safe-top) + 10px) 22px calc(var(--cc-safe-bottom) + 118px);
}

.acv-top { display: flex; align-items: center; justify-content: space-between; gap: 12px; height: 44px; }
.acv-back {
  display: grid; place-items: center; width: 38px; height: 38px; border: 0; background: none;
  border-radius: var(--cc-r-chip); color: var(--cc-ink-3); cursor: pointer;
  transition: transform 180ms var(--cc-spring);
}
.acv-back:active { transform: scale(0.92); }
.acv-chip {
  display: inline-flex; align-items: center; gap: 8px; height: 32px; padding: 0 14px;
  border-radius: var(--cc-r-chip); font-size: 12px; font-weight: 600; color: var(--cc-ink-2); white-space: nowrap;
}

.acv-title { margin: 18px 0 0; font-size: 30px; line-height: 36px; font-weight: 600; letter-spacing: -0.02em; color: var(--cc-ink); }
.acv-sub { margin: 4px 0 16px; font-size: 13px; line-height: 18px; color: var(--cc-ink-3); }

.acv-seg {
  display: grid; grid-template-columns: repeat(3, 1fr); gap: 2px;
  margin-bottom: 20px; padding: 3px; border-radius: var(--cc-r-chip);
  background: var(--cc-track);
}
.acv-seg-btn {
  height: 32px; border: 0; border-radius: var(--cc-r-chip); background: none; cursor: pointer;
  font: inherit; font-size: 12.5px; font-weight: 600; color: var(--cc-ink-3);
  transition: background 220ms var(--cc-ease), color 220ms var(--cc-ease);
}
.acv-seg-btn.is-on { background: var(--cc-accent-fill); color: var(--cc-accent-fill-ink); }

.acv-skel { display: flex; flex-direction: column; gap: 12px; }
.acv-skel-bar { height: 84px; border-radius: 26px; background: var(--cc-card); animation: acv-pulse 1.4s ease-in-out infinite; }
@keyframes acv-pulse { 50% { opacity: 0.5 } }

.acv-sum {
  display: flex; align-items: center; justify-content: space-between; gap: 12px;
  margin-top: 22px; padding: 12px 16px; border-radius: 16px;
  background: rgba(255, 255, 255, 0.035); border: 1px solid rgba(255, 255, 255, 0.07);
  font-size: 12.5px; color: var(--cc-ink-4);
}
.acv-sum-r { font-family: var(--cc-mono); font-weight: 600; color: var(--cc-ink-2); white-space: nowrap; }

.acv-err { margin: 14px 4px 0; font-size: 13px; color: var(--cc-sell-text); }

.acv-toast {
  position: fixed; left: 24px; right: 24px; top: calc(var(--cc-safe-top) + 54px); z-index: 80;
  padding: 11px 16px; border-radius: 18px; text-align: center;
  font-size: 13.5px; font-weight: 500; line-height: 18px; color: var(--cc-ink); pointer-events: none;
}
.acv-toast-enter-active, .acv-toast-leave-active { transition: opacity 250ms ease, transform 300ms var(--cc-ease); }
.acv-toast-enter-from, .acv-toast-leave-to { opacity: 0; transform: translateY(-8px); }

@media (prefers-reduced-motion: reduce) { .acv-skel-bar { animation: none } }
</style>
