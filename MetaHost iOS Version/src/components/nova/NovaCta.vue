<template>
  <button
    class="nv-cta"
    :class="{ 'nv-cta-disabled': disabled && !busy, 'nv-cta-busy': busy }"
    :disabled="disabled || busy"
    :aria-busy="busy || undefined"
    type="button"
    @click="$emit('click')"
  >
    <slot />
  </button>
</template>

<script setup>
/**
 * @description Primary pill CTA.
 *
 * 17px semibold is the iOS button label size, up from the design's 16px.
 * The press state is a scale-down rather than a colour shift, which is what
 * UIKit does and what the design's `style-active` hinted at.
 */
defineProps({
  disabled: { type: Boolean, default: false },
  /**
   * Working, as opposed to unavailable. Both block the press, but only
   * `disabled` drains the colour — a greyed-out button mid-request reads as
   * "this stopped being possible", which is the opposite of what is true.
   */
  busy: { type: Boolean, default: false },
})

defineEmits(['click'])
</script>

<style scoped>
.nv-cta {
  display: block;
  width: 100%;
  min-height: 52px;
  padding: 0 20px;
  border: 0;
  border-radius: 999px;
  background: var(--nv-accent-grad);
  color: var(--nv-on-accent);
  font-family: inherit;
  font-size: 17px;
  font-weight: 600;
  letter-spacing: -0.2px;
  cursor: pointer;
  box-shadow: var(--nv-shadow-cta);
  transition: transform 140ms var(--nv-ease-out), opacity 140ms linear;
  animation: nhUp 460ms var(--nv-ease) 290ms both;
}

.nv-cta:active { transform: scale(0.97); }

.nv-cta-disabled,
.nv-cta:disabled:not(.nv-cta-busy) {
  background: var(--nv-disabled-fill);
  color: var(--nv-ink-disabled);
  cursor: not-allowed;
  box-shadow: none;
}

/* Keeps the gradient and the shadow; only the press and the pointer change. */
.nv-cta-busy {
  cursor: wait;
  opacity: 0.9;
}

.nv-cta-disabled:active,
.nv-cta-busy:active,
.nv-cta:disabled:active { transform: none; }
</style>
