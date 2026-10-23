<template>
  <button
    class="thumb"
    :class="{ 'is-active': active }"
    :aria-pressed="active"
    @click="$emit('pick', id)"
  >
    <span class="thumb-frame">
      <!--
        A real miniature of the layout — see LayoutPreview.vue.

        This used to hold six hand-drawn SVG wireframes. The argument for
        them was that a screenshot goes stale the moment the accent, art
        or arrangement changes; that was right about screenshots and wrong
        about the conclusion. A live render reads the same stores the
        layout does, so it tracks all three instead of being immune to
        them — and it shows the user their OWN robot, which is the whole
        point of a picker mentors demo to prospects.
      -->
      <LayoutPreview :id="id" />
    </span>

    <span class="thumb-name">{{ name }}</span>
  </button>
</template>

<script setup>
import LayoutPreview from './LayoutPreview.vue'

defineProps({
  id:     { type: String, required: true },
  name:   { type: String, required: true },
  active: { type: Boolean, default: false },
})
defineEmits(['pick'])
</script>

<style scoped>
.thumb {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 0;
  border: none;
  background: none;
  cursor: pointer;
  min-width: 0;
  -webkit-tap-highlight-color: transparent;
  transition: transform 200ms var(--cc-spring);
}
.thumb:active { transform: scale(0.96) }

.thumb-frame {
  display: block;
  width: 100%;
  aspect-ratio: 60 / 104;
  border-radius: 10px;
  overflow: hidden;
  border: 1.5px solid var(--cc-border);
  background: var(--cc-canvas-2);
  transition: border-color 180ms var(--cc-ease), box-shadow 180ms var(--cc-ease);
  /* The preview inside paints art to the edges; without this the corner
     radius is drawn by the border while the image squares it off. */
  -webkit-mask-image: -webkit-radial-gradient(#fff, #000);
}
.thumb.is-active .thumb-frame {
  border-color: var(--cc-accent);
  box-shadow: var(--cc-glow-edge);
}

.thumb-name {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: -0.005em;
  color: var(--cc-ink-4);
  text-align: center;
  /* Two-word names must not force the 3-column grid wider. */
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}
.thumb.is-active .thumb-name { color: var(--cc-ink) }
</style>
