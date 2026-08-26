# NovaHost — Android Design Spec

Maps the **NovaHost Design System** (`claude.ai/design`, project `5e9fb928`) onto the real
Compose codebase. Written 2026-08-13.

---

## 0. Decisions locked

| Decision | Choice |
|---|---|
| **Theme** | **Premium Light** — `#F4F7F9` canvas, white cards, `#5C9CE6` accent |
| **Language** | **"Robot"** — not "strategy" / "operator" / "execution universe" |
| **Per-robot accent** | Kept. Each robot's `accent_color` themes the home screen |

**What this means for the design system:** its *structure* is adopted wholesale — component
set, screen inventory, information architecture, per-robot accent, pill shapes, no-emoji rule,
Lucide-style iconography. Its *palette* is not: the graphite/cobalt tokens translate to the
light ramp in §2. The "Liquid Cyber-Glass" treatment needs a dark ground to read, so on light
it becomes **soft neumorphic elevation** — see §3.

**What the design system got right that we keep verbatim:**
- Per-robot accent theming the whole home screen (already wired to `expert_advisors.accent_color`)
- Deliberate removal of theme switchers, gloss pickers, background-video galleries, emoji in chrome
- Monospace for all numbers — latency, P&L, lot sizes, log lines
- Pill shape for every action trigger; nothing square-cornered
- Sentence case headings; UPPERCASE mono only for labels and status pills

---

## 1. Current state — the honest audit

The theme foundation is **already correct**. `Theme.kt:172` applies
`premiumLightColorScheme(...)`. The problem is that individual screens ignore it.

### Screens hard-coding dark colours (override the light theme)

| File | What it does |
|---|---|
| `screens/MetaTraderConnectScreen.kt` | `background(Color.Black)`, full dark treatment |
| `screens/HomeScreen.kt` | Mixed — dark surfaces over a light scheme |
| `screens/AuthScreen.kt` | Dark |
| `screens/MarketsScreen.kt` | Dark |
| `screens/SettingsScreen.kt` | Dark |
| `screens/SymbolScannerScreen.kt` | Dark |
| `screens/OverlayPermissionActivity.kt` | Dark |

**7 of 13 screens.** This is the bulk of the work — not designing anything new, but deleting
hard-coded colours so the existing light scheme shows through.

### Theme cruft to remove

`NovaHostThemeState` (`theme/Theme.kt:46`) carries per-user styling options the design system
explicitly rejected as competitor-app clutter:

```kotlin
appTheme, isGlossTheme, holographicGlowMode, backgroundAssetIndex,
robotFontStyle (4 typefaces), robotNameFontSize, robotNameFontColor,
homeButtonScale, homeButtonShape, useRoundedShape, immersiveMode
```

Every one of these is a knob that lets a user break the design. Removing them is a
**simplification**, not a feature loss — the robot's `accent_color` is the only per-user
variation the system is designed to carry.

Also in there:
- `robotName` defaults to `"OPTIMUS PRIME SCALPER EA"` — another hardcoded placeholder of the
  same family as the `QUANTUM_BREAKER` bug. Should default to empty.
- `primaryColor` defaults to `Crimson`, then `Theme.kt:172` special-cases it back to
  `SoftLightBlue`. Default it to `SoftLightBlue` directly.
- `promoVideoUrl` points at the **legacy Supabase project** (`kivpdtisymhymmndndun`), not the
  live one. Already carries a TODO.

### Palette cruft

`theme/Color.kt` holds three generations of palette stacked on top of each other: "Obsidian
Forge v2" (neon orange / cyber cyan / hot pink / electric green), a mesh-gradient set, glow
variants, and — at the bottom — three Premium Light tokens. Only the light ones survive.

---

## 2. Premium Light token set

Translates the design system's semantic structure to light. Names mirror the design system so
the two stay legible against each other.

### Surfaces

| Token | Value | Use |
|---|---|---|
| `bg-canvas` | `#F4F7F9` | App background |
| `surface-card` | `#FFFFFF` | Cards, sheets, inputs |
| `surface-sunken` | `#EDF1F5` | Log panels, inset wells, disabled fields |
| `border-default` | `#E2E8F0` | Hairlines, card borders |
| `border-strong` | `#CBD5E1` | Emphasised dividers |

### Text

| Token | Value | Use |
|---|---|---|
| `text-primary` | `#1A1D20` | Headings, values |
| `text-secondary` | `#5A6472` | Body, descriptions |
| `text-muted` | `#8A94A6` | Captions, placeholders, disabled |
| `text-on-accent` | `#FFFFFF` | Label on a filled accent button |

### Accent + status

| Token | Value | Use |
|---|---|---|
| `accent-primary` | `#5C9CE6` | Primary actions, active states |
| `accent-secondary` | `#A288E3` | Secondary emphasis, sparingly |
| `accent-dim` | `#5C9CE6` @ 12% | Tinted backgrounds, selected rows |
| `status-success` | `#16A34A` | Profit, connected, executed |
| `status-danger` | `#DC2626` | Loss, disconnected, failed |
| `status-warning` | `#D97706` | Pending, degraded |
| `status-live` | `#5C9CE6` | Robot running |

> Status colours are darkened from the design system's dark-mode values — `#22C55E` on white
> fails contrast. These pass **WCAG AA** on `#FFFFFF` and `#F4F7F9`.

### Category tones (symbols)

Keep the design system's four fixed categories, flattened to solid fills for light:

| Category | Colour |
|---|---|
| Forex | `#0B68FF` |
| Crypto | `#8B00E0` |
| Metals | `#E08600` |
| Indices | `#DC2626` |

### Shape

| Token | Value |
|---|---|
| `shape-pill` | `50%` / `CircleShape` — **every** action trigger |
| `shape-card` | `24.dp` |
| `shape-control` | `12.dp` — inputs, chips, small controls |

### Elevation — replaces "Liquid Cyber-Glass"

Glass needs a dark ground. On light, interactivity is signalled by **soft elevation**:

| Level | Spec | Use |
|---|---|---|
| `elev-card` | `y+8, blur 24, #000 @ 6%` | Resting cards |
| `elev-raised` | `y+16, blur 40, #000 @ 8%` | Sheets, dialogs, active robot card |
| `elev-pressed` | `y+2, blur 8, #000 @ 10%` + `scale 0.97` | Press state |
| `elev-accent` | `y+8, blur 24, accent @ 30%` | Primary button rest |

The design system's **press physics survive**: `scale(0.97)`, spring easing, and the
accent-tinted glow under primary actions. Only the material changes — tinted glass becomes
white-on-soft-shadow.

### Type

| Role | Family | Use |
|---|---|---|
| Display | Space Grotesk | Robot name, screen titles |
| UI / body | Inter | Everything conversational |
| Mono | JetBrains Mono | **All numbers** — P&L, lots, latency, prices, log lines, licence keys |

Labels and status pills: mono, UPPERCASE, wide tracking. Headings and body: sentence case.

> **Fonts are not bundled.** The design system notes this too. Ship the three families in
> `res/font/` or the app silently falls back to Roboto and the whole system reads generic.

---

## 3. Component mapping

Design system component → what exists in Compose today.

| Design system | Compose today | Action |
|---|---|---|
| `Button` (glass, accent-derived) | scattered `Button(...)` per screen | **Build `NovaButton`** — pill, accent from `LocalNovaHostTheme`, press physics |
| `IconButton` | inline `IconButton` | Build `NovaIconButton` |
| `Input` | `DarkOutlinedField` (`MetaTraderConnectScreen`) | **Rename + re-theme → `NovaTextField`**, light |
| `Toggle` | raw `Switch` | Build `NovaToggle` |
| `Card` | `GlassComponents.kt` (dark glass) | Re-theme → white + `elev-card` |
| `Badge` | ad-hoc `Text` in pills | Build `NovaBadge` — tones: live / success / warning / danger |
| `StatusDot` | `StatusBadge` (connect screen) | Extract, pulse when live |
| `Avatar` | inline `AsyncImage` | Build `RobotAvatar` with `statusRing` |
| `BottomNav` | none found | Build — floating dock |
| `Accordion` | `PairManagementScreen` inline | Extract |
| `PriceTicker` | `MarketFeedContainer.kt` | Re-theme |

**Zero shared shape constants exist today** — `RoundedCornerShape(24.dp)` is repeated inline
across files. Define `NovaShapes` once and reference it everywhere.

---

## 4. Screen mapping

| # | Your screen | Design kit | Compose file | Status |
|---|---|---|---|---|
| 1 | Splash | — | none | **Missing** |
| 2 | Onboarding | `OnboardingScreen.jsx` | `OnboardingScreen.kt` | Re-theme |
| 3 | Licence gate | part of onboarding | `OnboardingScreen.kt` | Works; re-theme |
| 4 | Paygate | — | `PaywallOverlay.kt` | **Design missing**, component exists |
| 5 | Home dashboard | `CommandScreen.jsx` | `HomeScreen.kt` | Re-theme + de-cruft |
| 5a | Robot avatar | `Avatar` + portrait | inline in `HomeScreen` | Extract |
| 5b | Active robot sheet | — | none | **Missing** |
| 5c | Floating overlay | — | `NovaHostPulseService.kt` | Exists; re-theme |
| 6 | Quotes / symbols | `SymbolsScreen.jsx` | `MarketsScreen.kt`, `PairManagementScreen.kt` | Re-theme |
| 7 | Risk calculator | — | partial in `HomeScreen` | **Missing as a component** |
| 8 | Chart scanner | `ScannerScreen.jsx` | `SymbolScannerScreen.kt` | Re-theme + wire picker |
| 9 | Broker setup | `TerminalScreen.jsx` | `MetaTraderConnectScreen.kt` | Re-theme (most dark) |
| 10 | Settings & support | `SettingsScreen.jsx` | `SettingsScreen.kt`, `HelpSupportScreen.kt` | Re-theme + de-cruft |

### Gaps to design in Claude Design

1. **Splash** — logo, one-line wordmark, licence check spinner
2. **Paygate** — R599 lifetime + R349 scanner. Must explain *why* before asking for money
3. **Active robot status sheet** — bottom sheet: robot portrait, running state, symbols, open
   trades, stop control
4. **Risk calculator** — shared component used on both Quotes and Scanner: balance, risk %, SL
   pips → lot size

---

## 5. Per-robot accent — the contract

The one piece of per-user theming the system keeps. Already wired end to end:

```
expert_advisors.accent_color
  → validate-license returns accent_color
  → OnboardingScreen stores prefs["accent_color"]
  → MainViewModel.refreshRobotIdentity()
  → NovaHostThemeState.primaryColor
  → premiumLightColorScheme(primary)
```

**What the accent may colour:** the START/STOP trigger, the active robot card border and glow,
the robot portrait's ring, the selected row in the robot list, the connect action.

**What it may never colour:** body text, status colours (profit stays green regardless), card
surfaces, borders, or the canvas. A robot with a red accent must not make the app look like an
error state.

**Fallback:** `#5C9CE6` when the robot defines no accent.

---

## 6. Order of work

**Phase 1 — foundation (no visual risk)**
1. Prune `Color.kt` to the Premium Light set; delete Obsidian/neon/mesh/glow palettes
2. Add `NovaShapes` (pill / card / control) and elevation helpers
3. Strip the styling knobs from `NovaHostThemeState`; default `primaryColor = SoftLightBlue`,
   `robotName = ""`

**Phase 2 — components**
4. `NovaButton`, `NovaTextField`, `NovaCard`, `NovaBadge`, `StatusDot`, `RobotAvatar`

**Phase 3 — screens, worst first**
5. `MetaTraderConnectScreen` (fully dark, and on the critical connect path)
6. `HomeScreen` (most visible, most cruft)
7. `SymbolScannerScreen`, `MarketsScreen`, `SettingsScreen`, `AuthScreen`

**Phase 4 — the gaps**
8. Splash, Paygate, Robot status sheet, Risk calculator

> Phase 1 alone will visibly change the app, because 7 screens currently fight the light theme
> that is already applied.

---

## 7. Two rules worth keeping

**Delete a knob rather than add a setting.** The design system's sharpest call was removing
theme switchers, gloss pickers and font choosers. Every one is a way for a user to make the
product look worse. `accent_color` is the only variation that ships.

**Numbers are monospace, always.** It is the cheapest signal that a trading app is serious,
and the current build is inconsistent about it.
