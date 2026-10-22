<template>
  <!--
    One linked broker account. 196px tall, but in the stack only the top 74px
    shows until it is pulled out, so everything that must read while stacked
    (broker, masked login, tag, balance) sits in the header row above the rule.

    The broker tile is a letter on the broker's own tint, not a logo: logos are
    data the backend will supply, and CLAUDE.md forbids hardcoding broker
    marks in the UI.
  -->
  <div
    class="acc-card cc-card"
    :class="{ 'is-active': active }"
    :style="{ '--tint': account.tint }"
  >
    <div class="acc-head">
      <span class="acc-tile" aria-hidden="true">{{ account.broker.charAt(0) }}</span>
      <div class="acc-id">
        <div class="acc-name-row">
          <span class="acc-name">{{ account.broker }}</span>
          <span v-if="active" class="acc-active"><i></i>ACTIVE</span>
        </div>
        <div class="acc-sub">
          <span class="acc-mask">{{ mask }}</span>
          <span v-if="included" class="acc-tag"><svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polyline points="20 6 9 17 4 12"/></svg>Included</span>
          <span v-else class="acc-tag acc-tag-extra">{{ extraText }}</span>
        </div>
      </div>
      <div class="acc-bal">
        <span class="acc-bal-fig">{{ money }}</span>
        <span class="acc-bal-cur">{{ account.currency }}</span>
      </div>
    </div>

    <div class="acc-rule"></div>

    <div class="acc-body">
      <div class="acc-col">
        <span class="cc-label">Robot</span>
        <span class="acc-robot">
          <span class="cc-dot" :class="live ? 'cc-dot-live' : ''" :data-idle="!live"></span>
          {{ robotLine }}
        </span>
      </div>
      <div class="acc-col">
        <span class="cc-label">Server</span>
        <span class="acc-server">{{ account.server }}</span>
      </div>
    </div>

    <p class="acc-hint">{{ hint }}</p>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { maskLogin, formatMoney, ACCOUNT_ADDON } from '../../services/accountsService.js'

const props = defineProps({
  account: { type: Object, required: true },
  active: Boolean,
  included: Boolean,
  running: Boolean,
  robotName: { type: String, default: 'Your robot' },
  hint: { type: String, default: '' },
})

const mask = computed(() => maskLogin(props.account))
const money = computed(() => formatMoney(props.account.balance, props.account.currency))
const live = computed(() => props.active && props.running)
const extraText = `+${ACCOUNT_ADDON.currencySymbol}${ACCOUNT_ADDON.priceMonthly}/mo`
/* Short on purpose: the card is as narrow as 322px in the carousel, and the
   robot's name already appears in the details panel and the screen header. */
const robotLine = computed(() => {
  if (!props.active) return 'Paused'
  return props.running ? 'Trading' : 'Stopped'
})
</script>

<style scoped>
.acc-card {
  position: relative;
  width: 100%;
  height: 100%;
  padding: 0;
  border-radius: 26px;
  overflow: hidden;
  background:
    radial-gradient(220px 200px at 100% 0%, color-mix(in srgb, var(--tint) 24%, transparent), transparent 70%),
    linear-gradient(140deg, color-mix(in srgb, var(--tint) 16%, var(--cc-card)), var(--cc-card) 58%);
  box-shadow: var(--cc-shadow-raised), inset 0 1px 0 rgba(255, 255, 255, 0.12);
  transition: box-shadow 400ms var(--cc-ease);
}
.acc-card.is-active {
  box-shadow:
    0 0 0 1.5px var(--cc-accent),
    0 22px 50px -18px rgba(var(--cc-accent-rgb), 0.6),
    inset 0 1px 0 rgba(255, 255, 255, 0.16);
}

.acc-head {
  position: absolute;
  left: 18px; right: 18px; top: 12px;
  height: 44px;
  display: flex; align-items: center; gap: 12px;
}
.acc-tile {
  flex: none;
  display: grid; place-items: center;
  width: 42px; height: 42px;
  border-radius: 12px;
  background: var(--tint);
  color: #0B0B0D;
  font-size: 19px; font-weight: 800;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.18);
}
.acc-id { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4px; }
.acc-name-row { display: flex; align-items: center; gap: 7px; min-width: 0; }
.acc-name {
  min-width: 36px;
  font-size: 16px; font-weight: 600; color: var(--cc-ink);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.acc-active {
  flex: none;
  display: inline-flex; align-items: center; gap: 5px;
  height: 19px; padding: 0 7px;
  border-radius: 6px;
  background: var(--cc-accent-fill);
  color: var(--cc-accent-fill-ink);
  font-size: 10px; font-weight: 700; letter-spacing: 0.1em;
}
.acc-active i { width: 5px; height: 5px; border-radius: 3px; background: currentColor; }

.acc-sub { display: flex; align-items: center; gap: 7px; }
.acc-mask { font-family: var(--cc-mono); font-size: 12px; color: var(--cc-ink-3); letter-spacing: 0.02em; white-space: nowrap; }
.acc-tag {
  display: inline-flex; align-items: center; gap: 3px;
  height: 18px; padding: 0 6px 0 4px;
  border-radius: 5px;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.14);
  font-size: 10.5px; font-weight: 600; color: var(--cc-ink-2);
  white-space: nowrap;
}
.acc-tag-extra {
  padding: 0 6px;
  background: none;
  border-color: rgba(255, 255, 255, 0.16);
  font-family: var(--cc-mono);
}

.acc-bal { flex: none; display: flex; flex-direction: column; align-items: flex-end; gap: 3px; }
.acc-bal-fig { font-family: var(--cc-mono); font-size: 15px; font-weight: 600; letter-spacing: -0.02em; color: var(--cc-ink); white-space: nowrap; }
.acc-bal-cur { font-family: var(--cc-mono); font-size: 10.5px; letter-spacing: 0.08em; color: var(--cc-ink-4); }

.acc-rule { position: absolute; left: 18px; right: 18px; top: 72px; height: 1px; background: rgba(255, 255, 255, 0.08); }

.acc-body {
  position: absolute; left: 18px; right: 18px; top: 86px;
  display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 14px;
}
.acc-col { display: flex; flex-direction: column; gap: 6px; min-width: 0; }
.acc-robot { display: flex; align-items: center; gap: 7px; font-size: 14px; font-weight: 500; color: var(--cc-ink-2); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.acc-robot .cc-dot[data-idle="true"] { background: var(--cc-idle); box-shadow: none; animation: none; }
.acc-server { font-family: var(--cc-mono); font-size: 12px; line-height: 19px; color: var(--cc-ink-2); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

.acc-hint { position: absolute; left: 18px; right: 18px; top: 150px; margin: 0; font-size: 12.5px; color: var(--cc-ink-3); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
</style>
