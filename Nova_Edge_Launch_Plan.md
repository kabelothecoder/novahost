# Nova Edge — Launch Plan (Tue 2026-08-11 → Thu 2026-08-13)

**Scope:** Android app + Mentor Portal + Supabase/MetaCopier backend. iOS is **dropped** for this window.
**Definition of launched:** signed internal build on a real device, license gate + paygate + 10 screens functional, and **one real end-to-end trade proven** from mentor portal → connected broker account. No app-store submission.

> This plan is written against what is **actually in the repo and the live database**, not what `CLAUDE.md` claims. The two differ significantly. Read §1 before agreeing to anything.

---

## 1. Audit findings — read this first

I ran a real build and queried the live Supabase project (`MetaHost` / `epulmnfbxjmaimefhofp`, eu-west-3, ACTIVE_HEALTHY). Here is the honest state.

### 1.1 The good news — far more exists than expected

| Area | Status |
|---|---|
| Android screens | **13 Compose screens already written** (~5,400 lines) — Welcome, Onboarding, Auth, Vault, Home, Settings, Pairs, MetaTraderConnect, SymbolScanner, Markets, MarketsTabs, HelpSupport, OverlayPermission |
| Android navigation | `AppNavigation.kt` wires all 11 routes |
| Floating overlay | `NovaEdgePulseService` (503 lines) + `SYSTEM_ALERT_WINDOW` permission + `OverlayPermissionActivity` — the "float over apps" feature is built |
| Paygate | `PaywallOverlay.kt` (258 lines) exists |
| Mentor portal | React/Vite/shadcn, **21 pages** incl. `QuickTrade` (signal dispatcher), `LicenseManagement`, `GenerateKey`, `ReActivateKey`, `ManageEAs`, auth + protected routes |
| Backend | **15 edge functions deployed and ACTIVE**, 13 tables with RLS enabled |
| Payments | PayFast checkout + webhook + redirect deployed; deep link `metahost://payment` registered in the manifest |

**You are not starting from zero. You are debugging and finishing.** That is what makes Thursday plausible.

### 1.2 The bad news — five P0 blockers

Every one of these is currently blocking a working end-to-end trade.

#### P0-1 — The Android build was broken (UTF-8 BOM) — ✅ **RESOLVED**
```
settings.gradle: 1: Unexpected character: '?' @ line 1, column 1
```
`settings.gradle` and `app/build.gradle` were saved with a UTF-8 BOM. Groovy cannot parse it. **68 files across all three repos carry a BOM** — likely from the rename/refactor pass. Kotlin and TypeScript tolerate it; Gradle build scripts do not.

> **Actioned and verified:** I stripped the BOM from those two files (originals saved as `*.bom-backup`) and rebuilt:
>
> ```
> BUILD SUCCESSFUL in 6m 51s
> 35 actionable tasks: 35 executed
> ```
>
> **The BOM was the *only* thing broken in the Android build.** No compile errors behind it — just deprecation warnings (`MasterKeys`, `TYPE_PHONE`, some non-auto-mirrored icons). A debug APK now exists.
>
> This is the single biggest de-risk of the week: ~5,400 lines of Compose UI compile cleanly. The remaining 66 BOM files are cosmetic — normalise them Tuesday so this can't recur.

#### P0-2 — The signal pipeline cannot write to the database
The live `public.signals` table has columns:
`id, ea_id, pair, type, price, sl, tp, lot, status, created_at`

Every edge function writes `side` and `signal_id` — **neither column exists**:

| Function | Writes | Result |
|---|---|---|
| `broadcast-signal` | `side`, `signal_id` | insert throws; dedup `.eq('signal_id', …)` throws |
| `dispatch-signal` | `side` | insert throws |
| `execute-trade-v2` | `side`, `signal_id` | insert throws |
| `webhook-handler` | `side`, `signal_id` | insert throws |

**No signal has ever successfully persisted.** This is consistent with all 13 tables being empty. Fix is either a migration adding `side`/`signal_id` or renaming in code — decide once, apply everywhere (see Q3).

Related: `execute-trade-v2` passes a **text** `license_key` into `signal_logs.license_id`, which is **uuid** → type error on every failure-logging path.

#### P0-3 — Anyone on the internet can fire trades into your users' live accounts
- `broadcast-signal` is deployed with `verify_jwt: false`.
- Its only auth is header `x-novaedge-key`, compared against `ADMIN_BROADCAST_KEY` which **defaults to the literal string `dev-secret-key`**.
- `src/pages/QuickTrade.tsx:107` **hardcodes `'dev-secret-key'` in client-side React**, so it ships inside the public JS bundle.

If `ADMIN_BROADCAST_KEY` is not set in Supabase, the fallback matches the key printed in your public bundle. Anyone who opens devtools can broadcast BUY/SELL orders to every connected account. **This must be fixed before a single real user connects real money.** Non-negotiable.

#### P0-4 — The trading engine is a stub, and it's MetaAPI, not MetaCopier
- `MetaAPIManager.synchronize()` (`sdk/MetaAPIManager.kt:66-78`) does **not connect to anything**. It sets two booleans to `true` and appends `">> Connected to trade server"` to a log. The `TODO` at line 67 is explicit.
- `dispatch-signal` is a stub — it `console.log`s the terminal details and returns success without executing.
- The only **real** broker call is `test-broker-connection`, which provisions a MetaAPI `cloud-g2` terminal — that one is genuine.
- **There is zero MetaCopier code anywhere in the repo.** No tables, no columns, no API client. The entire execution layer today is MetaAPI.

`MetaAPIManager.kt:38-39` also hardcodes `TRADE245_PREFIX = "245"` and `BROKER_NAME = "Trade245"`, directly violating the "Zero Broker Hardcoding" rule in your own `CLAUDE.md`.

#### P0-5 — The AI Chart Scanner cannot open an image
`SymbolScannerScreen.kt:288` — `.clickable { /* TODO: Initiate image picker */ }`. The `analyze-chart` edge function is deployed and real, but nothing on the client can feed it a screenshot.

### 1.3 Also broken, lower severity

| Issue | Location | Impact |
|---|---|---|
| Broker password "encryption" **in transit** is `base64("MH_SALT_" + pw)` | `execute-trade-v2:37`, `test-broker-connection:53`, `dispatch-signal` | Trivially reversible obfuscation, not encryption. Passwords are decoded and `console.log`-adjacent in edge functions. **At rest on device it's fine** — `TerminalPrefs` uses `EncryptedSharedPreferences` (AES256-GCM + hardware-backed `MasterKeys`). The weakness is the wire format and function logging, not local storage. |
| `Theme.kt:57` points at a **different, legacy Supabase project** (`kivpdtisymhymmndndun`) for the promo video | `ui/theme/Theme.kt` | Leftover from rebrand; video will 404 if that project is gone |
| `NanoBananaService.kt:40` `isConnected = true // placeholder` | service | Connection watcher always reports healthy |
| `broker_accounts` has **no credentials column** | live schema | Credentials live only in device `SharedPreferences` → wipe app = lose broker link |
| No MetaCopier schema | live DB | Needs `metacopier_account_id` + copier-link storage |

---

## 2. The MetaCopier decision — this is the biggest risk to Thursday

You've asked to move execution from MetaAPI → MetaCopier.io. Here's what that actually means, from their own B2B docs:

- **Base URL:** `https://api.metacopier.io` (regional: `api-london`, `api-newyork`, `api-berlin`, `api-singapore`)
- **Auth:** API keys — project-level (all accounts) or account-scoped
- **Endpoints:**
  - `POST /rest/api/v1/accounts` — add a trading account
  - `POST /rest/api/v1/accounts/{accountId}/copiers` — create master→slave link
  - `POST /rest/api/v1/accounts/{accountId}/positions` — open position
  - `POST /rest/api/v1/accounts/{accountId}/orders` — send order
  - `GET  /rest/api/v1/accounts` / `GET .../positions`
- **Hard constraint:** *"the API cannot let your end customers sign up to MetaCopier, create their own MetaCopier projects, or add their own credit cards."* **You** own and pay for one project; every user account lives inside it, on your subscription. You build registration, auth and billing yourself — which you already have (Supabase + PayFast).

### Your three execution flows, mapped

| Flow | Path | Thursday verdict |
|---|---|---|
| **1. Mentor manual signal** | Portal → Supabase → edge fn → `POST /accounts/{masterId}/orders` → MetaCopier fans out to slaves | **In scope** — this is the rent-paying demo |
| **2. AI scanner "Execute Now"** | Android → `analyze-chart` → user taps → edge fn → `POST /accounts/{userAccountId}/positions` | **In scope, but needs P0-5 fixed first** |
| **3. Self-hosted Master EA** | Mentor's MT5 desktop = MetaCopier master; MetaCopier natively fans out. No code from us. | **In scope — zero dev work.** Config only. Highest confidence. |

### My recommendation: do not rip out MetaAPI on Wednesday

**Flow 3 requires no code.** If the goal is "prove trades reach connected accounts by Thursday," Flow 3 gets you there with configuration alone — you register the master and slave accounts in the MetaCopier dashboard and trades copy. That is your safety net and it should be validated **first**, on Tuesday.

Flows 1 and 2 need a new `metacopier-execute` edge function plus schema. That's real work but it's *additive* — a new function alongside `execute-trade-v2`, not a rewrite. Leave MetaAPI's `test-broker-connection` in place for broker validation; it works.

Ripping out MetaAPI wholesale is a Friday-or-later job. Say so out loud now rather than discovering it Thursday morning.

---

## 3. Screen status vs. your 10-screen list

| # | Screen | File | State | Work needed |
|---|---|---|---|---|
| 1 | Splash | `Theme.App.Starting` (manifest) | Exists | Verify branding renders |
| 2 | Onboarding | `OnboardingScreen.kt` (887 L) | Built | Remove simulated 1500 ms fake validation (`:760`) |
| 3 | License Gate | `LicenseVaultScreens.kt` (185 L) | Built | Wire to `validate-license`; test device binding |
| 4 | Paygate | `PaywallOverlay.kt` (258 L) | Built | Wire PayFast + verify `metahost://payment` deep link |
| 5 | Home Dashboard | `HomeScreen.kt` (1199 L) | Built | Replace stub `synchronize()` with real state |
| 5b | Robot Avatar | in `HomeScreen.kt` | Built | Visual QA |
| 5c | Robot Status Sheet | in `HomeScreen.kt` | Built | Bind to real connection state, not the boolean stub |
| 5d | Float over apps | `NovaEdgePulseService.kt` (503 L) | Built | Replace feed simulation (`:461`) |
| 6 | Quotes + risk calc | `MarketsScreen.kt`, `MarketsTabs.kt`, `PairManagementScreen.kt` | Built | Remove `// Simulate live calculation` (`:151`); symbols from robot |
| 7 | Chart Scanner + risk calc | `SymbolScannerScreen.kt` (507 L) | **Blocked** | **Implement image picker (P0-5)** — highest-value single fix |
| 8 | Broker Setup | `MetaTraderConnectScreen.kt` (522 L) | Built | De-hardcode Trade245; add MetaCopier account registration |
| 9 | Settings & Support | `SettingsScreen.kt` (575 L), `HelpSupportScreen.kt` (196 L) | Built | QA pass |

**Only one screen (7) has a functional gap. The rest is wiring, de-stubbing and QA.** That is genuinely good news for a 3-day window.

---

## 4. The plan

Times assume long days. Each block ends in a **verifiable** state — if a gate fails, cut scope (§6) rather than pushing into the next block.

### TUESDAY 2026-08-11 — Make it build, make it safe, prove copying works

| Block | Task | Done when |
|---|---|---|
| T1 | ✅ **DONE** — Gradle BOM stripped, `assembleDebug` verified green. Remaining: normalise the other 66 BOM files + add `.gitattributes` so it can't recur. | `./gradlew assembleDebug` → BUILD SUCCESSFUL ✅ |
| T2 | **Fix `signals` schema mismatch (P0-2).** One migration, one convention, applied to all 4 functions. Fix `signal_logs.license_id` uuid/text. | A test row inserts successfully from `broadcast-signal` |
| T3 | **Kill the `dev-secret-key` hole (P0-3).** Set a real `ADMIN_BROADCAST_KEY`; remove the fallback default; move the call server-side or behind mentor JWT. Remove hardcoded key from `QuickTrade.tsx`. | Broadcast with wrong/absent key → 403; portal still works |
| T4 | **Install app on real device.** Walk splash → onboarding → license gate → paygate → home. | You can reach Home on a physical phone |
| T5 | **MetaCopier Flow 3 dry run (no code).** Create project, register your MT5 master + one slave demo, place a manual trade on master. | **A trade appears on the slave account.** This is the single most important checkpoint of the week. |

> **T5 is the rent-safety checkpoint.** If Flow 3 copies on Tuesday, you have a demonstrable product even if Wednesday goes badly. Do not skip it or push it later.

### WEDNESDAY 2026-08-12 — Wire the real execution paths

| Block | Task | Done when |
|---|---|---|
| W1 | **Schema for MetaCopier:** add `metacopier_account_id` to `broker_accounts`, plus copier-link + credential storage. Move broker credentials out of `SharedPreferences`. | Migration applied; RLS verified |
| W2 | **New `metacopier-execute` edge function.** Wrap `POST /accounts/{id}/orders` and `/positions`. Store API key as a Supabase secret — never client-side. | `curl` places a real order on a demo account |
| W3 | **Flow 1 — mentor manual signal.** Point `QuickTrade` at `metacopier-execute`. Keep the `signals` row as the audit log. | Portal button → order lands on demo slave |
| W4 | **Fix P0-5 — chart scanner image picker.** `ActivityResultContracts.PickVisualMedia` → base64 → `analyze-chart`. | Screenshot in → structured signal out, on device |
| W5 | **Flow 2 — "Execute Now"** on the scanner result → `metacopier-execute`. | Scanner signal → real order |
| W6 | **De-stub:** real `MetaAPIManager.synchronize()` state, `NanoBananaService` health, remove market-feed simulation, de-hardcode Trade245. | Home shows genuine connection state |

### THURSDAY 2026-08-13 — Harden, prove, ship

| Block | Task | Done when |
|---|---|---|
| H1 | **Full end-to-end on a clean device.** Fresh install → license → pay → connect broker → receive mentor signal → order fills. | Clean-device run passes start to finish |
| H2 | **Risk calculators** on Quotes + Scanner: balance × risk% ÷ SL pips → lot size. Verify against a known-good manual calc. | Numbers match hand calculation |
| H3 | **Security sweep.** No hardcoded keys in either bundle; RLS on every table; broker credentials encrypted at rest; `.env` still untracked. | Sweep clean |
| H4 | **Release build**, signed, installed on your device + one tester's. | APK installs and runs from cold |
| H5 | **The rent demo:** record the full loop — portal broadcast → phone notification → order in MT5. | Video exists |
| H6 | Buffer. **Do not schedule work here.** | — |

---

## 5. Definition of done

Thursday is a success only if **all** of these are true:

- [ ] `./gradlew assembleRelease` succeeds; signed APK installs on a clean device
- [ ] New user: splash → onboarding → license gate → paygate → home, no crash
- [ ] License gate rejects an invalid key and binds device UUID on a valid one
- [ ] PayFast checkout completes and `metahost://payment` deep link returns to the app
- [ ] Broker connect succeeds against a **real demo account**
- [ ] **Mentor portal broadcast places a real order on that demo account** ← the product
- [ ] Chart scanner: screenshot → AI signal → "Execute Now" → real order
- [ ] Floating overlay renders over another app
- [ ] Risk calculator matches a hand calculation
- [ ] No hardcoded secrets in either shipped bundle
- [ ] `signals` / `signal_logs` populate correctly (proves P0-2 fixed)

---

## 6. Cut list — decide now, not at 2am Thursday

If you fall behind, cut **in this order**. Everything above the line still constitutes a launchable product.

1. Quotes-screen risk calculator (keep the scanner's)
2. Market feed live data (static list is fine)
3. Floating overlay polish (ship functional, not beautiful)
4. Robot avatar animation
5. Help & support content (link to WhatsApp/email instead)

— **do not cut below this line** —

6. ~~Chart scanner~~ — this is a headline feature
7. ~~Security fixes (P0-3)~~ — **never cut.** Shipping the `dev-secret-key` hole with real money attached is worse than not shipping.

---

## 7. Honest risk assessment

| Risk | Likelihood | Mitigation |
|---|---|---|
| MetaCopier onboarding (account approval, funding, API key) takes >1 day | **High** | Start Tuesday morning, before any code. It is the longest external dependency and you cannot compress it. |
| Broker demo account won't connect to MetaCopier | Medium | Test Tuesday (T5). Have a second broker ready. |
| ~~Hidden compile errors behind the BOM failure~~ | **Eliminated** | Rebuild came back green — there was nothing behind it |
| PayFast sandbox → production switch surprises | Medium | Test with a real R5 transaction Wednesday, not Thursday |
| Scope creep into iOS | Low | Already dropped. Keep it dropped. |

**My honest read:** Tue–Thu is achievable **because the screens already exist** — but only if Tuesday is spent on blockers and the MetaCopier dry run rather than on UI. The plan dies if Tuesday goes to visual polish. The most likely failure mode is not code; it is **waiting on MetaCopier account approval**. Start that today.

---

## 8. Critical questions — I need these before writing code

**Q1 — MetaCopier account status.** Do you already have a paid MetaCopier project and API key? If not, this is the critical path: everything in Flow 1 and 2 blocks on it, and account approval is outside our control. *(If the answer is "not yet" — start that signup before reading further.)*

**Q2 — Which broker/account for the Thursday test?** I need a **demo** account for the master and at least one for a slave. What broker, and is it MetaCopier-supported? We should not prove this on a live-funded account.

**Q3 — `signals` schema: migrate or rename?** Code writes `side`/`signal_id`; the table has `type` and no `signal_id`. Cleanest is a migration adding `side` and `signal_id` (keeping `type` for back-compat). The alternative is editing 4 edge functions. Migration is my recommendation — fewer places to get wrong. Your call, since it touches the live DB.

**Q4 — Existing MetaAPI subscription.** Is `METAAPI_TOKEN` still active and paid? `test-broker-connection` provisions real cloud-g2 terminals and is our only working broker-validation path. If that subscription has lapsed, broker setup breaks too and the plan changes.

**Q5 — Paygate pricing.** What is the actual price and billing period the paygate must show? The PayFast functions are deployed but I don't know what plan/amount to render.

**Q6 — Who is the mentor for the demo?** Flow 3 needs a real MT5 desktop terminal running as master. Is that your machine, and will it be on during Thursday's demo?

**Q7 — Broker credential storage.** On-device is already solid (`EncryptedSharedPreferences`, AES256-GCM). But `broker_accounts` has no credentials column, so a reinstall loses the broker link entirely. Move them server-side (encrypted) on Wednesday, or accept device-only for launch? Device-only is faster; the tradeoff is that reinstall = reconnect.

**Q8 — The `.bom-backup` files.** I left backups next to the two Gradle files I fixed. Delete them once you've confirmed the build is good, or should I keep them through Thursday?

---

## Appendix — verified environment

- **Supabase:** `MetaHost` / `epulmnfbxjmaimefhofp` / eu-west-3 / ACTIVE_HEALTHY / Postgres 17.6
- **Tables (13, all RLS on):** `profiles`, `licenses`, `plans`, `expert_advisors`, `device_activations`, `broker_accounts`, `signals`, `signal_logs`, `symbol_mappings`, `subscriptions` (1 row), `trade_logs`, `user_credits`, `itn_logs` (2 rows)
- **Edge functions (15, all ACTIVE):** `validate-license`, `generate-license`, `reactivate-license`, `send-license-email`, `manage-eas`, `analyze-chart`, `broadcast-signal`, `dispatch-signal`, `execute-trade-v2`, `test-broker-connection`, `webhook-handler`, `generate-payfast-checkout`, `payfast-webhook`, `payment-redirect`, `new-gen-fulfillment`
- **All tables empty except `subscriptions` (1) and `itn_logs` (2)** — nothing has run end-to-end yet
- **Other Supabase projects (all INACTIVE, unrelated):** `algokabs-portal`, `FlipZa`, `MirrorTrade`, `KaiLearn`
