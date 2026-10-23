# NovaHost — Claude Design prompts (Oct 2026 UX overhaul, v2)

**v2 (2026-10-21):** every prompt restyled for a cleaner, more beautiful look.
- The home is now **video + mic only**: no robot name, no symbol, no balance.
- A **Falling FX** layer (currency signs, coins and notes, gold and gems, pair names) runs softly
  over the robot video, and the user can switch it on or off.

**How to use:**
1. Paste **Prompt 0** first in every Claude Design session, then the screen prompt.
2. From session 2 on, also paste: *"Use the same visual system as the Command Core design."*
3. One shared design serves both apps (Android Compose + iOS Vue PWA).

**Order (one Claude Design session each):**

| Session | Prompt | Screen |
|---|---|---|
| 1 | 5 | Command Core HUD (sets the visual language; attach the 2 Jarvis images) |
| 2 | 4 | Home layouts, Falling FX, layout picker |
| 3 | 13 | Pay-first onboarding + paywall |
| 4 | 1 | Symbols + risk per account (wizard) |
| 5 | 8 | Broker accounts: connect, manage, delink, multiple |
| 6 | 2 | Chart Scan |
| 7 | 3 | Voice + help mode |
| 8 | 7 | Voice trade idea |
| 9 | 14 | Billing hub, tokens, extra accounts |
| 10 | 9–12 | Flex Cards, Prop Firms, News Predictions, target lock / kill switch / briefing |
| 11 | 6 | Portal loop-video upload |
| 12 | 15 | Settings v3 |
| 13 | 16 | Help & Support v2 |

**Decisions locked with Kabelo (2026-10-21):**
- **Home layouts:** keep Classic Core, Focus Engine, Full-Bleed, plus the new Command Core.
  Glass Stack, Signal Feed and Command Deck are removed.
- **Home content (revised after the first Prompt 4 run):** video + Falling FX, robot name,
  Start/Stop, Trading Symbols button, Switch Robot button, mic, and a status dot. Still no
  current symbol, balance, P/L or stats. The controls must be laid out as a beautiful pattern.
- **Falling FX:**
  - It plays always, softly, over the hero video.
  - Styles: Currency signs · Coins & notes · Gold & gems · Pair names · Mix · Off.
  - It is switched on or off in Settings.
- **Voice:** push-to-talk via Fish Audio. The robot can talk, navigate and act (scan / execute)
  ONLY after an on-screen confirm.
- **Voice trade idea:** 30 s review window, TAP TO CONFIRM. The idea expires; it never
  auto-places.
- **Chart scan:** step-by-step flow, a "No trade" verdict, Scenario A/B, confidence + reasons.
- **Symbols:** auto-match wizard against the broker's real symbol list.
- **Open trades** no longer block new ones: a margin-based check, shrinking the lot on low margin.
- **No icons (2026-10-22):** NO symbol icons and NO broker logos or lettered badges anywhere.
  Symbols are text (friendly name + broker symbol in monospace); brokers read "Exness · ···4821".
  This overrides the logo/icon lines in Prompts 1 and 8.
- **Trades at once (2026-10-22):** every symbol has lot size AND number of trades (1–10), with
  live combined risk per account.

---

## Prompt 0 — Shared context (paste first, every time)

```
You are designing screens for NovaHost, a mobile EA-hosting app. A trader buys a licence
for a "robot" (an Expert Advisor built by a robot provider) and NovaHost runs that robot
against the trader's own MT4/MT5 broker account from their phone — no PC, no VPS.
It is NOT a "mentor sends you trades" app; always frame it as "your robot".

Users: retail traders in South Africa, many first-timers, mostly on mid-range Android
phones and iPhones. Real money is at stake. Users said the current app is cluttered and
confusing, so the new design must feel CALM, CLEAN and EXPENSIVE:
- Less text, more robot. Every screen has ONE obvious next action. If a piece of
  information isn't needed to make the next decision, hide it behind a tap.
- Cinematic, not dashboard-y: generous negative space, large type, layered depth
  (soft glass over dark gradients, subtle light falloff, gentle glows), like a premium
  game or a luxury car's infotainment, not a spreadsheet.
- Futuristic "command centre" mood (holographic glass, thin luminous edges, energy lines),
  but always readable by a first-time trader in two seconds.

Colour: dark-first. Deep near-black ground with a faint gradient. One robot accent colour,
set per robot by its provider; the design must look great with ANY accent (crimson, cyan,
gold, violet). Semantic colours only for meaning: green = good/ready, amber = warning,
red = blocked/loss. Text passes WCAG AA on every surface, including over video and art
(use a darkening scrim, not just blur).

Frame: 390×844 phone, safe areas respected, primary actions in the bottom thumb zone.
Typography: one clean sans (Inter / SF Pro), big confident headings, tabular numbers for
prices and money. Icons: thin, consistent line icons.
Motion: smooth and purposeful (150–300 ms), springy where it feels physical; respect
reduced-motion; every effect has a LITE version for low-end Android (no blur, no particles).
Deliver each screen in its main state PLUS empty, loading and error states. Error text is
always plain English with the fix ("Broker disconnected — tap to reconnect"), never raw codes.
```

---

## Prompt 5 — "Command Core" layout (Jarvis / Iron-Man HUD)

Reference images (attach both): floating holographic glass panels around a glowing core,
cyan/violet neon, a deep-space or server-room backdrop.

```
Design a home layout called "Command Core" — a Jarvis / Iron-Man style holographic HUD for a
phone. Use the two attached images as MOOD: floating translucent glass panels with thin neon
edges, a glowing core in the centre, energy lines, deep-space ambience. It must stay CLEAN:
by default the screen shows only the core and the mic.

DEFAULT (clean) STATE:
- Centre: the robot's LOOP VIDEO (or still art) inside a glowing circular reactor core, with
  slowly rotating HUD rings in the robot's accent colour. The core breathes when idle, pulses
  when the robot is thinking, and flashes when a trade fires.
- Behind everything: the FALLING FX layer (see the home prompt) drifting softly down.
- Bottom: one big glowing push-to-talk mic button. No robot name, no symbol, no numbers.
- A tiny status dot on the core's ring: green = running, amber = needs attention,
  red = stopped/broken.

EXPANDED STATE (tap the core or swipe up): 4 glass panels unfold around the core, connected
by animated energy pipelines. Pipelines are honest — particles flow only when that link is
really live; a broken link turns amber/red:
  1. ACCOUNT — balance, equity, free margin %, currency.
  2. BROKER LINK — "Connected" / "Disconnected — tap to fix". Goes red when the account drops.
  3. LAST ACTION — the last trade in one line, or a plain-English refusal.
  4. TODAY — open trades and today's result.
When a trade fires, a light pulse travels Core → Broker → Account so the user SEES it go
through (or stop at the broken link). Tap outside or swipe down to fold back to clean.

Also design: the automatic LITE MODE for low-end Android (no particles, no blur, static
lines that change colour), the reduced-motion version, how it looks with a still image instead
of a video, two different accent colours, and a mini-preview tile for the layout picker.
Panels never cover readable text; every number passes WCAG AA over the glow.
```

---

## Prompt 4 — Home layouts, Falling FX, layout picker

```
BEFORE YOU DESIGN ANYTHING: ask me exactly 8 questions about how I want these home layouts
to look and behave (placement, style, motion, what each control does). Wait for my answers,
then design.

Redesign the home layouts. The home is about the ROBOT. The goal is a "wow, this is
beautiful" reaction: the controls are laid out as an elegant, intentional pattern (an arc,
an orbit around the video, a floating glass cluster), not a row of plain buttons.

ON EVERY HOME LAYOUT, exactly these elements and nothing more:
  1. The robot's LOOP VIDEO (hero) with the Falling FX layer.
  2. The ROBOT NAME, set as beautiful display typography, not a plain label.
  3. START / STOP: the main control that turns the robot on and off. It's the most
     important button: big, satisfying, clearly showing Running vs Stopped (e.g. a glowing
     power ring that charges up), with a confirm on Stop.
  4. TRADING SYMBOLS button: opens the symbol wizard; shows a small badge when a symbol
     needs attention ("1 needs fixing").
  5. SWITCH ROBOT button: opens a robot switcher (the user can own several robots); a sheet
     or carousel of the user's robots with their video thumbnails and accent colours.
  6. The push-to-talk mic button.
  7. A tiny status dot (tap → small sheet with status and the fix if something is wrong).
Still NO balance, NO P/L, NO stats, NO activity list, NO current-symbol text.

Layouts:
Each layout arranges the same 7 elements in its OWN distinct pattern:
1. Classic Core — the video in a large rounded hero card with a soft accent glow and
   reflection beneath it; name over the card; controls below as a tidy, balanced cluster.
2. Focus Engine — the video large and centred inside a halo of light; the controls orbit
   the halo (e.g. symbols and switch-robot as small satellites, Start as the main ring).
3. Full-Bleed — the video fills the whole screen edge-to-edge; name and controls float
   over a soft bottom scrim as a slim glass control bar. No dock.
(The 4th layout, Command Core, is designed separately — show it in the picker.)

FALLING FX (new, on top of the video, under the mic and status dot):
Things drift slowly down over the robot video like a gentle shower, with depth: some
larger and closer and slightly blurred, some small and far away, softly lit in the robot's
accent colour or gold. Always on (softly) unless the user turns it off. Styles:
  • Currency signs — R, $, €, £, ¥ glyphs
  • Coins & notes — 3D gold coins tumbling and banknotes fluttering
  • Gold & gems — small gold bars and crystal gems that catch the light
  • Pair names — "XAUUSD", "NAS100", "EURUSD", "US30" falling like Matrix code
  • Mix — a tasteful blend of all four
  • Off
Rules: it is calm, never busy (low density, slow speed); it never covers the mic or the
status dot; LITE MODE = fewer, flat, no blur; reduced-motion = off by default.
Show the same home in each style so we can compare.

SETTINGS → "Home look":
- Layout picker: live mini-previews of the 4 layouts (Classic Core, Focus Engine,
  Full-Bleed, Command Core) playing the robot's real video and accent.
- Falling FX: on/off switch, style picker (thumbnail per style, live preview),
  and an intensity slider (Subtle / Normal / Rich).
```

---

## Prompt 13 — Pay-first onboarding + plans paywall

```
Redesign onboarding so users PAY FIRST, then enter their licence key. Make it feel like the
opening of a premium game: cinematic, few words, the robot video as the star.
Order: Intro (3 short swipe slides max) → Choose plan → Pay (PayFast, external checkout)
→ Payment confirmed → Enter licence key → Activation → Home.

1. Intro slides: one line each over the robot loop video with Falling FX — "Your robot trades
   for you, from your phone", "No PC, no VPS", "You stay in control". Skip button.
2. CHOOSE PLAN: two beautiful cards, Monthly and Annual ("2 months free" badge, preselected).
   Prices are placeholders: R[MONTHLY]/month and R[ANNUAL]/year. Short included list with
   icons (1 broker account, auto-trading, voice robot, Flex Cards, daily target lock, kill
   switch, morning briefing). Small line: "Extra broker accounts R[EXTRA]/month each".
   Trust row: secure payment via PayFast, cancel anytime. CTA "Continue to payment".
   No dark patterns: price and renewal terms visible before the button.
3. Handoff + return: "Opening secure checkout…", then "Waiting for payment confirmation"
   (this can take up to a minute), success, and failed / cancelled with retry.
4. ENTER LICENCE KEY: big key input with paste button and format mask, "Where do I find my
   key?" helper, and a 2-phase unlock animation (checking → the robot core powers up and its
   video starts). Errors: invalid key, key used on another device, key for a different robot.
5. Returning user who paid but hasn't entered a key lands straight on step 4 with a
   "Payment received ✓" badge — never sees the paywall again.
6. Existing lifetime (R599) customers: a one-time "Thanks for being early — you get
   [N] free months" screen.
```

---

## Prompt 1 — Trading symbols + risk, PER ACCOUNT (auto-match wizard)

```
This is a REDESIGN. The project already has my V1 symbol screens. Keep what works, then make
this dramatically more beautiful in the Command Core visual system (glass, glow, depth, big
confident type, one obvious action per screen). Before designing, list 3–5 bullets of what
you're keeping from V1 and what you're changing, then ask me up to 8 questions and wait.

THE BIG IDEA: a user can link 2–3 broker accounts, and ONE robot trades ALL of them at once.
Symbols and risk are set PER ACCOUNT, because every broker names symbols differently
(gold may be "XAUUSD", "XAUUSD.ifx", "GOLD", "XAUUSDm"; Nasdaq may be ".USTECH." or "NAS100")
and every account has a different balance and currency. So this screen always says which
account it is configuring.

ACCOUNT CONTEXT (shown on EVERY step, always visible at the bottom, in a glass bar):
  "This configuration is for  [Exness logo] Exness · ···4821 · R1,000   ▾"
Tapping the bar opens an account switcher sheet with every linked account (logo, masked
login, balance, a "configured ✓ / needs setup" tag). Switching account switches the whole
wizard to that account's own settings. Each account keeps its own symbol matches, risk
preset and lot sizes, saved separately. A user can finish account 1, switch, and configure
account 2 completely differently (e.g. Low risk on the R1,000 account, High on the $200 one).

FLOW (3 steps, progress at top, one decision per screen):
Step 1 "Your robot trades these" — the robot's symbols with friendly names + icons (Gold,
  US30, Nasdaq, EURUSD), each auto-matched to THIS account's broker symbol:
  "Gold → XAUUSD.ifx ✓". Three states: ✓ Matched (green), ? Pick one (amber, 2–3 candidate
  chips), ✕ Not on this broker (grey, explains why). A per-symbol on/off switch.
  CTA "Looks good".
Step 2 "How much to risk" — ONE simple preset per account (Low / Medium / High) showing the
  real money in THIS account's currency ("≈ R50 per trade on your R1,000 account",
  "≈ $4 per trade on your $200 account"). "Advanced" reveals per-symbol LOT SIZE and
  NUMBER OF TRADES (how many trades the robot may hold on that symbol at once, 1–10, a
  stepper next to the lot size). Show the combined risk: "0.02 × 3 trades ≈ R150 at risk on
  Gold". Hard warning if lot × trades risks too much of this account's balance.
Step 3 "Ready" — a card per symbol with a live pill (Ready / Checking… / Problem) and
  "Test with broker". Success: "3 of 4 symbols ready on Exness — your robot can trade these".
  Then a prominent next action: "Set up your next account" (if another account isn't
  configured yet) or "All accounts ready".

ACCOUNTS OVERVIEW screen (opened from Settings and from the end of the wizard): one beautiful
row/card per linked account with logo, masked login, balance + currency, a "Robot trading"
switch (on/off), the risk preset, "3 of 4 symbols ready", and a "Set up" / "Fix" button.
At the top, one line: "Your robot trades 2 accounts". A "Copy settings from another
account" shortcut that re-matches symbols for the new broker and converts risk to that
account's balance and currency. An account whose switch is off, or that isn't configured, is
clearly shown as "not trading".

Also design: the compact "Symbols" card in Settings showing all accounts at a glance, the state
when the broker's symbol list can't load, a first-time user with ONE account (the account bar
is still there but calm, with "Add another account" as a quiet link), and an account whose
broker disconnected (the wizard shows "Reconnect to continue").
Make it feel rewarding: when an account becomes fully ready, a short glow-and-check moment.
```

---

## Prompt 8 — Broker accounts: connect, manage, delink, multiple accounts

```
This is a REDESIGN, not a blank page. The project already has my V1 broker screens and a first
build of the new ones. Keep what works, then make this OUTSTANDING: a trader should think
"wow, this is beautiful" and want to screenshot it. Use the Command Core visual system:
glass, glow, depth, big confident type, one obvious action per screen. Before designing, list
3–5 bullets of what you're keeping and changing, then ask me up to 8 questions and wait.

Make it FEEL good: broker logos glow softly on glass tiles; the server list feels like picking
a premium item, not reading a spreadsheet; inputs light up in the robot's accent colour as you
type; errors are calm and premium (a clear fix, never a scary red wall). Show 2 visual
directions for the connect screen so I can choose.

HERO MOMENT when linking succeeds: a pipeline from the robot core to the broker logo charges
up, energy flows across, the core flares, a soft burst of the Falling FX coins, a confident
"Connected" with a gentle haptic. It should feel like a reward.

PART 1 — CONNECT A BROKER (the user never has to guess a server name). A numbered step strip:
Broker → Server → Login → Connected. Design EVERY step:

1. FIND YOUR BROKER. The first build only showed 5 tiles (Exness, XM, Trade245, JustMarkets,
   Razor Markets) and no way to link any other broker. Fix: keep those 5 as popular shortcuts
   on top, plus a prominent SEARCH bar that searches ALL MT4/MT5 brokers (thousands). Results
   appear instantly as a list: logo, broker name, MT4/MT5 badges. Always show beneath:
   "Can't find your broker? Enter your server manually".
2. LOGOS come from the broker directory automatically. When a broker has none, show a
   beautiful lettered badge (first letters, tinted with a colour derived from the name, same
   shape and size as real logos) so no row ever looks broken. Show a real-logo tile and a
   lettered-badge tile side by side so both feel equally premium.
3. SERVER. After choosing a broker, show its servers grouped Real / Demo as tappable rows with
   MT4/MT5 badges and a "most used by NovaHost traders" hint on top, plus "Type your server
   instead". The manual route (from "Can't find your broker?") goes straight to a server field
   with a helper: "Copy it exactly as MT5 shows it — Settings → Trade accounts / Login to
   Trade Account". A server we know doesn't work yet is greyed with an explanation.
4. LOGIN + PASSWORD (this screen was MISSING from the first build): account number, password
   with show/hide, the note "Use your trading password, not the investor password", the chosen
   broker + server as a locked summary chip at the top, and a primary "Connect" button, then
   the linking animation. States: connecting, wrong password, server not found, investor
   (read-only) password detected ("This password is read-only — use your trading password"),
   broker not supported yet.

PART 2 — CONNECTED ACCOUNT card: broker logo, server, login (partly masked), MT5 badge, live
status (Connected / Connecting / Disconnected / Wrong password), balance + currency, last sync.
DELINK WAS MISSING from the first build, so make it easy to find:
  - On every account card a clear but quiet "⋯ Manage" menu with "Reconnect", "Rename" and
    "Delink account" (destructive, red text, unlink icon).
  - Also a "Delink account" row at the bottom of the account detail screen and in
    Settings → Broker accounts.
  - Delink opens a bottom sheet that says exactly what happens: "Your robot stops trading on
    this account, open trades stay open at your broker, and NovaHost removes the connection.
    Your other accounts keep trading. You can link this or another account any time."
    Slide-to-confirm → progress ("Stopping robot… Removing connection…") → success with
    "Link a different account". Also the error state if removal fails (retry, contact support).

PART 3 — MULTIPLE ACCOUNTS. A user can link several accounts and ONE robot trades ALL of them.
- The FIRST account is included in the plan (no extra charge). Each EXTRA account is a paid
  monthly add-on: +R[EXTRA]/month per account, billed with the plan.
- "My accounts" screen: all linked accounts as stacked glass cards (like a premium wallet),
  each with logo, masked login, status, balance + currency. The first has an "Included" tag;
  extras show "+R[EXTRA]/mo".
- Each card has a "Robot trading" switch (on/off) so the user chooses which accounts the robot
  trades, plus a one-line summary ("Medium risk · 4 symbols ready") that opens that account's
  symbol and risk setup (designed separately). A clear "Active" state shows which account the
  home screen is currently showing; switching is a quick swipe or tap on the card stack.
- "Add another account" is a glowing "+" card at the end of the stack. Tapping it opens a calm,
  beautiful sheet: "Link another account for R[EXTRA]/month — your robot trades it too", what's
  included, "Billed with your plan, cancel anytime" → PayFast → slot unlocked (a celebratory
  unlock animation) → the Part 1 connect flow.
- A first-time user with no account sees "Link your first account — included".
- Delinking an extra account says "Billing for this account stops at the end of this period".
- States: payment pending, payment failed, a paid slot with no account linked yet
  ("Your extra slot is ready — link an account").
In your questions, ask me where the account switcher should live on the home screen and how
switching should feel.
```

---

## Prompt 2 — Chart Scanner v3: styles, news-aware, advanced + beautiful

```
REDESIGN the AI "Chart Scanner". The project already has my V1 scanner and the new symbol and
broker screens — match those exactly (Command Core visual system, glass, glow, big type,
one action per screen, NO symbol icons and NO broker logos: text only). Users said the old
scanner gave the same answer every time and they lost trades. The new one must feel advanced,
honest and beautiful. Before designing, list 3–5 bullets of what you keep and change, ask me
up to 8 questions, and wait.

FLOW (progress at top, one decision per screen, swipe back allowed):
1. ACCOUNT + SYMBOL — the account bar at the bottom like the wizard ("Exness · ···4821 · R1,000 ▾").
   Symbol list = this account's READY symbols as text rows (Gold / XAUUSD.ifx). Each row shows
   a small live news badge when an event is close: "CPI in 14 min".
2. TRADING STYLE — 4 large cards, each with hold time, timeframes and stop size in plain words:
   SCALP (M1–M5, minutes, tight stop) · DAY TRADE (M15–H1, closes today) ·
   SWING (H4–D1, days) · BREAKOUT (waits for a key level to break). Style drives everything
   after it (timeframes scanned, stop distance, targets, order type).
3. CHART — "Use live chart" (default: robot pulls candles itself across 2–3 timeframes for the
   chosen style) or "Upload a screenshot" with a framing guide.
4. SCANNING — the robot core scans with a light sweep while real checks tick in one by one:
   "Higher timeframe trend… Structure… Key levels… Volatility… Spread… News in the next 2 h…
   Your margin…". 10–20 s, cancellable. Token cost shown ("1 token").
5. RESULT (the hero screen):
   - Verdict: BUY / SELL / NO TRADE ("Stay out — here's why"), visually distinct, with a
     confidence ring 0–100 (Low / Moderate / High) and the style chip ("Scalp").
   - "What lined up / What didn't" ✓ / ✕ rows.
   - ORDER the robot chose (user can tap "Change"): Market · Buy/Sell Limit · Buy/Sell Stop,
     with a mini price ladder (current price, entry, stop, TP1/TP2) explaining why
     ("Limit — waiting for a pullback to 2,398"). Pending orders show expiry.
   - SIZE: lot size × number of trades from this account's settings, money at risk in the
     account currency, R:R.
   - Scenario B: "If price breaks 2,412, the plan flips to SELL …".
   - Invalidation: "This plan is wrong if price closes above 2,412".
   - Guardrails (red, blocks Place): over budget, low margin, symbol not ready, broker
     disconnected, market closed.
   - Actions: "Place trade" (slide to confirm, disabled for NO TRADE / blocked), "Save idea",
     "Scan again".

NEWS INTELLIGENCE (new, important):
   - If a high-impact event (NFP, CPI, FOMC, GDP, rates) is within the style's window, the result
     shows a NEWS CARD on top: event, countdown, forecast vs previous, and the robot's call:
     "CPI in 14 min. My read: hotter than forecast → USD up → Gold SELL bias (62%)."
     with IF/THEN branches: "Above 3.1% → SELL Gold · Below 2.9% → BUY Gold · In between → stay out".
   - The robot then recommends ONE of: "Trade now (before news)", "Wait for the release"
     (default for Scalp/Day), or "Stand aside". If "Wait", the user can tap "Alert me at
     release" — at release the robot reads the actual number, says which branch fired, and
     opens a ready trade card (tap to place, 30 s review window, never auto-places).
   - 15 MINUTES BEFORE any event on a symbol the user scanned or trades, the robot proactively
     shows (and says, if voice is on) a pre-news briefing banner with the prediction and the
     branches. Design this banner on the home screen and inside the scanner.
   - Always warn calmly: "Spreads widen and prices jump at news time."

Also design: scan history (past scans with what actually happened, wins AND losses), token
balance pill and out-of-tokens sheet (packs, progress kept), market-closed state, no ready
symbols state ("Set up symbols first"), and a friendly error state.
Make it interactive end to end and use realistic data (Exness R1,000 account, Gold XAUUSD.ifx,
US30 .US30., US CPI today).
```

---

## Prompt 15 — Settings v3: every switch for every new feature + notifications

```
REDESIGN Settings so it controls EVERYTHING in the new app. Match the screens already in this
project exactly (Command Core visual system, glass, glow, big confident type, text only — thin
line icons on rows are fine, NO broker logos, NO symbol icons). The current Settings is
outdated. It must stay CALM even though it holds a lot: a short hub of groups, each group opens
its own page, one line per row with the current value on the right ("On", "15 min", "Medium").
Before designing, list 3–5 bullets of what you keep and change, ask me up to 8 questions, wait.

HUB HEADER: robot (small looping video) + robot name, plan ("Monthly · renews 22 Nov"),
licence status, and a health line ("2 accounts trading · 4 symbols ready" or an amber
"Broker disconnected — tap to fix"). Search field: "Search settings…".

GROUPS AND EVERY CONTROL INSIDE THEM:

1. TRADING
   - Broker accounts → "My accounts" (reuse existing): link, delink, robot trading on/off
     per account, active account.
   - Trading symbols & risk → the wizard (reuse existing): per account, lot size + number of
     trades per symbol, risk preset.
   - Margin protection: "Shrink lot when margin is low" on/off; minimum free margin % (default 50).
   - Daily target lock: on/off · profit target (amount or %) · loss limit (amount or %) ·
     reset time (default 00:00) · voice announcement on/off.
   - Kill switch: "Long-press robot to close all" on/off · voice "close everything" on/off ·
     default action (close all trades / stop robot, keep trades).
   - Trade review window: 30 s (15 / 30 / 60). Note under it: "Trades never place without your tap."

2. CHART SCANNER
   - Default trading style: Scalp / Day trade / Swing / Breakout / Ask every time.
   - Default chart source: Live chart / Upload screenshot.
   - Show news card in results: on/off.
   - Scan history: keep / clear.

3. NEWS & EVENTS
   - Pre-news briefing: on/off · how early: 5 / 15 / 30 / 60 min (default 15).
   - Which events: NFP, CPI, FOMC / rates, GDP, PMI, Retail sales (toggles).
   - Only for my symbols: on/off.
   - "Alert me at release" default: on/off.
   - Announce by voice: on/off.

4. PROP FIRMS
   - Prop accounts list → add / edit firm rules (daily loss %, max drawdown %, target, news ban).
   - Auto-pause before breaking a rule: on/off · warn at 70% / 90%.
   - Manual-mode trade cards (firms that ban EAs): on/off.

5. ROBOT & VOICE
   - Switch robot (licences on this phone).
   - Voice: on/off · voice speed · captions on/off · push-to-talk button on home on/off.
   - Help mode: guided walkthroughs on/off.
   - Morning briefing: on/off · time (on first open / fixed time) · length (short / full).
   - Microphone permission status with "Open phone settings".

6. NOTIFICATIONS (new — both Android and iOS)
   - Master switch + permission status ("Allowed" / "Blocked — open phone settings").
   - iOS note row when not installed: "Add NovaHost to your Home Screen to get notifications."
   - Categories, each its own toggle (and sound on/off per category):
       Trade placed / filled
       Trade refused (with reason)
       Trade closed (profit / loss)
       Broker disconnected / wrong password
       Daily target or loss limit hit
       Pre-news briefing (X min before)
       News release result ("CPI 3.3% — Gold SELL setup ready")
       Morning briefing ready
       Prop firm rule warning
       Low margin
       Scan tokens low / plan renewing / payment failed
       App updates & announcements
   - Quiet hours: on/off · from–to · "Still allow: trade refused, broker disconnected".
   - "Send a test notification" button.

7. LOOK & FEEL
   - Home layout: Command Core / Classic Core / Focus Engine / Full-Bleed (live mini previews).
   - Falling FX: Currency signs / Coins & notes / Gold & gems / Pair names / Mix / Off.
   - Accent override (robot's colour / custom).
   - Reduce effects: Auto / On / Off.  Loop video: Always / Wi-Fi only / Off (data saver).
   - Haptics on/off. Sounds on/off.
   - Privacy: hide balances on home and Flex Cards by default.

8. FLEX CARDS
   - Default template (Reactor / Gold Rush / Neon / Clean) · show amounts or % only.

9. PLAN & BILLING
   - Plan (Monthly / Annual, renew date, manage / cancel) · extra accounts (+R[EXTRA]/mo each)
     · scan tokens balance + buy · payment history.

10. ACCOUNT & DEVICE
   - Email · licence key (masked, copy) · this device · move to a new phone · sign out ·
     delete my data (with confirm).

11. HELP & SUPPORT → the new help centre.   12. ABOUT → version, terms, privacy, risk warning.

Design: the hub, every group page, the Notifications page in full detail (most important),
permission-blocked state, iOS "add to home screen" state, quiet-hours picker, test-notification
toast, search results, plan-expired banner, broker-disconnected warning row.
Make it clickable end to end, with realistic values.
```

---

## Prompt 16 — Help & Support v2

```
REDESIGN Help & Support to match the new screens (same visual system, text only). It's
outdated. Before designing, list 3–5 bullets of what you keep and change, ask me up to 8
questions, and wait.

1. TOP: "Ask your robot" — a big push-to-talk / type field. The robot answers from the app's
   knowledge AND the user's live state ("Your broker disconnected at 14:02 — want me to take
   you there?") with a "Take me there" button and an optional guided walkthrough (coach marks).
2. "FIX IT" cards that appear only when something is wrong for THIS user: broker disconnected,
   symbol not set up, wrong password, low margin, plan expired — each with a one-tap fix.
3. GUIDES: short step guides with progress — Connect your broker, Set up symbols & risk,
   Use the Chart Scanner, Understanding news predictions, Multiple accounts, Pay & licence key,
   Move to a new phone.
4. FAQ: searchable, grouped, plain English.
5. "Why didn't my trade go in?" — a dedicated screen that lists the user's last refused trades
   with the real reason and the fix for each.
6. CONTACT: WhatsApp support, email, ticket form (auto-attaches device, licence, last errors —
   shown to the user before sending), and expected reply time.
7. Status line: "All systems working" / "Broker connections delayed" with time.
States: no internet, robot can't answer, ticket sent confirmation.
```

---

## Prompt 3 — Talk to your robot (push-to-talk voice)

```
Design the "Talk to your robot" voice screen. The robot speaks with a real voice.
PUSH-TO-TALK: hold the big mic, speak, release; the robot answers aloud.

Hero: the robot's LOOP VIDEO fills the upper ~60% (cover-fit, poster fallback, Falling FX
softly behind). It reacts: Idle (gentle loop) → Listening (accent glow ring + live waveform)
→ Thinking (subtle pulse) → Speaking (waveform synced to the voice, captions below).
Below: captions of the last exchange (user bubble + robot bubble), large and readable.
First-time suggestion chips: "How's my account?", "Any open trades?", "Scan Gold on H1",
"Why didn't my trade go in?", "Help me connect my broker".

Actions by voice: the robot can navigate ("Opening your symbols…") and propose actions, but
ANY trade action appears as an on-screen confirm card — "Execute Gold BUY, 0.01 lots, risk
R45? [Cancel] [Confirm]". It never auto-executes.

HELP MODE: the robot is also the app's guide. It knows every screen and the user's live state
(broker connected? symbols ready? last refusal?). For "I'm stuck" / "why didn't my trade go
in?" it answers in plain language using their actual state, shows a "Take me there" card that
deep-links to the screen, and can run a walkthrough with a spotlight coach-mark and a voice
bubble. Fallback: "Still stuck? Contact support".

States: first-run mic permission, mic denied, no internet, robot can't answer, voice
credits used up.
```

---

## Prompt 7 — "Ask the robot for a trade idea" (voice → live analysis → tap to place)

```
Design the voice trade-idea flow — Chart Scan without a screenshot, wrapped in the HUD.

1. User holds the mic: "Give me a trade idea" (or "What's gold doing?").
2. The robot replies aloud and shows symbol chips from the user's READY symbols.
3. Trading style: voice + 4 elegant tappable cards — Scalp (minutes, tight stop) ·
   Intraday (closes same day) · Swing (days) · Breakout (waits for a level to break),
   each with typical hold time and stop size in plain words.
4. LIVE ANALYSIS (~10–20 s): the core spins up and narrates while checks land as ✓ / ✕ rows
   ("Pulling the last 200 candles… trend is up… at resistance… checking news… checking
   your margin…").
5. VERDICT card: BUY / SELL / NO TRADE, confidence ring, money at risk in account currency,
   one-line reason, and the ORDER TYPE the robot chose (Market · Buy/Sell Limit · Buy/Sell
   Stop) with a small "Change" link and a mini price ladder showing WHY (pullback vs breakout).
   Pending orders show an expiry ("cancels if not triggered in 4h").
   NO TRADE ends with "Ask me again in 15 min".
6. REVIEW WINDOW: a 30-second circular timer around the core ("BUY XAUUSD 0.01 · risk R45 ·
   expires in 30s"). The trade goes in ONLY when the user taps / slides "Place trade"; when
   the timer ends the idea EXPIRES. Secondary: "Dismiss". Any guardrail failure (budget,
   margin, symbol not ready, broker disconnected) replaces the timer with a red blocked state.
7. RESULT: a pulse along the broker pipeline → "Filled at 2,398.40", or a plain-English
   refusal with the fix ("Not enough margin — close a trade or lower risk").
States: mic denied, analysis failed, market closed, out of tokens.
```

---

## Prompt 14 — Billing hub: subscription, extra accounts, scan tokens

```
Design "Plan & billing" in Settings plus the purchase moments in the app. Calm and premium —
no countdown pressure, no fake scarcity.

A) BILLING HUB: a plan card (Monthly/Annual, renews on date, price, Manage/Cancel), linked
   broker accounts with slots ("2 of 3 accounts", each extra +R[EXTRA]/month), token
   balance, payment history.
B) ADD ANOTHER BROKER ACCOUNT: "Add account" → sheet "+R[EXTRA]/month, billed with your
   plan" → PayFast → slot unlocked → broker connect flow. Delinking an extra account shows
   "billing stops at the end of this period".
C) TOKENS: balance pill on Chart Scan and voice trade ideas ("7 tokens"); 1 scan or 1 idea
   = 1 token. Token store sheet with 3 packs (Starter 10 / Trader 40 "best value" / Pro 120,
   prices placeholder) shown as gorgeous collectible cards; an out-of-tokens state that
   sells a pack inline without losing progress; "failed scans are refunded automatically".
D) Problems: payment failed (grace banner, update payment), expired (robot paused, one-tap
   renew), cancelled-but-active-until-date.
```

---

## Prompt 9 — Flex Cards (shareable results)

```
Design "Flex Cards": one tap turns results into a premium animated share card. NO affiliate
link, NO QR code, NO promo text — pure bragging rights, styled like a luxury / gaming
achievement card.

Content (Today / This week / This month / Single trade): the robot video or art as the
background, profit in account currency AND %, win rate, trades, best trade, an equity line
that draws itself, date range, a subtle NovaHost wordmark.
Templates (4): "Reactor" (HUD, matches Command Core), "Gold Rush" (black + gold foil, with
the Falling FX coins), "Neon" (cyberpunk), "Clean" (minimal white type on black).
Toggles: hide amounts (% only), hide currency, hide robot name.
Export: 9:16 animated loop (6–8 s) for WhatsApp Status / TikTok / IG Stories + a static
image. Show the template carousel, the privacy toggles, "rendering", and the share sheet.
Losing periods offer "Show % only" and never mock the user.
```

---

## Prompt 10 — Prop Firm section

```
Design a "Prop Firms" section for traders with challenge / funded accounts.

1. Add a prop account: search firms (logo, name), pick Challenge phase 1 / phase 2 / Funded,
   rules prefilled where we know them (daily loss %, max drawdown %, profit target, minimum
   days, news allowed?, EAs allowed?).
2. EA CHECK — voice + a clear card:
   • ALLOWED → "Good news — <firm> allows robots. I'll trade it within your challenge rules."
     → broker connect, AUTO mode.
   • NOT ALLOWED / UNKNOWN → "<firm> doesn't allow robots. I'll send you the trades to place
     yourself." → MANUAL mode.
3. MANUAL trade card: symbol, BUY/SELL, order type, entry, SL, TP, lot size sized for THIS
   account's rules, a copy button per value, "I placed it" / "Skipped", and a validity countdown.
4. CHALLENGE HEALTH: beautiful gauges for daily loss vs limit, drawdown vs limit, profit vs
   target, days vs minimum. Amber at 70% of a limit, red at 90%; the robot pauses itself
   before a breach.
States: firm not in database, rules missing, breach warning, challenge passed (celebration →
Flex Card), challenge failed.
```

---

## Prompt 11 — News Predictions ("Event Playbook")

```
Design "News Predictions": the robot prepares for big events (NFP, CPI, FOMC, GDP) instead
of hiding from them.

1. Upcoming events strip: event, countdown, impact, affected symbols.
2. EVENT PLAYBOOK (published ~1 hour before): forecast vs previous, the robot's bias with
   confidence %, and IF/THEN branches drawn as a glowing fork diagram:
     "Above 180K → USD strong → SELL Gold" · "Below 150K → USD weak → BUY Gold" ·
     "In between → no trade, too noisy".
3. LIVE RELEASE: the screen shifts into "event mode", the number lands, the matching branch
   lights up ("NFP 212K, above forecast — Gold sell setup") and a trade card appears.
   The user TAPS to place it (review window, then it expires).
4. Per-event settings: auto-place off by default, max risk for news trades, and a warning
   that spreads widen and slippage is common.
5. History: past playbooks with what actually happened, wins AND losses.
```

---

## Prompt 12 — Daily target lock, voice kill switch, morning briefing

```
Design three small, premium features in the Command Core style.

A) DAILY TARGET LOCK — "Stop for the day at +R500 or −R300" (amounts or %). A ring around
   the core fills toward the target. On hit: the robot parks itself, a celebration (with a
   burst of the Falling FX) or a calm consolation, a voice line, and "Resume anyway" needs a
   deliberate long-press. Resets at a chosen time.
B) VOICE KILL SWITCH — "close everything" / "stop trading" → a full-screen red confirm sheet:
   open trades count, total floating P/L, options "Close all trades" / "Stop robot, keep
   trades open" / "Cancel". Slide-to-confirm, then a progress list closing each trade with
   results. Also reachable by long-pressing the robot core.
C) MORNING BRIEFING — first open of the day: the robot greets the user by name with a
   ~30-second voice briefing while cards glide in: yesterday's result, open trades, today's
   key events, what the robot is watching, account health. Skip / Replay, captions on.
```

---

## Prompt 6 — Robot provider portal: upload a loop video

```
Design the web portal screen where a robot provider uploads a LOOP VIDEO for their robot.
Desktop web, same dark premium design language.

- Drag-and-drop zone with requirements up front: MP4 or WebM, 3–10 seconds, max ~8 MB,
  portrait 9:16 recommended, no audio (stripped automatically).
- After upload: live preview in a phone frame on each home layout (Classic Core, Focus
  Engine, Full-Bleed, Command Core, Voice screen), looping, WITH the Falling FX on, so the
  provider sees exactly what users see.
- Poster frame picker (the still for loading / reduced-motion).
- Validation errors in plain English (too long, too large, wrong format, landscape warning).
- Processing state, then "Live on N users' phones".
```
