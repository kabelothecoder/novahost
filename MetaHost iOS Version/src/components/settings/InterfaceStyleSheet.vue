<template>
  <!--
    Teleported to <body>, and carrying its own `.cmd` token context.

    Two reasons it cannot render in place. The dock is `position: fixed;
    z-index: 200` and rendered after the RouterView, so at equal z-index
    it wins on DOM order and paints over the modal. Raising the number
    alone would not be enough either: the route transition puts a
    `transform` on the view, and a transformed ancestor forms a stacking
    context that no descendant z-index can escape.

    Teleporting leaves the `.cmd` ancestor behind, which is where every
    --cc-* token is declared, so the root repeats the class and the three
    data attributes.
  -->
  <Teleport to="body">
  <div
    class="cmd isheet-root"
    :data-scheme="resolvedScheme"
    :data-card="theme.cardStyle"
    :data-motion="theme.motion"
    role="dialog"
    aria-modal="true"
    aria-label="Interface style"
  >
    <div class="isheet-scrim" @click="$emit('close')"></div>

    <div class="isheet">
      <header class="isheet-head">
        <span class="isheet-grab"></span>
        <div class="isheet-title-row">
          <h2 class="cc-heading">Interface Style</h2>
          <button class="isheet-x" aria-label="Close" @click="$emit('close')">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 stroke-width="2" stroke-linecap="round" aria-hidden="true">
              <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>

        <div class="isheet-tabs" role="tablist">
          <button
            v-for="t in TABS"
            :key="t.id"
            class="isheet-tab"
            :class="{ 'is-on': pane === t.id }"
            role="tab"
            :aria-selected="pane === t.id"
            @click="pane = t.id"
          >{{ t.name }}</button>
        </div>
      </header>

      <div class="isheet-body">
        <!-- ── LAYOUTS ────────────────────────────────────────────── -->
        <section v-if="pane === 'layouts'" class="isheet-pane">
          <div class="isheet-grid">
            <LayoutThumb
              v-for="l in LAYOUTS"
              :key="l.id"
              :id="l.id"
              :name="l.name"
              :active="theme.layout === l.id"
              @pick="pickLayout"
            />
          </div>

          <p class="cc-caption isheet-blurb">{{ activeLayout.blurb }}</p>

          <!-- Art mode is contextual, not a top-level setting: it is
               stored per layout, so this card always edits whichever
               layout is selected above. Six per-layout settings stay
               one control. -->
          <div class="isheet-card cc-card">
            <div class="isheet-card-head">
              <span class="cc-label">Robot Art</span>
              <span class="cc-caption">{{ activeLayout.name }}</span>
            </div>
            <p class="cc-caption isheet-card-blurb">
              How your mentor's art fills this layout. Saved per layout.
            </p>
            <div class="isheet-seg isheet-seg-3">
              <button
                v-for="a in ART_MODES"
                :key="a.id"
                class="isheet-seg-btn"
                :class="{ 'is-on': activeArtMode === a.id }"
                @click="setArt(a.id)"
              >{{ a.name }}</button>
            </div>
          </div>

          <button class="isheet-row cc-card" @click="goArrange">
            <span class="isheet-row-copy">
              <span class="isheet-row-title">Arrange Widgets</span>
              <span class="cc-caption">{{ arrangeSummary }}</span>
            </span>
            <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="isheet-chev" aria-hidden="true">
              <polyline points="9 18 15 12 9 6" />
            </svg>
          </button>
        </section>

        <!-- ── COLOURS ────────────────────────────────────────────── -->
        <section v-else-if="pane === 'colours'" class="isheet-pane">
          <div class="isheet-card cc-card">
            <div class="isheet-card-head">
              <span class="cc-label">Accent Colour</span>
              <span class="cc-caption">{{ theme.accent.name }}</span>
            </div>
            <p class="cc-caption isheet-card-blurb">
              Drives every glow, border and control across all six layouts.
            </p>

            <!-- Offered as unavailable rather than hidden when no licence
                 has been validated on this device — a missing option is
                 more confusing than a disabled one that says why. -->
            <div class="isheet-seg isheet-seg-2">
              <button
                class="isheet-seg-btn"
                :class="{ 'is-on': isMentorAccent }"
                :disabled="!theme.mentorAccent"
                @click="useMentorAccent()"
              >Mentor default</button>
              <button
                class="isheet-seg-btn"
                :class="{ 'is-on': !isMentorAccent }"
                @click="theme.accentOverridden = true"
              >My override</button>
            </div>
            <p v-if="!theme.mentorAccent" class="cc-caption isheet-note">
              No mentor colour on this device — activate a licence key to receive one.
            </p>

            <div class="isheet-swatches">
              <button
                v-for="sw in SWATCHES"
                :key="sw.name"
                class="isheet-swatch"
                :class="{ 'is-on': theme.accent.name === sw.name }"
                :style="{ background: sw.hex }"
                :title="sw.name"
                :aria-label="sw.name"
                @click="pickAccent(sw)"
              >
                <svg v-if="theme.accent.name === sw.name" width="14" height="14" viewBox="0 0 24 24"
                     fill="none" :stroke="sw.ink" stroke-width="3.5" stroke-linecap="round" aria-hidden="true">
                  <polyline points="20 6 9 17 4 12" />
                </svg>
              </button>
            </div>
          </div>

          <div class="isheet-card cc-card">
            <div class="isheet-card-head">
              <span class="cc-label">Glow Mode</span>
              <span class="cc-caption">{{ activeGlow.name }}</span>
            </div>
            <p class="cc-caption isheet-card-blurb">{{ activeGlow.blurb }}</p>
            <div class="isheet-seg">
              <button
                v-for="g in GLOW_MODES"
                :key="g.id"
                class="isheet-seg-btn"
                :class="{ 'is-on': theme.glowMode === g.id }"
                @click="theme.glowMode = g.id"
              >{{ g.name }}</button>
            </div>
          </div>

          <div class="isheet-card cc-card">
            <div class="isheet-card-head">
              <span class="cc-label">Card Style</span>
              <span class="cc-caption">{{ activeCard.name }}</span>
            </div>
            <p class="cc-caption isheet-card-blurb">{{ activeCard.blurb }}</p>
            <div class="isheet-seg isheet-seg-3">
              <button
                v-for="c in CARD_STYLES"
                :key="c.id"
                class="isheet-seg-btn"
                :class="{ 'is-on': theme.cardStyle === c.id }"
                @click="theme.cardStyle = c.id"
              >{{ c.name }}</button>
            </div>
          </div>

          <!-- Gradient and Background are the doc's NAMED ROWS: name and
               description beside every colour, nothing clipped. A grid of
               dots cannot carry these — "Rust" and "Forest" are both dark
               and unreadable at 22px, and a gradient is not a colour you
               can sample at one point. -->
          <span class="cc-label isheet-sec">Gradient accent</span>
          <p class="cc-caption isheet-sec-blurb">
            Paints the ignition button. Borders and glows keep the flat accent.
          </p>
          <div class="isheet-list cc-card">
            <button
              v-for="g in GRADIENTS"
              :key="g.id"
              class="isheet-pick"
              @click="pick('gradient', g.id)"
            >
              <span class="isheet-pick-dot" :style="{ background: g.css || theme.accent.hex }"></span>
              <span class="isheet-pick-copy">
                <span class="isheet-pick-name">{{ g.name }}</span>
                <span class="cc-caption">{{ g.blurb }}</span>
              </span>
              <span v-if="theme.gradient === g.id" class="isheet-tick" v-html="TICK"></span>
            </button>
          </div>

          <span class="cc-label isheet-sec">Background</span>
          <p class="cc-caption isheet-sec-blurb">
            <template v-if="toneApplies">Base tone behind every screen.</template>
            <template v-else>
              Applies to the dark scheme only — light has its own canvas, so
              nothing here changes while Light is on.
            </template>
          </p>
          <div class="isheet-list cc-card">
            <button
              v-for="t in TONES"
              :key="t.id"
              class="isheet-pick"
              @click="pick('tone', t.id)"
            >
              <!-- The tone's own ladder, not just its canvas: two steps
                   side by side are what tell Obsidian from Slate. -->
              <span class="isheet-pick-dot" :style="{ background: `linear-gradient(135deg, ${t.canvas} 50%, ${t.card2} 50%)` }"></span>
              <span class="isheet-pick-copy">
                <span class="isheet-pick-name">{{ t.name }}</span>
                <span class="cc-caption">{{ t.blurb }}</span>
              </span>
              <span v-if="theme.tone === t.id" class="isheet-tick" v-html="TICK"></span>
            </button>
          </div>
        </section>

        <!-- ── TYPE ───────────────────────────────────────────────── -->
        <section v-else-if="pane === 'type'" class="isheet-pane">
          <div class="isheet-card cc-card">
            <div class="isheet-card-head">
              <span class="cc-label">Typeface</span>
              <span class="cc-caption">{{ activeNameFont.name }}</span>
            </div>
            <p class="cc-caption isheet-card-blurb">
              The doc's rule: set it in the real face. Each row is drawn by the
              same rule Home uses, so a preview cannot lie about the result.
            </p>
          </div>

          <div class="isheet-list cc-card">
            <button
              v-for="f in NAME_FONTS"
              :key="f.id"
              class="isheet-pick"
              :data-name-font="f.id"
              @click="pick('nameFont', f.id)"
            >
              <span class="isheet-pick-copy">
                <!-- robot.name is a getter that already falls back to 'MetaBot', so the
                     `|| 'Nova Edge'` that used to be here was both unreachable AND a
                     brand string the rename missed. One fallback, in the store. -->
                <span class="isheet-sample cc-name">{{ robot.name }}</span>
                <span class="cc-caption">{{ f.name }} · {{ f.face }}</span>
              </span>
              <span v-if="theme.nameFont === f.id" class="isheet-tick" v-html="TICK"></span>
            </button>
          </div>

          <div class="isheet-card cc-card">
            <div class="isheet-card-head">
              <span class="cc-label">Name size</span>
              <span class="cc-caption">{{ theme.nameScale.toFixed(1) }}×</span>
            </div>
            <p class="cc-caption isheet-card-blurb">
              A multiplier, not a fixed size — each layout draws the name at its
              own scale and this moves all of them together.
            </p>
            <input
              class="isheet-range"
              type="range"
              :min="NAME_SCALE.min"
              :max="NAME_SCALE.max"
              :step="NAME_SCALE.step"
              :value="theme.nameScale"
              aria-label="Name size"
              @input="setNameScale($event.target.value)"
            />
            <div class="isheet-ticks cc-mono">
              <span>{{ NAME_SCALE.min.toFixed(1) }}×</span>
              <span>{{ NAME_SCALE.max.toFixed(1) }}×</span>
            </div>
          </div>

          <span class="cc-label isheet-sec">Name colour</span>
          <div class="isheet-list cc-card">
            <button
              v-for="c in NAME_COLORS"
              :key="c.id"
              class="isheet-pick"
              :data-name-color="c.id"
              @click="pick('nameColor', c.id)"
            >
              <!-- Painted by --cc-name-color inside this row's own
                   attribute, so the dot is literally the colour the name
                   will take. -->
              <span class="isheet-pick-dot is-name"></span>
              <span class="isheet-pick-copy">
                <span class="isheet-pick-name">{{ c.name }}</span>
                <span class="cc-caption">{{ c.blurb }}</span>
              </span>
              <span v-if="theme.nameColor === c.id" class="isheet-tick" v-html="TICK"></span>
            </button>
          </div>
        </section>

        <!-- ── MODES ──────────────────────────────────────────────── -->
        <section v-else class="isheet-pane">
          <div class="isheet-card cc-card">
            <div class="isheet-card-head">
              <span class="cc-label">Colour Scheme</span>
              <span class="cc-caption">{{ schemeCaption }}</span>
            </div>
            <p class="cc-caption isheet-card-blurb">
              Dark is the default. Light applies to every layout.
            </p>
            <div class="isheet-seg isheet-seg-3">
              <button
                v-for="s in SCHEMES"
                :key="s.id"
                class="isheet-seg-btn"
                :class="{ 'is-on': theme.scheme === s.id }"
                @click="theme.scheme = s.id"
              >{{ s.name }}</button>
            </div>
          </div>

          <div class="isheet-card cc-card">
            <div class="isheet-card-head">
              <span class="cc-label">Motion</span>
              <span class="cc-caption">{{ theme.motion === 'reduced' ? 'Reduced' : 'Full' }}</span>
            </div>
            <p class="cc-caption isheet-card-blurb">
              Reduced stops the drifting backdrop and entrance animations.
              Your device's own reduced-motion setting is always respected.
            </p>
            <div class="isheet-seg isheet-seg-2">
              <button class="isheet-seg-btn" :class="{ 'is-on': theme.motion === 'full' }"
                      @click="theme.motion = 'full'">Full</button>
              <button class="isheet-seg-btn" :class="{ 'is-on': theme.motion === 'reduced' }"
                      @click="theme.motion = 'reduced'">Reduced</button>
            </div>
          </div>

          <p class="cc-caption isheet-note">
            These preferences are stored on this device only. Reinstalling the
            app clears your arrangement and colours.
          </p>
        </section>
      </div>
    </div>
  </div>
  </Teleport>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  theme, LAYOUTS, ART_MODES, GLOW_MODES, CARD_STYLES, SCHEMES, SWATCHES,
  GRADIENTS, TONES, NAME_FONTS, NAME_COLORS, NAME_SCALE,
  activeLayout, activeArtMode, activeNameFont, toneApplies,
  setAccent, setNameScale, useMentorAccent, isMentorAccent, resolvedScheme,
  REORDERABLE,
} from '../../stores/theme.js'
import { robot } from '../../stores/robot.js'
import { haptic } from '../../composables/useSpeech.js'
import LayoutThumb from './LayoutThumb.vue'

const emit = defineEmits(['close'])
const router = useRouter()

const TABS = [
  { id: 'layouts', name: 'Layout'  },
  { id: 'colours', name: 'Colour'  },
  { id: 'type',    name: 'Type'    },
  { id: 'modes',   name: 'Modes'   },
]

/* Which tab to open on. Settings' hub rows each name the tab that owns
   them, so tapping "Gradient accent" lands on Colour rather than on
   Layout with the setting somewhere below the fold. Home opens the sheet
   with no tab and gets the default.

   Validated rather than trusted: an id that no longer exists would
   render a sheet with every pane hidden and no way to tell why. */
const props = defineProps({
  tab: { type: String, default: 'layouts' },
})
const pane = ref(TABS.some(t => t.id === props.tab) ? props.tab : 'layouts')

const activeGlow = computed(() => GLOW_MODES.find(g => g.id === theme.glowMode) || GLOW_MODES[2])
const activeCard = computed(() => CARD_STYLES.find(c => c.id === theme.cardStyle) || CARD_STYLES[0])

const schemeCaption = computed(() => {
  const s = SCHEMES.find(x => x.id === theme.scheme)
  return theme.scheme === 'system' ? 'Follows device' : (s?.name || 'Dark')
})

const arrangeSummary = computed(() => {
  const cfg = theme.widgets[theme.layout]
  if (!cfg) return ''
  const shown = REORDERABLE.length - cfg.hidden.length
  return `${shown} shown · ${cfg.hidden.length} hidden`
})

function pickLayout(id) {
  if (theme.layout === id) return
  theme.layout = id
  haptic(30)
}

function setArt(id) {
  activeArtMode.value = id
  haptic(20)
}

function pickAccent(sw) {
  setAccent(sw)
  haptic(20)
}

/* One writer for the four plain enum settings. Each is a validated id
   straight from its own registry — the v-for is the only caller — so
   there is nothing to re-check here. */
function pick(key, id) {
  if (theme[key] === id) return
  theme[key] = id
  haptic(20)
}

const TICK = `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polyline points="20 6 9 17 4 12"/></svg>`

function goArrange() {
  emit('close')
  router.push('/arrange-widgets')
}
</script>

<style scoped>
.isheet-root {
  position: fixed;
  inset: 0;
  /* Above the floating dock, which is also fixed at 200. */
  z-index: 400;
  display: flex;
  align-items: flex-end;
}

.isheet-scrim {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.55);
  -webkit-backdrop-filter: blur(3px);
  backdrop-filter: blur(3px);
  animation: ccFade 220ms var(--cc-ease) both;
}
.cmd[data-scheme="light"] .isheet-scrim { background: rgba(20, 22, 26, 0.34) }

.isheet {
  position: relative;
  width: 100%;
  max-height: 88dvh;
  display: flex;
  flex-direction: column;
  border-radius: var(--cc-r-sheet) var(--cc-r-sheet) 0 0;
  background: var(--cc-canvas-2);
  border-top: 0.5px solid var(--cc-glass-rim);
  box-shadow: var(--cc-shadow-sheet);
  animation: ccSheet 300ms var(--cc-ease) both;
}

/* ── Head ────────────────────────────────────────────────────────── */
.isheet-head {
  flex: none;
  padding: 10px 20px 0;
  border-bottom: 1px solid var(--cc-border-soft);
}
.isheet-grab {
  display: block;
  width: 36px;
  height: 4px;
  margin: 0 auto 12px;
  border-radius: 2px;
  background: var(--cc-border-strong);
}
.isheet-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.isheet-x {
  display: grid;
  place-items: center;
  width: 32px; height: 32px;
  border: none; background: none;
  color: var(--cc-ink-3);
  cursor: pointer;
}

.isheet-tabs {
  display: flex;
  gap: 4px;
  margin-top: 14px;
}
.isheet-tab {
  flex: 1;
  padding: 9px 4px;
  border: none;
  background: none;
  border-bottom: 2px solid transparent;
  font-family: var(--cc-font);
  font-size: 13px;
  font-weight: 600;
  color: var(--cc-ink-4);
  cursor: pointer;
}
.isheet-tab.is-on {
  color: var(--cc-accent-text);
  border-bottom-color: var(--cc-accent);
}

/* ── Body ────────────────────────────────────────────────────────── */
.isheet-body {
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  padding: 18px 20px calc(var(--cc-safe-bottom) + 24px);
}
.isheet-pane { display: flex; flex-direction: column; gap: 14px }

.isheet-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.isheet-blurb { margin: -4px 0 0 }

.isheet-card { padding: 14px 16px 16px }
.isheet-card-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 6px;
}
.isheet-card-blurb { margin: 0 0 12px }
.isheet-note { margin: 0; color: var(--cc-ink-4) }

/* ── Segmented control ───────────────────────────────────────────── */
.isheet-seg {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 4px;
  padding: 4px;
  border-radius: var(--cc-r-chip);
  background: var(--cc-track);
  border: 1px solid var(--cc-border-soft);
}
/* Column count is declared, not inferred. `:has(> :nth-child(3):last-child)`
   would have read the group size automatically but only lands on Safari
   15.4+, and this is a PWA whose whole point is running on whatever
   iPhone the user has. */
.isheet-seg-2 { grid-template-columns: repeat(2, 1fr) }
.isheet-seg-3 { grid-template-columns: repeat(3, 1fr) }

.isheet-seg-btn {
  padding: 8px 4px;
  border: none;
  border-radius: var(--cc-r-chip);
  background: none;
  font-family: var(--cc-font);
  font-size: 12px;
  font-weight: 600;
  color: var(--cc-ink-4);
  cursor: pointer;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.isheet-seg-btn.is-on {
  color: var(--cc-accent-ink);
  background: var(--cc-accent);
}
.isheet-seg-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* ── Swatches ────────────────────────────────────────────────────── */
.isheet-swatches {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 8px;
  margin-top: 14px;
}
.isheet-swatch {
  aspect-ratio: 1;
  display: grid;
  place-items: center;
  border-radius: 50%;
  border: 2px solid transparent;
  cursor: pointer;
  padding: 0;
}
.isheet-swatch.is-on {
  border-color: var(--cc-ink);
  box-shadow: var(--cc-glow-edge);
}

/* ── Named rows ──────────────────────────────────────────────────────
   The doc's "swatches are named rows": one panel, hairline dividers,
   name and description beside every option. Used for the two settings a
   dot cannot describe on its own — a gradient has no single colour to
   sample, and Obsidian, Slate and Teal black are all near-black. */
.isheet-sec { margin: 6px 2px -6px }
.isheet-sec-blurb { margin: 0 2px }

.isheet-list {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: var(--cc-r-card-lg);
}
.isheet-pick {
  display: flex;
  align-items: center;
  gap: 14px;
  width: 100%;
  padding: 13px 16px;
  border: none;
  background: none;
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  transition: background 160ms var(--cc-ease);
}
.isheet-pick + .isheet-pick { border-top: 1px solid var(--cc-border-soft) }
.isheet-pick:active { background: var(--cc-accent-dim) }

.isheet-pick-dot {
  flex: none;
  width: 28px; height: 28px;
  border-radius: 50%;
  box-shadow: inset 0 0 0 1px var(--cc-glass-rim);
}
/* Reads --cc-name-color from this row's own data-name-color, so the dot
   is the exact colour the name will take rather than a copy of it. */
.isheet-pick-dot.is-name { background: var(--cc-name-color) }

.isheet-pick-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px }
.isheet-pick-name { font-size: 15px; font-weight: 500; color: var(--cc-ink) }
.isheet-tick { flex: none; color: var(--cc-accent-text); display: grid; place-items: center }

/* The face preview. Sized here rather than left to --cc-name-scale: the
   row is comparing typefaces, and a 1.4x sample would wrap and make the
   comparison about line breaks. */
.isheet-sample {
  font-size: 21px;
  font-weight: 600;
  line-height: 1.25;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ── Range ───────────────────────────────────────────────────────── */
.isheet-range {
  -webkit-appearance: none;
  appearance: none;
  width: 100%;
  height: 22px;
  background: none;
  cursor: pointer;
}
.isheet-range::-webkit-slider-runnable-track {
  height: 4px;
  border-radius: var(--cc-r-chip);
  background: var(--cc-track);
}
.isheet-range::-webkit-slider-thumb {
  -webkit-appearance: none;
  width: 22px; height: 22px;
  margin-top: -9px;
  border-radius: 50%;
  background: var(--cc-accent);
  box-shadow: var(--cc-glow-edge), var(--cc-shadow-card);
}
.isheet-range::-moz-range-track {
  height: 4px;
  border-radius: var(--cc-r-chip);
  background: var(--cc-track);
}
.isheet-range::-moz-range-thumb {
  width: 22px; height: 22px;
  border: none;
  border-radius: 50%;
  background: var(--cc-accent);
}
.isheet-ticks {
  display: flex;
  justify-content: space-between;
  margin-top: 2px;
}

/* ── Row ─────────────────────────────────────────────────────────── */
.isheet-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  text-align: left;
  cursor: pointer;
}
.isheet-row-copy { display: flex; flex-direction: column; gap: 3px; min-width: 0 }
.isheet-row-title { font-size: 14px; font-weight: 600; color: var(--cc-ink) }
.isheet-chev { color: var(--cc-ink-5); flex: none }
</style>
