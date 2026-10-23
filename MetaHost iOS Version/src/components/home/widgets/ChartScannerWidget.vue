<template>
  <WidgetCard label="AI Chart Scanner">
    <button class="cs" @click="open">
      <span class="cs-icon">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M3 8V5a2 2 0 0 1 2-2h3" /><path d="M16 3h3a2 2 0 0 1 2 2v3" />
          <path d="M21 16v3a2 2 0 0 1-2 2h-3" /><path d="M8 21H5a2 2 0 0 1-2-2v-3" />
          <polyline points="7 14 10 11 13 14 17 9" />
        </svg>
      </span>

      <span class="cs-copy">
        <span class="cs-title">Scan a chart</span>
        <span class="cs-sub">Screenshot in, levels out</span>
      </span>

      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor"
           stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="cs-chev" aria-hidden="true">
        <polyline points="9 18 15 12 9 6" />
      </svg>
    </button>
  </WidgetCard>
</template>

<script setup>
import { useRouter } from 'vue-router'
import WidgetCard from '../WidgetCard.vue'
import { haptic } from '../../../composables/useSpeech.js'

const router = useRouter()

/* Deliberately a doorway, not a dropzone. The scanner screen owns the
   picker, the credit check and ScannerPaywall — duplicating any of that
   here would mean two places that can start a paid analysis. */
function open() {
  haptic(20)
  router.push('/scanner')
}
</script>

<style scoped>
.cs {
  display: flex;
  align-items: center;
  gap: 13px;
  width: 100%;
  padding: 2px 0;
  border: none;
  background: none;
  text-align: left;
  cursor: pointer;
}

.cs-icon {
  flex: none;
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: var(--cc-r-tile);
  color: var(--cc-accent-text);
  background: var(--cc-accent-dim);
  border: 1px solid var(--cc-accent-line);
}

.cs-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px }

.cs-title { font-size: 14px; font-weight: 600; color: var(--cc-ink) }
.cs-sub   { font-size: 11px; color: var(--cc-ink-4) }

.cs-chev { color: var(--cc-ink-5); flex: none }
</style>
