<template>
  <div :class="{ 'nf-shake': showError }">
    <div class="nf-field" :class="fieldClass">
      <input
        ref="input"
        class="nf-input"
        type="text"
        :value="modelValue"
        placeholder="Display name"
        :maxlength="maxLength"
        autocomplete="off"
        autocapitalize="words"
        spellcheck="false"
        enterkeyhint="done"
        aria-label="Display name"
        :aria-invalid="showError"
        @input="onInput"
      >

      <!-- Trailing state glyph -->
      <svg v-if="showError" class="nf-glyph" width="20" height="20" viewBox="0 0 20 20" fill="none" aria-hidden="true">
        <circle cx="10" cy="10" r="9" fill="var(--nv-danger)" />
        <path d="M10 5.4 V11" stroke="#FFFFFF" stroke-width="2" stroke-linecap="round" />
        <circle cx="10" cy="14.3" r="1.2" fill="#FFFFFF" />
      </svg>

      <svg v-else-if="valid" class="nf-glyph nf-glyph-pop" width="20" height="20" viewBox="0 0 20 20" fill="none" aria-hidden="true">
        <circle cx="10" cy="10" r="9" fill="var(--nv-success)" />
        <path d="M5.6 10.2 L8.6 13 L14.4 6.9" stroke="#FFFFFF" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
    </div>

    <div class="nf-footer">
      <span class="nf-helper" :style="{ color: showError ? 'var(--nv-danger-text)' : 'var(--nv-ink-3)' }">
        {{ helper }}
      </span>
      <span class="nf-count">{{ modelValue.length }}/{{ maxLength }}</span>
    </div>
  </div>
</template>

<script setup>
/**
 * @description Display-name field carrying the design's three visual states:
 * resting, valid (blue border + green tick) and errored (red border, tinted
 * fill, shake). Validation itself lives in the view so the preview card and
 * the CTA can read the same verdict.
 */
import { computed } from 'vue'

const props = defineProps({
  modelValue: { type: String, required: true },
  valid:      { type: Boolean, required: true },
  showError:  { type: Boolean, required: true },
  helper:     { type: String, required: true },
  maxLength:  { type: Number, default: 18 },
})

const emit = defineEmits(['update:modelValue'])

function onInput (e) {
  emit('update:modelValue', e.target.value.slice(0, props.maxLength))
}

const fieldClass = computed(() => {
  if (props.showError) return 'nf-field-error'
  return props.valid ? 'nf-field-valid' : ''
})
</script>

<style scoped>
.nf-field {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 56px;
  padding: 0 16px;
  border: 1.5px solid var(--nv-border-input);
  border-radius: 16px;
  background: var(--nv-surface-field);
  transition: border-color 180ms var(--nv-ease-out), background-color 180ms linear;
}

.nf-field-valid { border-color: var(--nv-accent); }

.nf-field-error {
  border-color: var(--nv-danger);
  background: var(--nv-danger-tint);
}

.nf-shake { animation: nhShake 380ms var(--nv-ease); }

.nf-input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: none;
  background: transparent;
  padding: 16px 0;
  font-family: inherit;
  /* 16px minimum, or iOS Safari zooms the viewport on focus. */
  font-size: 16px;
  font-weight: 500;
  color: var(--nv-ink);
}

.nf-input::placeholder { color: var(--nv-ink-disabled); }

.nf-glyph { flex: none; }
.nf-glyph-pop { animation: nhPop 320ms var(--nv-spring) both; }

.nf-footer {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-top: 9px;
}

.nf-helper {
  font-size: 12.5px;
  line-height: 18px;
}

.nf-count {
  flex: none;
  font-family: var(--nv-mono);
  font-size: 11px;
  color: var(--nv-ink-disabled);
  font-variant-numeric: tabular-nums;
}
</style>
