/**
 * NovaHost — broker accounts store.
 *
 * Holds the list the My accounts screen draws and the actions on it. State
 * lives here rather than in the view so a route change does not reload the
 * list, and so the Home/Terminal chips added in a later phase read the same
 * thing.
 *
 * `loaded` is separate from `items` on purpose: an empty list means "no
 * accounts linked" only once the read has returned. Before that it is
 * unknown, and the first-time card must not flash up for someone who has five.
 */

import { reactive, computed } from 'vue'
import * as svc from '../services/accountsService.js'
import { secStorage } from '../utils/secStorage.js'

export const accounts = reactive({
  items: [],
  activeId: null,
  loaded: false,
  busy: false,
  /** Set when a read or write failed; shown, never thrown. */
  error: '',
})

/* ── Switcher layout ─────────────────────────────────────────────────
   Per-device display preference, persisted like the Home layout. Kept out of
   theme.js deliberately: that store is one shared file with its own seeding
   rules, and this is a three-value choice owned by one screen. The Settings
   row that points here lands with the wiring phase. */
export const ACCOUNT_LAYOUTS = [
  { id: 'stack',    name: 'Stack',    blurb: 'Cards overlap like a wallet' },
  { id: 'carousel', name: 'Carousel', blurb: 'Swipe through your accounts' },
  { id: 'list',     name: 'List',     blurb: 'Active account on top, the rest as rows' },
]
const LAYOUT_KEY = 'CC_ACCOUNTS_LAYOUT'
const savedLayout = secStorage.getItem(LAYOUT_KEY)

export const accountPrefs = reactive({
  layout: ACCOUNT_LAYOUTS.some((l) => l.id === savedLayout) ? savedLayout : 'stack',
})

export function setAccountLayout(id) {
  if (!ACCOUNT_LAYOUTS.some((l) => l.id === id)) return
  accountPrefs.layout = id
  secStorage.setItem(LAYOUT_KEY, id)
}

export const accountList = computed(() => accounts.items.filter((i) => i.kind === 'acc'))
export const activeAccount = computed(() => accountList.value.find((a) => a.id === accounts.activeId) || null)
export const includedAccountId = computed(() => svc.includedId(accounts.items))
/* Billed extras: every linked account after the included one, plus every slot
   that has been paid for or is awaiting payment confirmation. A FAILED slot
   was never charged, so it is not counted. */
export const extraCount = computed(() => {
  const billed = accounts.items.filter((i) => i.kind === 'acc' || i.state !== 'failed').length
  return Math.max(0, billed - 1)
})

function apply(res) {
  accounts.items = res.items
  accounts.activeId = res.activeId
  accounts.loaded = true
  accounts.error = ''
}

export async function loadAccounts(opts) {
  accounts.busy = true
  try {
    const res = await svc.listAccounts(opts)
    if (res.ok) apply(res)
    else accounts.error = res.message || 'Could not read your accounts.'
  } catch (e) {
    accounts.error = e?.message || 'Could not read your accounts.'
  } finally {
    accounts.busy = false
  }
}

async function act(fn, id) {
  accounts.busy = true
  try {
    const res = await fn(id)
    if (res.ok) apply(res)
    else accounts.error = res.message
    return res
  } catch (e) {
    const message = e?.message || 'Something went wrong. Try again.'
    accounts.error = message
    return { ok: false, message }
  } finally {
    accounts.busy = false
  }
}

export const buySlot = (slotId) => act((id) => svc.buySlot({ slotId: id }), slotId)
export const recheckSlot = (id) => act(svc.recheckSlot, id)
export const makeActive = (id) => act(svc.setActive, id)
export const delink = (id) => act(svc.delinkAccount, id)
