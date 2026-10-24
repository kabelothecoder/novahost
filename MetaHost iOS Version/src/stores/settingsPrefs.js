/**
 * Settings v3 preferences.
 *
 * Every switch and picker the Settings v3 design adds that has no home in
 * theme.js. They persist on this device only: protection (margin, daily
 * target lock, kill switch), prop-firm rules, news and briefing timing,
 * notification categories and quiet hours, Flex Cards, plan and tokens.
 * The notification keys (nOn, c_*, quiet hours) are the exception: they
 * travel with the push subscription and `send-notifications` honours them.
 * NOTHING else on the server reads these yet. A kill switch or a loss lock set
 * here does not stop a trade until the backend enforces it — wire each
 * key to its function before telling a user it protects them.
 *
 * Appearance that already exists (layout, Falling FX, haptics, motion)
 * stays in theme.js; the view reads and writes it there so there is still
 * one source of truth for the home screen.
 */
import { reactive, watch } from 'vue'
import { secStorage } from '../utils/secStorage.js'

const KEY = 'NH_SETTINGS_V3'

/* Alert categories: [key, label, example]. Order is the order drawn. */
export const NOTIF_CATS = [
  ['Trading', [
    ['c_wake',    'Robot wants to trade',                  '“Please turn me on as I want to trade”'],
    ['c_signal',  'New trade spotted',                     'Entry, TP and SL as soon as it’s seen'],
    ['c_placed',  'Trade placed / filled',                 'Gold BUY 0.20 filled at 2 384.10'],
    ['c_refused', 'Trade refused (with reason)',           'e.g. “Spread too wide on US30”'],
    ['c_closed',  'Trade closed (profit / loss)',          'e.g. “Nasdaq closed +R412.60”'],
    ['c_broker',  'Broker disconnected / wrong password',  ''],
    ['c_target',  'Daily target or loss limit hit',        ''],
    ['c_margin',  'Low margin',                            ''],
  ]],
  ['News & briefings', [
    ['c_prenews', 'Pre-news briefing',       ''],
    ['c_release', 'News release result',     '“CPI 3.3% — Gold SELL setup ready”'],
    ['c_morning', 'Morning briefing ready',  ''],
  ]],
  ['Account', [
    ['c_prop',    'Prop firm rule warning',                           ''],
    ['c_billing', 'Scan tokens low / plan renewing / payment failed', ''],
    ['c_updates', 'App updates & announcements',                      ''],
  ]],
]

const LOUD = ['c_wake', 'c_signal', 'c_placed', 'c_refused', 'c_closed', 'c_broker', 'c_target', 'c_margin', 'c_prop']

const DEFAULTS = {
  marginOn: true, marginMin: '50%',
  tgtOn: true, tgtProfit: 'R1 500', tgtLoss: 'R750', tgtReset: '00:00', tgtVoice: true,
  killLong: true, killVoice: true, killAction: 'Close all trades', review: '30 s',
  scanStyle: 'Ask every time', scanExplain: 'Normal', scanSource: 'Live chart', scanNews: true, scans: 38,
  newsBrief: true, newsEarly: '15 min',
  ev_nfp: true, ev_cpi: true, ev_fomc: true, ev_gdp: false, ev_pmi: false, ev_retail: true,
  newsMine: true, newsAlert: true, newsVoice: false,
  propPause: true, propWarn: '70%', propManual: true,
  voiceOn: true, voiceSpeed: '1.0×', captions: true, ptt: true, help: true,
  mbOn: true, mbTime: 'On first open', mbLen: 'Short',
  nOn: true, quietOn: true, qFrom: '22:00', qTo: '06:30', quietAllow: true,
  reduceMode: 'Auto', loop: 'Wi-Fi only', sounds: true, hideBal: false,
  flexTpl: 'Reactor', flexShow: 'Amounts',
  plan: 'Monthly', tokens: 42,
  /* Prop accounts the user has added. Seeded with the design's two so the
     page is not empty before the backend supplies real ones. */
  firms: [
    { id: 'f1', name: 'FTMO · 100k Challenge', phase: 'Phase 1', daily: '5%', dd: '10%', tgt: '10%', news: '2 min either side' },
    { id: 'f2', name: 'FundedNext · 50k',      phase: 'Funded',  daily: '4%', dd: '8%',  tgt: 'None (funded)', news: 'Off' },
  ],
  draft: { firm: 'FTMO', size: '$100k', daily: '5%', dd: '10%', tgt: '10%', news: 'Off' },
}
for (const [, list] of NOTIF_CATS) {
  for (const [k] of list) {
    DEFAULTS[k] = k !== 'c_updates'
    DEFAULTS['s_' + k] = LOUD.includes(k)
  }
}

/* Placeholder records until billing and history come from the server. */
export const PAYMENTS = [
  ['Monthly plan',    'R499', '22 Oct 2026 · Visa •••• 4417'],
  ['Extra account',   'R149', '22 Oct 2026'],
  ['120 scan tokens', 'R199', '9 Oct 2026'],
  ['Monthly plan',    'R499', '22 Sep 2026'],
  ['Extra account',   'R149', '22 Sep 2026'],
  ['Monthly plan',    'R499', '22 Aug 2026'],
]
export const PLAN_PRICE = { Monthly: 'R499/mo', Annual: 'R4 990/yr' }
export const RENEWS = 'Monthly · renews 22 Nov'

function load() {
  const saved = secStorage.getItem(KEY, true)
  const out = structuredClone(DEFAULTS)
  if (saved && typeof saved === 'object') {
    /* Only keys this build knows: a stale key from an older build is dropped,
       and one added since falls back to its default. */
    for (const k of Object.keys(out)) if (k in saved) out[k] = saved[k]
  }
  return out
}

export const prefs = reactive(load())

watch(prefs, v => secStorage.setItem(KEY, v), { deep: true })

export function resetPrefs() {
  Object.assign(prefs, structuredClone(DEFAULTS))
}
