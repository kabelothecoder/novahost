<template>
  <button
    class="so"
    :class="{ 'so-on': selected }"
    type="button"
    role="radio"
    :aria-checked="selected"
    @click="$emit('select')"
  >
    <span class="so-text">
      <span class="so-label">{{ label }}</span>
      <span class="so-note">{{ note }}</span>
    </span>

    <span class="so-dot" :class="{ 'so-dot-on': selected }">
      <span class="so-dot-inner" />
    </span>
  </button>
</template>

<script setup>
defineProps({
  label:    { type: String, required: true },
  note:     { type: String, required: true },
  selected: { type: Boolean, default: false },
})

defineEmits(['select'])
</script>

<style scoped>
.so {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  min-height: 60px;
  padding: 0 16px;
  text-align: left;
  border: 1.5px solid var(--nv-border-soft);
  border-radius: 16px;
  background: var(--nv-surface);
  font-family: inherit;
  cursor: pointer;
  transition:
    border-color 180ms var(--nv-ease-out),
    background-color 180ms linear,
    transform 140ms var(--nv-ease-out);
}

.so:active { transform: scale(0.985); }

.so-on {
  border-color: var(--nv-accent);
  background: var(--nv-accent-selected);
}

.so-text {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.so-label {
  font-size: 16px;
  font-weight: 600;
  color: var(--nv-ink);
}

.so-note {
  font-size: 12.5px;
  color: var(--nv-ink-2);
}

.so-dot {
  flex: none;
  width: 22px;
  height: 22px;
  border-radius: 999px;
  border: 1.5px solid var(--nv-dot-border);
  background: var(--nv-surface);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: border-color 180ms var(--nv-ease-out), background-color 180ms linear;
}

.so-dot-on {
  border-color: var(--nv-accent);
  background: var(--nv-accent);
}

.so-dot-inner {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: transparent;
  transition: background-color 180ms linear;
}

.so-dot-on .so-dot-inner { background: var(--nv-on-accent); }
</style>
