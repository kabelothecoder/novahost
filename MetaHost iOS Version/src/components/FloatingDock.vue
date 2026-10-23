<template>
  <!--
    Floating glass pill, four destinations.

    The active tab is marked by a FILLED accent lozenge behind the icon
    and label, not by an ink change alone. That is the difference between
    this and the previous version: colour-only selection reads as "this
    label is a slightly different grey" on an OLED phone in daylight, and
    on the mentor accents that sit low in luminance (Rust, Forest) it was
    almost invisible against the glass. The lozenge is a shape, and a
    shape survives sunlight, a dark backdrop and a low-chroma accent.

    It is drawn as a per-tab ::before rather than one sliding pill,
    because a slider has to MEASURE the target tab — and the measurement
    is wrong on first paint, wrong again after a font swap, and wrong for
    the whole of a backgrounded tab where rAF is throttled. Four cheap
    capsules that cross-fade cannot desynchronise from the thing they
    mark.
  -->
  <nav class="dock cc-thin" role="navigation" aria-label="Main navigation">
    <RouterLink
      v-for="item in tabs"
      :key="item.to"
      :to="item.to"
      class="dock-tab"
      :class="{ active: isActive(item.to) }"
      :aria-label="item.label"
      :aria-current="isActive(item.to) ? 'page' : undefined"
    >
      <span class="dock-icon" v-html="item.icon" />
      <span class="dock-label">{{ item.label }}</span>
    </RouterLink>
  </nav>
</template>

<script setup>
import { useRoute } from 'vue-router'

const route = useRoute()
const isActive = (to) => route.path === to || (to === '/home' && route.path === '/')

/* Stroke width is set in CSS, not on the icons: the doc's rule is
   "filled = active, outline 300 = rest", and the closest honest
   equivalent for a stroke icon set is a weight change plus the accent.
   Leaving it out of the markup keeps that one rule in one place.

   Geometry is on a 24 grid with round caps and joins throughout, so the
   four read as one family at 22px. The Settings cog is the one that
   changed: the Feather gear is a 12-node path whose teeth collapse into
   a grey smudge at this size on a 3x screen. Six teeth drawn as discrete
   strokes stay legible. */
const tabs = [
  {
    to: '/home',
    label: 'Home',
    icon: `<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M3.6 9.6 12 3.2l8.4 6.4v9.3a2 2 0 0 1-2 2H5.6a2 2 0 0 1-2-2z"/><path d="M9.4 20.9v-6.6h5.2v6.6"/></svg>`
  },
  {
    /* The MetaTrader terminal keeps this slot. It was briefly given to
       /trades, which was wrong: this is the dock of the four places the
       product is ABOUT, and connecting a broker is one of them. Trades is
       reached from the Open Trades widget on Home and from the Settings
       hub — both one tap, and both from where the user is already looking
       at the number that makes them want it. */
    to: '/terminal',
    label: 'MT5',
    icon: `<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><rect x="2.6" y="4.2" width="18.8" height="15.6" rx="3.2"/><path d="m7.4 10.2 2.6 2.4-2.6 2.4"/><path d="M13 15.2h3.6"/></svg>`
  },
  {
    to: '/scanner',
    label: 'Scanner',
    icon: `<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M3.2 8.4V5.6a2.4 2.4 0 0 1 2.4-2.4h2.8"/><path d="M15.6 3.2h2.8a2.4 2.4 0 0 1 2.4 2.4v2.8"/><path d="M20.8 15.6v2.8a2.4 2.4 0 0 1-2.4 2.4h-2.8"/><path d="M8.4 20.8H5.6a2.4 2.4 0 0 1-2.4-2.4v-2.8"/><path d="m7.4 14.2 3-3 2.4 2.4 3.8-4.2"/></svg>`
  },
  {
    to: '/settings',
    label: 'Settings',
    /* A cog SILHOUETTE, not a hub with spokes. The first attempt here was
        a circle with eight radiating strokes, which is the universal sun
        glyph — on a trading app whose Settings screen carries a Light/Dark
        switch, an icon that reads "sun" is actively misleading. This is one
        closed outline with eight rounded teeth plus the bore, so it keeps
        its shape at 22px where the 12-node Feather cog smudges. */
    icon: `<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2.6 13.85 4.8l2.85-.5.5 2.85L20 9l-1.45 2.5L20 14l-2.8 1.85-.5 2.85-2.85-.5L12 20.4l-1.85-2.2-2.85.5-.5-2.85L4 14l1.45-2.5L4 9l2.8-1.85.5-2.85 2.85.5z"/><circle cx="12" cy="12" r="3.1"/></svg>`
  },
]
</script>

<style scoped>
.dock {
  position: fixed;
  /*
    Sits on the bottom edge, not floating a third of the way up it.

    It was `safe-bottom + 20px`, which on a notched phone puts the pill
    54px clear of the screen edge — the gap that made it read as hovering
    in the middle of the content rather than as the app's floor. 8px is
    the whole clearance the home indicator actually needs once the safe
    inset has already been added.

    Installed to the Home screen there is no browser toolbar underneath,
    so it tightens again — that is the configuration this app ships in.
  */
  bottom: calc(var(--cc-safe-bottom) + 8px);
  left: 50%;
  transform: translateX(-50%);
  z-index: 200;
  display: flex;
  align-items: stretch;
  width: calc(100% - 28px);
  max-width: 348px;
  padding: 6px 5px;
  border-radius: var(--cc-r-chip);
  /* Material comes from .cc-thin. The accent tint is a wash on top of it
     rather than the background itself — the old dock used
     rgba(--accent-rgb, .15) AS the background, which meant the pill's
     whole colour changed with the swatch and the neon swatches turned it
     into a solid slab. */
  box-shadow:
    inset 0 1px 0 var(--cc-glass-lip),
    0 14px 38px rgba(0, 0, 0, 0.5);
}

@media (display-mode: standalone) {
  .dock { bottom: calc(var(--cc-safe-bottom) + 4px) }
}

.dock-tab {
  position: relative;
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  padding: 7px 2px 6px;
  border-radius: var(--cc-r-chip);
  text-decoration: none;
  color: var(--cc-ink-4);
  -webkit-tap-highlight-color: transparent;
  /* transform only — colour here reads from a scheme token, and a
     transition on a token-fed property keeps painting the previous
     scheme after a Light/Dark swap. */
  transition: transform 200ms var(--cc-spring);
}
.dock-tab:active { transform: scale(0.94) }

/*
  The lozenge.

  Painted on ::before and kept BEHIND its own content (z-index -1 against
  a tab that establishes a stacking context via position: relative), so
  the icon and label are never washed by the accent fill they sit on.

  `opacity` and `transform` only — both are compositor properties, so the
  selection animates on four tabs at once without a layout pass, and
  neither is fed by a custom property, which is what lets this one
  actually transition across a Light/Dark swap when the ink around it
  cannot.
*/
.dock-tab::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: -1;
  border-radius: var(--cc-r-chip);
  background: rgba(var(--cc-accent-rgb), 0.18);
  border: 0.5px solid rgba(var(--cc-accent-rgb), 0.42);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.10),
    0 0 calc(16px * var(--cc-glow)) rgba(var(--cc-accent-rgb), calc(0.42 * var(--cc-glow)));
  opacity: 0;
  transform: scale(0.82);
  transition: opacity 240ms var(--cc-ease), transform 280ms var(--cc-spring);
}
.dock-tab.active::before {
  opacity: 1;
  transform: scale(1);
}

/* Light mode needs more fill, not less: the same 18% accent over a white
   frosted pill is a pastel smudge, where over dark glass it is a clear
   shape. The rim carries most of the definition there. */
.cmd[data-scheme="light"] .dock-tab::before {
  background: rgba(var(--cc-accent-rgb), 0.15);
  border-color: rgba(var(--cc-accent-rgb), 0.55);
}

.dock-tab.active { color: var(--cc-accent-text) }

.dock-icon {
  display: grid;
  place-items: center;
}
/* The doc's "filled = active, outline 300 = rest", expressed for a
   stroke icon set. The drop-shadow is on the glyph rather than the
   lozenge so an accent at Glow: Off still gets a crisp selection —
   the shape carries it, and the halo is the bonus. */
.dock-icon :deep(svg) { stroke-width: 1.6 }
.dock-tab.active .dock-icon :deep(svg) {
  stroke-width: 2.05;
  filter: drop-shadow(0 0 calc(6px * var(--cc-glow)) rgba(var(--cc-accent-rgb), calc(0.7 * var(--cc-glow))));
}

.dock-label {
  font-family: var(--cc-font);
  font-size: 9.5px;
  font-weight: 600;
  letter-spacing: 0.02em;
  line-height: 1;
  /* Four labels on a 375pt phone: "Settings" is the widest and still
     fits, but a longer one must clip rather than wrap the pill open. */
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dock-tab.active .dock-label { font-weight: 700 }

/* Reduced motion keeps the lozenge — it is the affordance, not the
   decoration — and drops only its travel. */
@media (prefers-reduced-motion: reduce) {
  .dock-tab::before { transition: opacity 120ms linear; transform: none }
  .dock-tab.active::before { transform: none }
}
.cmd[data-motion="reduced"] .dock-tab::before { transition: opacity 120ms linear; transform: none }
.cmd[data-motion="reduced"] .dock-tab.active::before { transform: none }
</style>
