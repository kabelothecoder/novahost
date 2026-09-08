<template>
  <!--
    4 · NEON FRAME — "circular art · text nav".

    The identity is the frame itself: an accent-bordered panel holding
    a ringed circular portrait, with the broker link stated plainly
    underneath. Its art mode defaults to `framed`, which is what draws
    the glowing ring in RobotHeroWidget.

    The frame's glow is driven by --cc-glow like everything else, so
    setting Glow to Off leaves a clean bordered panel rather than
    breaking the layout's whole premise.
  -->
  <div class="neon">
    <HomeTopBar @interface="$emit('interface')" @robots="$emit('robots')" />

    <div class="neon-frame">
      <RobotHeroWidget size="md" />

      <IgnitionPod class="neon-pod" @assets="$emit('assets')" @toast="$emit('toast', $event)" />

      <!-- The doc prints "CONNECTED · Exness · MT5 · 41273". None of
           that is on the client until the broker wiring lands, so the
           strip reports the link state it does know and omits the
           server/login detail rather than printing a placeholder
           broker name. -->
      <div class="neon-broker" :class="{ 'is-on': session.brokerConnected }">
        <span class="cc-dot" :class="{ 'cc-dot-live': session.brokerConnected }"></span>
        <span class="neon-broker-label cc-mono">{{ brokerLine }}</span>
      </div>
    </div>

    <div v-if="widgets.length" class="neon-widgets cc-ground cc-stagger">
      <WidgetSlot v-for="id in widgets" :key="id" :id="id" @toast="$emit('toast', $event)" />
    </div>

    <span class="neon-mark cc-label">Powered by Nova Edge</span>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { activeWidgets } from '../../../stores/theme.js'
import { session } from '../../../stores/session.js'
import HomeTopBar from '../HomeTopBar.vue'
import WidgetSlot from '../WidgetSlot.vue'
import RobotHeroWidget from '../widgets/RobotHeroWidget.vue'
import IgnitionPod from '../widgets/IgnitionPod.vue'

defineEmits(['interface', 'robots', 'assets', 'toast'])

const widgets = computed(() => activeWidgets.value)

const brokerLine = computed(() => {
  if (!session.brokerConnected) return 'No broker linked'
  const parts = ['Connected']
  if (session.brokerServer) parts.push(session.brokerServer)
  if (session.brokerLogin)  parts.push(session.brokerLogin)
  return parts.join(' · ')
})
</script>

<style scoped>
.neon {
  position: relative;
  z-index: 1;
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  padding: calc(var(--cc-safe-top) + 8px) 20px calc(var(--cc-safe-bottom) + 108px);
}

.neon-frame {
  margin: 26px 0 0;
  padding: 30px 20px 22px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 24px;
  border: 1px solid var(--cc-accent-line);
  border-radius: var(--cc-r-sheet);
  background: var(--cc-glass-fill);
  -webkit-backdrop-filter: var(--cc-glass-blur);
  backdrop-filter: var(--cc-glass-blur);
  box-shadow:
    var(--cc-glow-edge),
    inset 0 0 calc(28px * var(--cc-glow)) rgba(var(--cc-accent-rgb), calc(0.07 * var(--cc-glow))),
    var(--cc-shadow-raised);
}

@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .neon-frame { background: var(--cc-card) }
}

.neon-pod { max-width: 300px }

.neon-broker {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 7px 14px;
  border-radius: var(--cc-r-chip);
  border: 1px solid var(--cc-border);
  background: var(--cc-card-2);
}
.neon-broker.is-on {
  border-color: var(--cc-accent-line);
  background: var(--cc-accent-dim);
}

.neon-broker-label {
  font-size: 10px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--cc-ink-3);
}

.neon-widgets {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.neon-mark {
  align-self: center;
  margin-top: 22px;
  font-size: 9px;
  letter-spacing: 0.22em;
  color: var(--cc-ink-4);
  position: relative;
  z-index: 1;
}
</style>
