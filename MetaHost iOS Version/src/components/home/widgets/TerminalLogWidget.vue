<template>
  <WidgetCard label="Terminal Activity">
    <template #action>
      <button class="tl-more" @click="$router.push('/terminal')">Open</button>
    </template>

    <!-- The log is in-memory for the life of the session, so an empty
         state is the normal first view rather than an error. Saying so
         is better than an empty box that reads as a failed load. -->
    <p v-if="!events.length" class="tl-empty cc-caption">
      No events yet this session.
    </p>

    <ul v-else class="tl-list">
      <li v-for="e in events" :key="e.id" class="tl-row">
        <span class="tl-time cc-figure">{{ clock(e.at) }}</span>
        <span class="tl-dot" :class="`is-${e.kind}`"></span>
        <span class="tl-text">{{ e.text }}</span>
      </li>
    </ul>
  </WidgetCard>
</template>

<script setup>
import { computed } from 'vue'
import WidgetCard from '../WidgetCard.vue'
import { session } from '../../../stores/session.js'

/* The store keeps 60; the widget shows the newest handful. The full run
   belongs to /terminal, which is what the Open action is for. */
const events = computed(() => session.events.slice(0, 5))

function clock(ts) {
  const d = new Date(ts)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}
</script>

<style scoped>
.tl-more {
  border: none;
  background: none;
  padding: 0;
  font-family: var(--cc-font);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.06em;
  color: var(--cc-accent-text);
  cursor: pointer;
}

.tl-empty { margin: 0 }

.tl-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 7px }

.tl-row {
  display: flex;
  align-items: baseline;
  gap: 9px;
  min-width: 0;
}

.tl-time {
  flex: none;
  font-size: 10px;
  color: var(--cc-ink-4);
}

.tl-dot {
  flex: none;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--cc-idle);
  transform: translateY(-1px);
}
.tl-dot.is-ok    { background: var(--cc-live) }
.tl-dot.is-error { background: var(--cc-sell) }
.tl-dot.is-info  { background: var(--cc-accent) }

.tl-text {
  flex: 1;
  min-width: 0;
  font-size: 12px;
  color: var(--cc-ink-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
