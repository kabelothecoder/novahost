<template>
  <div :key="sheetKey" class="nv-sheet" :class="{ 'nv-sheet-tall': tall }">
    <div class="nv-sheet-body">
      <div class="nv-stagger">
        <div class="nv-step-label">{{ label }}</div>
        <h1 class="nv-title">{{ title }}</h1>
        <p class="nv-body">{{ body }}</p>
      </div>

      <!-- Fields, option rows — anything between the copy and the CTA. -->
      <div v-if="$slots.default" class="nv-sheet-content">
        <slot />
      </div>

      <div class="nv-sheet-spacer" />

      <div class="nv-sheet-action">
        <slot name="action" />
        <div v-if="$slots.secondary" class="nv-sheet-secondary">
          <slot name="secondary" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * @description The frosted step sheet.
 *
 * This is where the Apple treatment departs most from the source design:
 * the design used an opaque #FFFFFF panel, this is a translucent thick
 * material over the live art pane, with the specular top hairline that
 * makes it read as glass rather than as a tinted rectangle.
 */
defineProps({
  /** Eyebrow, e.g. "STEP 03". */
  label:  { type: String, required: true },
  title:  { type: String, required: true },
  body:   { type: String, required: true },
  /** Steps carrying a field or option list need more of the screen. */
  tall:   { type: Boolean, default: false },
  /** Change this to replay the sheet-rise animation. */
  sheetKey: { type: [String, Number], default: 0 },
})
</script>

<style scoped>
.nv-sheet {
  position: relative;
  z-index: 4;
  margin-top: auto;
  /* Never taller than the shell — the body scrolls instead. */
  max-height: 100%;
  display: flex;
  flex-direction: column;
  border-radius: 26px 26px 0 0;

  background: var(--nv-glass-thick);
  -webkit-backdrop-filter: var(--nv-glass-thick-blur);
  backdrop-filter: var(--nv-glass-thick-blur);
  /* Specular lip along the top edge, then the sheet's own drop shadow
     and the accent bleeding out from the art underneath. */
  box-shadow:
    inset 0 1px 0 var(--nv-glass-hairline),
    var(--nv-shadow-sheet),
    var(--nv-glass-bleed);

  animation: nhSheet 520ms var(--nv-ease) both;
}

/* Rim light along the two top corners only — a full border would draw a
   line across the bottom of the screen. */
.nv-sheet::before {
  content: '';
  position: absolute;
  inset: 0 0 auto;
  height: 26px;
  border-radius: 26px 26px 0 0;
  border: 0.5px solid var(--nv-glass-rim);
  border-bottom: 0;
  pointer-events: none;
}

@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .nv-sheet { background: var(--nv-surface); }
}

.nv-sheet-tall { min-height: 62%; }

.nv-sheet-body {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
  /* Bottom padding clears the home indicator on a notched device and
     still leaves the design's 40px breathing room on a flat one. */
  padding: 32px 28px calc(40px + var(--nv-safe-bottom));
  /* Long steps (the how-to list), large Dynamic Type settings and the
     open keyboard can all push past the fold. Scroll inside the sheet
     rather than letting the CTA get clipped by .ob's overflow:hidden. */
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior: contain;
}

.nv-stagger > .nv-step-label { margin-bottom: 12px; }
.nv-stagger > .nv-title      { margin: 0 0 12px; }
.nv-stagger > .nv-body       { margin: 0; }

.nv-sheet-content { margin-top: 22px; }

/* Pushes the CTA to the bottom on tall sheets; collapses to the
   design's 26px gap on short ones. */
.nv-sheet-spacer {
  flex: 1 1 auto;
  min-height: 26px;
}

.nv-sheet-action { flex: none; }

.nv-sheet-secondary {
  display: flex;
  justify-content: center;
  margin-top: 6px;
  animation: nhUp 460ms var(--nv-ease) 350ms both;
}
</style>
