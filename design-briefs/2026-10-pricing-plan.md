# Pricing plan: move to monthly (DRAFT, 2026-10-21)

Status: **planning only, nothing built.** Numbers marked (proposed) need Kabelo's sign-off.

## Today
Three once-off PayFast products: R599 app access, R349 AI scanner, R150 device move.
No subscription, and `generate-payfast-checkout` deliberately never sends recurring params.
The problem: a user pays ~$33 once but costs the bridge (and soon voice/AI) **every month**.

## New model

### 1. Monthly subscription (lower entry price)
| Plan | Price (proposed) | Includes |
|---|---|---|
| **NovaHost Monthly** | **R149 / month** | 1 broker account, robot hosting + auto-execution, symbol wizard, daily target lock, kill switch, Flex Cards, morning briefing, help-mode voice |
| **NovaHost Annual** | **R1,490 / year** (2 months free) | same |

Cost per user/month: MetaRPC is NOT $1 at our size. The deep dive found it's $2.79/acct at 21 accounts,
$1.93 at 200, and $1 only above ~2,080 accounts. So ≈ R35–50 bridge + ~R15–35 voice/AI ≈ **R50–85**.
Price the extra-account add-on with that in mind.

### 2. Extra broker accounts (monthly add-on)
- **R49 / month per extra account** (proposed). The user can link 2, 3, 5… accounts; each extra is a
  separate recurring line.
- At MetaRPC $1 (~R18) per account that's a ~R30 margin per extra account.
- UX: "Add another account" → shows "+R49/month" → PayFast → account slot unlocked.
  Removing (Delink) an extra account cancels its add-on at period end.
- The robot runs on every linked account; Command Core gets an account switcher.

### 3. Scanner and AI tokens (pay-as-you-go)
Chart Scan and voice "trade ideas" spend **tokens**:
| Pack (proposed) | Price | Per scan |
|---|---|---|
| Starter: 10 tokens | R49 | R4.90 |
| Trader: 40 tokens | R149 | R3.73 |
| Pro: 120 tokens | R349 | R2.91 |
- 1 chart scan = 1 token. 1 voice trade idea = 1 token. News Prediction playbooks are free
  (generated once per event, shared by all users).
- Monthly subscribers get a few free tokens each month (e.g. 5) to get them hooked.
- Tokens never expire (simpler, fewer complaints). Balance is shown on the scan screen.
- Real cost per scan is cents on paid Gemini, so packs are almost pure margin, but we MUST
  move the scanner off Gemini's free tier first (20 scans/day for the whole product).

### 4. Device move
Keep R150 once-off, or fold it into the subscription as "1 free move per year" (simpler).

## Decisions so far (2026-10-21)
- Existing R599 buyers → **free months of Monthly, then normal billing** (option B; number of
  free months TBD).
- Monthly price, extra-account price, free tokens: **open, being discussed with co-founder.**
  Figures above are proposals only.

## Existing customers (options considered)
Everyone who paid R599 once must be treated fairly. Options:
- A: **Grandfather**: lifetime access to the core robot on 1 account, forever; new features
  (voice, prop, news predictions) need the monthly plan.
- B: Convert to X free months of Monthly, then normal billing.
- C: Lifetime stays fully lifetime, including new features (generous, but costs ~R35–55/month each).

## Onboarding order flip (decided 2026-10-21)
Today: onboarding wizard → **licence key** (`/unlock`) → **payment** (`/payment`) → home.
"Key first, pay later" isn't converting. New order: onboarding → **pay first**
(choose plan → PayFast) → **then enter / activate the licence key** → home.
Watch-outs when building:
- `generate-payfast-checkout` (v34) and `payfast-webhook` (v31) currently **require** a
  licence key on the checkout and store it on `subscriptions.license_key`. Paying first
  means checkout must work keyless (bind by email + device), and the key is attached afterwards.
- Affiliate attribution rides on the key (`custom_str4`). With no key at checkout,
  attribution has to come from somewhere else (referral code at checkout, or bind it when the key
  is entered). This needs deciding.
- Android and iOS both change. The route guard must allow "paid, no key yet" as a state that
  lands on the key screen, not on the paywall again.
- Needs a Claude Design pass: paywall-first screen showing the plans (ties into monthly
  pricing above).

## Technical notes (for later)
- PayFast supports recurring billing (`subscription_type=1`, frequency + cycles) and
  ad-hoc tokenised charges. The ITN webhook must handle renewals, failed payments and
  cancellations, and a `subscriptions` row needs `current_period_end` + status.
- Entitlement stays server-side in `check-subscription-status` (fail closed). Add
  `account_slots` and `token_balance` to what it returns.
- Token ledger table (append-only: purchase +N, scan −1, refund +1 on failed scan), so a
  failed scan never eats a token.
- Affiliate commissions currently key off once-off sales. Decide whether commission applies
  to the first month only or is recurring.
