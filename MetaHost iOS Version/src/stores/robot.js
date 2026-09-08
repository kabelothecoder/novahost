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
 * Everything genuinely new (the EA list, selection) is plain state.
 */

import { reactive, computed } from 'vue'
import { accent } from './accent.js'

export const robot = reactive({
  /* ── Façade over accent.js — see note above ────────────────────── */
  get name()  { return accent.robotName || 'MetaBot' },
  set name(v) { accent.robotName = v },

  get avatarUrl()  { return accent.avatarUrl || '' },
  set avatarUrl(v) { accent.avatarUrl = v },

  get allowedSymbols()  { return accent.allowedSymbols || [] },
  set allowedSymbols(v) { accent.allowedSymbols = v },

  /* ── Owned here ────────────────────────────────────────────────────
     Seeded from licence identity, the way HomeView used to do inline.
     Lifting it out of the view is the point: a layout switch remounts
     Home, and a list held in the view would be rebuilt — losing the
     user's selection every time they changed layout.                  */
  eaList: [],
  activeEaId: null,

  /**
   * Mentor credit, shown under the robot name on the art-forward
   * layouts. Not in the licence payload today, so it stays null and
   * the layouts omit the line rather than inventing one.
   */
  mentor: null,
})

/** Placeholder art. The doc's art modes all need *something* to crop. */
export const FALLBACK_AVATAR = '/robot_avatar.png'

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
 * Seeds the list from licence identity if it is still empty. Safe to
 * call on every Home mount — it will not clobber a selection or
 * duplicate the entry.
 */
export function ensureSeeded() {
  if (robot.eaList.length) return
  robot.eaList = [{
    id: 1,
    name: robot.name,
    active: true,
    avatar: robot.avatarUrl || FALLBACK_AVATAR,
  }]
  robot.activeEaId = 1
}

export function selectRobot(id) {
  if (robot.eaList.some(e => e.id === id)) robot.activeEaId = id
}
