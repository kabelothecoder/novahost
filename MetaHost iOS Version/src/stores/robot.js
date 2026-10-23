/**
 * NovaHost — robot identity store.
 *
 * The identity half of the `accent.js` split: which robot is selected,
 * what it is called, its art, and the symbols its licence permits.
 *
 * ── Why this is a façade, not a copy ─────────────────────────────────
 * `robotName`, `avatarUrl` and `allowedSymbols` cannot simply move out
 * of `accent.js`, because writers outside this redesign's scope still
 * set them there:
 *
 *   authService.js   writes all three on licence verification
 *   PairsView.vue    reads allowedSymbols
 *
 * Copying the values into a second reactive object would mean writing
 * mirror watches in both directions and living with the window where
 * they disagree. Instead the fields below are accessors over the SAME
 * `accent` object — one source of truth, two names for it. accent.js
 * keeps its own persistence watches, so nothing here writes storage.
 *
 * ── The fleet (2026-09-12) ───────────────────────────────────────────
 * `eaList` used to be a list of one, built by `ensureSeeded()` out of
 * whatever the last `validate-license` had written to secStorage.
 * Selecting a row moved `activeEaId` and nothing else — the name, art,
 * accent and allowed symbols stayed on the robot the stored key had
 * bound — and "Connect new robot" raised a toast. It is now the real
 * set of licences this handset holds, read from `my-licenses`, and
 * selecting one RE-VALIDATES that key so the whole identity moves with
 * it. See services/robotService.js for why that round trip is the point
 * rather than an overhead.
 */

import { reactive, computed } from 'vue'
import { accent } from './accent.js'
import { secStorage } from '../utils/secStorage.js'
import { loadFleet } from '../services/robotService.js'
import { activateLicenseKey } from '../services/authService.js'

export const robot = reactive({
  /* ── Façade over accent.js — see note above ────────────────────── */
  get name()  { return accent.robotName || 'MetaBot' },
  set name(v) { accent.robotName = v },

  get avatarUrl()  { return accent.avatarUrl || '' },
  set avatarUrl(v) { accent.avatarUrl = v },

  get allowedSymbols()  { return accent.allowedSymbols || [] },
  set allowedSymbols(v) { accent.allowedSymbols = v },

  /* ── Owned here ────────────────────────────────────────────────────
     Lifting the list out of the view is the point: a layout switch
     remounts Home, and a list held in the view would be rebuilt — losing
     both the fetch and the user's selection every time they changed
     layout.                                                            */
  eaList: [],
  activeEaId: null,

  /** Fleet load state, so the list can tell "no robots" from "not asked
   *  yet" from "we could not ask". Three different sentences. */
  loading: false,
  error: null,
  loadedAt: null,

  /** Set while a switch or an activation is in flight, so the row the
   *  user tapped can show it and the list can refuse a second tap. */
  busyId: null,

  /**
   * Mentor credit, shown under the robot name on the art-forward
   * layouts. Not in the licence payload today, so it stays null and
   * the layouts omit the line rather than inventing one.
   */
  mentor: null,

  /**
   * Robot build version, for v2's "verified v6" badge on the hero art.
   *
   * Null until a licence supplies one — the doc's "v6" is sample copy.
   * A hardcoded constant here would put a verification mark on every
   * install claiming a version nothing checked, which is worse than no
   * badge: the badge's whole meaning is that the licence vouched for it.
   */
  version: null,
})

/**
 * Placeholder art. The doc's art modes all need *something* to crop.
 *
 * v2 of the design doc replaces the robot entirely — `assets/new_avatar.jpg`
 * there, shipped here as `/robot_avatar_v2.jpg`. The swap is not just a
 * filename: the old PNG was wide and top-heavy (767 KB), the new one is a
 * 576x1024 PORTRAIT at 73 KB. Every crop tuned for the old aspect —
 * `object-position: 50% 38%` — was pulling the frame down past the
 * subject's face on a 9:16 source, so those move with it.
 */
export const FALLBACK_AVATAR = '/robot_avatar_v2.jpg'

export const activeEa = computed(() =>
  robot.eaList.find(e => e.id === robot.activeEaId) || robot.eaList[0] || null
)

/** True when the robot has real art — decides whether HomeBackdrop
 *  renders imagery or falls back to accent pools. */
export const hasArt = computed(() => Boolean(robot.avatarUrl))

export function artFor(ea) {
  return ea?.avatar || robot.avatarUrl || FALLBACK_AVATAR
}

/**
 * The row for the licence currently held in secStorage.
 *
 * Matched on the KEY rather than remembered, because the active licence
 * is whatever `validate-license` last wrote — a value authService can
 * change on a cold start without this store hearing about it.
 */
function activeIdFromStorage(list) {
  const key = secStorage.getItem('LICENSE_KEY')
  const hit = key && list.find(r => r.key === key)
  return hit ? hit.id : (list[0]?.id ?? null)
}

/**
 * Seeds the list from licence identity if it is still empty.
 *
 * The local fallback only. It exists for the first paint — Home should
 * not render an empty Robot List for the two seconds `my-licenses` takes
 * — and for the handset whose licence predates `device_activations` and
 * carries no metadata device id, which the server genuinely cannot list.
 * `refreshFleet()` replaces whatever this produced as soon as it answers.
 */
export function ensureSeeded() {
  if (robot.eaList.length) return
  const key = secStorage.getItem('LICENSE_KEY')
  robot.eaList = [{
    id: key || 'local',
    key,
    name: robot.name,
    avatar: robot.avatarUrl || null,
    video: accent.videoUrl || null,
    hasArt: Boolean(robot.avatarUrl),
    accent: null,
    symbols: robot.allowedSymbols,
    expiresAt: null,
    local: true,
  }]
  robot.activeEaId = robot.eaList[0].id
}

/** How long a fleet read stays fresh enough to reuse. */
const FLEET_TTL_MS = 30_000

/** The in-flight read, shared by every caller that arrives during it. */
let inFlight = null

/**
 * Replaces the list with what the licence server says this device holds.
 *
 * A failed fetch does NOT clear the list. The rows already on screen came
 * from a licence that was valid a moment ago, and blanking a user's robot
 * because a lift lost signal is a worse answer than a stale row plus an
 * error line.
 *
 * Deduplicated, because three separate things ask for it on one Home mount:
 * the view itself, the Robot List widget, and — the moment it is opened —
 * the switcher sheet. Each of those is correct to ask (the sheet can be
 * opened an hour after Home mounted, and a licence bought in between should
 * appear without a restart); what was wrong was three identical round trips
 * inside one second. Callers within the TTL get the last answer, callers
 * during a read share it, and `force` is there for a deliberate re-read —
 * which is what `addRobot` needs, since it has just changed the very thing
 * being cached.
 *
 * @param {{force?: boolean}} [opts]
 */
export async function refreshFleet({ force = false } = {}) {
  if (inFlight) return inFlight
  if (!force && robot.loadedAt && Date.now() - robot.loadedAt < FLEET_TTL_MS) {
    return { ok: true, robots: robot.eaList, cached: true }
  }

  robot.loading = true
  inFlight = doRefresh().finally(() => { inFlight = null })
  return inFlight
}

async function doRefresh() {
  const res = await loadFleet()

  if (res.ok) {
    /* An empty answer with a locally-seeded row is the pre-`device_activations`
       install described in ensureSeeded(). Keeping the seed is correct there:
       the licence works, the server simply has no binding row to list it by. */
    if (res.robots.length) {
      robot.eaList = res.robots
      robot.activeEaId = activeIdFromStorage(res.robots)
    }
    robot.error = null
    robot.loadedAt = Date.now()
  } else {
    robot.error = res.message
  }

  robot.loading = false
  return res
}

/**
 * Switches the active robot.
 *
 * Re-validates the chosen licence rather than moving a local pointer —
 * that call is what carries the name, art, accent and allowed symbols
 * across, and what re-checks the key's status and device binding at the
 * moment of the switch. A key revoked since the list was fetched fails
 * here, visibly, instead of leaving the app trading under a dead licence.
 *
 * @returns {Promise<{ok: boolean, message?: string}>}
 */
export async function selectRobot(id) {
  const ea = robot.eaList.find(e => e.id === id)
  if (!ea) return { ok: false, message: 'That robot is no longer on this device.' }
  if (id === robot.activeEaId) return { ok: true }

  /* The local seed has no key to re-validate against — it IS the active
     licence, by construction, so selecting it is a no-op rather than a
     failed call. */
  if (!ea.key) {
    robot.activeEaId = id
    return { ok: true }
  }

  robot.busyId = id
  try {
    const res = await activateLicenseKey(ea.key)
    if (!res.success) {
      return { ok: false, message: res.message || 'That licence was not accepted.' }
    }
    robot.activeEaId = id
    /* The list row is refreshed from what the server just returned, so a
       mentor who renamed the robot or changed its art sees it immediately
       rather than at the next fleet fetch. */
    ea.name = accent.robotName || ea.name
    if (accent.avatarUrl) ea.avatar = accent.avatarUrl
    ea.video = accent.videoUrl || null
    ea.symbols = accent.allowedSymbols || ea.symbols
    return { ok: true }
  } finally {
    robot.busyId = null
  }
}

/**
 * Binds another licence key to this device and adds its robot.
 *
 * The same call the unlock gate makes, which is the point: there is one
 * way a key becomes valid on a handset, and adding a second robot must
 * not be a second one. On success the fleet is re-read so the new row
 * arrives with its real identity rather than one assembled here.
 *
 * The new robot becomes active, because that is what the user was asking
 * for — `validate-license` has already overwritten the stored identity by
 * the time this returns, so leaving the old row selected would show one
 * robot's name over another's art.
 *
 * @returns {Promise<{ok: boolean, message?: string}>}
 */
export async function addRobot(rawKey) {
  const key = String(rawKey || '').trim().toUpperCase()
  if (!key) return { ok: false, message: 'Enter the licence key.' }

  if (robot.eaList.some(e => e.key && e.key.toUpperCase() === key)) {
    return { ok: false, message: 'That robot is already on this device.' }
  }

  robot.busyId = 'new'
  try {
    const res = await activateLicenseKey(key)
    if (!res.success) {
      return { ok: false, message: res.message || 'That licence key was not accepted.' }
    }

    /* Forced: the activation just changed what the server would answer,
       so the TTL is exactly wrong here. */
    const fleet = await refreshFleet({ force: true })
    /* The activation succeeded even if the list read did not. Selecting by
       key rather than trusting the refresh keeps the two independent: the
       robot is active either way, and the row appears at the next fetch. */
    const added = robot.eaList.find(e => e.key && e.key.toUpperCase() === key)
    if (added) robot.activeEaId = added.id
    else if (!fleet.ok) ensureSeededFrom(key)

    return { ok: true }
  } finally {
    robot.busyId = null
  }
}

/** The add-path twin of ensureSeeded: a row for a key that activated but
 *  that the list endpoint could not confirm. Without it a successful
 *  activation on a flat network leaves the user looking at the previous
 *  robot's row while the app runs the new one. */
function ensureSeededFrom(key) {
  robot.eaList = [
    ...robot.eaList.filter(e => !e.local),
    {
      id: key,
      key,
      name: accent.robotName || 'NovaHost AI',
      avatar: accent.avatarUrl || null,
      video: accent.videoUrl || null,
      hasArt: Boolean(accent.avatarUrl),
      accent: null,
      symbols: accent.allowedSymbols || [],
      expiresAt: null,
      local: true,
    },
  ]
  robot.activeEaId = key
}
