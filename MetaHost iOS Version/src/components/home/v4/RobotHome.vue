<template>
  <!--
    Home v4 — Classic Core, Focus Engine and Full-Bleed (Claude Design,
    "NovaHost Home Customization v4", component RobotHomeControls).

    Every layout carries the same eight things: the robot's loop video with
    Falling FX, the neon robot name, a slide-to-start power control, Trading
    symbols, Switch robot, a Screens button, the push-to-talk mic and a tiny
    status dot. Each arranges them in its own pattern. Coordinates are the
    design's own (390×844); useStage scales them to the phone.
  -->
  <div ref="root" class="rh" :class="{ 'rh-mini': mini }" :style="{ '--a': accent }">
    <div ref="probe" class="rh-probe" aria-hidden="true"></div>

    <div v-if="ready" ref="stage" class="rh-stage" :style="stageStyle">

      <!-- ══ CLASSIC CORE ════════════════════════════════════════════ -->
      <template v-if="L === 'classic'">
        <!-- The robot art fills the screen behind the card, blurred, rather
             than a near-black wash. The gradient only darkens toward the
             bottom, where the name and controls need contrast. -->
        <div v-if="!lite" class="rh-wash" :style="{ ...washBox, backgroundImage: artUrl, backgroundPosition: '50% 36%', filter: 'blur(26px) brightness(.78) saturate(1.1)' }"></div>
        <div class="rh-fill" style="background:linear-gradient(180deg, rgba(8,8,11,.22) 0%, rgba(8,8,11,.08) 40%, rgba(8,8,11,.55) 74%, rgba(8,8,11,.82) 100%)"></div>
        <div class="rh-fill rh-glow" :style="{ opacity: glowOp, background: `radial-gradient(62% 36% at 50% ${((354 + dy / 2) / H * 100).toFixed(1)}%, color-mix(in srgb, var(--a) 26%, transparent), transparent 72%)` }"></div>

        <div class="rh-col" :style="colStyle">
          <div class="rh-name-c" :style="{ top: y(512, 'mid') }">
            <div class="rh-name" :style="nameStyle(nmC)">
              <div>{{ nmC.l1 }}</div>
              <div v-if="nmC.two">{{ nmC.l2 }}</div>
            </div>
            <div class="rh-powered"><span>Powered by</span><span class="rh-powered-b">NovaHost</span></div>
          </div>

          <div
            class="rh-hero rh-hero-c"
            :style="{ top: y(96, 'mid'), boxShadow: cardShadow }"
            @pointerdown="swDown" @pointermove="swMove" @pointerup="swUp" @pointercancel="swUp"
          >
            <div class="rh-hero-in" :style="heroStyle"><RobotMedia :art="art" :video="video" :play="videoPlay" :allowed="videoAllowed" :media-style="mediaStyle" /></div>
            <div class="rh-hero-edge"></div>
          </div>
          <div v-if="!lite" class="rh-refl" :style="{ top: y(502, 'mid') }">
            <div class="rh-refl-in">
              <div class="rh-hero-in" :style="heroStyle"><img class="rh-media" :src="art" alt="" draggable="false" :style="mediaStyle"></div>
            </div>
          </div>

          <div role="button" :aria-label="symLabel" class="rh-sat" :style="{ left: '91px', top: y(738, 'bot'), ...satStyle }" @click="openSheet('symbols')">
            <span class="ms" style="font-size:24px">candlestick_chart</span>
            <div v-if="issues" class="rh-badge">{{ issues }}</div>
          </div>

          <div class="rh-track" :style="{ left: '20px', top: y(646, 'bot'), width: '270px', height: '68px', borderRadius: '34px', ...trackStyle }">
            <div class="rh-track-fill" :style="{ width: (prog * 202 + 68) + 'px', opacity: fillOp, borderRadius: '34px' }"></div>
            <div class="rh-track-label" :style="{ padding: leftSide ? '0 62px 0 0' : '0 0 0 62px' }"><span :style="labelStyle">{{ label }}</span></div>
            <div role="slider" :aria-label="knobLabel" aria-valuemin="0" aria-valuemax="1" :aria-valuenow="prog.toFixed(2)" class="rh-knob" :style="{ top: '6px', left: (6 + prog * 202) + 'px', width: '56px', height: '56px' }" @pointerdown="knobDown" @pointermove="knobMove" @pointerup="knobUp" @pointercancel="knobUp">
              <div class="rh-knob-disc" :style="knobDisc"></div>
              <div v-for="o in orbits(56)" :key="o.key" :style="o.style"><div v-if="o.dot" :style="o.dot"></div></div>
              <svg width="56" height="56" viewBox="0 0 56 56" class="rh-ring">
                <circle cx="28" cy="28" r="25" fill="none" stroke="rgba(255,255,255,.14)" stroke-width="2.5" />
                <circle cx="28" cy="28" r="25" fill="none" stroke-width="2.5" stroke-linecap="round" stroke-dasharray="157.08" :stroke-dashoffset="(157.08 * (1 - ringFrac)).toFixed(2)" :style="ringStyle" />
              </svg>
              <span class="ms rh-knob-ic" :style="{ fontSize: '26px', color: knobIcon }">power_settings_new</span>
            </div>
          </div>

          <div role="button" aria-label="Switch robot" class="rh-sat" :style="{ left: '247px', top: y(738, 'bot'), ...satStyle }" @click="openSheet('robots')">
            <span class="ms" style="font-size:24px">swap_horiz</span>
          </div>
          <div role="button" :aria-label="gridLabel" class="rh-sat rh-grid" :style="{ left: '169px', top: y(738, 'bot'), ...satStyle }" @click="toggleStrip">
            <span class="ms rh-chev">{{ strip ? 'keyboard_arrow_down' : 'keyboard_arrow_up' }}</span>
            <span class="ms" style="font-size:21px">apps</span>
          </div>

          <template v-if="talking">
            <div class="rh-wave" :style="{ top: y(652, 'bot'), background: waveBg, ...glassFx }">
              <voice-wave :state="voice" :accent="accent" :mode="voiceMode" gap="322,50"></voice-wave>
            </div>
            <div class="rh-hint" :style="{ left: '28px', top: y(628, 'bot'), color: hintColor }"><span class="ms" style="font-size:16px">chevron_left</span><div>{{ hintText }}</div></div>
          </template>
          <div class="rh-mic-wrap" :style="{ left: '306px', top: y(648, 'bot'), width: '64px', height: '64px' }">
            <div class="rh-mic-ring"><voice-ring :state="voice" :accent="accent" :mode="voiceMode"></voice-ring></div>
            <div role="button" :aria-label="micLabel" class="rh-mic" :style="micStyle" @pointerdown="pttDown" @pointermove="pttMove" @pointerup="pttUp" @pointercancel="pttUp">
              <span class="ms ms-fill" :style="{ fontSize: '30px', color: micIcon }">{{ micGlyph }}</span>
            </div>
          </div>
        </div>
      </template>

      <!-- ══ FOCUS ENGINE ════════════════════════════════════════════ -->
      <template v-else-if="L === 'focus'">
        <div class="rh-fill" style="background:#050508"></div>
        <!-- Same idea as Classic: the art, blurred, behind the whole screen. -->
        <div v-if="!lite" class="rh-wash" :style="{ ...washBox, backgroundImage: artUrl, backgroundPosition: '50% 38%', filter: 'blur(26px) brightness(.72) saturate(1.1)' }"></div>
        <div v-if="!lite" class="rh-fill" style="background:linear-gradient(180deg, rgba(5,5,8,.55) 0%, rgba(5,5,8,.4) 14%, rgba(5,5,8,.1) 40%, rgba(5,5,8,.6) 80%, rgba(5,5,8,.85) 100%)"></div>

        <div class="rh-col" :style="colStyle">
          <div class="rh-abs" :style="{ left: '-125px', top: y(72, 'mid'), width: '640px', height: '640px', borderRadius: '50%', opacity: glowOp, transition: 'opacity .6s ease', background: 'radial-gradient(circle, color-mix(in srgb, var(--a) 36%, transparent) 0%, color-mix(in srgb, var(--a) 11%, transparent) 36%, transparent 62%)' }"></div>
          <div v-if="!lite" class="rh-abs rh-halo" :style="{ left: '25px', top: y(222, 'mid'), opacity: running ? .72 : .12, animation: anim ? 'rhSpin 32s linear infinite' : 'none', animationPlayState: running ? 'running' : 'paused' }"></div>

          <svg width="390" height="844" viewBox="0 0 390 844" class="rh-abs rh-arc" :style="{ top: (dy / 2) + 'px' }">
            <circle cx="195" cy="392" r="170" fill="none" stroke="rgba(255,255,255,.14)" stroke-width="1" stroke-dasharray="2 7" />
            <path :d="ARC_D" fill="none" :stroke="arcEdge" stroke-width="66" stroke-linecap="round" />
            <path :d="ARC_D" fill="none" :stroke="lite ? 'rgba(18,18,24,.95)' : 'rgba(12,12,16,.62)'" stroke-width="64" stroke-linecap="round" />
            <path :d="ARC_D" fill="none" stroke-width="64" stroke-linecap="round" :stroke-dasharray="(prog * ARC.len).toFixed(1) + ' 1000'" :style="{ stroke: 'color-mix(in srgb, var(--a) 42%, transparent)', opacity: fillOp }" />
            <defs><path :id="arcId" :d="ARC_D" /></defs>
            <text font-family="Geist, system-ui, sans-serif" font-size="12.5" font-weight="600" letter-spacing="3.2" fill="#F4F4F7" :opacity="labelOp" dominant-baseline="middle" text-anchor="middle">
              <textPath :href="'#' + arcId" startOffset="50%">{{ label }}</textPath>
            </text>
          </svg>
          <div class="rh-powered rh-abs" :style="{ left: 0, right: 0, top: y(600, 'mid'), justifyContent: 'center', height: '16px' }"><span>Powered by</span><span class="rh-powered-b">NovaHost</span></div>

          <div class="rh-name rh-abs rh-name-f" :style="{ top: y(64), ...nameStyle(nmF) }">
            <div>{{ nmF.l1 }}</div>
            <div v-if="nmF.two">{{ nmF.l2 }}</div>
          </div>

          <div
            class="rh-hero rh-hero-f"
            :style="{ top: y(268, 'mid'), boxShadow: focusShadow }"
            @pointerdown="swDown" @pointermove="swMove" @pointerup="swUp" @pointercancel="swUp"
          >
            <div class="rh-hero-in" :style="heroStyle"><RobotMedia :art="art" :video="video" :play="videoPlay" :allowed="videoAllowed" :media-style="mediaStyle" /></div>
            <div class="rh-hero-edge-f"></div>
          </div>

          <div role="button" :aria-label="symLabel" class="rh-sat" :style="{ left: '15px', top: y(294, 'mid'), ...satStyle, borderColor: 'rgba(255,255,255,.16)' }" @click="openSheet('symbols')">
            <span class="ms" style="font-size:23px">candlestick_chart</span>
            <div v-if="issues" class="rh-badge" style="right:-4px;top:-4px;border-color:#050508">{{ issues }}</div>
          </div>
          <div role="button" aria-label="Switch robot" class="rh-sat" :style="{ left: '323px', top: y(294, 'mid'), ...satStyle, borderColor: 'rgba(255,255,255,.16)' }" @click="openSheet('robots')">
            <span class="ms" style="font-size:23px">swap_horiz</span>
          </div>

          <div role="slider" :aria-label="knobLabel" aria-valuemin="0" aria-valuemax="1" :aria-valuenow="prog.toFixed(2)" class="rh-knob" :style="{ left: (arcKnob[0] - 28).toFixed(1) + 'px', top: (arcKnob[1] - 28 + dy / 2).toFixed(1) + 'px', width: '56px', height: '56px', zIndex: 8 }" @pointerdown="knobDown" @pointermove="knobMove" @pointerup="knobUp" @pointercancel="knobUp">
            <div class="rh-knob-disc" :style="knobDisc"></div>
            <div v-for="o in orbits(56)" :key="o.key" :style="o.style"><div v-if="o.dot" :style="o.dot"></div></div>
            <svg width="56" height="56" viewBox="0 0 56 56" class="rh-ring">
              <circle cx="28" cy="28" r="25" fill="none" stroke="rgba(255,255,255,.14)" stroke-width="2.5" />
              <circle cx="28" cy="28" r="25" fill="none" stroke-width="2.5" stroke-linecap="round" stroke-dasharray="157.08" :stroke-dashoffset="(157.08 * (1 - ringFrac)).toFixed(2)" :style="ringStyle" />
            </svg>
            <span class="ms rh-knob-ic" :style="{ fontSize: '26px', color: knobIcon }">power_settings_new</span>
          </div>

          <template v-if="talking">
            <div class="rh-wave" :style="{ top: y(672, 'bot'), background: waveBg, ...glassFx }">
              <voice-wave :state="voice" :accent="accent" :mode="voiceMode" gap="179,50"></voice-wave>
            </div>
            <div class="rh-hint" :style="{ left: '28px', top: y(646, 'bot'), color: hintColor }"><span class="ms" style="font-size:16px">chevron_left</span><div>{{ hintText }}</div></div>
          </template>
          <div class="rh-mic-wrap" :style="{ left: '163px', top: y(668, 'bot'), width: '64px', height: '64px' }">
            <div class="rh-mic-ring"><voice-ring :state="voice" :accent="accent" :mode="voiceMode"></voice-ring></div>
            <div role="button" :aria-label="micLabel" class="rh-mic" :style="micStyle" @pointerdown="pttDown" @pointermove="pttMove" @pointerup="pttUp" @pointercancel="pttUp">
              <span class="ms ms-fill" :style="{ fontSize: '30px', color: micIcon }">{{ micGlyph }}</span>
            </div>
          </div>
          <div role="button" :aria-label="gridLabel" class="rh-sat rh-grid" :style="{ left: '171px', top: y(752, 'bot'), width: '48px', height: '48px', ...satStyle }" @click="toggleStrip">
            <span class="ms rh-chev">{{ strip ? 'keyboard_arrow_down' : 'keyboard_arrow_up' }}</span>
            <span class="ms" style="font-size:21px">apps</span>
          </div>
        </div>
      </template>

      <!-- ══ FULL-BLEED ══════════════════════════════════════════════ -->
      <template v-else>
        <div class="rh-fill rh-full-media" @pointerdown="swDown" @pointermove="swMove" @pointerup="swUp" @pointercancel="swUp">
          <div class="rh-hero-in" :style="heroStyle"><RobotMedia :art="art" :video="video" :play="videoPlay" :allowed="videoAllowed" :media-style="mediaStyle" /></div>
        </div>
        <div class="rh-abs rh-nopt" style="left:0;right:0;top:0;height:140px;z-index:2;background:linear-gradient(180deg, rgba(0,0,0,.5), transparent)"></div>
        <div class="rh-abs rh-nopt" style="left:0;right:0;bottom:0;height:420px;z-index:2;background:linear-gradient(180deg, rgba(6,6,9,0) 0%, rgba(6,6,9,.5) 45%, rgba(6,6,9,.9) 100%)"></div>
        <div class="rh-abs rh-nopt" :style="{ left: 0, right: 0, bottom: 0, height: '340px', zIndex: 2, opacity: glowOp, transition: 'opacity .6s ease', background: 'radial-gradient(70% 55% at 50% 85%, color-mix(in srgb, var(--a) 30%, transparent), transparent 75%)' }"></div>

        <!-- Full-Bleed Styles 1a (glass sheet) + 3a (bare mic) + 4a–4c (news). -->
        <div class="rh-col rh-nopt" :style="colStyle">
          <div class="rh-abs rh-thin-wrap" :style="{ top: y(556, 'bot'), opacity: talking ? 0 : (running ? 1 : .7) }">
            <div class="rh-thin" :style="{ fontSize: thinSize, textShadow: thinGlow, animation: anim && running ? 'rhBreath 4s ease-in-out infinite' : 'none' }">{{ thinName }}</div>
            <div class="rh-thin-by"><div class="rh-thin-rule"></div><span>POWERED BY</span><span class="rh-thin-b">NOVAHOST</span></div>
          </div>

          <template v-if="talking">
            <div class="rh-wave" :style="{ top: y(568, 'bot'), zIndex: 9, background: waveBg, ...glassFx }">
              <voice-wave :state="voice" :accent="accent" :mode="voiceMode" gap="320,48"></voice-wave>
            </div>
            <div class="rh-hint" :style="{ left: '24px', top: y(544, 'bot'), color: hintColor }"><span class="ms" style="font-size:16px">chevron_left</span><div>{{ hintText }}</div></div>
          </template>
          <div role="button" :aria-label="micLabel" class="rh-bare rh-pt" :style="{ top: y(626, 'bot') }" @pointerdown="pttDown" @pointermove="pttMove" @pointerup="pttUp" @pointercancel="pttUp">
            <span class="ms" :style="bareStyle">{{ micGlyph }}</span>
          </div>

          <div class="rh-sheet rh-pt" :style="{ top: y(680, 'bot'), left: -dx + 'px', width: W + 'px', ...sheetStyle }">
            <div class="rh-sheet-edge" :style="{ opacity: glowOp }"></div>
            <div class="rh-sheen" :style="{ opacity: running ? .9 : 0, animation: anim && running ? 'rhSheen 5.5s ease-in-out infinite' : 'none' }"></div>
            <div class="rh-sheet-row">
              <div role="button" :aria-label="news.aria" class="rh-news" :style="{ '--n': news.color }" @click="openNews">
                <div class="rh-news-l1">
                  <div class="rh-news-lvl">{{ news.lvl }}</div>
                  <div class="rh-news-name">{{ news.name }}</div>
                  <div style="flex:1"></div>
                  <div class="rh-news-t">{{ news.when }}</div>
                </div>
                <div class="rh-news-l2">{{ news.sub }}</div>
                <div v-if="news.frac != null" class="rh-news-bar">
                  <div class="rh-news-fill" :style="{ width: (news.frac * 100).toFixed(1) + '%' }">
                    <div class="rh-news-dot" :style="{ animation: anim ? 'rhPulse 1.6s ease-in-out infinite' : 'none' }"></div>
                  </div>
                </div>
              </div>
              <div role="button" :aria-label="symLabel" class="rh-sbtn" @click="openSheet('symbols')">
                <span class="ms" style="font-size:22px">candlestick_chart</span>
                <div v-if="issues" class="rh-badge" style="border-color:#101014">{{ issues }}</div>
              </div>
              <div role="button" aria-label="Switch robot" class="rh-sbtn" @click="openSheet('robots')">
                <span class="ms" style="font-size:22px">swap_horiz</span>
              </div>
              <div role="button" :aria-label="gridLabel" class="rh-sbtn rh-grid" @click="toggleStrip">
                <span class="ms rh-chev" style="font-size:15px">{{ strip ? 'keyboard_arrow_down' : 'keyboard_arrow_up' }}</span>
                <span class="ms" style="font-size:20px">apps</span>
              </div>
            </div>
            <div class="rh-sheet-track" :style="{ border: trackStyle.border, boxShadow: trackStyle.boxShadow }">
              <div class="rh-track-fill" :style="{ width: (prog * 294 + 62) + 'px', opacity: fillOp, borderRadius: '32px' }"></div>
              <div class="rh-track-label" :style="{ padding: leftSide ? '0 62px 0 0' : '0 0 0 62px' }"><span :style="labelStyle">{{ label }}</span></div>
              <div role="slider" :aria-label="knobLabel" aria-valuemin="0" aria-valuemax="1" :aria-valuenow="prog.toFixed(2)" class="rh-knob" :style="{ top: '3px', left: (3 + prog * 294) + 'px', width: '56px', height: '56px' }" @pointerdown="knobDown" @pointermove="knobMove" @pointerup="knobUp" @pointercancel="knobUp">
                <div class="rh-knob-disc" :style="knobDisc"></div>
                <div v-for="o in orbits(56)" :key="o.key" :style="o.style"><div v-if="o.dot" :style="o.dot"></div></div>
                <svg width="56" height="56" viewBox="0 0 56 56" class="rh-ring">
                  <circle cx="28" cy="28" r="25" fill="none" stroke="rgba(255,255,255,.14)" stroke-width="2.5" />
                  <circle cx="28" cy="28" r="25" fill="none" stroke-width="2.5" stroke-linecap="round" stroke-dasharray="157.08" :stroke-dashoffset="(157.08 * (1 - ringFrac)).toFixed(2)" :style="ringStyle" />
                </svg>
                <span class="ms rh-knob-ic" :style="{ fontSize: '26px', color: knobIcon }">power_settings_new</span>
              </div>
            </div>
          </div>
        </div>
      </template>

      <!-- ══ Shared layers ═══════════════════════════════════════════ -->
      <div class="rh-fill rh-nopt" :style="{ zIndex: 5, opacity: running ? 1 : .5, transition: 'opacity .6s ease' }">
        <falling-fx :fx="fx" :intensity="theme.fxIntensity" :accent="accent" :mode="fxMode" :clear="fxClear" :res="mini ? .35 : 1" :seed="seed"></falling-fx>
      </div>

      <!-- What the robot is saying (voice-turn). -->
      <Transition name="rh-cap">
        <div v-if="caption" class="rh-caption" :style="{ left: (dx + 20) + 'px', top: y(capTop, 'bot') }">
          <div class="rh-cap-who">{{ rb.name.toUpperCase() }}</div>
          <div>{{ caption }}</div>
        </div>
      </Transition>

      <div role="button" :aria-label="dotLabel" class="rh-dot" :style="{ left: (dx + 334) + 'px', top: y(48) }" @click="openSheet('status')">
        <div v-if="kind === 'error'" class="rh-dot-ring" :style="{ animation: reduced ? 'none' : 'rhRing 2.2s ease-out infinite' }"></div>
        <div class="rh-dot-core" :style="{ background: dotColor, boxShadow: `0 0 0 2.5px rgba(8,8,11,.7), 0 0 12px ${kind === 'stopped' ? 'transparent' : dotColor}` }"></div>
      </div>

      <ScreensStrip v-if="strip" current="/home" :style="{ '--a': accent }" @close="strip = false" @open="openScreen" />

      <VoiceActionCard :pending="vt.pending.value" :busy="vt.busy.value" :accent="accent" @confirm="voiceConfirm" @dismiss="vt.dismiss" />

      <HomeSheets
        :sheet="sheet"
        :kind="kind"
        :robot-name="rb.name"
        :list="list"
        :current="idx"
        :running="running"
        :rows="symbolRows"
        :issues="issues"
        @close="sheet = null"
        @pick="pickRobot"
        @go="go"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { theme } from '../../../stores/theme.js'
import { session } from '../../../stores/session.js'
import { useSpeech, haptic } from '../../../composables/useSpeech.js'
import { useVoiceTurn } from '../../../composables/useVoiceTurn.js'
import VoiceActionCard from '../../VoiceActionCard.vue'
import { useStage } from './useStage.js'
import HomeSheets from './HomeSheets.vue'
import ScreensStrip from './ScreensStrip.vue'
import RobotMedia from './RobotMedia.vue'
import {
  robots, activeIndex, statusKind, symbolRows, symbolIssues, refreshPlan,
  startRobot, stopRobot, switchRobot, darkOnAccent, fitName, effectMode,
  GREEN, GREY, RED,
} from './homeData.js'
import { loadWire, whenLabel } from '../../../services/marketWireService.js'
import '../fx/falling-fx.js'
import '../fx/voice-fx.js'

const props = defineProps({
  layout: { type: String, default: 'classic' }, // classic | focus | full
  mini:   { type: Boolean, default: false },
})
const emit = defineEmits(['look', 'go', 'toast'])

const { root, probe, stage, ready, W, H, dx, dy, st, stageStyle, y, local } = useStage({ mini: props.mini })
const { speak } = useSpeech()

const L = computed(() => (props.layout === 'focus' || props.layout === 'full') ? props.layout : 'classic')
const live = computed(() => !props.mini)

/* ── Motion level ────────────────────────────────────────────────── */
const mode = ref(effectMode())
const lite = computed(() => mode.value === 'lite')
const reduced = computed(() => mode.value === 'reduced')
const anim = computed(() => mode.value === 'full')
const voiceMode = computed(() => reduced.value ? 'reduced' : lite.value ? 'lite' : 'full')
watch(() => theme.motion, () => { mode.value = effectMode() })

/* ── Robot ───────────────────────────────────────────────────────── */
const list = robots
const idx = activeIndex
const rb = computed(() => list.value[idx.value] || { name: 'NovaHost AI', raw: 'NovaHost AI', accent: theme.accent.hex, art: '' })
const accent = computed(() => rb.value.accent || '#E0313F')
const art = computed(() => rb.value.art)
/* The mentor's loop video, played in place of the art. Never in Lite, Reduce
   Motion or a miniature (layout previews); paused to the still image while
   the robot is stopped. The blurred wash behind stays the still image, so
   only one video ever decodes. */
const video = computed(() => rb.value.video || '')
const videoAllowed = computed(() => anim.value && live.value)
const videoPlay = computed(() => running.value && videoAllowed.value)
const artUrl = computed(() => `url(${art.value})`)
const running = computed(() => session.isRunning)
const kind = computed(() => statusKind.value)
const issues = computed(() => symbolIssues.value)
const darkOn = computed(() => darkOnAccent(accent.value))

/* ── Geometry helpers ────────────────────────────────────────────── */
const colStyle = computed(() => ({ left: dx.value + 'px', width: '390px', height: H.value + 'px' }))
const washBox = computed(() => ({ left: '-40px', top: '-40px', width: (W.value + 80) + 'px', height: (H.value + 80) + 'px' }))
const pos = computed(() => L.value === 'focus' ? '50% 38%' : L.value === 'full' ? '50% 34%' : '50% 36%')

/* ── Slide to start / hold to stop (design 2d–2f) ────────────────── */
const ARC = { cx: 195, cy: 392, R: 170, a0: 155, span: 130, len: 385.72 }
const ARC_D = 'M 40.93 463.84 A 170 170 0 0 0 349.07 463.84'
const arcId = 'rhArc' + Math.random().toString(36).slice(2, 8)

const drag = ref(null)
const charging = ref(false)
const holding = ref(false)
const hint = ref(false)
let ds = null
let raf = 0
let holdT = 0
const timers = []
const later = (fn, ms) => timers.push(setTimeout(fn, ms))

const prog = computed(() => drag.value != null ? drag.value : running.value ? 1 : 0)
const charged = computed(() => running.value || charging.value)
const fillOp = computed(() => running.value ? 1 : (drag.value != null ? 0.35 + 0.65 * drag.value : 0))
const label = computed(() => holding.value ? 'KEEP HOLDING' : hint.value ? 'HOLD TO STOP' : charging.value ? 'STARTING' : running.value ? 'RUNNING' : 'SLIDE TO START')
const leftSide = computed(() => charged.value || holding.value)
const labelOp = computed(() => (drag.value != null && !running.value) ? Math.max(0, 1 - drag.value * 1.6) : 1)
const ringFrac = computed(() => holding.value ? 0 : charged.value ? 1 : drag.value != null ? drag.value * 0.6 : 0)
const arcKnob = computed(() => {
  const th = (ARC.a0 - prog.value * ARC.span) * Math.PI / 180
  return [ARC.cx + ARC.R * Math.cos(th), ARC.cy + ARC.R * Math.sin(th)]
})

function animate(from, to, ms, cb) {
  cancelAnimationFrame(raf)
  const t0 = performance.now()
  const step = now => {
    const u = Math.min(1, (now - t0) / ms), e = 1 - Math.pow(1 - u, 3)
    drag.value = from + (to - from) * e
    if (u < 1) raf = requestAnimationFrame(step); else if (cb) cb()
  }
  raf = requestAnimationFrame(step)
}

function progFromPointer(x, py) {
  if (L.value === 'focus') {
    const yy = py - dy.value / 2
    let a = Math.atan2(yy - ARC.cy, x - ARC.cx) * 180 / Math.PI
    if (a < 0) a = x < ARC.cx ? 180 : 0
    return Math.max(0, Math.min(1, (ARC.a0 - a) / ARC.span))
  }
  const travel = L.value === 'full' ? 294 : 202
  return Math.max(0, Math.min(1, ds.p + (x - ds.x) / travel))
}

function knobDown(e) {
  if (!live.value) return
  e.stopPropagation()
  try { e.currentTarget.setPointerCapture(e.pointerId) } catch { /* not capturable */ }
  if (charging.value) return
  if (running.value) {
    holding.value = true; hint.value = false
    clearTimeout(holdT)
    holdT = setTimeout(doStop, 1200)
    return
  }
  const [x, py] = local(e)
  cancelAnimationFrame(raf)
  ds = { x, y: py, p: drag.value || 0 }
  drag.value = ds.p
}
function knobMove(e) {
  if (!ds) return
  const [x, py] = local(e)
  drag.value = progFromPointer(x, py)
}
function knobUp() {
  if (holding.value) {
    clearTimeout(holdT)
    holding.value = false; hint.value = true
    later(() => { hint.value = false }, 1600)
    return
  }
  if (!ds) return
  ds = null
  const d = drag.value || 0
  if (d >= 0.85) {
    animate(d, 1, 120, () => {
      charging.value = true
      later(() => { startRobot(); charging.value = false; drag.value = null }, 680)
    })
  } else {
    animate(d, 0, 280, () => { drag.value = null })
  }
}
function doStop() {
  stopRobot()
  holding.value = false; hint.value = false
  drag.value = 1
  animate(1, 0, 460, () => { drag.value = null })
}

/* ── Swipe the hero: change robot, or swipe up for screens ────────── */
const dxSwipe = ref(0)
const swiping = ref(false)
let sw = null
function swDown(e) {
  if (!live.value) return
  try { e.currentTarget.setPointerCapture(e.pointerId) } catch { /* not capturable */ }
  const [x, py] = local(e)
  sw = { x, y: py, dx: 0, dy: 0 }
}
function swMove(e) {
  if (!sw) return
  const [x, py] = local(e)
  const ddx = x - sw.x, ddy = py - sw.y
  sw.dx = ddx; sw.dy = ddy
  if (!swiping.value && Math.abs(ddy) > Math.abs(ddx)) return
  if (Math.abs(ddx) > 6 || swiping.value) { dxSwipe.value = ddx; swiping.value = true }
}
function swUp() {
  if (!sw) return
  const { dx: ddx, dy: ddy } = sw
  sw = null
  if (!swiping.value && ddy < -60 && Math.abs(ddx) < 50) { strip.value = true; return }
  const n = list.value.length
  if (Math.abs(dxSwipe.value) > 70 && n > 1) {
    const dir = dxSwipe.value < 0 ? 1 : -1
    pickRobot(list.value[(idx.value + dir + n) % n].id, dir)
  } else {
    dxSwipe.value = 0; swiping.value = false
  }
}
async function pickRobot(id, dir = 0) {
  sheet.value = dir ? sheet.value : null
  strip.value = false
  dxSwipe.value = dir * 60; swiping.value = true
  requestAnimationFrame(() => requestAnimationFrame(() => { dxSwipe.value = 0; swiping.value = false }))
  const res = await switchRobot(id)
  if (!res.ok) emit('toast', res.message || 'Could not switch robot.')
}

/* ── Push-to-talk (design 2w–2y), live on Fish Audio via voice-turn ── */
const vt = useVoiceTurn('robot', { screen: 'home', onNavigate: (p) => go(p) })
const slide = ref(false)
const voiceState = computed(() => slide.value && vt.state.value === 'listen' ? 'cancel'
  : vt.state.value === 'think' ? 'answer' : vt.state.value)
const voice = computed(() => voiceState.value)
const talking = computed(() => voice.value === 'listen' || voice.value === 'cancel')
const caption = computed(() => vt.state.value === 'think' ? 'Thinking…' : vt.caption.value)
let pv = null
function pttDown(e) {
  if (!live.value) return
  try { e.currentTarget.setPointerCapture(e.pointerId) } catch { /* not capturable */ }
  const [x] = local(e)
  pv = { x }
  slide.value = false
  vt.start()
  haptic(20)
}
function pttMove(e) {
  if (!pv) return
  const [x] = local(e)
  slide.value = x - pv.x < -80
}
function pttUp() {
  if (!pv) return
  pv = null
  vt.stop(slide.value)
  slide.value = false
}
async function voiceConfirm() {
  const res = await vt.confirm()
  if (res) emit('toast', res.success ? (res.message || 'Sent to your broker.') : (res.error || 'That did not go through.'))
}
const capTop = computed(() => L.value === 'focus' ? 560 : L.value === 'full' ? 470 : 560)

/* ── Screens strip and sheets ────────────────────────────────────── */
const strip = ref(false)
const sheet = ref(null)
function toggleStrip() { if (live.value) strip.value = !strip.value }
function openSheet(k) {
  if (!live.value) return
  if (k === 'symbols') refreshPlan()
  sheet.value = k
}
function go(path) { sheet.value = null; strip.value = false; emit('go', path) }
function openScreen(c) {
  strip.value = false
  if (c.action === 'look') emit('look')
  else emit('go', c.to)
}

/* ── Derived styles ──────────────────────────────────────────────── */
const glassVal = computed(() => lite.value ? 'none' : 'blur(16px) saturate(1.3)')
const glassFx = computed(() => ({ backdropFilter: glassVal.value, WebkitBackdropFilter: glassVal.value }))
const satStyle = computed(() => ({ background: lite.value ? 'rgba(20,20,26,.94)' : 'rgba(14,14,19,.5)', ...glassFx.value }))
const trackStyle = computed(() => ({
  background: lite.value ? 'rgba(16,16,21,.94)' : 'rgba(14,14,19,.5)',
  border: '1px solid ' + (running.value ? 'color-mix(in srgb, var(--a) 55%, transparent)' : 'rgba(255,255,255,.14)'),
  boxShadow: (running.value && !lite.value) ? '0 0 34px -8px color-mix(in srgb, var(--a) 70%, transparent)' : '0 0 34px -8px transparent',
  ...glassFx.value,
}))
const glowOp = computed(() => running.value ? 1 : 0.15)
const cardShadow = computed(() => lite.value
  ? '0 0 0 1px color-mix(in srgb, var(--a) 40%, transparent)'
  : running.value
    ? '0 30px 80px -26px color-mix(in srgb, var(--a) 75%, transparent), 0 0 60px -10px color-mix(in srgb, var(--a) 35%, transparent)'
    : '0 30px 80px -26px rgba(0,0,0,.8), 0 0 60px -10px transparent')
const focusShadow = computed(() => lite.value
  ? '0 0 0 2px color-mix(in srgb, var(--a) 70%, transparent)'
  : running.value
    ? '0 0 0 1px rgba(255,255,255,.08), 0 0 50px 6px color-mix(in srgb, var(--a) 55%, transparent), 0 0 140px 30px color-mix(in srgb, var(--a) 22%, transparent)'
    : '0 0 0 1px rgba(255,255,255,.08), 0 0 50px 6px transparent, 0 0 140px 30px transparent')
const arcEdge = computed(() => running.value ? 'color-mix(in srgb, var(--a) 50%, transparent)' : 'rgba(255,255,255,.14)')

const heroStyle = computed(() => ({
  transform: `translateX(${dxSwipe.value}px)`,
  opacity: 1 - Math.min(0.7, Math.abs(dxSwipe.value) / 260),
  filter: [running.value ? '' : 'brightness(.5) saturate(.55)', talking.value ? 'brightness(.72)' : ''].filter(Boolean).join(' ') || 'none',
  transition: swiping.value ? 'filter .6s ease' : 'transform .38s cubic-bezier(.2,.8,.2,1), opacity .38s ease, filter .6s ease',
}))
const mediaStyle = computed(() => ({
  objectPosition: pos.value,
  animation: reduced.value ? 'none' : 'rhLoop 14s ease-in-out infinite',
  animationPlayState: running.value ? 'running' : 'paused',
}))

const nmC = computed(() => fitName(rb.value.raw, 300, 60, 34, 40))
const nmF = computed(() => fitName(rb.value.raw, 310, 62, 36, 50))
const nameStroke = computed(() => running.value ? 'color-mix(in srgb, var(--a) 45%, #FFFFFF)' : 'color-mix(in srgb, var(--a) 45%, #4A4A54)')
const nameGlow = computed(() => lite.value
  ? (running.value ? 'drop-shadow(0 0 6px var(--a))' : 'none')
  : running.value
    ? 'drop-shadow(0 0 2px var(--a)) drop-shadow(0 0 10px var(--a)) drop-shadow(0 0 26px color-mix(in srgb, var(--a) 70%, transparent))'
    : 'drop-shadow(0 0 2px transparent) drop-shadow(0 0 10px transparent) drop-shadow(0 0 26px transparent)')
function nameStyle(nm) {
  return {
    fontSize: nm.size,
    WebkitTextStroke: `${nm.stroke} ${nameStroke.value}`,
    filter: nameGlow.value,
    opacity: running.value ? 1 : 0.7,
  }
}

const knobDisc = computed(() => ({
  background: charged.value ? 'radial-gradient(circle at 50% 35%, color-mix(in srgb, var(--a) 70%, #FFFFFF), var(--a) 70%)' : 'radial-gradient(circle at 50% 35%, #34343D, #1A1A21)',
  boxShadow: charged.value
    ? (lite.value ? 'none' : '0 0 26px color-mix(in srgb, var(--a) 80%, transparent), inset 0 0 0 1px rgba(255,255,255,.25)')
    : '0 4px 12px rgba(0,0,0,.5), inset 0 0 0 1px rgba(255,255,255,.12)',
}))
const knobIcon = computed(() => charged.value ? (darkOn.value ? '#0B0B0E' : '#FFFFFF') : '#E6E6EA')
const ringStyle = computed(() => ({
  stroke: charged.value ? (darkOn.value ? 'rgba(11,11,14,.7)' : 'rgba(255,255,255,.9)') : 'var(--a)',
  transition: holding.value ? 'stroke-dashoffset 1.2s linear' : charging.value ? 'stroke-dashoffset .65s cubic-bezier(.3,.7,.2,1)' : drag.value != null ? 'none' : 'stroke-dashoffset .3s ease',
  filter: (charged.value && !lite.value) ? 'drop-shadow(0 0 3px rgba(255,255,255,.7))' : 'none',
}))
const labelStyle = computed(() => {
  const base = { fontSize: '12.5px', fontWeight: 600, letterSpacing: '.18em', whiteSpace: 'nowrap', opacity: labelOp.value, transition: 'opacity .2s ease' }
  const shimmer = !running.value && !charging.value && anim.value
  return shimmer
    ? { ...base, color: 'transparent', backgroundImage: 'linear-gradient(90deg, rgba(255,255,255,.38) 0%, rgba(255,255,255,.38) 35%, #FFFFFF 50%, rgba(255,255,255,.38) 65%, rgba(255,255,255,.38) 100%)', backgroundSize: '220% 100%', WebkitBackgroundClip: 'text', backgroundClip: 'text', animation: 'rhShimmer 2.6s linear infinite' }
    : { ...base, color: running.value ? '#F4F4F7' : 'rgba(255,255,255,.7)' }
})

/** The spark that invites the slide, and the charged orbit (design 2d). */
function orbits(sz) {
  const a = accent.value
  const dot = (d, alpha, extra) => ({ position: 'absolute', left: '50%', top: 0, width: d + 'px', height: d + 'px', marginLeft: -d / 2 + 'px', marginTop: -d / 2 + 'px', borderRadius: '50%', background: `color-mix(in srgb, ${a} 45%, #FFFFFF)`, boxShadow: `0 0 6px ${a}, 0 0 12px ${a}`, opacity: alpha, ...(extra || {}) })
  if (!charged.value) {
    if (!anim.value) return []
    return [{ key: 'os', style: { position: 'absolute', left: '-9px', top: '-9px', width: sz + 18 + 'px', height: sz + 18 + 'px', animation: 'rhSpin 9s linear infinite', pointerEvents: 'none' }, dot: dot(3, 0.6) }]
  }
  const ringO = { key: 'r', style: { position: 'absolute', inset: '-12px', borderRadius: '50%', border: `1px dashed color-mix(in srgb, ${a} 60%, transparent)`, animation: anim.value ? 'rhSpin 16s linear infinite' : 'none', pointerEvents: 'none' } }
  if (!anim.value) return [ringO]
  const layer = (k, inset, dur, rev, d, alpha, bottom) => ({ key: k, style: { position: 'absolute', inset: inset + 'px', animation: `rhSpin ${dur}s linear infinite${rev ? ' reverse' : ''}`, pointerEvents: 'none' }, dot: dot(d, alpha, bottom ? { top: '100%' } : null) })
  return [ringO, layer('a', -12, 4.5, false, 4, 0.95), layer('b', -17, 7, true, 3, 0.7, true), layer('c', -6, 3.2, false, 2.5, 0.8)]
}

const micStyle = computed(() => {
  const v = voice.value
  return {
    background: v === 'listen' ? accent.value : v === 'cancel' ? 'rgba(255,93,93,.22)' : (lite.value ? 'color-mix(in srgb, var(--a) 30%, #101014)' : 'color-mix(in srgb, var(--a) 24%, rgba(12,12,16,.6))'),
    border: '1.5px solid ' + (v === 'cancel' ? 'rgba(255,93,93,.85)' : 'color-mix(in srgb, var(--a) 70%, #FFFFFF)'),
    boxShadow: lite.value ? 'none' : v === 'listen' ? '0 0 50px 0 color-mix(in srgb, var(--a) 80%, transparent)' : v === 'answer' ? '0 0 40px -4px color-mix(in srgb, var(--a) 75%, transparent)' : '0 10px 26px -8px rgba(0,0,0,.6), 0 0 30px -6px color-mix(in srgb, var(--a) 65%, transparent)',
    transform: v === 'listen' ? 'scale(1.08)' : 'scale(1)',
    ...glassFx.value,
  }
})
const micIcon = computed(() => voice.value === 'listen' && darkOn.value ? '#0B0B0E' : '#FFFFFF')
const micGlyph = computed(() => voice.value === 'cancel' ? 'close' : voice.value === 'answer' ? 'graphic_eq' : 'mic')
const micLabel = computed(() => voice.value === 'listen' ? 'Listening. Release to send, slide left to cancel.' : voice.value === 'answer' ? `${rb.value.name} is answering` : `Hold to talk to ${rb.value.name}`)
const waveBg = computed(() => lite.value ? 'rgba(16,16,21,.95)' : 'rgba(10,10,14,.6)')
const hintText = computed(() => voice.value === 'cancel' ? 'Release to cancel' : 'Slide left to cancel')
const hintColor = computed(() => voice.value === 'cancel' ? '#FF8A8A' : 'rgba(255,255,255,.75)')

/* ── Full-Bleed: thin name, bare mic, glass sheet ────────────────── */
const thinName = computed(() => (rb.value.raw || rb.value.name || '').replace('|', ' ').trim().toUpperCase())
const thinSize = computed(() => Math.max(22, Math.min(50, Math.floor(272 / (Math.max(1, thinName.value.length) * 1.06)))) + 'px')
const thinGlow = computed(() => lite.value
  ? (running.value ? '0 0 8px var(--a)' : 'none')
  : running.value
    ? '0 0 10px color-mix(in srgb, var(--a) 95%, transparent), 0 0 28px color-mix(in srgb, var(--a) 75%, transparent), 0 0 60px color-mix(in srgb, var(--a) 45%, transparent)'
    : '0 2px 12px rgba(0,0,0,.7)')
/** The mic is only a glyph: thin outline at rest, filled and glowing while held. */
const bareStyle = computed(() => {
  const v = voice.value
  return {
    fontSize: '30px', fontWeight: 300,
    fontVariationSettings: `'FILL' ${v === 'listen' || v === 'answer' ? 1 : 0}, 'wght' 300`,
    color: v === 'listen' ? 'color-mix(in srgb, var(--a) 45%, #FFFFFF)' : v === 'cancel' ? '#FF8A8A' : v === 'answer' ? 'color-mix(in srgb, var(--a) 30%, #FFFFFF)' : 'rgba(255,255,255,.9)',
    textShadow: lite.value ? 'none' : v === 'listen' ? '0 0 10px var(--a), 0 0 26px var(--a), 0 0 48px color-mix(in srgb, var(--a) 60%, transparent)' : v === 'answer' ? '0 0 14px color-mix(in srgb, var(--a) 80%, transparent)' : v === 'cancel' ? '0 0 14px rgba(255,93,93,.7)' : '0 2px 12px rgba(0,0,0,.7)',
    transform: v === 'listen' ? 'scale(1.08)' : 'scale(1)',
  }
})
const sheetStyle = computed(() => {
  const g = lite.value ? 'none' : 'blur(30px) saturate(1.6)'
  return {
    background: lite.value ? 'rgba(16,16,21,.95)' : 'linear-gradient(180deg, rgba(20,20,26,.36), rgba(10,10,14,.62))',
    backdropFilter: g, WebkitBackdropFilter: g,
    boxShadow: (running.value && !lite.value) ? '0 -24px 60px -20px color-mix(in srgb, var(--a) 55%, transparent)' : '0 -20px 50px -24px rgba(0,0,0,.6)',
  }
})

/* ── Next news release (market-wire), design 4a–4c ───────────────── */
/* Impact sets the colour. The bar fills over the last four hours before the
   release. There is no robot bias in the wire, so the card names the currency
   and the figures instead of inventing a BUY/SELL. */
const IMPACT = { high: ['HIGH', '#FF7A45'], medium: ['MED', '#F2B53A'], low: ['LOW', '#A3A3AE'], none: ['LOW', '#A3A3AE'] }
const RANK = { high: 0, medium: 1, low: 2, none: 3 }
const wire = ref(null)
const nowMs = ref(Date.now())
let wireT = 0
async function loadNews() {
  if (!live.value || L.value !== 'full') return
  try {
    const w = await loadWire(symbolRows.value.map(r => r.sym))
    wire.value = w.events
  } catch { wire.value = { ok: false, items: [] } }
}
const nextEvent = computed(() => {
  const items = (wire.value?.items || []).filter(e => e.at && new Date(e.at).getTime() > nowMs.value)
  if (!items.length) return null
  const t0 = new Date(items.reduce((a, e) => (e.at < a ? e.at : a), items[0].at)).getTime()
  /* The soonest release, unless a bigger one lands within the next hour of it. */
  return items
    .filter(e => new Date(e.at).getTime() - t0 < 3600e3)
    .sort((a, b) => (RANK[a.impact] - RANK[b.impact]) || (a.at < b.at ? -1 : 1))[0]
})
const news = computed(() => {
  const e = nextEvent.value
  if (!e) {
    const msg = !wire.value ? 'Loading calendar…' : wire.value.ok ? 'No releases ahead' : 'Calendar unavailable'
    return { lvl: 'NEWS', name: msg, when: '', sub: 'Tap for Fundamentals', color: '#A3A3AE', frac: null, aria: `${msg}. Open Fundamentals` }
  }
  const [lvl, color] = IMPACT[e.impact] || IMPACT.none
  const mins = Math.max(0, Math.round((new Date(e.at).getTime() - nowMs.value) / 60000))
  const when = mins < 60 ? `${mins}m` : mins < 1440 ? `${Math.floor(mins / 60)}h ${String(mins % 60).padStart(2, '0')}m` : whenLabel(e.at, nowMs.value)
  const figs = [e.forecast && `F ${e.forecast}`, e.previous && `P ${e.previous}`].filter(Boolean).join(' · ')
  return {
    lvl, color, when, name: e.title,
    sub: [e.currency, figs].filter(Boolean).join(' · ') || 'Tap for Fundamentals',
    frac: Math.max(0.02, Math.min(1, 1 - mins / 240)),
    aria: `${e.title} ${whenLabel(e.at, nowMs.value)}, ${lvl.toLowerCase()} impact. Open Fundamentals`,
  }
})
function openNews() { if (live.value) go('/scanner?screen=fund') }
watch(L, loadNews)

const knobLabel = computed(() => running.value ? `Running. Hold to stop ${rb.value.name}` : `Slide to start ${rb.value.name}`)
const symLabel = computed(() => issues.value ? `Trading symbols, ${issues.value} need setting up` : 'Trading symbols')
const gridLabel = computed(() => strip.value ? 'Close screens' : 'Other screens. Or swipe up.')
const dotColor = computed(() => kind.value === 'error' ? RED : kind.value === 'running' ? GREEN : GREY)
const dotLabel = computed(() => kind.value === 'error' ? 'Status: problem. Tap for details and the fix.' : kind.value === 'running' ? 'Status: running. Tap for details.' : 'Status: stopped. Tap for details.')

/* ── Falling FX ──────────────────────────────────────────────────── */
const fx = computed(() => reduced.value ? 'off' : theme.fx)
const fxMode = computed(() => reduced.value ? 'reduced' : !running.value ? 'static' : mode.value)
const seed = computed(() => L.value === 'classic' ? 3 : L.value === 'focus' ? 5 : 9)
/** Keep-out zones around the status dot and the mic, in stage px. */
const fxClear = computed(() => {
  const x = v => (v + dx.value).toFixed(0)
  const dot = `${x(356)},${(70 + st.value).toFixed(0)},24`
  const mic = L.value === 'focus' ? `${x(195)},${(700 + dy.value).toFixed(0)},48`
    : L.value === 'full' ? `${x(195)},${(653 + dy.value).toFixed(0)},40`
    : `${x(338)},${(680 + dy.value).toFixed(0)},50`
  return `${dot};${mic}`
})

/* Re-read the symbol plan when returning to Home from the wizard. */
function onVisible() { if (document.visibilityState === 'visible') refreshPlan() }
onMounted(() => {
  refreshPlan(); document.addEventListener('visibilitychange', onVisible)
  loadNews()
  if (live.value) wireT = setInterval(() => { nowMs.value = Date.now() }, 30000)
})
onUnmounted(() => {
  clearInterval(wireT)
  document.removeEventListener('visibilitychange', onVisible)
  cancelAnimationFrame(raf)
  clearTimeout(holdT)
  timers.forEach(clearTimeout)
})
</script>

<style>
@keyframes rhLoop { 0%,100% { transform: scale(1.04) translate(0,0) } 50% { transform: scale(1.11) translate(-1.6%,-1.2%) } }
@keyframes rhSpin { to { transform: rotate(360deg) } }
@keyframes rhRing { 0% { transform: scale(1); opacity: .7 } 100% { transform: scale(1.9); opacity: 0 } }
@keyframes rhShimmer { 0% { background-position: 120% 0 } 100% { background-position: -120% 0 } }
@keyframes rhSheen { 0% { transform: translateX(-140px) } 60%,100% { transform: translateX(420px) } }
@keyframes rhBreath { 0%,100% { opacity: 1 } 50% { opacity: .78 } }
@keyframes rhPulse { 0%,100% { transform: scale(1); opacity: 1 } 50% { transform: scale(1.6); opacity: .4 } }
</style>

<style scoped>
.rh { position: fixed; inset: 0; overflow: hidden; background: #08080B; }
.rh-mini { position: absolute; pointer-events: none; }
.rh-probe { position: absolute; visibility: hidden; pointer-events: none; padding-top: env(safe-area-inset-top, 0px); }
.rh-stage {
  position: absolute; left: 0; top: 0; transform-origin: 0 0; overflow: hidden;
  background: #08080B; color: #F4F4F7;
  font-family: Geist, system-ui, sans-serif; -webkit-font-smoothing: antialiased;
  user-select: none; -webkit-user-select: none; isolation: isolate;
}
.ms {
  font-family: 'Material Symbols Outlined';
  font-weight: 400; font-style: normal; line-height: 1; letter-spacing: normal;
  font-feature-settings: 'liga'; color: #F4F4F7;
}
.ms-fill { font-variation-settings: 'FILL' 1; }
.rh-fill { position: absolute; inset: 0; }
.rh-abs { position: absolute; }
.rh-nopt { pointer-events: none; }
.rh-pt { pointer-events: auto; }
.rh-wash { position: absolute; background-size: cover; }
.rh-col { position: absolute; top: 0; }
.rh-glow { transition: opacity .6s ease; }

.rh-name {
  display: flex; flex-direction: column; align-items: center; gap: 4px;
  font-family: Unbounded, Geist, sans-serif; font-weight: 700; line-height: 1.04;
  letter-spacing: .06em; color: transparent; white-space: nowrap; text-align: center;
  transition: filter .6s ease, opacity .4s ease;
}
.rh-name-c { position: absolute; left: 28px; right: 28px; display: flex; flex-direction: column; align-items: center; gap: 10px; z-index: 6; pointer-events: none; }
.rh-name-f { left: 0; right: 0; height: 120px; z-index: 6; pointer-events: none; justify-content: center; }
.rh-powered { display: flex; align-items: center; gap: 4px; font-size: 11.5px; letter-spacing: .02em; color: rgba(255,255,255,.5); text-shadow: 0 1px 6px rgba(0,0,0,.7); pointer-events: none; z-index: 9; }
.rh-powered-b { font-weight: 600; color: rgba(255,255,255,.8); }

.rh-hero { position: absolute; overflow: hidden; background: #111116; touch-action: pan-y; cursor: grab; transition: box-shadow .6s ease; }
.rh-hero-c { left: 28px; width: 334px; height: 400px; border-radius: 34px; z-index: 1; }
.rh-hero-f { left: 71px; width: 248px; height: 248px; border-radius: 50%; z-index: 2; }
.rh-hero-in { position: absolute; inset: 0; }
.rh-media { position: absolute; left: 0; top: 0; width: 100%; height: 100%; object-fit: cover; transform-origin: 50% 40%; pointer-events: none; }
.rh-hero-edge { position: absolute; inset: 0; border-radius: 34px; pointer-events: none; box-shadow: inset 0 0 0 1px rgba(255,255,255,.09); background: linear-gradient(180deg, rgba(255,255,255,.06), transparent 22%); }
.rh-hero-edge-f { position: absolute; inset: 0; border-radius: 50%; pointer-events: none; box-shadow: inset 0 0 0 1.5px color-mix(in srgb, var(--a) 55%, rgba(255,255,255,.5)), inset 0 0 40px rgba(0,0,0,.45); }
.rh-refl { position: absolute; left: 28px; width: 334px; height: 60px; overflow: hidden; z-index: 1; pointer-events: none; -webkit-mask-image: linear-gradient(180deg, rgba(0,0,0,.4), transparent); mask-image: linear-gradient(180deg, rgba(0,0,0,.4), transparent); }
.rh-refl-in { position: absolute; left: 0; top: 0; width: 334px; height: 400px; border-radius: 34px; overflow: hidden; transform: scaleY(-1); filter: blur(2px); }
.rh-full-media { background: #08080B; overflow: hidden; z-index: 1; touch-action: pan-y; cursor: grab; }
.rh-halo { width: 340px; height: 340px; border-radius: 50%; background: conic-gradient(from 0deg, var(--a), #E0B04A 16%, transparent 32%, var(--a) 50%, transparent 68%, #E0B04A 84%, var(--a)); filter: blur(22px); transition: opacity .6s ease; }
.rh-arc { left: 0; z-index: 3; pointer-events: none; overflow: visible; }

.rh-sat { position: absolute; width: 52px; height: 52px; border-radius: 50%; z-index: 7; display: flex; align-items: center; justify-content: center; cursor: pointer; border: 1px solid rgba(255,255,255,.14); transition: transform .15s ease; }
.rh-sat:active { transform: scale(.94); }
.rh-grid { flex-direction: column; }
.rh-chev { font-size: 16px; color: rgba(255,255,255,.7); margin: -7px 0 -5px; }
.rh-badge { position: absolute; right: -3px; top: -3px; min-width: 20px; height: 20px; padding: 0 5px; border-radius: 10px; background: #F2B53A; border: 2px solid #08080B; display: flex; align-items: center; justify-content: center; font-size: 11px; font-weight: 700; color: #141008; }

.rh-track { position: absolute; z-index: 7; transition: border-color .5s ease, box-shadow .5s ease; }
.rh-track-fill { position: absolute; left: 0; top: 0; bottom: 0; background: linear-gradient(90deg, color-mix(in srgb, var(--a) 8%, transparent), color-mix(in srgb, var(--a) 55%, transparent)); }
.rh-track-label { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; pointer-events: none; }
.rh-knob { position: absolute; touch-action: none; cursor: grab; }
.rh-knob-disc { position: absolute; inset: 0; border-radius: 50%; transition: background .4s ease, box-shadow .4s ease; }
.rh-ring { position: absolute; left: 0; top: 0; transform: rotate(-90deg); overflow: visible; }
.rh-knob-ic { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; font-weight: 500; }

/* Full-Bleed (design 1a/3a/4a–4c) */
.rh-thin-wrap { left: 26px; width: 272px; z-index: 6; display: flex; flex-direction: column; gap: 12px; transition: opacity .4s ease; }
.rh-thin { font-family: Geist, sans-serif; font-weight: 200; line-height: 1; letter-spacing: .42em; white-space: nowrap; color: #FFFFFF; transition: text-shadow .6s ease; }
.rh-thin-by { display: flex; align-items: center; gap: 8px; font-size: 10.5px; font-weight: 500; letter-spacing: .24em; color: rgba(255,255,255,.55); white-space: nowrap; }
.rh-thin-rule { width: 18px; height: 1px; background: var(--a); box-shadow: 0 0 6px var(--a); }
.rh-thin-b { font-weight: 600; color: rgba(255,255,255,.88); }
.rh-bare { position: absolute; left: 163px; width: 64px; height: 54px; z-index: 8; cursor: pointer; touch-action: none; display: flex; align-items: center; justify-content: center; -webkit-tap-highlight-color: transparent; }
.rh-bare .ms { transition: color .2s ease, text-shadow .3s ease, transform .18s ease; }
.rh-sheet { position: absolute; bottom: 0; z-index: 7; border-radius: 34px 34px 0 0; border-top: 1px solid rgba(255,255,255,.16); overflow: hidden; transition: box-shadow .6s ease; }
.rh-sheet-edge { position: absolute; left: 28px; right: 28px; top: 0; height: 1px; background: linear-gradient(90deg, transparent, var(--a), transparent); box-shadow: 0 0 16px 1px var(--a); transition: opacity .6s ease; }
.rh-sheen { position: absolute; left: 0; top: 0; width: 140px; height: 1px; background: linear-gradient(90deg, transparent, #FFFFFF, transparent); transition: opacity .6s ease; }
.rh-sheet-row { position: absolute; left: 50%; margin-left: -179px; width: 358px; top: 16px; height: 44px; display: flex; align-items: center; gap: 8px; }
.rh-sheet-track { position: absolute; left: 50%; margin-left: -179px; width: 358px; top: 72px; height: 64px; border-radius: 32px; background: rgba(0,0,0,.32); transition: border-color .5s ease, box-shadow .5s ease; }
.rh-sbtn { position: relative; flex: none; width: 44px; height: 44px; border-radius: 50%; display: flex; flex-direction: column; align-items: center; justify-content: center; cursor: pointer; background: rgba(255,255,255,.08); border: 1px solid rgba(255,255,255,.1); transition: transform .15s ease; }
.rh-sbtn:active { transform: scale(.94); }
.rh-news {
  position: relative; flex: 1; min-width: 0; height: 44px; padding: 0 12px 2px; border-radius: 14px;
  display: flex; flex-direction: column; justify-content: center; gap: 2px; overflow: hidden; cursor: pointer;
  background: linear-gradient(180deg, color-mix(in srgb, var(--n) 13%, transparent), color-mix(in srgb, var(--n) 4%, transparent));
  border: 1px solid color-mix(in srgb, var(--n) 34%, transparent); transition: transform .15s ease;
}
.rh-news:active { transform: scale(.98); }
.rh-news-l1 { display: flex; align-items: center; gap: 6px; min-width: 0; }
.rh-news-lvl { font-size: 9.5px; font-weight: 700; letter-spacing: .12em; color: var(--n); white-space: nowrap; }
.rh-news-name { font-size: 13.5px; font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.rh-news-t { font-family: 'Geist Mono', monospace; font-size: 13px; font-weight: 500; color: var(--n); white-space: nowrap; }
.rh-news-l2 { font-size: 11.5px; color: rgba(255,255,255,.6); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.rh-news-bar { position: absolute; left: 0; right: 0; bottom: 0; height: 2px; background: rgba(255,255,255,.08); }
.rh-news-fill { position: relative; height: 100%; background: var(--n); box-shadow: 0 0 8px var(--n); transition: width 1s ease; }
.rh-news-dot { position: absolute; right: -2px; top: -1.5px; width: 5px; height: 5px; border-radius: 50%; background: var(--n); }

.rh-wave { position: absolute; left: 16px; right: 16px; height: 56px; border-radius: 28px; z-index: 7; border: 1px solid rgba(255,255,255,.1); overflow: hidden; pointer-events: none; }
.rh-hint { position: absolute; height: 18px; z-index: 7; display: flex; align-items: center; gap: 4px; font-size: 12.5px; font-weight: 500; pointer-events: none; white-space: nowrap; text-shadow: 0 1px 6px rgba(0,0,0,.6); }
.rh-hint .ms { color: inherit; }
.rh-mic-wrap { position: absolute; z-index: 8; }
.rh-mic-ring { position: absolute; left: -16px; top: -16px; right: -16px; bottom: -16px; pointer-events: none; }
.rh-mic { position: absolute; inset: 0; border-radius: 50%; display: flex; align-items: center; justify-content: center; cursor: pointer; touch-action: none; transition: transform .18s ease, background-color .18s ease, border-color .18s ease; }

.rh-dot { position: absolute; width: 44px; height: 44px; display: flex; align-items: center; justify-content: center; cursor: pointer; z-index: 10; }
.rh-dot-ring { position: absolute; left: 11px; top: 11px; width: 22px; height: 22px; border-radius: 50%; border: 1.5px solid #FF5D5D; box-sizing: border-box; }
.rh-dot-core { position: relative; width: 10px; height: 10px; border-radius: 50%; transition: background .4s ease; }

.rh-caption {
  position: absolute; width: 350px; z-index: 11; padding: 12px 16px; border-radius: 20px;
  background: rgba(14,14,19,.78); border: 1px solid color-mix(in srgb, var(--a) 40%, transparent);
  backdrop-filter: blur(16px); -webkit-backdrop-filter: blur(16px);
  font-size: 14px; line-height: 20px; color: #E6E6EA; pointer-events: none;
}
.rh-cap-who { font-size: 10.5px; font-weight: 600; letter-spacing: .14em; color: #9C9CA8; margin-bottom: 3px; }
.rh-cap-enter-active, .rh-cap-leave-active { transition: opacity .25s ease, transform .25s ease; }
.rh-cap-enter-from, .rh-cap-leave-to { opacity: 0; transform: translateY(8px); }
</style>
