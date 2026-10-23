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
          <!--
            The way out to the full Settings hub, and it is load-bearing.

            HomeTopBar used to carry two buttons: a sliders icon to /settings
            and this sheet's layers icon. They read as two settings buttons
            and the sliders one is gone — but Full-Bleed Art and Focus Engine
            are DOCKLESS (see DOCKLESS_LAYOUTS in App.vue), so on those two
            layouts it was the only route to Settings on the whole screen.
            Deleting it without this would have traded a duplicate for a dead
            end. Here rather than in one tab's pane because it must be
            reachable from all four.
          -->
          <button class="isheet-more" @click="goSettings">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <circle cx="12" cy="12" r="3.2" />
              <path d="M12 3v2.4M12 18.6V21M3 12h2.4M18.6 12H21M5.6 5.6l1.7 1.7M16.7 16.7l1.7 1.7M18.4 5.6l-1.7 1.7M7.3 16.7l-1.7 1.7" />
            </svg>
            <span>All settings</span>
          </button>
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

          <!-- Falling FX (Home v4, design 2j–2o, 2r). Replaces the per-layout
               art mode and widget arrangement, which the v4 layouts own. -->
          <div class="isheet-card cc-card">
            <div class="isheet-card-head">
              <span class="cc-label">Falling FX</span>
              <span class="cc-caption">{{ activeFx.name }}</span>
            </div>
            <p class="cc-caption isheet-card-blurb">Shapes drift down over your robot.</p>
            <div class="isheet-seg isheet-seg-2">
              <button
                v-for="f in FX_STYLES"
                :key="f.id"
                class="isheet-seg-btn"
                :class="{ 'is-on': theme.fx === f.id }"
                @click="pickFx(f.id)"
              >{{ f.name }}</button>
            </div>
          </div>

          <div class="isheet-card cc-card" :class="{ 'is-muted': theme.fx === 'off' }">
            <div class="isheet-card-head">
              <span class="cc-label">Intensity</span>
            </div>
            <div class="isheet-seg">
              <button
                v-for="i in FX_INTENSITIES"
                :key="i.id"
                class="isheet-seg-btn"
                :class="{ 'is-on': theme.fxIntensity === i.id }"
                :disabled="theme.fx === 'off'"
                @click="pickIntensity(i.id)"
              >{{ i.name }}</button>
            </div>
            <p class="cc-caption isheet-card-blurb">
              Lite mode shows fewer, flat shapes. If your phone asks for reduced motion, Falling FX starts off.
            </p>
          </div>
        </section>

        <!-- ── COLOURS ────────────────────────────────────────────── -->
        <section v-else-if="pane === 'colours'" class="isheet-pane">
          <div class="isheet-card cc-card">
            <div class="isheet-card-head">
              <span class="cc-label">Accent Colour</span>
              <span class="cc-caption">{{ accentCaption }}</span>
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

            <!--
              The spectrum.

              Two rails, not a colour wheel and not three sliders. Hue is
              the obvious axis; the second is INTENSITY, which walks
              saturation and lightness together along the band every preset
              already sits in (S .32-.85 against L .74-.48). A raw S/L pair
              would let the user leave that band and land on an accent that
              fails contrast at one end or the other — see spectrumHex().

              The ink and the two text siblings are derived from whatever
              comes out, with the same maths the presets were measured
              with, so a custom colour is a first-class swatch rather than
              a hex the rest of the app has to cope with.
            -->
            <div class="isheet-spectrum">
              <div class="isheet-spec-head">
                <span class="cc-label">Spectrum</span>
                <span class="isheet-spec-hex cc-mono">{{ specHex }}</span>
              </div>

              <label class="isheet-rail-wrap">
                <span class="sr-only">Hue</span>
                <input
                  class="isheet-rail isheet-rail-hue"
                  type="range" min="0" max="359" step="1"
                  :value="spec.hue"
                  :style="{ '--thumb': specHex }"
                  @input="onSpectrum('hue', $event.target.value)"
                />
              </label>

              <label class="isheet-rail-wrap">
                <span class="sr-only">Intensity</span>
                <input
                  class="isheet-rail"
                  type="range" min="0" max="100" step="1"
                  :value="Math.round(spec.intensity * 100)"
                  :style="{ '--thumb': specHex, '--rail': intensityRail }"
                  @input="onSpectrum('intensity', $event.target.value / 100)"
                />
              </label>

              <p class="cc-caption isheet-spec-note">
                Pale to deep. Every position holds its contrast — the rails
                cannot reach a colour the app would render illegibly.
              </p>
            </div>
          </div>

          <!--
            Glow, with the thing it does next to the control that does it.

            It used to be four words in a segmented control, and three of
            its four settings changed one button on one screen — so it read
            as broken. The orb is the setting applied at full size, and
            everything else that carries the accent now moves with it too
            (see the GLOW REACH block in command.css).
          -->
          <div class="isheet-card cc-card">
            <div class="isheet-card-head">
              <span class="cc-label">Glow Mode</span>
              <span class="cc-caption">{{ activeGlow.name }}</span>
            </div>
            <p class="cc-caption isheet-card-blurb">{{ activeGlow.blurb }}</p>

            <div class="isheet-glow-demo">
              <span
                v-for="g in GLOW_MODES"
                :key="g.id"
                class="isheet-glow-orb"
                :class="{ 'is-on': theme.glowMode === g.id }"
                :style="{ '--g': g.value }"
                aria-hidden="true"
              ></span>
            </div>

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

          <!--
            Card Style, drawn rather than named.

            "Glass / Neon border / Solid" as three words asks the user to
            imagine three materials. Each tile is the real material, built
            from the same tokens the cards on Home use and painted with the
            live accent, so the choice is made by looking at it.
          -->
          <div class="isheet-card cc-card">
            <div class="isheet-card-head">
              <span class="cc-label">Card Style</span>
              <span class="cc-caption">{{ activeCard.name }}</span>
            </div>
            <p class="cc-caption isheet-card-blurb">{{ activeCard.blurb }}</p>
            <div class="isheet-cards">
              <button
                v-for="c in CARD_STYLES"
                :key="c.id"
                class="isheet-card-pick"
                :class="{ 'is-on': theme.cardStyle === c.id }"
                @click="theme.cardStyle = c.id"
              >
                <span class="isheet-card-demo" :data-demo="c.id" aria-hidden="true">
                  <span class="isheet-card-demo-bar"></span>
                  <span class="isheet-card-demo-line"></span>
                  <span class="isheet-card-demo-line is-short"></span>
                </span>
                <span class="isheet-card-name">{{ c.name }}</span>
              </button>
            </div>
          </div>

          <!--
            Gradient and Background were named ROWS: one full-width row per
            option, name and description beside a dot. That was right for
            six gradients and five tones. It is wrong for twenty and
            thirteen — thirty-four rows is four screens of scrolling to
            compare two colours that are never on screen together.

            They are square grids now, which is the whole point of the
            swap: a gradient tile IS the gradient at a size you can judge,
            and a tone tile carries the two ladder steps that tell Obsidian
            from Slate. The name goes under the tile so nothing is lost;
            the blurb moves to the caption above, which reports whichever
            one is selected.
          -->
          <span class="cc-label isheet-sec">Gradient accent</span>
          <p class="cc-caption isheet-sec-blurb">
            Paints the ignition button. Borders and glows keep the flat
            accent. <strong class="isheet-sec-on">{{ activeGradient.name }}</strong>
            — {{ activeGradient.blurb }}
          </p>
          <div class="isheet-tiles">
            <button
              v-for="g in GRADIENTS"
              :key="g.id"
              class="isheet-tile"
              :class="{ 'is-on': theme.gradient === g.id }"
              :title="g.blurb"
              @click="pick('gradient', g.id)"
            >
              <span
                class="isheet-tile-fill"
                :style="{ background: g.css || theme.accent.hex }"
                aria-hidden="true"
              >
                <svg v-if="theme.gradient === g.id" width="15" height="15" viewBox="0 0 24 24" fill="none"
                     :stroke="g.ink || theme.accent.ink" stroke-width="3.5" stroke-linecap="round"
                     stroke-linejoin="round" aria-hidden="true">
                  <polyline points="20 6 9 17 4 12" />
                </svg>
              </span>
              <span class="isheet-tile-name">{{ g.name }}</span>
            </button>
          </div>

          <span class="cc-label isheet-sec">Background</span>
          <p class="cc-caption isheet-sec-blurb">
            <template v-if="toneApplies">
              Base tone behind every screen.
              <strong class="isheet-sec-on">{{ activeTone.name }}</strong> — {{ activeTone.blurb }}
            </template>
            <template v-else>
              Applies to the dark scheme only — light has its own canvas, so
              nothing here changes while Light is on.
            </template>
          </p>
          <div class="isheet-tiles">
            <button
              v-for="t in TONES"
              :key="t.id"
              class="isheet-tile"
              :class="{ 'is-on': theme.tone === t.id }"
              :title="t.blurb"
              @click="pick('tone', t.id)"
            >
              <!-- The tone's own ladder, not just its canvas: two steps
                   side by side are what tell Obsidian from Slate. -->
              <span
                class="isheet-tile-fill is-tone"
                :style="{ background: `linear-gradient(135deg, ${t.canvas} 50%, ${t.card2} 50%)` }"
                aria-hidden="true"
              >
                <svg v-if="theme.tone === t.id" width="15" height="15" viewBox="0 0 24 24" fill="none"
                     stroke="#F4F4F6" stroke-width="3.5" stroke-linecap="round" stroke-linejoin="round"
                     aria-hidden="true">
                  <polyline points="20 6 9 17 4 12" />
                </svg>
              </span>
              <span class="isheet-tile-name">{{ t.name }}</span>
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
import { ref, reactive, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  theme, LAYOUTS, ART_MODES, GLOW_MODES, CARD_STYLES, SCHEMES, SWATCHES,
  GRADIENTS, TONES, NAME_FONTS, NAME_COLORS, NAME_SCALE,
  activeLayout, activeArtMode, activeNameFont, activeGradient, activeTone,
  toneApplies, setAccent, setCustomAccent, setNameScale, useMentorAccent,
  isMentorAccent, resolvedScheme, REORDERABLE, FX_STYLES, FX_INTENSITIES,
} from '../../stores/theme.js'
import { spectrumHex, spectrumPosition } from '../../stores/palette.js'
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

const activeFx = computed(() => FX_STYLES.find(f => f.id === theme.fx) || FX_STYLES[4])
function pickFx(id) { theme.fx = id; haptic(20) }
function pickIntensity(id) { theme.fxIntensity = id; haptic(20) }

function pickAccent(sw) {
  setAccent(sw)
  haptic(20)
}

/* ── The spectrum ──────────────────────────────────────────────────────
   Two rails over one derived colour. `spec` is the rail POSITION, not
   the accent — the accent is whatever spectrumHex() makes of it, and the
   store is the only thing that holds a colour.

   Seeded from the live accent so opening the sheet puts the rails where
   the colour already is, and re-seeded whenever the accent changes from
   somewhere else (a preset tap, the mentor default, a licence
   re-validation). Without that the rails would keep pointing at a colour
   the user had already moved away from, and the next nudge of either one
   would snap the accent back to it. */
const spec = reactive(spectrumPosition(theme.accent.hex))

const specHex = computed(() => spectrumHex(spec.hue, spec.intensity))

/** The intensity rail, drawn in the hue the user is actually on. */
const intensityRail = computed(() =>
  `linear-gradient(90deg, ${spectrumHex(spec.hue, 0)}, ${spectrumHex(spec.hue, 0.5)}, ${spectrumHex(spec.hue, 1)})`
)

const accentCaption = computed(() =>
  theme.accent.custom ? `Custom · ${theme.accent.hex}` : theme.accent.name
)

function onSpectrum(key, value) {
  spec[key] = Number(value)
  setCustomAccent(specHex.value)
}

/* Only re-seed on a change this component did NOT make. Re-seeding on
   our own writes would fight the rail under the user's thumb: the derived
   hex round-trips through spectrumPosition() with rounding, so the value
   coming back is occasionally a step off the one they are dragging. */
watch(() => theme.accent.hex, (hex) => {
  if (hex === specHex.value) return
  const at = spectrumPosition(hex)
  spec.hue = at.hue
  spec.intensity = at.intensity
})

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

function goSettings() {
  emit('close')
  router.push('/settings')
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

/* Sits between the title and the close button, pushed to the right by the
   row's `justify-content: space-between` plus the title's own growth. */
.isheet-more {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
  height: 30px;
  padding: 0 11px;
  border: 1px solid var(--cc-border);
  border-radius: var(--cc-r-chip);
  background: none;
  font-family: var(--cc-font);
  font-size: 12px;
  font-weight: 600;
  color: var(--cc-ink-3);
  white-space: nowrap;
  cursor: pointer;
  transition: transform 180ms var(--cc-spring);
}
.isheet-more:active { transform: scale(0.95) }

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
.isheet-card.is-muted { opacity: 0.5 }
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

/* ── Spectrum ────────────────────────────────────────────────────────
   Two rails and a readout. Deliberately inside the Accent card rather
   than beside it: the presets and the spectrum set the same value, and
   splitting them into two cards would read as two settings that can
   disagree. */
.isheet-spectrum {
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid var(--cc-border-soft);
}
.isheet-spec-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}
.isheet-spec-hex { font-size: 11px; color: var(--cc-ink-4) }
.isheet-spec-note { margin: 10px 0 0 }

.isheet-rail-wrap { display: block }
.isheet-rail-wrap + .isheet-rail-wrap { margin-top: 12px }

/* The screen-reader label for each rail. Present because an <input
   type="range"> with no accessible name is announced as "slider" and
   nothing else, and there are two of them. */
.sr-only {
  position: absolute;
  width: 1px; height: 1px;
  margin: -1px; padding: 0;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
  border: 0;
}

.isheet-rail {
  -webkit-appearance: none;
  appearance: none;
  width: 100%;
  height: 30px;
  background: none;
  cursor: pointer;
}
/* The track IS the choice, so it is 14px rather than the 4px the name
   slider uses — a hue rail you cannot see the colours in is a hue rail
   that has to be guessed at. */
.isheet-rail::-webkit-slider-runnable-track {
  height: 14px;
  border-radius: var(--cc-r-chip);
  background: var(--rail, var(--cc-track));
  box-shadow: inset 0 0 0 1px var(--cc-glass-rim);
}
.isheet-rail::-moz-range-track {
  height: 14px;
  border-radius: var(--cc-r-chip);
  background: var(--rail, var(--cc-track));
  box-shadow: inset 0 0 0 1px var(--cc-glass-rim);
}
/* The thumb carries the RESULT, not a neutral handle: on a hue rail a
   grey knob tells you where you are and nothing about what you picked. */
.isheet-rail::-webkit-slider-thumb {
  -webkit-appearance: none;
  width: 26px; height: 26px;
  margin-top: -6px;
  border-radius: 50%;
  background: var(--thumb, var(--cc-accent));
  border: 2.5px solid var(--cc-ink);
  box-shadow: var(--cc-shadow-card);
}
.isheet-rail::-moz-range-thumb {
  width: 26px; height: 26px;
  border-radius: 50%;
  background: var(--thumb, var(--cc-accent));
  border: 2.5px solid var(--cc-ink);
  box-shadow: var(--cc-shadow-card);
}

/* The wheel, at the saturation and lightness the intensity rail's midpoint
   produces — so the two rails agree about what a hue looks like. */
.isheet-rail-hue::-webkit-slider-runnable-track {
  background: linear-gradient(90deg,
    #E06A6A, #E0A76A, #D9E06A, #7FE06A, #6AE0A7, #6AD9E0, #6A7FE0, #A76AE0, #E06AD9, #E06A6A);
}
.isheet-rail-hue::-moz-range-track {
  background: linear-gradient(90deg,
    #E06A6A, #E0A76A, #D9E06A, #7FE06A, #6AE0A7, #6AD9E0, #6A7FE0, #A76AE0, #E06AD9, #E06A6A);
}

/* ── Glow demo ───────────────────────────────────────────────────────
   Four orbs at the four settings, side by side. Each one hardcodes its
   own multiplier through --g rather than reading --cc-glow, so the row
   shows all four AT ONCE — a preview that only ever drew the current
   setting would say nothing about the other three, which is the whole
   question being asked here. */
.isheet-glow-demo {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  place-items: center;
  gap: 8px;
  padding: 18px 4px 22px;
  margin-bottom: 4px;
}
.isheet-glow-orb {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--cc-accent);
  box-shadow:
    0 0 0 calc(1px * var(--g)) rgba(var(--cc-accent-rgb), calc(0.50 * var(--g))),
    0 0 calc(16px * var(--g)) calc(3px * var(--g)) rgba(var(--cc-accent-rgb), calc(0.45 * var(--g))),
    0 0 calc(40px * var(--g)) calc(9px * var(--g)) rgba(var(--cc-accent-rgb), calc(0.22 * var(--g)));
  opacity: 0.34;
  transition: opacity 200ms var(--cc-ease), transform 200ms var(--cc-spring);
}
.isheet-glow-orb.is-on { opacity: 1; transform: scale(1.14) }

/* ── Card style tiles ────────────────────────────────────────────────
   Each tile renders the real material. The demo surface deliberately
   does NOT use .cc-card: that class reads data-card off the .cmd root,
   so all three tiles would draw whichever style is currently selected
   and the picker would preview one option three times. The three token
   sets are restated here against a [data-demo] attribute instead. */
.isheet-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}
.isheet-card-pick {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
  padding: 6px 6px 8px;
  border: 1.5px solid transparent;
  border-radius: var(--cc-r-card);
  background: none;
  cursor: pointer;
  transition: border-color 180ms var(--cc-ease), transform 180ms var(--cc-spring);
}
.isheet-card-pick:active { transform: scale(0.97) }
.isheet-card-pick.is-on { border-color: var(--cc-accent) }

.isheet-card-demo {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
  height: 56px;
  padding: 0 10px;
  border-radius: var(--cc-r-tile);
  /* The demo needs something behind it or Glass has nothing to refract. */
  background-color: var(--cc-canvas-2);
}
.isheet-card-demo-bar {
  width: 46%;
  height: 5px;
  border-radius: 3px;
  background: var(--cc-accent);
}
.isheet-card-demo-line {
  width: 88%;
  height: 4px;
  border-radius: 3px;
  background: var(--cc-ink-5);
}
.isheet-card-demo-line.is-short { width: 62% }

.isheet-card-demo[data-demo="glass"] {
  background:
    linear-gradient(var(--cc-glass-fill), var(--cc-glass-fill)),
    radial-gradient(circle at 25% 20%, rgba(var(--cc-accent-rgb), 0.55), transparent 62%),
    var(--cc-canvas-2);
  border: 0.5px solid var(--cc-glass-rim);
  box-shadow: inset 0 1px 0 var(--cc-glass-lip), inset 0 -1px 0 var(--cc-glass-seat);
}
.isheet-card-demo[data-demo="neon"] {
  background: var(--cc-card);
  border: 1px solid var(--cc-accent-line);
  box-shadow:
    var(--cc-glow-edge),
    inset 0 0 calc(18px * var(--cc-glow)) rgba(var(--cc-accent-rgb), calc(0.08 * var(--cc-glow)));
}
.isheet-card-demo[data-demo="solid"] {
  background: var(--cc-card);
  border: 1px solid var(--cc-border);
  box-shadow: var(--cc-shadow-card);
}

.isheet-card-name {
  font-family: var(--cc-font);
  font-size: 11px;
  font-weight: 600;
  text-align: center;
  color: var(--cc-ink-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.isheet-card-pick.is-on .isheet-card-name { color: var(--cc-ink) }

/* ── Tile grids — gradients and tones ────────────────────────────────
   Four across. At 390px that is a 74px tile, which is large enough to
   read a two-stop ramp's direction and to tell two near-black tones
   apart by their card step. Three across would be larger and need six
   rows for twenty gradients; five would be under 58px, where Rust and
   Plum stop being distinguishable. */
.isheet-tiles {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}
.isheet-tile {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 0;
  border: none;
  background: none;
  cursor: pointer;
  transition: transform 180ms var(--cc-spring);
}
.isheet-tile:active { transform: scale(0.94) }

.isheet-tile-fill {
  display: grid;
  place-items: center;
  width: 100%;
  aspect-ratio: 1;
  border-radius: 14px;
  box-shadow: inset 0 0 0 1px var(--cc-glass-rim);
  transition: box-shadow 180ms var(--cc-ease);
}
/* A near-black tone tile against a near-black sheet needs its own edge,
   or thirteen of them read as one dark band. */
.isheet-tile-fill.is-tone { box-shadow: inset 0 0 0 1px var(--cc-border-strong) }

/* Selection is a ring OUTSIDE the tile, never a border inside it: an
   inset ring eats 3px of a 74px gradient, and on the tone tiles it would
   cover the ladder step that distinguishes them. */
.isheet-tile.is-on .isheet-tile-fill {
  box-shadow:
    0 0 0 2px var(--cc-canvas-2),
    0 0 0 4px var(--cc-accent),
    var(--cc-glow-edge);
}

.isheet-tile-name {
  width: 100%;
  font-family: var(--cc-font);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.01em;
  text-align: center;
  color: var(--cc-ink-4);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.isheet-tile.is-on .isheet-tile-name { color: var(--cc-ink) }

.isheet-sec-on { color: var(--cc-ink-2); font-weight: 600 }

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
