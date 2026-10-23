/**
 * Settings v3 — the page tree.
 *
 * Each page is { title, parent, sub?, sections: [{ h?, foot?, rows }] } and
 * each row is a plain descriptor the view draws by type:
 *
 *   line   label · value · chevron, tappable when it has `tap`
 *   tog    a switch bound to `get`/`set` (or to prefs[key])
 *   cat    a notification category: switch plus a per-alert sound toggle
 *   act    a coloured action row (tone: accent | danger | muted)
 *   warn   amber "something needs you" row
 *   note   icon + paragraph, not tappable
 *   layouts / style   the Look & feel tile grid and appearance block
 *
 * Kept as data so search can walk every label without rendering anything.
 */
import { NOTIF_CATS, PAYMENTS, PLAN_PRICE } from '../../../stores/settingsPrefs.js'
import { ACCOUNT_LAYOUTS, accountPrefs, setAccountLayout } from '../../../stores/accounts.js'

export const OPT = {
  daily: ['3%', '4%', '5%'],
  dd:    ['6%', '8%', '10%', '12%'],
  tgt:   ['8%', '10%', 'None (funded)'],
  news:  ['Off', '2 min either side', '5 min either side', '15 min either side'],
}

export function buildPages(c) {
  const { p, R, f } = c
  const onOff = b => (b ? 'On' : 'Off')
  const nav  = (label, to, value, sub) => ({ t: 'line', label, sub, value, tap: () => c.go(to), chev: true })
  const pk   = (label, key, opts, sub, note) => ({ t: 'line', label, sub, value: p[key], chev: true, tap: () => c.pick(label, opts, p[key], v => { p[key] = v }, note) })
  const info = (label, value, sub, vc) => ({ t: 'line', label, value, sub, vc })
  const tg   = (label, key, sub, dis) => ({ t: 'tog', label, key, sub, dis })
  const act  = (label, icon, tap, tone) => ({ t: 'act', label, icon, tap, tone })
  const warn = (label, icon, tap) => ({ t: 'warn', label, icon, tap })
  const note = (label, icon) => ({ t: 'note', label, icon })
  const nDis = !c.pushOn || f.blocked || f.ios

  const firmPage = firm => ({
    title: firm.name, parent: 'prop',
    sub: 'NovaHost checks every trade against these rules before it’s sent.',
    sections: [
      { h: 'FIRM RULES', rows: [
        { t: 'line', label: 'Daily loss limit', value: firm.daily, chev: true, tap: () => c.pick('Daily loss limit', OPT.daily, firm.daily, v => { firm.daily = v }) },
        { t: 'line', label: 'Max drawdown',     value: firm.dd,    chev: true, tap: () => c.pick('Max drawdown', OPT.dd, firm.dd, v => { firm.dd = v }) },
        { t: 'line', label: 'Profit target',    value: firm.tgt,   chev: true, tap: () => c.pick('Profit target', OPT.tgt, firm.tgt, v => { firm.tgt = v }) },
        { t: 'line', label: 'News ban',         value: firm.news,  chev: true, tap: () => c.pick('News ban', OPT.news, firm.news, v => { firm.news = v }) },
      ] },
      { rows: [act('Remove this prop account', 'delete', () => c.ask(
        'Remove ' + firm.name + '?', R + ' stops checking trades against these rules.', 'Remove', true,
        () => { c.pop(); p.firms = p.firms.filter(x => x.id !== firm.id); c.toast('Prop account removed', firm.name + ' is no longer tracked.') },
      ), 'danger')] },
    ],
  })

  const d = p.draft
  const P = {
    trading: {
      title: 'Trading', parent: 'hub', sections: [
        { h: 'ACCOUNTS', rows: [
          f.broker ? warn(f.brokerName + ' · wrong password — tap to fix', 'link_off', c.openReconnect) : null,
          nav('Broker accounts', 'accounts', c.accountsLine),
          nav('Trading symbols & risk', 'symbols', c.symbolsLine),
        ].filter(Boolean) },
        { h: 'PROTECTION', rows: [
          nav('Margin protection', 'margin', onOff(p.marginOn)),
          nav('Daily target lock', 'target', onOff(p.tgtOn)),
          nav('Kill switch', 'kill', onOff(p.killLong || p.killVoice)),
        ] },
        /* The one switch on this page the server already honours. */
        { h: 'EXECUTION', rows: [
          { t: 'tog', label: 'Auto-execute signals', sub: c.autoExecCaption, get: () => c.autoExec === true, set: c.toggleAutoExec, dis: c.autoExec === null },
          pk('Trade review window', 'review', ['15 s', '30 s', '60 s']),
        ], foot: c.autoExec ? 'The server places trades even when this app is closed.' : 'Trades never place without your tap.' },
      ],
    },
    margin: {
      title: 'Margin protection', parent: 'trading', sections: [
        { rows: [tg('Shrink lot when margin is low', 'marginOn'), pk('Minimum free margin', 'marginMin', ['30%', '40%', '50%', '60%', '70%'])],
          foot: 'Below ' + p.marginMin + ' free margin, ' + R + ' halves the lot size of the next trade. Default 50%.' },
      ],
    },
    target: {
      title: 'Daily target lock', parent: 'trading',
      sub: 'When either limit is hit, ' + R + ' stops opening trades until the reset time.',
      sections: [
        { rows: [tg('Daily target lock', 'tgtOn')] },
        { h: 'LIMITS', rows: [
          pk('Profit target', 'tgtProfit', ['R500', 'R1 000', 'R1 500', 'R3 000', '2%', '3%', '5%']),
          pk('Loss limit', 'tgtLoss', ['R250', 'R500', 'R750', 'R1 000', '1%', '2%', '3%']),
          pk('Reset time', 'tgtReset', ['00:00', '06:00', '08:00', 'Broker server time']),
        ] },
        { rows: [tg('Voice announcement', 'tgtVoice', R + ' says when the day is locked')] },
      ],
    },
    kill: {
      title: 'Kill switch', parent: 'trading', sections: [
        { rows: [tg('Long-press robot to close all', 'killLong'), tg('Voice “close everything”', 'killVoice')] },
        { rows: [pk('Default action', 'killAction', ['Close all trades', 'Stop robot, keep trades'])], foot: 'You always confirm once before anything closes.' },
      ],
    },
    scanner: {
      title: 'Chart scanner', parent: 'hub', sections: [
        { h: 'DEFAULTS', rows: [
          pk('Default trading style', 'scanStyle', ['Scalp', 'Day trade', 'Swing', 'Breakout', 'Ask every time']),
          pk('Explanations', 'scanExplain', ['Normal', 'Pro']),
          pk('Default chart source', 'scanSource', ['Live chart', 'Upload screenshot']),
          tg('Show news card in results', 'scanNews'),
        ] },
        { h: 'SCAN HISTORY', rows: [
          info('Saved on this phone', p.scans + ' scans'),
          act('Clear scan history', 'delete_sweep', () => p.scans && c.ask('Clear scan history?', 'All ' + p.scans + ' saved scans are removed from this phone.', 'Clear', true,
            () => { p.scans = 0; c.toast('Scan history cleared', 'New scans will be kept.') }), p.scans ? 'danger' : 'muted'),
        ] },
        { rows: [nav('Open the scanner', 'route:/scanner', '')] },
      ],
    },
    news: {
      title: 'News & events', parent: 'hub', sections: [
        { h: 'PRE-NEWS BRIEFING', rows: [tg('Pre-news briefing', 'newsBrief'), pk('How early', 'newsEarly', ['5 min', '15 min', '30 min', '60 min'], null, 'Default 15 min.')] },
        { h: 'WHICH EVENTS', rows: [tg('NFP', 'ev_nfp', 'US jobs report'), tg('CPI', 'ev_cpi', 'Inflation'), tg('FOMC / rates', 'ev_fomc'), tg('GDP', 'ev_gdp'), tg('PMI', 'ev_pmi'), tg('Retail sales', 'ev_retail')] },
        { rows: [tg('Only for my symbols', 'newsMine', c.symbolNames), tg('“Alert me at release” default', 'newsAlert'), tg('Announce by voice', 'newsVoice')] },
      ],
    },
    prop: {
      title: 'Prop firms', parent: 'hub', sections: [
        { h: 'PROP ACCOUNTS', rows: [
          ...p.firms.map(x => nav(x.name, 'firm:' + x.id, x.phase)),
          act('Add prop account', 'add', () => c.go('firmNew'), 'accent'),
        ] },
        { h: 'PROTECTION', rows: [tg('Auto-pause before breaking a rule', 'propPause'), pk('Warn at', 'propWarn', ['70%', '90%'], null, 'Share of the rule used up before you get a warning.')] },
        { rows: [tg('Manual-mode trade cards', 'propManual', 'For firms that ban EAs. ' + R + ' sends a card, you place the trade.')] },
      ],
    },
    firmNew: {
      title: 'Add prop account', parent: 'prop', sections: [
        { h: 'ACCOUNT', rows: [
          { t: 'line', label: 'Firm', value: d.firm, chev: true, tap: () => c.pick('Firm', ['FTMO', 'FundedNext', 'The5ers', 'Other'], d.firm, v => { d.firm = v }) },
          { t: 'line', label: 'Account size', value: d.size, chev: true, tap: () => c.pick('Account size', ['$10k', '$25k', '$50k', '$100k', '$200k'], d.size, v => { d.size = v }) },
        ] },
        { h: 'FIRM RULES', rows: [
          { t: 'line', label: 'Daily loss limit', value: d.daily, chev: true, tap: () => c.pick('Daily loss limit', OPT.daily, d.daily, v => { d.daily = v }) },
          { t: 'line', label: 'Max drawdown', value: d.dd, chev: true, tap: () => c.pick('Max drawdown', OPT.dd, d.dd, v => { d.dd = v }) },
          { t: 'line', label: 'Profit target', value: d.tgt, chev: true, tap: () => c.pick('Profit target', OPT.tgt, d.tgt, v => { d.tgt = v }) },
          { t: 'line', label: 'News ban', value: d.news, chev: true, tap: () => c.pick('News ban', OPT.news, d.news, v => { d.news = v }) },
        ] },
        { rows: [act('Save prop account', 'check', () => {
          const name = d.firm + ' · ' + d.size.replace('$', '')
          p.firms = p.firms.concat({ id: 'f' + Date.now(), name, phase: d.tgt === 'None (funded)' ? 'Funded' : 'Phase 1', daily: d.daily, dd: d.dd, tgt: d.tgt, news: d.news })
          c.pop()
          c.toast('Prop account saved', d.firm + ' · ' + d.size + ' rules are now checked on every trade.')
        }, 'accent')] },
      ],
    },
    robot: {
      title: 'Robot & voice', parent: 'hub', sections: [
        { h: 'ROBOT', rows: [{ t: 'line', label: 'Switch robot', sub: c.licencesLine, value: R, chev: true, tap: c.switchRobot }] },
        { h: 'VOICE', rows: [tg('Voice', 'voiceOn'), pk('Voice speed', 'voiceSpeed', ['0.8×', '1.0×', '1.2×', '1.5×']), tg('Captions', 'captions'), tg('Push-to-talk button on home', 'ptt')] },
        { rows: [tg('Help mode', 'help', 'Guided walkthroughs on each screen')] },
        { h: 'MORNING BRIEFING', rows: [tg('Morning briefing', 'mbOn'), pk('Time', 'mbTime', ['On first open', '06:30', '07:00', '08:00']), pk('Length', 'mbLen', ['Short', 'Full'])] },
        { h: 'MICROPHONE', rows: [
          info('Microphone permission', c.micLabel, null, c.micLabel === 'Allowed' ? '#30D158' : null),
          act('Open phone settings', 'open_in_new', () => c.toast('iPhone Settings', 'Settings › Safari › Microphone'), 'accent'),
        ] },
      ],
    },
    notif: {
      title: 'Notifications', parent: 'hub', sections: [
        { rows: [
          f.ios ? note('Add NovaHost to your Home Screen to get notifications.', 'add_to_home_screen') : null,
          f.ios ? act('Show me how', 'ios_share', () => c.sheet({ type: 'how', title: 'Add to Home Screen' }), 'accent') : null,
          f.blocked ? warn('Blocked — open phone settings', 'notifications_off', () => c.toast('Notifications are blocked', 'Settings › NovaHost › Notifications › Allow.', true)) : null,
          (!f.blocked && !f.ios) ? info('iPhone permission', c.permLabel, null, c.permLabel === 'Allowed' ? '#30D158' : null) : null,
          { t: 'tog', label: 'Allow notifications', sub: c.pushSub, get: () => c.pushOn, set: c.togglePush, dis: f.blocked || f.ios || c.pushBusy },
        ].filter(Boolean) },
        ...NOTIF_CATS.map(([h, list]) => ({
          h: h.toUpperCase(),
          rows: list.map(([k, label, sub]) => ({ t: 'cat', label, key: k, dis: nDis, sub: k === 'c_prenews' ? p.newsEarly + ' before each event' : sub })),
        })),
        { h: 'QUIET HOURS', rows: [
          tg('Quiet hours', 'quietOn'),
          { t: 'line', label: 'From – to', value: p.qFrom + ' – ' + p.qTo, chev: true, dis: !p.quietOn, tap: () => c.sheet({ type: 'quiet', title: 'Quiet hours' }) },
          tg('Still allow urgent alerts', 'quietAllow', 'Trade refused, broker disconnected', !p.quietOn),
        ], foot: 'The speaker icon turns sound on or off for each alert.' },
        { rows: [
          act('Send a test notification', 'send', c.testNotif, 'accent'),
          act('Preview the island', 'smart_toy', c.previewIsland, 'accent'),
        ], foot: 'The island shows alerts at the top of the screen while NovaHost is open, and when you open it from a notification.' },
      ],
    },
    look: {
      title: 'Look & feel', parent: 'hub', sections: [
        { h: 'HOME LAYOUT', rows: [{ t: 'layouts' }] },
        { h: 'STYLE', rows: c.styleRows },
        // How My accounts lays out the linked accounts. Chosen here rather
        // than on that screen, which keeps it to the accounts themselves.
        { h: 'MY ACCOUNTS', rows: [{
          t: 'line', label: 'Accounts layout',
          sub: ACCOUNT_LAYOUTS.find(l => l.id === accountPrefs.layout)?.blurb,
          value: ACCOUNT_LAYOUTS.find(l => l.id === accountPrefs.layout)?.name, chev: true,
          tap: () => c.pick('Accounts layout', ACCOUNT_LAYOUTS.map(l => l.name),
            ACCOUNT_LAYOUTS.find(l => l.id === accountPrefs.layout)?.name,
            v => setAccountLayout(ACCOUNT_LAYOUTS.find(l => l.name === v)?.id),
            'Stack: cards overlap like a wallet. Carousel: swipe through them. List: the active account on top, the rest as rows.'),
        }] },
        { h: 'EFFECTS', rows: [
          { t: 'line', label: 'Falling FX', value: c.fxName, chev: true, tap: c.pickFx },
          { t: 'line', label: 'Accent', value: c.accentModeLabel, chev: true, tap: c.pickAccentMode },
          { t: 'line', label: 'Reduce effects', value: p.reduceMode, chev: true, tap: c.pickReduce },
          pk('Loop video', 'loop', ['Always', 'Wi-Fi only', 'Off (data saver)']),
        ] },
        { rows: [{ t: 'tog', label: 'Haptics', get: () => c.theme.haptics, set: () => { c.theme.haptics = !c.theme.haptics } }, tg('Sounds', 'sounds')] },
        { h: 'PRIVACY', rows: [tg('Hide balances by default', 'hideBal', 'On home and Flex Cards. Tap a balance to show it.')] },
      ],
    },
    flex: {
      title: 'Flex Cards', parent: 'hub', sections: [
        { rows: [pk('Default template', 'flexTpl', ['Reactor', 'Gold Rush', 'Neon', 'Clean']), pk('Show', 'flexShow', ['Amounts', '% only'])],
          foot: p.hideBal ? 'Balances are hidden by default (Look & feel › Privacy).' : '' },
      ],
    },
    plan: {
      title: 'Plan & billing', parent: 'hub', sections: [
        f.expired ? { rows: [
          warn('Plan ended · ' + R + ' stopped trading', 'event_busy', () => {}),
          act('Renew ' + p.plan + ' · ' + PLAN_PRICE[p.plan], 'autorenew', () => c.toast('Renewal', 'PayFast checkout opens here once billing is connected.'), 'accent'),
        ] } : null,
        { h: 'PLAN', rows: [
          pk('Plan', 'plan', ['Monthly', 'Annual'], null, 'Monthly R499/mo · Annual R4 990/yr (2 months free).'),
          info(f.expired ? 'Ended' : 'Renews', f.expired ? '22 Oct 2026' : (p.plan === 'Annual' ? '22 Oct 2027' : '22 Nov 2026')),
          act('Manage payment method', 'credit_card', () => c.toast('PayFast', 'Visa •••• 4417 · expires 08/28')),
          f.expired ? null : act('Cancel plan', 'cancel', () => c.ask('Cancel your plan?', R + ' keeps trading until 22 Nov, then stops.', 'Cancel plan', true, () => c.toast('Plan cancelled', R + ' trades until 22 Nov.')), 'danger'),
        ].filter(Boolean) },
        { h: 'EXTRA ACCOUNTS', rows: [info('Extra accounts', c.extrasLine), nav('Add an account slot', 'accounts', 'R149/mo')] },
        { h: 'SCAN TOKENS', rows: [
          info('Balance', p.tokens + ' tokens', null, p.tokens < 10 ? '#FFC24D' : null),
          { t: 'line', label: 'Buy tokens', chev: true, value: '', tap: () => c.pick('Buy scan tokens', ['50 tokens · R99', '120 tokens · R199', '300 tokens · R449'], null, x => {
            const n = parseInt(x, 10); const was = p.tokens; p.tokens = was + n
            c.toast('Tokens added', was + ' → ' + p.tokens + ' scan tokens')
          }, 'Charged once via PayFast. Tokens never expire.') },
        ] },
        { rows: [nav('Payment history', 'payments', '')] },
      ].filter(Boolean),
    },
    payments: {
      title: 'Payment history', parent: 'plan', sections: [
        { rows: PAYMENTS.map(([label, value, sub]) => info(label, value, sub)) },
      ],
    },
    account: {
      title: 'Account & device', parent: 'hub', sections: [
        { rows: [
          info('Email', c.email || 'Not set'),
          { t: 'line', label: 'Licence key', value: c.maskedKey, chev: !!c.licenceKey, chevIcon: 'content_copy', tap: c.licenceKey ? c.copyKey : null },
          info('This device', c.deviceLine),
        ] },
        { rows: [nav('Move to a new phone', 'move', '')] },
        { rows: [
          act('Sign out', 'logout', () => c.go('route:/sign-out')),
          act('Delete my data', 'delete_forever', () => c.ask('Delete all your data?', 'Your accounts, scans and settings are erased within 24 h. Your plan is cancelled. This can’t be undone.', 'Delete', true,
            () => c.toast('Deletion requested', 'We’ll confirm by email' + (c.email ? ' to ' + c.email : '') + '.')), 'danger'),
        ] },
      ],
    },
    move: {
      title: 'Move to a new phone', parent: 'account',
      sub: 'Your licence works on one phone at a time. Moving it signs this phone out.',
      sections: [
        { rows: [note('1. Install NovaHost on the new phone.', 'counter_1'), note('2. Choose “I have a licence” and enter the code below.', 'counter_2'), note('3. This phone signs out once the new one is active.', 'counter_3')] },
        { rows: [act('Create transfer code', 'qr_code_2', () => c.toast('Transfer code', 'Codes are issued here once device moves are connected.'), 'accent')] },
      ],
    },
    help: {
      title: 'Help & support', parent: 'hub', sections: [
        { h: 'HELP CENTRE', rows: ['Getting started', 'Linking a broker', 'Symbols, lots & risk', 'Prop firm rules', 'Notifications on iPhone']
          .map(x => ({ t: 'line', label: x, chev: true, value: '', tap: () => c.go('route:/support') })) },
        { rows: [{ t: 'line', label: 'Contact support', sub: 'Usually replies within 2 hours', chev: true, value: '', tap: () => c.go('route:/support') }] },
      ],
    },
    about: {
      title: 'About', parent: 'hub', sections: [
        { rows: [
          info('Version', c.version),
          { t: 'line', label: 'Terms of use', chev: true, value: '', tap: () => c.open('https://novahost-ea.app/terms') },
          { t: 'line', label: 'Privacy policy', chev: true, value: '', tap: () => c.open('https://novahost-ea.app/privacy') },
          nav('Risk warning', 'risk', ''),
        ] },
      ],
    },
    risk: {
      title: 'Risk warning', parent: 'about', sections: [
        { rows: [note('Trading forex, gold and indices on margin carries a high level of risk and may not suit every investor. You can lose more than your deposit. Past results of any robot do not guarantee future results. Only trade money you can afford to lose.', 'warning')] },
      ],
    },
  }
  for (const x of p.firms) P['firm:' + x.id] = firmPage(x)
  return P
}

export function hubGroups(c) {
  const { p, R, f } = c
  const on = NOTIF_CATS.reduce((a, [, l]) => a + l.filter(([k]) => p[k]).length, 0)
  const total = NOTIF_CATS.reduce((a, [, l]) => a + l.length, 0)
  return [
    ['TRADING', [
      ['show_chart', 'Trading', 'trading', f.broker ? 'Fix broker' : c.accountsLine, f.broker],
      ['document_scanner', 'Chart scanner', 'scanner', p.scanStyle],
      ['event', 'News & events', 'news', p.newsBrief ? p.newsEarly + ' before' : 'Off'],
      ['shield', 'Prop firms', 'prop', p.firms.length + (p.firms.length === 1 ? ' account' : ' accounts')],
    ]],
    ['APP', [
      ['graphic_eq', 'Robot & voice', 'robot', R + (p.voiceOn ? ' · Voice on' : '')],
      ['notifications', 'Notifications', 'notif', f.blocked ? 'Blocked' : f.ios ? 'Not set up' : c.pushOn ? 'On · ' + on + ' of ' + total : 'Off', f.blocked || f.ios],
      ['palette', 'Look & feel', 'look', c.layoutName],
      ['style', 'Flex Cards', 'flex', p.flexTpl],
    ]],
    ['ACCOUNT', [
      ['credit_card', 'Plan & billing', 'plan', f.expired ? 'Expired' : p.plan, f.expired],
      ['smartphone', 'Account & device', 'account', c.deviceShort],
      ['help', 'Help & support', 'help', ''],
      ['info', 'About', 'about', c.version],
    ]],
  ].map(([h, rows]) => ({ h, rows: rows.map(([icon, label, to, value, w]) => ({ icon, label, to, value, warn: !!w })) }))
}
