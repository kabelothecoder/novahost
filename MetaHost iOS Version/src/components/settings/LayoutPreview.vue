<template>
  <!--
    A real miniature of the layout, not a diagram of it.

    The previous version drew six hand-authored SVG wireframes. They were
    accurate the week they were drawn and wrong by the time Command Deck
    shipped, because nothing connected a wireframe to the screen it
    claimed to describe — Focus Engine's thumb showed two stacked bars
    where the real screen is one enormous stacked NAME. This renders the
    same tokens, the same art and the same robot name the layout itself
    uses, so the only way it can lie is if someone changes a layout and
    does not change this file, which is a diff away rather than invisible.

    That matters beyond tidiness: this picker is what mentors show
    prospects, so it is a marketing surface in its own right.

    ── Geometry ─────────────────────────────────────────────────────────
    Everything positional is a PERCENTAGE of the frame, so one definition
    covers a 96px thumb on an SE and a 115px one on a Pro Max. Type is the
    exception and is in fixed px: percentage type would go sub-pixel on
    the small end, and these strings only have to read as type, not be
    read. They are `aria-hidden` for exactly that reason — the accessible
    name comes from the button's own label in LayoutThumb.

    ── What it deliberately shows ───────────────────────────────────────
    The dock. Focus Engine and Full-Bleed Art render chrome-free (see
    DOCKLESS_LAYOUTS in App.vue), and that is a real difference between
    the six that a user is entitled to see BEFORE choosing. Every other
    preview draws the pill.
  -->
  <div
    class="pv"
    :data-layout="id"
    :data-name-font="theme.nameFont"
    :data-name-color="theme.nameColor"
    aria-hidden="true"
  >

    <!-- ART BED — only the layouts whose art mode is `bleed` get the
         full-frame portrait; the rest get the accent wash the real
         HomeBackdrop falls back to. -->
    <template v-if="artMode === 'bleed'">
      <img :src="art" class="pv-bleed" alt="" />
      <span class="pv-scrim" :class="`is-${id}`"></span>
    </template>
    <span v-else class="pv-wash"></span>

    <!-- ═══ CLASSIC CORE ═══════════════════════════════════════════ -->
    <template v-if="id === 'classic'">
      <span class="pv-bar"><i class="pv-b-i" /><i class="pv-b-chip" /><i class="pv-b-i" /></span>
      <img :src="art" class="pv-portrait" alt="" />
      <span class="pv-pod">
        <i class="pv-sat" /><i class="pv-disc" /><i class="pv-sat" />
        <b class="pv-mark">POWERED BY <u>NOVAHOST</u></b>
      </span>
      <span class="pv-card pv-h14" /><span class="pv-card pv-h14" />
    </template>

    <!-- ═══ FOCUS ENGINE ═══════════════════════════════════════════ -->
    <template v-else-if="id === 'focus'">
      <span class="pv-bar"><i class="pv-b-i" /><i class="pv-b-chip" /><i class="pv-b-i" /></span>
      <span class="pv-centre">
        <b class="pv-name pv-name-stack">
          <s v-for="(w, i) in nameLines" :key="i">{{ w }}</s>
        </b>
        <i class="pv-pill" />
        <b class="pv-mark">POWERED BY <u>NOVAHOST</u></b>
      </span>
    </template>

    <!-- ═══ FULL-BLEED ART ═════════════════════════════════════════ -->
    <template v-else-if="id === 'bleed'">
      <span class="pv-bar"><i class="pv-b-i" /><i class="pv-b-chip" /><i class="pv-b-i" /></span>
      <span class="pv-foot">
        <b class="pv-name">{{ name }}</b>
        <i class="pv-rule" />
        <i class="pv-pill" />
        <b class="pv-mark">POWERED BY <u>NOVAHOST</u></b>
      </span>
    </template>

    <!-- ═══ NEON FRAME ═════════════════════════════════════════════ -->
    <template v-else-if="id === 'neon'">
      <span class="pv-bar"><i class="pv-b-i" /><i class="pv-b-chip" /><i class="pv-b-i" /></span>
      <span class="pv-frame">
        <img :src="art" class="pv-ring" alt="" />
      </span>
      <span class="pv-pod">
        <i class="pv-sat" /><i class="pv-disc" /><i class="pv-sat" />
        <b class="pv-mark">POWERED BY <u>NOVAHOST</u></b>
      </span>
      <span class="pv-broker"><i class="pv-dot" /><i class="pv-line" /></span>
    </template>

    <!-- ═══ SIGNAL FEED ════════════════════════════════════════════ -->
    <template v-else-if="id === 'feed'">
      <span class="pv-head">
        <b class="pv-name pv-name-sm">{{ name }}</b>
        <i class="pv-cog" />
      </span>
      <i class="pv-pill pv-pill-wide" />
      <span class="pv-stats"><i v-for="n in 4" :key="n" class="pv-stat" /></span>
      <span class="pv-card pv-h16"><i class="pv-tag" /></span>
      <span class="pv-card pv-h16"><i class="pv-tag is-sell" /></span>
      <span class="pv-card pv-h10" />
    </template>

    <!-- ═══ COMMAND DECK ═══════════════════════════════════════════ -->
    <template v-else-if="id === 'deck'">
      <span class="pv-head">
        <img :src="art" class="pv-chip-av" alt="" />
        <b class="pv-name pv-name-sm">{{ name }}</b>
      </span>
      <span class="pv-bento">
        <span class="pv-cell-tall"><i class="pv-gauge" /></span>
        <i v-for="n in 4" :key="n" class="pv-cell" />
      </span>
      <i class="pv-pill pv-pill-wide" />
      <span class="pv-quad"><i v-for="n in 4" :key="n" class="pv-act" /></span>
      <span class="pv-card pv-h12" /><span class="pv-card pv-h12" />
    </template>

    <!-- The floating dock, where the layout actually has one. -->
    <span v-if="hasDock" class="pv-dock">
      <i class="pv-d-on" /><i class="pv-d" /><i class="pv-d" /><i class="pv-d" />
    </span>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { robot, FALLBACK_AVATAR } from '../../stores/robot.js'
import { theme, LAYOUTS } from '../../stores/theme.js'

const props = defineProps({
  id: { type: String, required: true },
})

/* Reads the SAME per-layout art preference Home reads, so a user who set
   Full-Bleed Art's art mode to `avatar` sees that reflected here rather
   than being shown a screen they have not got. */
const artMode = computed(() =>
  theme.art[props.id] || LAYOUTS.find(l => l.id === props.id)?.defaultArt || 'avatar'
)

const art  = computed(() => robot.avatarUrl || FALLBACK_AVATAR)
const name = computed(() => robot.name || 'MetaBot')

/* Focus Engine sets the name as stacked display type, so the preview has
   to break it the same way the layout does. Capped at two lines: a third
   at this scale is a grey smear, and the layout itself clamps. */
const nameLines = computed(() => name.value.trim().split(/\s+/).slice(0, 2))

/* Kept in step with DOCKLESS_LAYOUTS in App.vue by hand — there are two
   of them and they are named in both files' comments. A shared constant
   would have to live in the theme store, which neither the shell nor a
   preview has any other reason to reach into. */
const hasDock = computed(() => !['focus', 'bleed'].includes(props.id))
</script>

<style scoped>
.pv {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3.5%;
  padding: 6% 8%;
  background: var(--cc-canvas);
  font-family: var(--cc-font);
}

/* ── Beds ─────────────────────────────────────────────────────────── */
.pv-bleed {
  position: absolute; inset: 0;
  width: 100%; height: 100%;
  object-fit: cover;
  /* The new portrait is 576x1024 and its subject sits high; 50% 30%
     frames the face at this crop the way HomeBackdrop does at full size. */
  object-position: 50% 30%;
}
.pv-scrim { position: absolute; inset: 0 }
/* Full-Bleed anchors its copy to a deep bottom scrim; Focus Engine
   washes the whole frame so centred type reads over the middle of the
   art. Same split as the real layouts. */
.pv-scrim.is-bleed {
  background: linear-gradient(180deg, transparent 38%, rgba(0,0,0,.86) 88%);
}
.pv-scrim.is-focus {
  background: linear-gradient(180deg, rgba(0,0,0,.34), rgba(0,0,0,.72));
}
.pv-wash {
  position: absolute; inset: 0;
  background:
    radial-gradient(60% 40% at 18% 6%, rgba(var(--cc-accent-rgb), .20), transparent 70%),
    radial-gradient(70% 45% at 88% 96%, rgba(var(--cc-accent-rgb), .13), transparent 72%);
}

/* Everything drawn sits above the bed.

   .pv-dock is excluded, not incidentally: this selector is four levels
   deep and out-specifies the single .pv-dock class, so `position:
   relative` here beat the `absolute` there and pulled the pill back into
   the column as a shrink-to-fit flex item — it rendered as a lone dot at
   the bottom of four of the six previews. */
.pv > *:not(.pv-bleed):not(.pv-scrim):not(.pv-wash):not(.pv-dock) {
  position: relative;
  z-index: 1;
}

/* ── Chrome ───────────────────────────────────────────────────────── */
.pv-bar {
  display: flex; align-items: center; justify-content: space-between;
  width: 100%; flex: none;
}
.pv-b-i    { width: 11%; aspect-ratio: 1; border-radius: 2px; background: var(--cc-ink-5); opacity: .7 }
.pv-b-chip { width: 40%; height: 8px; border-radius: 4px; background: var(--cc-ink-5); opacity: .5 }

.pv-head {
  display: flex; align-items: center; gap: 5%;
  width: 100%; flex: none; padding-top: 2%;
}
.pv-cog { width: 9%; aspect-ratio: 1; border-radius: 50%; background: var(--cc-ink-5); margin-left: auto }
.pv-chip-av { width: 16%; aspect-ratio: 1; border-radius: 50%; object-fit: cover; flex: none }

/* ── Art ──────────────────────────────────────────────────────────── */
.pv-portrait {
  width: 46%; aspect-ratio: 1;
  border-radius: 18%;
  object-fit: cover; object-position: 50% 30%;
  margin: 4% 0 2%;
  flex: none;
}

.pv-frame {
  display: grid; place-items: center;
  width: 100%; padding: 8% 0;
  border-radius: 8px;
  border: 1px solid var(--cc-accent);
  box-shadow: 0 0 calc(7px * var(--cc-glow)) rgba(var(--cc-accent-rgb), calc(.5 * var(--cc-glow)));
  flex: none;
}
.pv-ring {
  width: 46%; aspect-ratio: 1; border-radius: 50%;
  object-fit: cover; object-position: 50% 26%;
  border: 1.5px solid var(--cc-accent);
}

/* ── Type ─────────────────────────────────────────────────────────── */
/* Face and ink come from the same two tokens .cc-name reads, so the
   Type tab's choices show up here rather than every preview rendering
   the system face in plain ink. */
.pv-name {
  font-family: var(--cc-name-font);
  font-size: 11px; font-weight: 800; line-height: 1.05;
  letter-spacing: -0.02em;
  color: var(--cc-name-color);
  text-align: center;
  max-width: 100%;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.pv-name-sm { font-size: 8px; text-align: left; flex: 1; min-width: 0 }

/* Focus Engine's stacked display name — the identity of that layout, and
   the thing the old wireframe missed entirely. */
/* Mirrors .focus-name / .focus-line in FocusEngine.vue, including its
   one non-obvious rule: on the DEFAULT name colour — and only then —
   that layout accents its first line
   (`[data-name-color="default"] .focus-line:first-child`). A preview
   that hardcoded either behaviour would be wrong for half the settings,
   so the condition is copied along with the colour. */
.pv-name-stack {
  display: flex; flex-direction: column; align-items: center;
  font-size: 13px; letter-spacing: -0.03em; white-space: normal;
  text-transform: uppercase;
}
.pv-name-stack s { text-decoration: none; display: block }
[data-name-color="default"] .pv-name-stack s:first-child {
  color: var(--cc-accent-text);
}

.pv-mark {
  font-size: 4px; font-weight: 700; letter-spacing: .14em;
  color: var(--cc-ink-4);
}
.pv-mark u { text-decoration: none; color: var(--cc-accent-text) }

/* ── Controls ─────────────────────────────────────────────────────── */
.pv-pod {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  place-items: center;
  row-gap: 4px;
  width: 100%;
  padding: 7% 5% 5%;
  border-radius: 7px;
  background: var(--cc-glass-thin);
  border: 0.5px solid var(--cc-glass-rim);
  flex: none;
}
.pv-pod .pv-mark { grid-column: 1 / -1 }
.pv-sat  { width: 42%; height: 5px; border-radius: 2.5px; background: var(--cc-ink-5) }
.pv-disc {
  width: 26px; height: 26px; border-radius: 50%;
  border: 1px solid var(--cc-accent);
  background: radial-gradient(circle at 50% 42%,
    rgba(var(--cc-accent-rgb), .34), rgba(0,0,0,.72));
  box-shadow: 0 0 calc(8px * var(--cc-glow)) rgba(var(--cc-accent-rgb), calc(.55 * var(--cc-glow)));
}

.pv-pill {
  width: 54%; height: 13px; border-radius: 7px;
  background: var(--cc-accent-fill, var(--cc-accent));
  box-shadow: 0 0 calc(9px * var(--cc-glow)) rgba(var(--cc-accent-rgb), calc(.5 * var(--cc-glow)));
  flex: none;
}
.pv-pill-wide { width: 70%; align-self: center }

.pv-centre {
  flex: 1;
  display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  gap: 8%;
  width: 100%;
}
.pv-foot {
  margin-top: auto;
  display: flex; flex-direction: column; align-items: center; gap: 5px;
  width: 100%;
  padding-bottom: 6%;
}
.pv-rule { width: 34%; height: 2px; border-radius: 1px; background: var(--cc-ink-5) }

.pv-broker {
  display: flex; align-items: center; justify-content: center; gap: 4px;
  width: 58%; padding: 3px 0;
  border-radius: 5px;
  background: var(--cc-card);
  border: 1px solid var(--cc-border);
  flex: none;
}
.pv-dot  { width: 3px; height: 3px; border-radius: 50%; background: var(--cc-ink-5) }
.pv-line { width: 52%; height: 2px; border-radius: 1px; background: var(--cc-ink-5) }

/* ── Cards ────────────────────────────────────────────────────────── */
.pv-card {
  width: 100%;
  border-radius: 4px;
  background: var(--cc-card);
  border: 1px solid var(--cc-border);
  flex: none;
  display: flex; align-items: center;
  padding: 0 5%;
}
.pv-h10 { height: 10% }
.pv-h12 { height: 8% }
.pv-h14 { height: 11% }
.pv-h16 { height: 14% }

.pv-tag {
  width: 22%; height: 4px; border-radius: 2px;
  background: var(--cc-buy, #3FD6A8);
}
.pv-tag.is-sell { background: var(--cc-sell, #E0544E) }

.pv-stats { display: flex; gap: 4px; width: 100%; flex: none }
.pv-stat {
  flex: 1; height: 16px; border-radius: 3px;
  background: var(--cc-card); border: 1px solid var(--cc-border);
}

/* The bento IS Command Deck — one tall hero cell carrying the gauge with
   a 2x2 of stat tiles beside it. */
.pv-bento {
  display: grid;
  grid-template-columns: 1fr 1fr;
  grid-template-rows: repeat(2, 1fr);
  gap: 4px;
  width: 100%; height: 24%;
  flex: none;
}
.pv-cell-tall {
  grid-row: 1 / 3;
  display: grid; place-items: center;
  border-radius: 4px;
  background: var(--cc-card); border: 1px solid var(--cc-border);
}
.pv-cell { border-radius: 4px; background: var(--cc-card); border: 1px solid var(--cc-border) }
.pv-gauge {
  width: 58%; aspect-ratio: 1; border-radius: 50%;
  border: 2px solid var(--cc-accent);
  border-right-color: var(--cc-border);
  border-bottom-color: var(--cc-border);
}

.pv-quad { display: flex; gap: 4px; width: 100%; flex: none }
.pv-act {
  flex: 1; height: 18px; border-radius: 4px;
  background: var(--cc-card); border: 1px solid var(--cc-border);
}

/* ── The dock ─────────────────────────────────────────────────────── */
.pv-dock {
  position: absolute;
  left: 8%; right: 8%; bottom: 5%;
  z-index: 2;
  display: flex; align-items: center; gap: 3px;
  padding: 3px;
  border-radius: 8px;
  background: var(--cc-glass-thin);
  border: 0.5px solid var(--cc-glass-rim);
  box-shadow: 0 3px 10px rgba(0, 0, 0, .45);
}
.pv-d    { flex: 1; height: 9px; border-radius: 5px; background: var(--cc-ink-5); opacity: .55 }
/* The active lozenge, same as the real dock — so the preview sells the
   thing that changed about the nav, not just its presence. */
.pv-d-on {
  flex: 1; height: 9px; border-radius: 5px;
  background: rgba(var(--cc-accent-rgb), .30);
  border: 0.5px solid rgba(var(--cc-accent-rgb), .55);
  box-shadow: 0 0 calc(5px * var(--cc-glow)) rgba(var(--cc-accent-rgb), calc(.5 * var(--cc-glow)));
}

/* Layouts that draw a dock must not run their content under it. */
.pv[data-layout="classic"],
.pv[data-layout="neon"],
.pv[data-layout="feed"],
.pv[data-layout="deck"] { padding-bottom: 20% }
</style>
