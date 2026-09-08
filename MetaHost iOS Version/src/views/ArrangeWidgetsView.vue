<template>
  <div
    class="cmd arrange"
    :data-scheme="resolvedScheme"
    :data-card="theme.cardStyle"
    :data-motion="theme.motion"
  >
    <header class="ar-head">
      <button class="ar-back" aria-label="Back" @click="$router.back()">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <polyline points="15 18 9 12 15 6" />
        </svg>
      </button>
      <div class="ar-title">
        <h1 class="cc-heading">Arrange Widgets</h1>
        <span class="cc-caption">{{ activeLayout.name }}</span>
      </div>
      <button class="ar-reset" @click="reset">Reset</button>
    </header>

    <div class="ar-body">
      <p class="cc-caption ar-intro">
        Order and visibility save per layout. Hero and ignition are pinned in
        all five.
      </p>

      <!-- ── Pinned ─────────────────────────────────────────────────── -->
      <span class="cc-label ar-section">Pinned</span>
      <div class="ar-list">
        <div v-for="w in pinned" :key="w.id" class="ar-row cc-card is-pinned">
          <span class="ar-lock" aria-hidden="true">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
              <rect x="4" y="11" width="16" height="10" rx="2" />
              <path d="M8 11V7a4 4 0 0 1 8 0v4" />
            </svg>
          </span>
          <span class="ar-copy">
            <span class="ar-name">{{ w.name }}</span>
            <span class="cc-caption">{{ w.desc }}</span>
          </span>
        </div>
      </div>

      <!-- ── Reorderable ────────────────────────────────────────────── -->
      <span class="cc-label ar-section">Order &amp; visibility</span>
      <div class="ar-list">
        <div
          v-for="(id, i) in order"
          :key="id"
          class="ar-row cc-card"
          :class="{ 'is-hidden': isHidden(id) }"
        >
          <!-- Up/down rather than a drag handle. The doc draws
               `drag_indicator`, but HTML5 drag-and-drop does not fire on
               touch at all, and a pointer-events reimplementation is a
               lot of surface to get wrong on the one screen whose entire
               job is not losing the user's arrangement. These also work
               from a keyboard and read correctly to a screen reader. -->
          <span class="ar-move">
            <button
              class="ar-arrow"
              :disabled="i === 0"
              :aria-label="`Move ${nameOf(id)} up`"
              @click="move(i, i - 1)"
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                   stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <polyline points="18 15 12 9 6 15" />
              </svg>
            </button>
            <button
              class="ar-arrow"
              :disabled="i === order.length - 1"
              :aria-label="`Move ${nameOf(id)} down`"
              @click="move(i, i + 1)"
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                   stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <polyline points="6 9 12 15 18 9" />
              </svg>
            </button>
          </span>

          <span class="ar-copy">
            <span class="ar-name">{{ nameOf(id) }}</span>
            <span class="cc-caption">{{ descOf(id) }}</span>
          </span>

          <button
            class="ar-eye"
            :class="{ 'is-on': !isHidden(id) }"
            :aria-pressed="!isHidden(id)"
            :aria-label="`${isHidden(id) ? 'Show' : 'Hide'} ${nameOf(id)}`"
            @click="toggle(id)"
          >
            <svg v-if="!isHidden(id)" width="17" height="17" viewBox="0 0 24 24" fill="none"
                 stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" /><circle cx="12" cy="12" r="3" />
            </svg>
            <svg v-else width="17" height="17" viewBox="0 0 24 24" fill="none"
                 stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94" />
              <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19" />
              <line x1="1" y1="1" x2="23" y2="23" />
            </svg>
          </button>
        </div>
      </div>

      <p class="cc-caption ar-note">
        Preferences are stored on this device only. Reinstalling the app clears
        your arrangement and colours.
      </p>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import {
  theme, resolvedScheme, activeLayout, WIDGETS,
  moveWidget, toggleWidget, resetArrangement,
} from '../stores/theme.js'
import { haptic } from '../composables/useSpeech.js'

const pinned = WIDGETS.filter(w => w.pinned)

/* Always read through the store rather than copying into local state:
   this screen edits the same arrangement Home is rendering from, and a
   local copy would need syncing back on every change. */
const cfg = computed(() => theme.widgets[theme.layout])
const order = computed(() => cfg.value?.order || [])

const byId = Object.fromEntries(WIDGETS.map(w => [w.id, w]))
const nameOf = id => byId[id]?.name || id
const descOf = id => byId[id]?.desc || ''

const isHidden = id => cfg.value?.hidden.includes(id) ?? false

function move(from, to) {
  moveWidget(theme.layout, from, to)
  haptic(20)
}

function toggle(id) {
  toggleWidget(theme.layout, id)
  haptic(20)
}

function reset() {
  resetArrangement(theme.layout)
  haptic([30, 40, 30])
}
</script>

<style scoped>
.arrange {
  width: 100%;
  height: 100dvh;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  background: var(--cc-canvas);
}

/* ── Head ────────────────────────────────────────────────────────── */
.ar-head {
  position: sticky;
  top: 0;
  z-index: 5;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: calc(var(--cc-safe-top) + 10px) 16px 12px;
  background: var(--cc-canvas);
  border-bottom: 1px solid var(--cc-border-soft);
}
.ar-back {
  flex: none;
  display: grid;
  place-items: center;
  width: 36px; height: 36px;
  border: none; background: none;
  color: var(--cc-ink-2);
  cursor: pointer;
}
.ar-title { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 1px }
.ar-title h1 { margin: 0; font-size: 17px }
.ar-reset {
  flex: none;
  border: none; background: none; padding: 6px 4px;
  font-family: var(--cc-font);
  font-size: 13px; font-weight: 600;
  color: var(--cc-accent-text);
  cursor: pointer;
}

/* ── Body ────────────────────────────────────────────────────────── */
.ar-body {
  padding: 16px 16px calc(var(--cc-safe-bottom) + 110px);
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.ar-intro { margin: 0 0 4px }
.ar-note  { margin: 8px 0 0; color: var(--cc-ink-4) }

.ar-section { margin-top: 8px }

.ar-list { display: flex; flex-direction: column; gap: 8px }

.ar-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
}
.ar-row.is-pinned { opacity: 0.72 }
/* A hidden widget stays legible — it is a state, not a disabled control,
   and the user still has to read it to turn it back on. */
.ar-row.is-hidden .ar-name { color: var(--cc-ink-4) }

.ar-lock { flex: none; color: var(--cc-ink-5); display: grid; place-items: center }

.ar-move { flex: none; display: flex; flex-direction: column; gap: 2px }
.ar-arrow {
  display: grid;
  place-items: center;
  width: 28px; height: 22px;
  border: 1px solid var(--cc-border);
  border-radius: 6px;
  background: var(--cc-card-2);
  color: var(--cc-ink-3);
  cursor: pointer;
  padding: 0;
}
.ar-arrow:disabled { opacity: 0.32; cursor: not-allowed }
.ar-arrow:not(:disabled):active { background: var(--cc-accent-dim); color: var(--cc-accent-text) }

.ar-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px }
.ar-name {
  font-size: 14px; font-weight: 600; color: var(--cc-ink);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}

.ar-eye {
  flex: none;
  display: grid; place-items: center;
  width: 36px; height: 36px;
  border: 1px solid var(--cc-border);
  border-radius: var(--cc-r-chip);
  background: none;
  color: var(--cc-ink-4);
  cursor: pointer;
  padding: 0;
}
.ar-eye.is-on {
  color: var(--cc-accent-text);
  border-color: var(--cc-accent-line);
  background: var(--cc-accent-dim);
}
</style>
