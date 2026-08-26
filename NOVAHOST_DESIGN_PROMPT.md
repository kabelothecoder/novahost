# NovaHost — Claude Design prompt template

One screen per session. Paste **Part A** unchanged every time, then fill in **Part B** for the
one screen you're on. Screen queue and per-screen Part B fills are at the bottom.

---

## Part A — paste verbatim every session

```
You are designing one screen of NovaHost, a mobile-first cloud trading app for retail forex
and crypto traders. Android native (Jetpack Compose, Material 3). The user connects their
MT4/MT5 account, runs an automated trading robot 24/7 in the cloud, and scans chart
screenshots with AI.

The design system is LOCKED. Do not propose an alternative palette, type stack, shape
language, or elevation model, and do not suggest a dark variant. Your job is layout,
hierarchy, copy, and interaction WITHIN these tokens:

SURFACES   canvas #F4F7F9 · card #FFFFFF · sunken #EDF1F5 · border #E2E8F0 · border-strong #CBD5E1
TEXT       primary #1A1D20 · secondary #5A6472 · muted #8A94A6 · on-accent #FFFFFF
ACCENT     primary #5C9CE6 · secondary #A288E3 (sparingly) · accent-dim #5C9CE6 @12%
STATUS     success #16A34A · danger #DC2626 · warning #D97706 · live #5C9CE6
CATEGORY   forex #0B68FF · crypto #8B00E0 · metals #E08600 · indices #DC2626
SHAPE      pill (CircleShape) for EVERY action trigger · card 24dp · control 12dp
ELEVATION  card: y+8 blur24 #000@6% · raised: y+16 blur40 #000@8%
           pressed: y+2 blur8 #000@10% + scale 0.97 · accent glow: y+8 blur24 accent@30%
TYPE       Space Grotesk = display/titles · Inter = UI/body · JetBrains Mono = ALL numbers

Non-negotiable rules:
- Every number is monospace. Prices, P&L, lot sizes, latency, licence keys, log lines.
- Every action trigger is pill-shaped. Nothing square-cornered.
- No emoji anywhere in chrome. Lucide-style icons only.
- Sentence case for headings and body. UPPERCASE mono only for labels and status pills.
- Call it a "robot". Never "strategy", "operator", "bot", or "execution universe".
- Delete a knob rather than add a setting. No theme switchers, font pickers, or gloss
  options — the only per-user variation that ships is the robot's accent colour.
- Never hardcode a broker name. Server fields are dynamic and searchable.
- Per-robot accent may colour: the START/STOP trigger, active robot card border and glow,
  robot portrait ring, selected row, connect action. It may NEVER colour body text, status
  colours (profit stays green even on a red-accent robot), card surfaces, borders, or canvas.

Reuse these components — do not invent new ones unless the screen genuinely has no home:
NovaButton · NovaIconButton · NovaTextField · NovaCard · NovaBadge · StatusDot ·
RobotAvatar (with statusRing) · BottomNav (floating dock) · PriceTicker · Accordion

PROCESS — follow in order:

1. Search Mobbin for the reference patterns I name below. Pull real screens. For each one
   you draw from, say in a sentence what you are borrowing and why it applies here. If a
   pattern is common in the references but wrong for a trading app where a mistake costs
   real money, say so and reject it.

2. Propose THREE genuinely distinct directions for this screen. Distinct means different
   layout and different information hierarchy — not three colour variations of one layout.
   For each: a one-line thesis, what goes above the fold, what it de-emphasises, and the
   main risk. Then recommend one and say why. Stop and wait for my pick.

3. After I pick, build only that direction. Deliver:
   - The screen at 390x844, in the locked tokens
   - Every state: loading, empty, error, success, and disabled where applicable
   - Real copy, not lorem. Write the actual microcopy.
   - A component list naming which of the components above each region uses
   - The press/transition behaviour for anything interactive

SCOPE — stay inside this screen. Do not design adjacent screens, do not propose navigation
restructuring, do not redesign components that already exist. If you think a neighbouring
screen needs to change for this one to work, note it in one line and move on.
```

---

## Part B — fill per screen

```
THE SCREEN: <name>
WHAT IT IS FOR: <one sentence, the user's goal>
ENTERED FROM: <preceding screen>
EXITS TO: <next screens>
MUST SHOW: <the data that has to be on screen>
MUST NOT SHOW: <what to keep off>
MOBBIN SEARCHES: <3-4 concrete search terms>
CONSTRAINT: <the hard thing about this screen>
```

---

## Screen queue

Splash, Paygate, the robot sheet and the risk calculator need *designing from scratch*.
Onboarding is in the queue because it needs layout work and four missing states, but the
screen already exists — that session is re-theme plus states, not a rebuild.

Every other screen needs **re-theming only**, which is a code task (deleting hard-coded dark
colours so the light scheme already applied at `Theme.kt:172` shows through). Don't spend
design sessions on those.

**Animation format is a hard constraint on both active screens.** In-app motion must be
AnimatedVectorDrawable or Lottie, never video: the Android splash API caps around 1000ms and
does not accept video at all, and §5's per-robot accent contract requires motion to be
re-tintable at runtime, which baked video cannot do. Generated video belongs in the Play Store
preview and the `promoVideoUrl` slot, not in the UI.

### 1. Splash — active

Reference set checked 2026-08-17. Mercury, Stake, Linear, Believe and Anything all show the
mark alone on a plain canvas with **no progress indicator at all**; Linear/Believe/Anything
render it in low-contrast grey, closer to a watermark. Craft is the only one that shows a
spinner, over a blurred skeleton.

```
THE SCREEN: Splash
WHAT IT IS FOR: Hold the first 400ms–8s while the licence check hits Supabase.
ENTERED FROM: App launch
EXITS TO: Home (licence valid) · Onboarding (no licence) · Paygate (expired)
MUST SHOW: NovaHost mark, centred
MUST NOT SHOW: Version numbers, loading percentages, tips, marketing copy, tagline
MOBBIN REFERENCES: Mercury splash · Stake splash · Linear Mobile splash · Craft onboarding
  loading state
CONSTRAINT: The real question is not what splash looks like — it is WHEN the progress signal
  appears. Every serious fintech reference shows no spinner, because a spinner makes a splash
  read as a loading screen instead of a brand moment. But my licence check can take 8 seconds
  on bad signal, and 8 seconds of silence reads as frozen. Design the delayed reveal: mark
  alone for the first ~600ms, progress fading in only if the check has not returned. Also
  design the outright-failure state for when the check errors.
  Animation must be an AnimatedVectorDrawable (Android splash API caps ~1000ms and does not
  accept video), so the motion has to work as vector — no raster, no video.
```

### 2. Onboarding — active

Five-step wizard: value → display name → server allocation → licence key → activated. The
Compose screen already exists (`OnboardingScreen.kt`) and needs re-theming, so this session is
about layout and the missing states, not a rebuild.

```
THE SCREEN: Onboarding (5-step wizard)
WHAT IT IS FOR: Take a trader from install to an activated, device-bound licence.
ENTERED FROM: Splash, when no valid licence is found
EXITS TO: Home (activated) · Paygate (no subscription)
MUST SHOW: Step 1 value — why cloud execution matters (phone off, 24/7, no battery drain) ·
  Step 2 display name · Step 3 server allocation progress · Step 4 licence key + email ·
  Step 5 activated confirmation
MUST NOT SHOW: Account creation (licence key IS the auth), social sign-in, password fields,
  marketing testimonials we don't have
MOBBIN REFERENCES: Quicken Simplifi onboarding · Craft onboarding · 7-Eleven onboarding
CONSTRAINT: Four patterns from the references to resolve into one flow —
  1. Quicken's value screen carries a REAL product artifact (a live data card), not an
     illustration. Ours should be a real robot card with a P&L figure in JetBrains Mono.
  2. Quicken and Craft both land activation on a dedicated low-density confirmation screen
     before entering the app. This is where the robot's accent colour first appears.
  3. Craft keeps Continue disabled and grey until the field validates — NovaButton needs that
     disabled state designed.
  4. 7-Eleven's inline field error (red border, red helper text, warning icon) is the pattern
     for a rejected licence key. Plus a thin progress bar pinned top through all five steps.
  Step 3 "server allocation" is simulated, not real. It must not lie about provisioning
  hardware — design honest copy for a step that is really just a paced transition.
```

### 3. Paygate

Highest stakes and the one where design thinking actually earns its keep.

```
THE SCREEN: Paygate
WHAT IT IS FOR: Convince a trader who has finished onboarding that NovaHost is worth paying
  for, then take the payment.
ENTERED FROM: Onboarding, when licence validation finds no active subscription
EXITS TO: PayFast checkout (external), or back to the licence gate
MUST SHOW: R599 lifetime (full platform) · R349 scanner-only · what each unlocks · why cloud
  execution matters at all (phone-off, 24/7, no battery drain)
MUST NOT SHOW: Countdown timers, fake scarcity, "limited spots", testimonials we don't have
MOBBIN SEARCHES: fintech paywall · lifetime purchase pricing · two-tier plan comparison ·
  trading app subscription upsell
CONSTRAINT: It must explain the value BEFORE it asks for money. A trader who has just typed
  their MT5 password has not yet seen the product work. Two prices on one screen also has to
  not read as a downsell — the R349 scanner tier is a real product, not a consolation prize.
```

### 4. Active robot status sheet

```
THE SCREEN: Active robot status sheet (bottom sheet)
WHAT IT IS FOR: Answer "what is my robot doing right now" and let the user stop it.
ENTERED FROM: Tapping the active robot card on Home
EXITS TO: Dismiss back to Home · Symbols · confirm-stop
MUST SHOW: Robot portrait + name · running state and uptime · symbols it is trading · open
  trades with live P&L · last heartbeat · STOP control
MUST NOT SHOW: Settings, per-symbol config (that lives on Symbols), historical performance
MOBBIN SEARCHES: bottom sheet active session · trading app open positions · live status
  detail sheet · running process control
CONSTRAINT: STOP is destructive and irreversible mid-trade, but must stay reachable in one
  gesture during a panic. Solve that tension. All numbers are mono and some update live —
  the layout must not jump when a P&L figure changes width.
```

### 5. Risk calculator

```
THE SCREEN: Risk calculator (shared component, not a full screen)
WHAT IT IS FOR: Turn account balance + risk % + SL distance into a lot size the user trusts.
ENTERED FROM: Embedded in both Symbols and Chart Scanner
EXITS TO: Writes the computed lot size into the order it is attached to
MUST SHOW: Balance · risk % · SL in pips · resulting lot size · the cash amount at risk
MUST NOT SHOW: Leverage explainers, margin tables, educational content
MOBBIN SEARCHES: position size calculator · fintech numeric input slider · loan amount
  calculator · inline calculation result
CONSTRAINT: It appears inside two different screens, so it cannot assume its own header or
  full width. The output feeds a live broker order, so the risk figure has to be impossible
  to misread — and it must handle the case where the computed lot is below the broker's
  minimum.
```

---

## Notes

- **Ask for divergence explicitly.** Design agents settle into one default house style and
  generic "make it different" pushes them to a different fixed default rather than to variety.
  The three-directions step in Part A is what breaks that — don't drop it.
- **Feed the result back here.** Once a direction is built, the next step is Compose against
  the components in §3 of `NOVAHOST_ANDROID_DESIGN_SPEC.md`. Naming components in the design
  output is what makes that handoff cheap.
