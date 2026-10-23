/**
 * What every Home v4 layout reads: the robots, the run/broker status, and
 * which trading symbols still need setting up. Shared so Classic, Focus,
 * Full-Bleed and Command Core can never disagree about the same robot.
 */

import { computed, ref } from 'vue'
import { robot, artFor, selectRobot, FALLBACK_AVATAR } from '../../../stores/robot.js'
import { accent } from '../../../stores/accent.js'
import { session, startSession, stopSession, pushEvent } from '../../../stores/session.js'
import { theme } from '../../../stores/theme.js'
import { loadPlan, normaliseSymbol } from '../../../services/symbolPlanService.js'
import { haptic } from '../../../composables/useSpeech.js'

export const GREEN = '#4CD787'
export const GREY = '#8E8E99'
export const RED = '#FF5D5D'
export const AMBER = '#F2B53A'

/** The robots on this device, with the active one's live identity. */
export const robots = computed(() => {
  const active = robot.activeEaId
  const list = robot.eaList.length ? robot.eaList : [{ id: 'local', name: robot.name }]
  return list.map(ea => {
    const isActive = ea.id === active || list.length === 1
    const raw = (isActive ? robot.name : ea.name) || 'NovaHost AI'
    return {
      id: ea.id,
      raw,
      /* "Algo Wave | Scalper EA" — the pipe is the provider's line break,
         never shown as a character. */
      name: raw.replace(/\s*\|\s*/g, ' '),
      accent: isActive ? theme.accent.hex : (ea.accent || theme.accent.hex),
      /* Another robot's art must never fall back to the active robot's. */
      art: isActive ? artFor(null) : (ea.avatar || FALLBACK_AVATAR),
      /* The active robot's video comes from the last validation (fresher
         than the fleet list); another robot's from its own row only. */
      video: (isActive ? accent.videoUrl : '') || ea.video || '',
      busy: robot.busyId === ea.id,
    }
  })
})

export const activeIndex = computed(() => {
  const i = robots.value.findIndex(r => r.id === robot.activeEaId)
  return i < 0 ? 0 : i
})

export const activeRobot = computed(() => robots.value[activeIndex.value])

/**
 * Something is wrong only when the robot is meant to be trading and the
 * broker link is down. A stopped robot with no broker is not an error —
 * it is a robot that has not been set up yet, and painting it red would
 * cry wolf at every new user.
 */
export const statusKind = computed(() => {
  if (session.isRunning && !session.brokerConnected) return 'error'
  return session.isRunning ? 'running' : 'stopped'
})

/** Re-read on demand: the plan lives in device storage, not in a store. */
const planTick = ref(0)
export function refreshPlan() { planTick.value++ }

export const symbolRows = computed(() => {
  planTick.value // dependency for refreshPlan()
  const plan = loadPlan()
  return (robot.allowedSymbols || []).map(sym => {
    const row = plan[normaliseSymbol(sym)]
    const ready = Boolean(row && row.enabled && row.lot > 0)
    return {
      sym,
      ready,
      note: ready
        ? `Ready · ${row.lot} lots · up to ${row.max_trades || 1} trades`
        : 'Not set up yet. Choose a lot size in the symbol wizard.',
    }
  })
})

export const symbolIssues = computed(() => symbolRows.value.filter(r => !r.ready).length)

export function startRobot() {
  if (session.isRunning) return
  startSession()
  haptic(60)
  pushEvent(`START · ${robot.name}`, 'ok')
}

export function stopRobot() {
  if (!session.isRunning) return
  stopSession()
  haptic(30)
  pushEvent(`STOP · ${robot.name}`, 'idle')
}

/** Switches robot by re-validating its licence (see stores/robot.js). */
export async function switchRobot(id) {
  return selectRobot(id)
}

/** WCAG relative luminance, to pick dark or light ink on the accent. */
export function lum(h) {
  h = String(h || '#E0313F').replace('#', '')
  if (h.length === 3) h = h.split('').map(c => c + c).join('')
  const n = parseInt(h, 16)
  if (isNaN(n)) return 0.18
  const f = v => { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4) }
  return 0.2126 * f((n >> 16) & 255) + 0.7152 * f((n >> 8) & 255) + 0.0722 * f(n & 255)
}
export function darkOnAccent(hex) {
  const L = lum(hex)
  return (L + 0.05) / 0.05 > 1.05 / (L + 0.05)
}

/**
 * Fits the neon name into `avail` design px (design spec 2t–2v).
 * One line shrinks to `minOne`; below that it splits — at the provider's
 * "|" if they set one, else at the space nearest the middle.
 */
export function fitName(raw, avail, maxOne, minOne, maxTwo) {
  const up = String(raw || '').toUpperCase()
  const sw = z => Math.max(1, Math.min(1.6, z * 0.03)).toFixed(2) + 'px'
  const fz = (t, mx) => Math.max(14, Math.min(mx, Math.floor(avail / Math.max(1, t.length))))
  let parts = up.includes('|') ? up.split('|').map(x => x.trim()).filter(Boolean).slice(0, 2) : null
  if (!parts) {
    const one = fz(up, maxOne)
    if (one >= minOne || !up.includes(' ')) return { l1: up, l2: '', two: false, size: one + 'px', stroke: sw(one) }
    const mid = (up.length - 1) / 2
    let best = -1
    for (let i = 0; i < up.length; i++) if (up[i] === ' ' && (best < 0 || Math.abs(i - mid) < Math.abs(best - mid))) best = i
    parts = [up.slice(0, best), up.slice(best + 1)]
  }
  if (parts.length < 2) {
    const one = fz(parts[0], maxOne)
    return { l1: parts[0], l2: '', two: false, size: one + 'px', stroke: sw(one) }
  }
  const z = fz(parts[0].length >= parts[1].length ? parts[0] : parts[1], maxTwo)
  return { l1: parts[0], l2: parts[1], two: true, size: z + 'px', stroke: sw(z) }
}

/**
 * Effect level for this device: the user's Reduce-motion setting and the
 * OS preference both force 'reduced'; low-memory or Save-Data phones get
 * 'lite' (design 2p).
 */
export function effectMode() {
  const rm = window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches
  if (rm || theme.motion === 'reduced') return 'reduced'
  const mem = navigator.deviceMemory
  const saver = navigator.connection?.saveData
  if ((mem && mem <= 4) || saver) return 'lite'
  return 'full'
}
