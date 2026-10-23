# NovaHost overhaul: roadmap (planning, 2026-10-21)

One page tying the briefs together. Nothing here is built yet.
Briefs: `2026-10-claude-design-prompts.md` (UI), `2026-10-trade-rejection-report.md` (fill rate),
`2026-10-broker-bridge-alternatives.md` (MetaRPC), `2026-10-pricing-plan.md`, `2026-10-budget.md`,
`2026-10-wow-features.md`.

## Phase 0: Decide and design (now, no code)
- [ ] Prices with co-founder: monthly, annual, extra account, token packs, free months for R599 buyers
- [ ] Claude Design sessions in the order listed in the prompts file (Command Core first)
- [ ] Higgsfield Plus (1 month) → robot loop videos, Flex Card backgrounds, HUD backdrop
- [ ] MetaRPC: written resale consent + confirm broker coverage (email when ready)

## Phase 1: Make trades go in (target ≥95% fill on supported brokers)
Depends on: MetaRPC consent + 1-week bake-off.
- MetaRPC bake-off on real Exness / XM / IFX / Deriv accounts → go / no-go
- Provider-neutral bridge layer (`metacopier-*` → bridge adapter), MetaRPC adapter
- Margin check + shrink-lot replaces POSITION_CAP; re-quote on "Invalid price"
- Auto-reconnect + "your broker disconnected" prompt
- Delink account button (backend delete already exists for MetaCopier)
- Broker server search: type "XM" → pick from all XM servers. Serve it from our own cached
  `broker_servers` table (fast, typo-tolerant, works offline from the provider), refreshed
  nightly from the bridge. MetaRPC's server-list calls need one always-on helper terminal
  (~$3–4/mo); MetaCopier already has a server-list endpoint, so this can ship before the switch.
- Scanner off Gemini free tier
Why first: every wow feature is worthless if the trade doesn't land (35% / 16% today).

## Phase 2: Money (pay first, monthly)
Depends on: prices decided, Prompt 13/14 designs.
- PayFast recurring billing + renewals / failures / cancellations in the webhook
- Keyless checkout → pay first, then licence key; new "paid, no key" route state
- Affiliate attribution without a key at checkout
- Extra-account slots, token ledger (refund on failed scan)
- Migrate R599 buyers to free months

## Phase 3: New look + easier setup
Depends on: Claude Design output.
- Remove Glass Stack, Signal Feed, Command Deck; add Command Core (lite mode on weak Android)
- Refresh Classic Core / Focus Engine / Full-Bleed with loop video
- Portal: loop-video upload
- Symbol auto-match wizard (sets default size → kills NO_SIZE_CONFIGURED)
- Chart Scan step flow with NO TRADE / Scenario A/B / confidence

## Phase 4: The robot talks
Depends on: Fish Audio account, LLM budget caps.
- Push-to-talk voice + help mode (deep links, coach marks)
- Voice trade idea (styles, robot-chosen order type, 30 s review, tap to confirm)
- Voice kill switch, morning briefing, daily target lock

## Phase 4b: Notifications (Android + iOS)
- Android: FCM push from Supabase (trade placed/refused, broker disconnected, pre-news, etc.).
- iOS PWA: Web Push via service worker + VAPID. Works ONLY when installed to the Home Screen
  (iOS 16.4+). Memory note "the app CANNOT notify" means nothing is built yet, not that it's
  impossible; `new Notification()` is still off-limits, so push goes through the service worker.
- One `notification_prefs` row per user drives both (categories, quiet hours).

## Phase 5: Wow features
- Flex Cards (no link / QR)
- Prop Firm section (EA check → auto or manual cards, challenge health)
- News Predictions (Event Playbook)

## Open questions parked
- Exact prices (co-founder)
- Who maintains the prop-firm EA-rules database (us or admin portal)
- Recurring vs first-month affiliate commission
- Fish Audio: one NovaHost voice, or a voice per robot provider
