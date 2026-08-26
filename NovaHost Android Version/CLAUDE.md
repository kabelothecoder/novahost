# NovaHost Architecture & Claude Code Onboarding Brief

This document serves as the master technical blueprint and context brief for NovaHost. It onboards Claude Code as an autonomous Lead Engineer for this repository.

## 1. Executive Summary & Core Value Proposition

### What is NovaHost?
NovaHost is a mobile-first cloud VPS and automated algorithmic trading management platform ("Silent Precision"). It allows retail forex and crypto traders to connect their MetaTrader 4 (MT4) or MetaTrader 5 (MT5) accounts, execute automated trading strategies (Expert Advisors / EAs) 24/7 in the cloud without draining phone battery, scan chart patterns using Claude 3.5 Sonnet AI, and execute remote trade signals broadcast instantly from mentors or trading desks.

### Target Users
- **Retail Traders**: Users seeking zero-latency, 24/7 cloud execution for automated trading strategies directly from their iOS or Android smartphones.
- **Mentors & Signal Providers**: Trading educators who broadcast high-probability trade setups to student accounts and manage subscriber fleets.

### Key Business Goals
NovaHost Direct-to-Consumer: A unified mobile application and platform offering premium cloud hosting, AI chart scanning, and risk management exclusively under the proprietary NovaHost brand.

## 2. Architecture & Tech Stack Breakdown

```
                                  ┌─────────────────────────────────────────┐
                                  │           ADMIN / MENTOR PORTAL         │
                                  │     (Vue.js / React Web Dashboard)      │
                                  └────────────────────┬────────────────────┘
                                                       │
                                                       │ Pushes Signals & Resets Hardware UUIDs
                                                       ▼
┌────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                           SUPABASE BACKEND                                             │
│  ┌───────────────────────────┐   ┌───────────────────────────┐   ┌──────────────────────────────────┐  │
│  │   PostgreSQL Database     │   │     Realtime WebSockets   │   │        Deno Edge Functions       │  │
│  │ (bot_states, subscriptions│   │     ('signals_channel')   │   │  (/functions/v1/analyze-chart    │  │
│  │   symbol_preferences)     │   │                           │   │   /generate-payfast-checkout)    │  │
│  └───────────────────────────┘   └───────────────────────────┘   └──────────────────────────────────┘  │
└──────────────────────────────┬─────────────────────────────────────────────────┬───────────────────────┘
                               │                                                 │
          Listens to Signals & │ Verifies Device UUID           Executes Trades  │ Reads Saved Lot Size
          Updates Bot State    │                                                 │ & Symbol Parameters
                               ▼                                                 ▼
┌─────────────────────────────────────────────────────────────┐   ┌──────────────────────────────────────┐
│                      MOBILE CLIENTS                         │   │            METAAPI ENGINE            │
│  ┌────────────────────────┐     ┌────────────────────────┐  │   │      (MT4 / MT5 Broker Bridge)       │
│  │   Android Native App   │     │      iOS Hybrid App    │  │   └──────────────────┬───────────────────┘
│  │ (Kotlin/Compose, M3,   │     │  (Vue.js, Capacitor,   │  │                      │
│  │ NovaHostPulseService)  │     │   Apple HIG, Pinia)    │  │                      │ Opens Orders Instantly
│  └────────────────────────┘     └────────────────────────┘  │                      ▼
└─────────────────────────────────────────────────────────────┘   ┌──────────────────────────────────────┐
                                                                  │         BROKER INFRASTRUCTURE        │
                                                                  │    (Exness, Deriv, XM, IC Markets)   │
                                                                  └──────────────────────────────────────┘
```

### Mobile Applications
- **Android Application**: Native Kotlin built with Jetpack Compose (Material 3, dark scheme by default with an opt-in Light scheme — see §4). Uses `NovaHostPulseService` (a foreground service) to maintain persistent WebSocket connections to Supabase Realtime without system-level background process throttling.
- **iOS Application**: Vue.js combined with Capacitor for cross-platform native runtime, styled according to Apple Human Interface Guidelines (HIG). Uses `@capacitor/device` for hardware-level fingerprinting and Pinia/Vuex for state management.

### Web Mentor Portal
- **Tech Stack**: Vue.js / React Web Application.
- **Core Responsibilities**: Central command dashboard allowing mentors to broadcast trade signals to connected student accounts, monitor active subscriber counts, manage license keys, and perform 1-tap "Reset Device UUID" actions when users change physical devices.

### Core Data Models & Schema
- **subscriptions / licenses**: Stores `email`, `status` (active/expired), `license_key`, `device_id` (hardware UUID), and `created_at`.
- **bot_states**: Stores `account_id`, `is_active` (boolean), `selected_pair`, and `last_heartbeat`.
- **signals**: Stores `symbol`, `action` (BUY/SELL), `stop_loss`, `take_profit`, and `timestamp`.
- **symbol_preferences**: Stores `user_id`, `symbol` (XAUUSD, US30, etc.), `custom_lot_size`, `risk_percentage`, and `max_open_trades`.

### API Endpoints & External Integrations
- `POST /functions/v1/analyze-chart`: Supabase Edge Function accepting a base64 image string + `trading_mode`. Invokes Anthropic API (`claude-3-5-sonnet-latest`) to perform visual analysis and return structured JSON.
- `POST /functions/v1/generate-payfast-checkout`: Builds a PayFast checkout for one of three **once-off** products — R599 app access (`LIFETIME`), R349 AI chart scanner (`SCANNER`), R150 device move (`REACTIVATION`). There is no subscription; recurring parameters are never sent.
- `POST /functions/v1/check-subscription-status`: The **only** authority on entitlement. Answers `is_premium` / `has_scanner` for an email + device, owns the one-email-one-device rule, and fails closed. Android reaches it through `sdk/Entitlements.kt`, never directly.
- **MetaAPI Cloud SDK**: Remote API connecting the client app to MetaTrader broker terminals (Trade245, Exness, Deriv, etc.).

### Authentication & Security Engine ("The Vault")
- **Device Fingerprinting**: Every subscription is bound to One Email, One Device. The app reads the physical device hardware identifier (`Device.getId()` via Capacitor on iOS, Android Hardware ID on Android).
- **Un-bypassable Route Guards**: Global navigation guards (`router.beforeEach` on iOS, NavHost state check on Android) query Supabase directly on boot. Client-side storage tampering (localStorage / SharedPreferences) cannot bypass the security gate.

## 3. Comprehensive Feature Matrix

### Mobile Features (Android & iOS)

| Feature | Primary Purpose | Tech Implementation |
|---|---|---|
| System Setup Onboarding | 5-step setup wizard introducing value, capturing display name, simulating server allocation, and verifying activation. | `HorizontalPager` (Compose) / 5-step Touch Slider (Vue). |
| Device-Locked Activation | Validates user license/email and binds physical device UUID to Supabase database. | `@capacitor/device` + Supabase `subscriptions` table. |
| Home Dashboard | Five swappable interfaces over one Home state — Classic Core, Focus Engine, Full-Bleed Art (default), Glass Stack, Signal Feed. Art dominant, robot name legible at thumbnail size, one obvious glowing control. | `HomeLayoutHost` (`ui/home/`), state in `HomeViewModel`. Layout is presentation only — a running bot survives a layout switch. |
| Interface Picker | Applies a layout instantly; art mode (Avatar / Framed / Full) is set per layout on that layout's own card. | `InterfaceScreen`, `Routes.INTERFACE`. |
| Arrange Widgets | Drag-to-reorder and per-widget visibility, saved per layout. Robot Hero and Ignition Pod are pinned in all five. | `ArrangeWidgetsScreen`, `Routes.ARRANGE_WIDGETS`. Device-local via `NovaPrefs`; nothing syncs. |
| Robot Ignition (START/STOP) | Toggles cloud trading engine status, spins up local logging service, and joins Realtime socket. | `NovaHostPulseService` / WebSocket connection. |
| AI Chart Scanner | Analyzes uploaded chart screenshots to extract patterns, signals, Stop Loss, and Take Profit levels. | Camera/Picker -> base64 -> Claude 3.5 Sonnet Edge Function. |
| Trade Calculator | Balance, a typed total risk %, and a trade count; splits the budget and reports risk per trade and a suggested lot. Lives on the **Chart Scanner** input step, where its per-trade figure is what `TradePlanner` sizes the position with. It is not a home widget — `TradeCalculatorCard.kt`. |
| Trading Symbols | One scroll, six sections: the robot's allowance, the user's selection with per-symbol lot and concurrent-trade limits, the trade calculator, newsfeed, session windows, and high-impact events. Reached **only** from the Quotes button on the home ignition row — it is deliberately not in the nav menu. | `Routes.PAIRS`, `PairManagementScreen.kt` + `MarketContext.kt` embeds. |
| Per-symbol trading plan | Which of the robot's permitted symbols this subscriber actually trades, at what size, and how many at once. Stored device-side by `SymbolPlanStore` and pushed to `license_symbol_config` so the executor can enforce it. **Never** written into `allowed_symbols` — that is the mentor's allowance and is read-only on the device. | `sdk/SymbolPlan.kt`, `sync-symbol-config`, `metacopier-execute`. |
| Agnostic Broker Setup | Secure form collecting MT4/MT5 Server, Account ID, and Password without hardcoded broker strings. | Material 3 / HIG Outlined Inputs + MetaAPI SDK. |

### Mentor Portal Features

| Feature | Primary Purpose | Tech Implementation |
|---|---|---|
| Signal Broadcast Engine | Sends instant trade orders (Symbol, Action, Entry, SL, TP) to all active subscriber accounts. | Web form pushing to Supabase `signals` table / Realtime broadcast. |
| Student Fleet Monitor | Displays live count of active online trading robots and subscriber subscription health. | Realtime PostgreSQL channel query. |
| License Management | Generates new license keys, checks payment statuses, and manages user accounts. | Supabase Admin SDK. |
| 1-Tap Hardware Reset | Unbinds a user's `device_id` in Supabase when they upgrade or replace their phone. | PostgreSQL `UPDATE subscriptions SET device_id = NULL`. |

## 4. Component & Design System Guidelines

**Dark is the app's real colour scheme.** `NovaHostTheme` builds `obsidianColorScheme` by default; Light is an explicit opt-in toggle in Settings (`NovaHostThemeState.useLightScheme`, persisted by `NovaPrefs`). The "Premium Light" palette below survives as that opt-in and as the palette the onboarding and splash art were drawn against — it is no longer the default, and it is not what most screens render.

This changed because every screen already painted dark surfaces while `premiumLightColorScheme` was hardwired underneath them. Anything the screens did not paint themselves — ripples, disabled text, unstyled dividers — resolved against the wrong palette.

### Home Command Center (dark only, no opt-out)

The five home layouts sit on mentor-supplied art, and a light ground under a photograph has no reading that works. They are dark regardless of the Light toggle, using the `Home*` tokens in `ui/theme/Color.kt`.

| Token group | Purpose |
|---|---|
| `HomeCanvas` / `HomeCanvasArt` / `HomeCanvasFocus` / `HomeCanvasGlass` / `HomeCanvasFeed` | Per-layout grounds. Each layout needs a slightly different black to keep its art gradient from banding. |
| `HomeSurface`, `HomeSurfaceRaised`, `HomeSurfaceSunken`, `HomeSurfaceWell` | Rows, picker cards, stat tiles, terminal log well. |
| `HomeBorder`, `HomeBorderStrong`, `HomeBorderSubtle`, `HomeBorderFaint` | Hairlines, chips, control outlines. |
| `HomeTextBright` → `HomeTextFaint` | Headings, body, monospace readouts, captions, field labels. |
| `HomeAccentBlue/Violet/Jade/Amber/Crimson` | The five mentor accents. Blue is the fallback when a robot defines no `accent_color`. |
| `HomeLive`, `HomeSell` | Live/OK and sell/error on a dark ground. Distinct from `NovaSuccess`/`NovaDanger`, which are tuned for light. |

**Contrast floor is not optional.** Mentor accent, user override, art mode and glow multiply into combinations that go illegible. Any accent drawn as text or as a control edge over art must pass through `Color.onArtFloor()` (`ui/home/HomeKit.kt`), and any art reaching the background must carry a scrim. A mentor is free to pick a near-black accent; the floor is what stops that from shipping an invisible START button.

### Premium Light tokens (opt-in scheme, onboarding + splash)

| Token | Value |
|---|---|
| App Background | Soft Icy Off-White (`#F4F7F9`) |
| Cards / Surfaces | Pure White (`#FFFFFF`), 24px Border Radius |
| Ambient Card Shadow | Soft Diffused (`box-shadow: 0 16px 40px rgba(...)`) |
| Primary Action Accent | Soft Light Blue (`#5C9CE6`) |
| Secondary Accent | Soft Light Purple (`#A288E3`) |
| Primary Typography | Deep Charcoal (`#1A1D20`) |
| Muted Subtitles | Soft Slate Grey (`#8A94A6`) |

### Component Rules & Conventions
- **Pill Shapes Only**: Every primary button MUST be pill-shaped (`CircleShape` in Compose, `border-radius: 9999px` in Vue/CSS). Square or rectangular buttons are strictly prohibited. Glass Stack's 22.dp rounded controls are the one deliberate exception, and they are a home-layout choice, not a licence to square off buttons elsewhere.
- **Floating Cards**: On the Light scheme, content blocks sit in pure white (`#FFFFFF`) cards with a 24px / 24.dp radius and soft drop shadows (`box-shadow: 0 16px 40px rgba(0, 0, 0, 0.06)`). On dark, depth comes from the `GlassPanel` / `HomeCard` pair in `ui/home/HomeKit.kt` instead — glass over art, flat card over a flat ground.
- **One glowing control per screen**: Each home layout has exactly one obvious ignition. Satellite controls never compete with it.
- **Zero Broker Hardcoding**: Never hardcode specific broker names (such as "Trade245") in UI text or placeholders. All server selection fields must use dynamic, searchable inputs.

### Where appearance is stored

`NovaPrefs` (plain `nova_appearance` store) is the single home for every appearance and layout value. `TerminalPrefs` holds credentials only — do not add appearance keys back to it. Robot identity (`display_name`, `avatar_url`, `accent_color`) stays in `metahost_prefs`, written by licence activation.

## 5. Instructions for Claude Code

You are the Lead Engineer working on NovaHost, a mobile-first cloud VPS and automated trading management suite (Android native in Kotlin/Compose, iOS hybrid in Vue.js/Capacitor, Admin Portal in Vue/React, and Supabase backend).

### Mandatory Workflow & Implementation Rules

Before writing or modifying any code in this repository, strictly adhere to the following 5-step execution workflow:

#### 1. Pre-Flight Verification & Skill Check
- Analyze the codebase to understand existing architecture, state management patterns, and design tokens.
- Review necessary core concepts before writing code (e.g., Jetpack Compose state hoisting, Vue 3 Composition API, Supabase Realtime WebSocket subscriptions, Capacitor native plugins, or Deno Edge Function constraints).
- Check proposed UI against the right scheme for the surface (§4): the five home layouts are dark-only and use the `Home*` tokens; other screens follow the dark default with Light as an opt-in. Never reintroduce a hardcoded single scheme.
- Any accent used as text or a control edge over mentor art must go through `Color.onArtFloor()`, and art reaching the background must carry a scrim.

#### 2. Step-by-Step Implementation Plan
- Provide a concise, numbered plan detailing every file you intend to create, modify, or delete.
- Explicitly state how data will flow between the Frontend, Supabase Database, Realtime Channels, and MetaAPI SDK.
- Wait for user confirmation if the task involves database schema changes or architecture shifts.

#### 3. Strategic UI/UX Hacks & Micro-Interactions
- Detail micro-interactions to make the feature feel world-class (e.g., tactile haptic feedback on button presses, smooth CSS/Compose transition animations, debounced inputs, or skeleton loader states).
- Ensure buttons are strictly pill-shaped (`CircleShape` / `border-radius: 9999px`).
- Ensure no hardcoded broker names or static API keys exist in the UI code.

#### 4. Reference Search Terms
- List 2-3 exact YouTube or web search terms for design teardowns and build references related to the feature (e.g., "Jetpack Compose animated modal bottom sheet tutorial", "Vue 3 Capacitor device fingerprinting setup", or "Supabase Edge Functions Anthropic vision API").

#### 5. Execution & Quality Guardrails
- Write clean, production-ready, type-safe code.
- Wrap all network operations and asynchronous API calls in robust `try/catch` blocks that present user-friendly error messages (e.g., Snackbars or Toasts) instead of raw HTTP stack traces.
- Run project build tools (`./gradlew assembleDebug` for Android or `npm run build` for iOS/Vue) to verify zero compilation errors.
