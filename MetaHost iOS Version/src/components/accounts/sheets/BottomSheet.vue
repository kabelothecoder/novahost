<template>
  <!--
    Shared shell for the accounts sheets: scrim, grab handle, slide-up panel.
    Teleported to <body> with its own `.cmd` token context for the same reason
    RobotSwitcherSheet is: the dock sits above a view's stacking context, and
    the route transition puts a transform on the view that no inner z-index can
    escape.
  -->
  <Teleport to="body">
    <div
      class="cmd bsh-root"
      :data-scheme="resolvedScheme"
      :data-card="theme.cardStyle"
      :data-motion="theme.motion"
      role="dialog"
      aria-modal="true"
      :aria-label="label"
    >
      <div class="bsh-scrim" @click="$emit('close')"></div>
      <div class="bsh" ref="panel" tabindex="-1">
        <span class="bsh-grab" aria-hidden="true"></span>
        <slot />
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { theme, resolvedScheme } from '../../../stores/theme.js'

defineProps({ label: { type: String, default: 'Sheet' } })
const emit = defineEmits(['close'])
const panel = ref(null)

function onKey(e) { if (e.key === 'Escape') emit('close') }
onMounted(() => { window.addEventListener('keydown', onKey); panel.value?.focus({ preventScroll: true }) })
onUnmounted(() => window.removeEventListener('keydown', onKey))
</script>

<style scoped>
.bsh-root { position: fixed; inset: 0; z-index: 400; }
.bsh-scrim {
  position: absolute; inset: 0; background: rgba(4, 4, 6, 0.62);
  -webkit-backdrop-filter: blur(5px); backdrop-filter: blur(5px);
  animation: bsh-fade 300ms ease both;
}
.bsh {
  position: absolute; left: 0; right: 0; bottom: 0;
  max-height: 92dvh; overflow-y: auto; outline: none;
  display: flex; flex-direction: column; gap: 18px;
  padding: 12px 24px calc(var(--cc-safe-bottom) + 26px);
  border-radius: 34px 34px 0 0;
  background: linear-gradient(180deg, rgba(34, 34, 42, 0.96), rgba(16, 16, 20, 0.99));
  -webkit-backdrop-filter: blur(30px); backdrop-filter: blur(30px);
  border-top: 1px solid rgba(255, 255, 255, 0.14);
  box-shadow: var(--cc-shadow-sheet);
  animation: bsh-up 500ms cubic-bezier(.2, .8, .2, 1) both;
}
.bsh-grab { flex: none; align-self: center; width: 38px; height: 5px; border-radius: 3px; background: rgba(255, 255, 255, 0.22); }
@keyframes bsh-up { from { transform: translateY(100%) } to { transform: none } }
@keyframes bsh-fade { from { opacity: 0 } to { opacity: 1 } }
@media (prefers-reduced-motion: reduce) { .bsh, .bsh-scrim { animation: none } }
</style>

<style>
/* Shared by every sheet body; unscoped because the content lives in slots. */
.sh-title { margin: 0; font-size: 22px; line-height: 28px; font-weight: 600; letter-spacing: -0.015em; color: var(--cc-ink); }
.sh-title-c { text-align: center; text-wrap: balance; }
.sh-title span { color: var(--cc-ink-3); }
.sh-lead { margin: 6px 0 0; font-size: 14px; line-height: 20px; color: var(--cc-ink-3); text-wrap: pretty; }
.sh-primary {
  height: 54px; border: 0; border-radius: 18px; cursor: pointer; width: 100%;
  display: flex; align-items: center; justify-content: center; gap: 8px;
  font: inherit; font-size: 16px; font-weight: 600;
  background: var(--cc-accent-fill); color: var(--cc-accent-fill-ink);
  box-shadow: 0 18px 36px -16px var(--cc-accent);
  transition: transform 180ms var(--cc-spring);
}
.sh-primary:active:not(:disabled) { transform: scale(0.985); }
.sh-primary:disabled { opacity: 0.6; }
.sh-primary.is-danger { background: var(--cc-sell); color: #fff; box-shadow: none; }
.sh-ghost { height: 44px; border: 0; background: none; cursor: pointer; font: inherit; font-size: 14px; font-weight: 500; color: var(--cc-ink-3); width: 100%; }
.sh-note { margin: 0; font-size: 13px; line-height: 19px; color: var(--cc-ink-3); text-wrap: pretty; }
.sh-actions { display: flex; flex-direction: column; gap: 6px; }
</style>
