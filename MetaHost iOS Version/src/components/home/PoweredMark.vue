<template>
  <!--
    POWERED BY NOVAHOST — the brand mark, with the brand half in colour.

    One component rather than a literal in each layout, because there
    were four copies and three of them still read "Nova Edge" long after
    the rename shipped. A mark that can go stale in one file and not the
    others is a mark that WILL.
  -->
  <span class="pm" :class="`pm-${size}`">
    <span class="pm-pre">Powered by</span>
    <span class="pm-brand">NovaHost</span>
  </span>
</template>

<script setup>
defineProps({
  /** 'sm' under the ignition control, 'xs' as a page footer. */
  size: { type: String, default: 'sm' },
})
</script>

<style scoped>
.pm {
  display: inline-flex;
  align-items: baseline;
  gap: 0.42em;
  font-family: var(--cc-font);
  font-weight: 700;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  white-space: nowrap;
  /*
    The mark sits directly on mentor art in four of the six layouts, and
    art is the one background whose luminance this file cannot know. The
    shadow is what holds it legible over a bright frame — the same reason
    the status line it replaces carried one.
  */
  text-shadow: 0 1px 8px rgba(0, 0, 0, 0.7);
}

.pm-sm { font-size: 10px }
.pm-xs { font-size: 9px; letter-spacing: 0.22em }

/* ink-3, not ink-4: this line sits on the backdrop rather than inside a
   card, and at 10px uppercase ink-4 measures 2.84:1 in light mode —
   under the 4.5 floor. ink-3 clears it at 5.58. */
.pm-pre { color: var(--cc-ink-3) }

/*
  --cc-accent-text, not --cc-accent.

  The raw swatch only clears 4.5:1 against the dark canvas; on the light
  scheme it needs the darker sibling, and `accent-text` is the token that
  already carries that switch. Using --cc-accent here would put a mentor's
  mint or amber on a white canvas at around 1.9:1 — a brand mark nobody
  can read is worse than no brand mark.

  Deliberately NOT the gradient fill. `background-clip: text` over full
  bleed mentor art has no reliable contrast floor at all, and this is the
  one string on the screen that must survive every accent, every gradient
  and every portrait.
*/
.pm-brand {
  color: var(--cc-accent-text);
}

.cmd[data-scheme="light"] .pm {
  text-shadow: 0 1px 8px rgba(255, 255, 255, 0.9);
}
</style>
