<template>
  <!--
    PREVIEW of the hand-off to PayFast. It is a stand-in, not a payment page:
    it takes no card details, calls no payment service and charges nothing, and
    says so on screen. When billing is wired for real, the real flow leaves the
    app for PayFast's own page (a standalone PWA breaks out to Safari for it),
    and this component is deleted rather than adapted.
  -->
  <Teleport to="body">
    <div class="pfs" role="dialog" aria-modal="true" aria-label="Checkout preview">
      <header class="pfs-top">
        <button class="pfs-x" aria-label="Close" :disabled="paying" @click="$emit('close')">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
        </button>
        <div class="pfs-mid">
          <span class="pfs-t">Checkout preview</span>
          <span class="pfs-u">Nothing is charged</span>
        </div>
        <span class="pfs-sp"></span>
      </header>

      <div class="pfs-body">
        <section class="pfs-card">
          <div class="pfs-who">
            <img src="/app_logo.png" alt="" class="pfs-logo" />
            <div><b>NovaHost</b><span>Extra broker account · monthly</span></div>
          </div>
          <div class="pfs-amt">{{ pr.today }}</div>
          <p class="pfs-sub">Today, prorated to {{ pr.endLabel }}. Then {{ price }}.00 a month with your plan.</p>
        </section>

        <div class="pfs-h">PAY WITH</div>
        <section class="pfs-card pfs-rows">
          <div v-for="(r, i) in rows" :key="r.label" class="pfs-row" :class="{ 'is-sel': i === 0 }">
            <span class="pfs-rl">{{ r.label }}</span>
            <span class="pfs-ring"></span>
          </div>
        </section>
      </div>

      <footer class="pfs-foot">
        <button class="pfs-pay" :disabled="paying" @click="$emit('pay')">
          <span v-if="paying" class="pfs-spin"></span>
          {{ paying ? 'Processing…' : `Pay ${pr.today}` }}
        </button>
        <p>You’ll return to NovaHost after payment</p>
      </footer>
    </div>
  </Teleport>
</template>

<script setup>
import { ACCOUNT_ADDON, proration } from '../../../services/accountsService.js'

defineProps({ paying: Boolean })
defineEmits(['close', 'pay'])

const pr = proration()
const price = `${ACCOUNT_ADDON.currencySymbol}${ACCOUNT_ADDON.priceMonthly}`
const rows = [{ label: 'Card' }, { label: 'Instant EFT' }, { label: 'Mobile wallet' }]
</script>

<style scoped>
/* Deliberately light: it stands in for a page that is not ours. */
.pfs { position: fixed; inset: 0; z-index: 420; display: flex; flex-direction: column; background: #F3F4F6; color: #15161B; font-family: var(--cc-font, system-ui); animation: pfs-up 500ms cubic-bezier(.2, .8, .2, 1) both; }
@keyframes pfs-up { from { transform: translateY(100%) } to { transform: none } }
.pfs-top { flex: none; display: flex; align-items: center; gap: 10px; padding: calc(env(safe-area-inset-top, 0px) + 6px) 16px 6px; min-height: 52px; border-bottom: 1px solid #E3E4E8; }
.pfs-x { width: 40px; height: 40px; border: 0; background: none; border-radius: 20px; color: #4A4B55; display: grid; place-items: center; cursor: pointer; }
.pfs-mid { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 1px; }
.pfs-t { font-size: 14px; font-weight: 600; }
.pfs-u { font-size: 11px; color: #B26A00; font-weight: 600; }
.pfs-sp { width: 40px; }
.pfs-body { flex: 1; overflow-y: auto; padding: 20px; display: flex; flex-direction: column; gap: 16px; }
.pfs-card { border-radius: 20px; background: #fff; border: 1px solid #E3E4E8; padding: 18px; display: flex; flex-direction: column; gap: 14px; }
.pfs-who { display: flex; align-items: center; gap: 12px; }
.pfs-who div { display: flex; flex-direction: column; gap: 2px; }
.pfs-who b { font-size: 15px; font-weight: 600; }
.pfs-who span { font-size: 12.5px; color: #6A6B75; }
.pfs-logo { width: 40px; height: 40px; border-radius: 11px; object-fit: cover; }
.pfs-amt { font-family: var(--cc-mono, monospace); font-size: 34px; font-weight: 600; letter-spacing: -0.02em; }
.pfs-sub { margin: 0; font-size: 13px; color: #6A6B75; }
.pfs-h { padding: 4px 4px 0; font-size: 12px; font-weight: 600; letter-spacing: 0.12em; color: #7A7B85; }
.pfs-rows { padding: 0; gap: 0; overflow: hidden; }
.pfs-row { height: 56px; padding: 0 16px; display: flex; align-items: center; gap: 12px; }
.pfs-row + .pfs-row { border-top: 1px solid #EDEEF1; }
.pfs-rl { flex: 1; font-size: 14.5px; font-weight: 500; }
.pfs-ring { width: 20px; height: 20px; border-radius: 10px; border: 1.5px solid #C5C6CC; }
.pfs-row.is-sel .pfs-ring { border: 6px solid #15161B; }
.pfs-foot { flex: none; padding: 16px 20px calc(env(safe-area-inset-bottom, 0px) + 20px); display: flex; flex-direction: column; gap: 10px; }
.pfs-foot p { margin: 0; text-align: center; font-size: 12px; color: #7A7B85; }
.pfs-pay { height: 56px; border: 0; border-radius: 16px; background: #15161B; color: #fff; display: flex; align-items: center; justify-content: center; gap: 10px; font: inherit; font-size: 16px; font-weight: 600; cursor: pointer; }
.pfs-pay:disabled { opacity: 0.85; }
.pfs-spin { width: 18px; height: 18px; border-radius: 50%; border: 2px solid rgba(255, 255, 255, 0.3); border-top-color: #fff; animation: pfs-spin 0.8s linear infinite; }
@keyframes pfs-spin { to { transform: rotate(360deg) } }
@media (prefers-reduced-motion: reduce) { .pfs { animation: none } .pfs-spin { animation-duration: 2.4s } }
</style>
