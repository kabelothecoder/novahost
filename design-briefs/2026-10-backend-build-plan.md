# Backend build plan — Oct 2026

Written 2026-10-22. Covers everything the backend needs for the Oct 22 scope: push and email,
targets and funded rules, the flex card, the terminal, **Fish Audio voice**, and the **MetaRPC
bridge move**. Live state was checked against Supabase project `epulmnfbxjmaimefhofp` the same day.

## Where things stand

| Area | State |
|---|---|
| Push + email + wake button | Code written on `feat/connection-area-redesign`, **uncommitted and not live**: no tables, no cron job, no functions deployed |
| P/L data | **None in the DB.** `trade_logs` has 0 rows in 30 days; `signal_executions` only records placed (16) and refused (80), never closes or profit |
| Bridge | MetaCopier, 9 functions call `api.metacopier.io` directly. The connect wall has refused every new link since 2026-10-04 |
| MetaRPC | Researched in depth (`2026-10-broker-bridge-alternatives.md`, section "MetaRPC deep dive"). No account yet, no bake-off yet |
| Voice | Client UI exists (push-to-talk ring and waveform) with a placeholder reply. No backend at all |
| Gemini | Key was **free tier (20 calls/day for the whole product)** as of 2026-09-16. Voice can't share that |

## Do these first (owner actions, outside the code)

1. **MetaRPC:** sign up, top up about $25, and email `admin@mrpc.pro` for **written resale
   consent**, the name of the contracting entity, and whether `Reconnect` survives a host
   restart. Their Terms forbid resale without consent.
2. **Fish Audio:** create an API key. Prepay $100 when voice launches: under $100 paid the account
   is limited to **5 concurrent requests**, which a handful of users talking at once would hit.
3. **Gemini:** move to a paid key before voice. The free tier can't carry the scanner and voice.
4. **Resend:** confirm `RESEND_API_KEY` and `RESEND_FROM` (sending from `novahost-ea.app`).
5. **Decide:** one NovaHost voice, or a voice per robot provider (open question in the roadmap).

## Build order

The bridge layer moves up front. Daily targets, funded rules, the flex card, trade-closed alerts
and the voice kill switch all read from the broker, so they should be written **once against a
bridge interface**, not against MetaCopier and then again against MetaRPC.

| # | Phase | Needs | Size |
|---|---|---|---|
| 1 | Push + email + wake button (deploy what's written) | VAPID keys, Resend | 1–2 days |
| 2 | Bridge layer + MetaRPC adapter, bake-off running alongside | MetaRPC account | 2–3 weeks incl. pilot |
| 3 | Trade closes + risk guard (daily target, funded rules) | Phase 2 interface | 4–6 days |
| 4 | Voice backend (Fish Audio + Gemini) + kill switch + trade-idea confirm | Fish key, paid Gemini, `closeAll` from phase 2 | 1–1.5 weeks |
| 5 | Flex card stats + terminal log feed | Phase 3 data | 2–3 days |

Phases 2 and 4 are independent except for the kill switch, so they can run in parallel.

---

## Phase 1 — Push, email, wake button

Already written: migration `20261022100000_push_notifications.sql`, plus `push-subscribe`,
`send-notifications`, `wake-robots` and `_shared/webpush.ts`.

To finish:
- Generate a VAPID key pair and set it as function secrets; set the Resend secrets.
- Apply the migration, deploy the 3 functions, verify the hashes (git ≠ live has bitten us before).
- Test end to end: subscribe from an installed iPhone, fire a wake-up, insert a test signal.
- Wire the `closed` and `target` alerts once phase 3 writes them (the queue already has the `target` kind).
- Client side (not backend): a custom service worker (`injectManifest`) with `push` and
  `notificationclick` handlers, plus the portal wake button.

## Phase 2 — Bridge layer and MetaRPC

Full mapping, error-code table and bake-off plan are in the MetaRPC deep dive. Summary:

**2a. Provider-neutral layer (`_shared/bridge/`)**
- One interface: `connect`, `disconnect`, `health`, `reconnect`, `accountSummary` (balance, equity,
  free margin, currency together), `symbols`, `quote`, `positions`, `history(since)`,
  `placeOrder`, `closeAll`.
- One error vocabulary: our existing codes (`INSUFFICIENT_MARGIN`, `INVALID_STOPS`,
  `MARKET_CLOSED`, `SERVER_NOT_SUPPORTED`, `ACCOUNT_READ_ONLY`, …). Adapters translate into it.
- Adapters: `metacopier.ts` (today's code, moved) and `metarpc.ts`. Each licence chooses via
  `metadata.bridge_provider` and `metadata.bridge_account_id`.
- Move the 9 functions onto it: `metacopier-connect`, `metacopier-execute`, `broker-account`,
  `broker-symbols`, `broker-quote`, `broker-positions`, `broker-history`, `broker-performance`,
  `broker-diagnostics`. **Names and response shapes stay the same, so no app release is needed.**

**2b. MetaRPC-specific work**
- **Connect:** `ConnectEx` by server name, falling back to `Connect` by host and port (the fix
  for `XMGlobal-MT5 20`). At link time, check `ACCOUNT_TRADE_ALLOWED` / `ACCOUNT_TRADE_EXPERT` so
  investor passwords and server-disabled autotrading are refused up front.
- **Server picker:** a `broker_servers` cache table, filled by one always-on helper terminal
  (about $3–4 a month).
- **Orders:** on `COMMAND_TIMEOUT_EXCEEDED`, reconcile against `OpenedOrders` and
  `PositionsHistory` (magic + symbol + side + volume + time) **before** any retry, because a
  timed-out order can still execute. Keep one order in flight per account.
- **Kill switch:** there's no single endpoint, so `closeAll` loops: `OpenedOrders`, then
  `OrderClose` in batches of 5, until empty.
- **Upkeep crons:** `bridge-health` (`CheckConnect`, `Reconnect` without a password) and
  `bridge-billing` (alert at under 10 days of prepaid runway; **at zero, every user stops
  trading**). Disconnect expired or unlinked licences so they stop costing money.
- **Password exposure:** REST `ConnectEx` carries the broker password in the URL query. Check
  whether a POST or body form exists, or whether gRPC works from Deno. If not, accept it and
  make sure we never log the URL.

**2c. Rollout**
- Bake-off week on about 10 accounts (Exness, XM incl. server 20, IFX, plus the secondary brokers).
- Then new links go to MetaRPC. Existing users **re-link once**, because we never stored their
  passwords: an in-app prompt plus a push or email. Delete each MetaCopier account as it moves,
  since MetaCopier bills accounts until they're deleted.

## Phase 3 — Trade closes, daily target, funded rules

- **`trade_closes`:** closed trades pulled through `bridge.history()` (MetaRPC `PositionsHistory`
  includes magic, so ours and manual trades can be told apart). Feeds the trade-closed alert, daily
  P/L and the flex card.
- **`risk_rules` per licence:**
  - daily target and stop-or-keep-going
  - daily loss limit and max drawdown
  - account type (Personal, Funded or Challenge), firm, phase and target %
  - starting balance, and lot size as % of the challenge balance
- **`risk_state` per licence per day:** opening equity, realised P/L, equity, peak equity,
  `halted_at`, `halt_reason`.
- **`risk-guard` cron** (1–2 minutes): reads the account, updates state, halts the licence and
  queues a `target` alert when a rule is crossed, and resets at the broker's day rollover.
- **Enforcement:** `auto-execute-signals` and `claim-signals` refuse new orders while halted
  (`DAILY_TARGET_HIT`, `DAILY_LOSS_LIMIT`, `FUNDED_DD_LIMIT`). Voice trade ideas go through the
  same check.
- **Settings:** `set-risk-rules`, authenticated by licence key the same way as `set-auto-execute`.
- **To decide:** is the daily loss limit measured from opening balance or equity? Does a halt cover
  one licence, or every licence on that broker account?

## Phase 4 — Voice (Fish Audio)

### What Fish Audio offers (checked 2026-10-22)

| | Detail |
|---|---|
| Speech out (TTS) | `POST https://api.fish.audio/v1/tts`, `Authorization: Bearer`, model in a `model` header. Body: `text`, `reference_id` (the voice), `format` (mp3, opus, wav, pcm), `latency` (`low`/`normal`/`balanced`), `prosody.speed`, `normalize`. Response is chunked. A WebSocket live-TTS endpoint also exists |
| TTS models | `s2.1-pro` (default, $15 per million UTF-8 bytes), **`s2.1-pro-free` ($0)**, `s2-pro`, `drama-3`. `s1` retires 2026-12-31 |
| Speech in (ASR) | `POST /v1/asr`, multipart upload, $0.36 per audio hour. `transcribe-1` for short clips (WAV, MP3, AAC, FLAC, Ogg; **browser WebM may be refused**). `transcribe-1-pro` also takes WebM and MP4. Realtime streaming ASR is in preview |
| Voices | Clone a voice from 10–30 s of clean audio (create-model endpoint), or design one (`voice-design-1`, $0.01 a request) |
| Limits | 5 / 15 / 50 concurrent requests at under $100 / $100+ / $1,000+ prepaid; a 429 comes back with no `Retry-After` |

"Free until November" in the budget is probably the `s2.1-pro-free` model. Check that its quality
and limits are fit for production before relying on it.

### Shape

One turn = the user holds the button, speaks, and lets go:

1. **`voice-turn`** (licence-key auth): audio upload → Fish ASR → Gemini with a strict tool list →
   returns JSON `{ transcript, reply, actions[] }`. Text appears straight away.
2. **`voice-speak`**: streams Fish TTS mp3 for `reply` in the robot's voice (`latency: low`).
   Fixed lines (wake-up, briefing openers, errors) are cached in Storage, keyed by hash(voice + text).

Use `transcribe-1-pro` for uploads: iOS Safari records MP4/AAC and Android Chrome records WebM,
and only pro accepts both.

**Allowed actions** (the robot proposes, the screen confirms, never auto-placed):
- `navigate(screen)`, `explain(topic)`, `account_status`
- `trade_idea`: writes a `voice_trade_ideas` row with a **30 s expiry**. Only a tap on
  `voice-trade-confirm` places it, through the same guardrails as any signal (budget, margin,
  risk-guard halt).
- `kill_switch`: shows a confirm card; the confirm calls `close-all` → `bridge.closeAll()`.
- `morning_briefing`: account, today's events from `market-wire`, and target progress.

**Voice rules in the system prompt:** speaks as the robot, **never names a mentor or says
"signals from"** (positioning rule), gives no financial advice beyond the robot's own plan.

**Cost control:**
- A `voice_usage` table per licence per day (ASR seconds, TTS bytes, LLM calls), with a daily cap
  enforced in `voice-turn`.
- Trade ideas spend **tokens** (pricing plan: 1 idea = 1 token).
- Budget estimate: about $15 a month for 50 users × 45 s a day of speech.

**Voice per robot:** add `expert_advisors.voice_id` (a Fish model id). The portal lets a provider
pick a stock voice or upload a 10–30 s sample to clone, with consent. If it's empty, use the
NovaHost default.

## Phase 5 — Flex card and terminal

- **`flex-stats`:** today's, this week's or one trade's profit and % gain from `trade_closes` and
  `risk_state`, plus robot branding and a funded badge. The card is drawn on the phone; no
  affiliate link.
- **`robot-log`:** a polled, time-ordered feed for one licence covering signals, placed, refused,
  closes, halts and wake-ups. Polled, not realtime, because realtime is blocked by row-level
  security for key-only users.

## New secrets

`VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY`, `RESEND_API_KEY`, `RESEND_FROM`, `METARPC_API_KEY`,
`FISH_AUDIO_API_KEY`, `FISH_DEFAULT_VOICE_ID`, and a paid `GEMINI_API_KEY`.

Sources: [Fish TTS](https://docs.fish.audio/api-reference/endpoint/openapi-v1/text-to-speech.md),
[Fish ASR](https://docs.fish.audio/api-reference/endpoint/openapi-v1/speech-to-text.md),
[Fish pricing and limits](https://docs.fish.audio/developer-guide/models-pricing/pricing-and-rate-limits.md),
MetaRPC sources as listed in `2026-10-broker-bridge-alternatives.md`.
