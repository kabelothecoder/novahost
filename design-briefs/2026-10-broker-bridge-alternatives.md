# Broker bridge alternatives to MetaCopier

Research date: 2026-10-21 (two rounds; round 2 went looking specifically for something **cheaper**
than MetaCopier). This was read-only research: no code was changed and the live MetaCopier project
was not touched. Prices are vendor list prices, excluding VAT. "Per month" means 30 days / 720 h
(MetaCopier, MetaRPC) or 730 h (MetaApi). € prices are left in euros.

## Why this matters

Since 2026-10-04 MetaCopier has refused **1,514** connect attempts with
`ACCOUNT_LIMIT_PER_PROJECT_REACHED_TRIAL (allowed=10, needed=160, funding=75.00)`. New users
cannot link a broker. Fill rate is 35% on the handset path and 16% server-side, against a 95%
goal (`2026-10-trade-rejection-report.md`). MetaCopier also costs **$8.10 per account per month
with no volume discount**, which the owner considers too expensive.

Changing the bridge fixes the **connect wall**, the **price**, and (depending on vendor) the
**generic `[BROKER_REJECTION]`** rows. NO_SIZE_CONFIGURED, POSITION_CAP and margin refusals are
our own logic and stay the same whichever provider we use.

## Headline

Two vendors found in round 2 are **4–6× cheaper than MetaCopier** and fit our use case:

| | MetaCopier today | **TickerAll** | **MetaRPC** |
|---|---|---|---|
| Per account per month at 200 accounts | $8.10 | **≈ $1.90** | **≈ $1.93** |
| 200 accounts, per month | $1,620 | **$379** | **≈ $386** |

Both are small vendors, so the recommendation is a cheap one-week **bake-off on real Exness, XM
and IFX accounts** before choosing, not a straight switch. Details below.

> **Update 2026-10-21: the owner has chosen MetaRPC.** See [MetaRPC deep dive](#metarpc-deep-dive)
> at the end for verified pricing, API fit, risks, the migration mapping and the bake-off plan.

## Options considered

| # | Option | What it is |
|---|---|---|
| A | **MetaCopier** (baseline) | What we use. A copier platform whose REST API we drive directly. |
| B | **MetaApi.cloud** | Largest vendor. Hosted MT4/MT5 terminals behind REST + WebSocket. The codebase used it before. |
| C | **mtapi.io / mtapi.online** | Hosted "Cloud API" that speaks the MT protocol with no terminal. Sold in account bundles. |
| D | **MetaTrader API** (metatraderapi.net, formerly api2trade) | Hosted per-seat REST API. |
| E | **Self-hosted MT5 fleet** | Our own servers running MT5 terminals under Wine, with a bridge EA or the Python `MetaTrader5` package exposed over HTTP. |
| **F** | **MetaRPC** (mrpc.pro) — *new in round 2* | Hosts the **real** MT4/MT5 terminals for you (Falkenstein, Germany), REST + WebSocket + gRPC. Prepaid, billed per minute of connection, with volume tiers. Same operator contact (Telegram `@timurila`) as mtapi.io, i.e. the successor/sibling of option C. |
| **G** | **TickerAll** (tickerall.com) — *new in round 2* | Holds a direct protocol connection to the broker **presenting as the MT5/MT4 mobile app** (no terminal). REST + WebSocket, TypeScript/Python SDKs, the best documentation of the lot. |
| H | **FxSocket** (fxsocket.com) — *new in round 2* | One real terminal per account in Kubernetes. Clean API, but €8–12 per account, so not cheaper. |
| — | Copier products (Duplikium, TradersConnect, Social Trader Tools) | **Ruled out.** They are consumer copiers, not developer order APIs, and cost $6–15 per account. |
| — | MTsocketAPI | **Not a hosted service.** A licence (€419/month for unlimited accounts) for an API you install in your own terminals. Only relevant if we pick E. |
| — | cTrader Open API / TradeLocker / Match-Trader | **Ruled out as the main bridge.** They only reach brokers on those platforms; our users are on MT4/MT5 brokers. (MetaRPC also covers cTrader and TradeLocker at half price if we ever want them.) |

## Cost per month

| Linked accounts | A: MetaCopier | B: MetaApi, all deployed / 30% deployed | **F: MetaRPC** | **G: TickerAll** | C: mtapi.io | H: FxSocket | D: MetaTrader API | E: Self-hosted (estimate) |
|---|---|---|---|---|---|---|---|---|
| **50** | $405 | $490 / $195 | **≈ $117** | **$79** | $500 | €549 | $623 | $60–200 infra |
| **200** | $1,620 | $1,870 / $689 | **≈ $386** | **$379** | $1,500 | ≈ €1,669 | ≈ $2,420 | $150–500 infra |
| **1,000** | $8,100 | $9,228 / $3,326 | **≈ $1,510** | Enterprise quote (Pro caps at 300) | $5,000 | ≈ €8,069 | ≈ $12,000 | $800–2,500 infra + an engineer |

How each column is worked out:

- **A, MetaCopier:** $0.27 per account-day, no volume discount; disconnected accounts billed until
  deleted. Dedicated projects run $0.32 → $0.22 per account-day in blocks of 25
  ([billing docs](https://docs.metacopier.io/metacopier/billing)).
- **B, MetaApi:** from MetaApi's live rate feed (effective 2026-01-10). G2 high reliability is
  $0.0126/h ($9.20/month); a parked (undeployed) account is $0.00105/h ($0.77/month); plus $30/month
  subscription and $2.10 per account added. "30% deployed" parks licences without auto-execute.
- **F, MetaRPC** ([mrpc.pro](https://mrpc.pro/#pricing)): $4.00 per terminal per 720 h, and each
  hour is charged at the tier your **month-to-date account-hours** have reached: $3.00 from 2,000,
  $2.00 from 10,000, $1.50 from 100,000, $1.25 from 500,000, $1.00 from 1,500,000 account-hours.
  My figures assume every account connected 24/7: 50 accounts ≈ $2.33 each, 200 ≈ $1.93, 1,000 ≈
  $1.51. Billed per minute **only while connected**; prepaid balance (card, wire or crypto, $1
  minimum top-up). **At zero balance all connections are suspended**, so we would need auto top-up
  and a low-balance alert.
- **G, TickerAll** ([pricing](https://tickerall.com/pricing)): Free = demo accounts only. Trader =
  $3.99 per live account (1–10) plus $0.99 per "always-hot" connection. **Pro = $39/month for 30
  live accounts + $2 per extra account, always-hot included, cap 300 live accounts**; above that,
  Enterprise (custom quote). Reads are never metered; demo orders are free.
- **C, mtapi.io:** bundles of 100 / 500 / 2,500 accounts at $500 / $1,500 / $5,000.
- **H, FxSocket:** Starter €12 per account; Pro €549 including 60 accounts, then €9 each; Premium
  €949 including 110, then €8 each. Its site showed "20 / 32 accounts active", which suggests very
  small scale.
- **D, MetaTrader API:** $14 / $13 / $12 per seat; above 50 seats "on request".
- **E, self-hosted (my estimate):** FxSocket's engineers report each MT terminal using **300–500 MB
  steady state** (they reserve 1 GiB). So about 80 terminals per 64 GB dedicated box. Server prices
  moved a lot in 2026 (quotes for a Hetzner AX42 range from €47 to €187/month), hence the wide
  range. Excludes 4–6 weeks of build and keeping it running. **MetaRPC already sells managed
  terminals at $1.25–1.50 each at scale, so self-hosting no longer saves money.**

## Capabilities

| | A: MetaCopier | B: MetaApi | **F: MetaRPC** | **G: TickerAll** |
|---|---|---|---|---|
| How it connects | Its own infrastructure | Real terminal in their cloud | **Real MT4/MT5 terminal** in their cloud, plus an EA that forwards requests | **No terminal**: a direct protocol session that presents as the MT5 *mobile* app (WEB/desktop modes on Pro) |
| MT4 + MT5 | Yes | Yes | Yes (also cTrader, TradeLocker at 50%) | Yes |
| Server coverage | Own catalogue only; **XMGlobal-MT5 20 missing** | Finds servers by name; missing ones can be added from `servers.dat` | Whatever the real terminal can log into; we pass the server name to `ConnectEx` | Resolves server names itself; unknown names return `BROKER_NOT_FOUND`. Doc examples use Exness servers. |
| Order with SL/TP | `POST /accounts/{id}/positions` | `POST …/trade` | `GET /OrderSend?symbol&operation&volume…` (SL/TP params; GET-style API) | `POST /v1/accounts/{id}/orders` with `stopLoss`, `takeProfit`; **required `Idempotency-Key`** so a retry never double-fires |
| Fan-out to many accounts | One call per account | One call per account | One call per account (copier product separate) | **`POST /v1/bulk/orders`**: one call, per-account results (Pro) |
| Symbols | `/symbols` | `/symbols`, `/specification` | `Symbols`, `SymbolParams` | `GET /v1/accounts/{id}/symbols` (broker-native names), `/symbol-specs` |
| Account info | `/information` | balance, equity, freeMargin, currency | `AccountSummary` | `GET /v1/accounts/{id}`: balance, equity, margin, **freeMargin**, marginLevel, **currency**, leverage, plus open positions |
| How refusals arrive | Bracketed tokens in free text; **56 of ours have no detail** | MT5 retcode as data (`numericCode`, `stringCode`) | **MT retcode as data** (`returnedCode: 10009`, `returnedStringCode: "TRADE_RETCODE_DONE"`) | Stable codes: `BROKER_AUTH_FAILED`, `BROKER_NOT_FOUND`, `BROKER_ACCOUNT_INVALID`, `BROKER_UNREACHABLE`; orders fail as `422 BROKER_REJECTED` **with the broker's reason in `message`** ("Not enough money", "Invalid stops"), not a numeric retcode |
| Latency | No published figure | Median 6 ms added (vendor) | Not published; single region (Germany) | Not published ("low-latency") |
| Account cap / trial wall | **The current blocker** | None; quotas scale with deployed accounts | None; prepaid balance | 300 live accounts on Pro, then Enterprise. Free tier is demo-only. |
| Broker password | We send it once; MetaCopier keeps the session | MetaApi stores it | Sent with `ConnectEx`; "managed terminal recovery" claimed | **Held in memory only**, so a restart or a "cooled" connection needs it again, **unless** Pro's opt-in "remembered credentials" stores it encrypted |
| Vendor risk | Small vendor | Largest, SDKs in 4 languages | Small operation (same Telegram contact as mtapi.io); claims a decade in MetaTrader APIs | **Young** (doc examples dated May 2026), operator and location not stated. Protocol emulation of the mobile app could break on a MetaQuotes change, or be blocked by a broker. |

## Recommendation

1. **Shortlist TickerAll (G) and MetaRPC (F) and run a one-week bake-off.** It costs almost
   nothing: TickerAll is free on demo and $3.99 per live account; MetaRPC needs no card and a $1
   minimum top-up. Connect the **same** real accounts on both:
   - one Exness MT5, one XM including an **`XMGlobal-MT5 20`** login, one IFX;
   - one MT4 account if any user still runs MT4.

   Score each on: did it connect, how long did it take, does it stay connected for a week, does
   an order with SL/TP fill, and what text comes back on a deliberate refusal (too-big lot, stops
   too tight). Pick the winner on reliability, not the price difference, which is small.
2. **Lean towards TickerAll if it connects reliably.** It is the cheapest at our size ($79 at 50
   accounts), its error codes map almost one-to-one onto ours, it has bulk orders that suit our
   signal fan-out, and it requires idempotency keys (which closes the double-order risk on
   retries). Its weak points are age, the 300-account Pro cap and the mobile-app emulation.
3. **MetaRPC is the safer cheap choice if TickerAll is flaky.** It runs the real terminal, so
   anything a user can log into on their PC works, and refusals come back as raw MT retcodes. Its
   weak points are the prepaid balance (zero balance = every user stops trading), a single region,
   and a thinner GET-style API.
4. **Keep MetaApi (B) as the premium fallback** if both cheap vendors fail the bake-off. It is the
   most established, and with dormant licences parked it costs about 40% of MetaCopier.
5. **Don't choose C, D, E or H.** C (mtapi.io) is superseded by MetaRPC from the same operator. D
   and H cost as much as MetaCopier or more. E (self-hosting) now costs more than MetaRPC's managed
   terminals.
6. **Bridging the gap:** the bake-off plus adapter is about 2–3 weeks. If leaving new signups
   blocked for that long is worse than the money, funding MetaCopier for one month at today's ~21
   accounts costs about $170 and needs no code. That is the owner's call.

**Before committing:** the codebase used MetaApi before moving to MetaCopier, and git doesn't say
why. Ask the owner, because the same reason (cost, deploy delays, a broker that wouldn't connect)
may apply to whichever vendor wins.

## Decision the owner needs to make: broker passwords

Today we **never store broker passwords**; they go out of scope after `metacopier-connect`. Both
cheap vendors may need the password again later:

- **TickerAll** keeps it in memory only. After a restart or a cooled connection it needs it again,
  unless we use Pro's **remembered credentials** (TickerAll stores it encrypted).
- **MetaRPC** needs it on `ConnectEx`. If we disconnect dormant accounts to save money, reconnecting
  needs it again.

Options: (a) let the vendor store it (TickerAll Pro), (b) store it ourselves encrypted in Supabase
Vault, or (c) ask the user to re-enter it when a connection drops (more `ACCOUNT_DISCONNECTED`
refusals, which already make up about half of handset refusals). (a) is the simplest.

## Rough migration plan

### What exists today

- **18 edge functions** call `api.metacopier.io` directly with `fetch`:
  `metacopier-connect`, `metacopier-execute`, `broker-account`, `broker-symbols`, `broker-quote`,
  `broker-positions`, `broker-history`, `broker-performance`, `broker-diagnostics`,
  `auto-execute-signals`, `claim-signals`, `sync-symbol-config`, `license-status`,
  `set-auto-execute`, `admin-reclaim-orphans`, `generate-license`, `broadcast-signal`,
  `test-broker-connection`.
- The link lives on the licence as `metadata.metacopier_account_id`, `metacopier_status` and
  `metacopier_connected_verified_at`.
- **The apps only call our edge functions by name.** They never call MetaCopier directly, so the
  switch can be made **entirely on the server, with no app release**, as long as response shapes
  and our error codes (`ACCOUNT_DISCONNECTED`, `ACCOUNT_WRONG_CREDENTIALS`, `SERVER_NOT_SUPPORTED`,
  `INSUFFICIENT_MARGIN`, …) stay the same.
- Because we don't store passwords, existing MetaCopier users **can't be moved silently**. New
  links go to the new provider; existing users move when they next reconnect, or we ask them to
  re-link once (which also cuts the MetaCopier bill to zero sooner).

### Phases

| Phase | Work | Rough effort |
|---|---|---|
| 0. Bake-off | Accounts on TickerAll and MetaRPC; connect the test accounts listed above; run for a week; record results. Ask the owner why MetaApi was dropped, and decide the password question. | 1 day of work, 1 week elapsed |
| 1. Bridge interface | `supabase/functions/_shared/bridge/` with one interface: `connect`, `listServers`, `accountInfo`, `symbols`, `symbolSpec`, `quote`, `placeOrder`, `positions`, `history`, `disconnect`. Move the MetaCopier calls into a `metacopier.ts` adapter **without changing behaviour**. Add `metadata.bridge_provider` (default `'metacopier'`) and `metadata.bridge_account_id`, falling back to `metacopier_account_id`. Can run in parallel with phase 0. | 4–6 days |
| 2. Winner's adapter | e.g. `tickerall.ts`: map `BROKER_AUTH_FAILED`→`ACCOUNT_WRONG_CREDENTIALS`, `BROKER_NOT_FOUND`→`SERVER_NOT_SUPPORTED`, `BROKER_ACCOUNT_INVALID`→`ACCOUNT_DISABLED`, `BROKER_ACCOUNT_NOT_HOT`→reconnect then retry once, `BROKER_UNREACHABLE`→`ACCOUNT_DISCONNECTED`, and parse `BROKER_REJECTED` messages into `INSUFFICIENT_MARGIN` / `INVALID_STOPS` / `MARKET_CLOSED` / `INVALID_VOLUME`. Send the signal id as the `Idempotency-Key`. For MetaRPC, map the MT retcodes directly (`10019` no money, `10016` invalid stops, `10018` market closed, `10014` invalid volume, `10004`/`10015` requote → retry once at market). | 4–6 days |
| 3. Connection upkeep | Keep auto-execute accounts always connected; reconnect on cooled/dropped connections before a signal arrives. For MetaRPC also: auto top-up and a low-balance alert. | 2–3 days |
| 4. Pilot | Route **new** connects for a flag-listed set of licences to the new provider; compare fill rate and refusal mix against MetaCopier for 1–2 weeks with the queries from the rejection report. | 1–2 weeks elapsed |
| 5. Default | New links default to the new provider. Ask existing users to re-link once; delete their MetaCopier accounts as they move (MetaCopier bills disconnected accounts until deleted). | — |

Total: roughly **2–3 weeks of build** plus a 1–2 week pilot. Phase 1 is worth doing whichever
vendor wins, because it is what lets us change provider again if a small vendor disappears.

## Not verified

- No vendor has been tested against real Exness, XM or IFX accounts yet; that is what the bake-off
  is for.
- TickerAll and MetaRPC prices are from their own pricing pages on 2026-10-21. MetaRPC's per-tier
  totals are my arithmetic from its published tier rules.
- TickerAll's operator and location are not published. MetaRPC's link to mtapi.io is inferred from
  the shared Telegram contact (`@timurila`) and the identical API style.
- Whether brokers' terms allow a third party to hold a mobile-app-style session (TickerAll) is not
  checked.
- No vendor publishes latency except MetaApi; none was measured.
- Self-hosting costs are estimates; server prices varied 4× across 2026 sources.

## MetaRPC deep dive

Researched 2026-10-21, read-only: no signup, no test connections. Sources:
[pricing](https://mrpc.pro/#pricing), [Terms of Service](https://mrpc.pro/terms) (updated
2026-10-20), [Privacy Policy](https://mrpc.pro/privacy), [contacts](https://mrpc.pro/contacts),
[developer hub](https://mrpc.pro/sandbox-apis), the
[integration guide for AI agents](https://mrpc.pro/metarpc-api-for-ai-agents.md) (the most complete
single reference), and the OpenAPI files for [MT5](https://mrpc.pro/openapi/mt5.json) (70 endpoints)
and [MT4](https://mrpc.pro/openapi/mt4.json) (48 endpoints). Anything marked **unverified** needs the
bake-off.

### Verdict

MetaRPC can do everything NovaHost needs. It costs **$2.79 per account at today's 21 accounts and
$1.51 at 1,000**, against MetaCopier's flat $8.10, and it has no account-count trial wall. It runs a
**real MT4/MT5 terminal per account**, so broker coverage should be as good as a user's own PC, and
broker refusals come back as structured MetaTrader codes.

The risks are about **the company, not the API**:
- It is effectively a one-person operation with no named legal entity.
- It is prepaid, and every user stops trading if the balance hits zero.
- Its Terms forbid reselling access without written consent, and NovaHost's model arguably is that.
  **Get written consent before going live.**

### Head-to-head with MetaCopier

| | MetaCopier (today) | MetaRPC |
|---|---|---|
| Price per account per month | $8.10 flat ($0.27/day), no volume discount | $4.00 falling to $1.00 with volume (see Q1); **$2.79 at 21 accounts, $1.93 at 200** |
| Account cap / trial wall | 10 accounts until funded, then a 100 soft cap needing support | **None.** No limits per API key; any number of terminals |
| How it connects | Its own infrastructure | One real MT4/MT5 terminal per account, in Hetzner Falkenstein, Germany |
| Refusals | Bracketed tokens in free text; 56 of ours have no detail | `returnedCode` / `returnedStringCode` (MT5 retcode), plus `mqlErrorTradeCode` and a stable `errorCode` |
| Billing failure | Invoice; account blocked after about 12 days unpaid | **Prepaid; terminals stop immediately at zero balance** |
| Company | Swiss-billed company (USD/EUR/CHF invoices) | No named legal entity; virtual-office addresses (see Q6) |

### Q1. Pricing as actually offered

**Is it $1 per account per month? Only at very large volume.** The rates come from Terms §4 and the
pricing page.

- **Billing unit.** A terminal is billed **per minute while it is connected**, at the rate of the
  tier your **month-to-date account-hours** across all terminals have reached. Each tier applies
  to usage from then on, not retroactively. The count resets at 00:00 UTC on the 1st.
- **Tiers per 720 hours** (one month of continuous running):

  | Month-to-date account-hours | Price per 720 h |
  |---|---|
  | 0+ | $4.00 |
  | 2,000+ | $3.00 |
  | 10,000+ | $2.00 |
  | 100,000+ | $1.50 |
  | 500,000+ | $1.25 |
  | 1,500,000+ | **$1.00** |

  1,500,000 account-hours means **about 2,080 accounts connected all month**, and even then only
  the hours above that line cost $1.00.
- **What that comes to for us** (my arithmetic, accounts connected 24/7 for 720 h):

  | Accounts | MetaRPC per month | Average per account | MetaCopier |
  |---|---|---|---|
  | 1 | $4 | $4.00 | $8 |
  | 21 (today) | **$59** | $2.79 | $170 |
  | 50 | $117 | $2.33 | $405 |
  | 100 | $217 | $2.17 | $810 |
  | 200 | $386 | $1.93 | $1,620 |
  | 500 | $836 | $1.67 | $4,050 |
  | 1,000 | $1,510 | $1.51 | $8,100 |
  | 2,500 | $3,281 | $1.31 | $20,250 |

  A 31-day month (744 h) is about 3% more.
- **Included:** every API call (REST, WebSocket, gRPC), streams, history, the OpenAPI console and
  the SDKs. **API calls are not metered.** There are no region charges, because there is only one
  region.
- **Costs extra:**
  - **1-hour minimum per terminal launch.** A terminal that runs 1–59 minutes is billed a full
    hour (Terms §4).
  - **A second terminal on the same account is billed separately.** That is only needed for extra
    throughput.
  - Trade copier: $1 per 720 h. We don't need it.
  - VPS: $4 per vCPU, $4 per 2 GB RAM and $4 per 32 GB SSD per month. We don't need it.
- **Parked accounts:** **there is no parked state.** `Disconnect` stops the meter, but
  reconnecting needs the broker password again (see Q3). So in practice an account is either
  connected and paid for, or gone. Compare MetaApi's $0.77 a month for a parked account.
- **Minimums and contracts:** no contract and no monthly minimum. The minimum top-up is $1.
  Payment is by card (Stripe), crypto or wire. **Deposits and accrued charges are
  non-refundable** (Terms §4).
- **Trial:** free signup with no card. The public key `TRIAL` runs terminals that live **at most
  15 minutes**. "Any welcome credit is shown in your dashboard"; the amount is **unverified**.
  There is **no account-count cap like MetaCopier's 10**.

### Q2. Broker coverage

- **MT4 and MT5:** both, with separate hosts (`mt4.mrpc.pro`, `mt5.mrpc.pro`). cTrader and
  TradeLocker are also available at half price.
- **How servers are resolved:**
  - `ConnectEx?user&password&mtClusterName=<server name exactly as MetaTrader shows it>` connects
    by server name. An unknown name returns `BROKER_SERVER_NOT_FOUND`.
  - `Connect?user&password&host&port` connects by **IP or DNS name and port**. That is how a
    server missing from their directory can be added: we supply the access point ourselves.
    MetaCopier has no equivalent, and this is the fix for the `XMGlobal-MT5 20` class of failure.
  - `DemoAccount/FindCompanies?searchText=` and `GetBrokerServersByBrokerName` return
    companies → servers → access points. **Both need an `id` header**, i.e. a terminal that is
    already connected, so a server picker needs one always-on helper terminal (about $3–4 a
    month).
- **Our brokers:**

  | Broker | Platforms | Status |
  |---|---|---|
  | Exness | MT4 + MT5 | Exness and XM logos appear on the homepage. **No broker list is published, so coverage is unverified.** |
  | XM, including `XMGlobal-MT5 20` | MT4 + MT5 | Logo on homepage. If `ConnectEx` can't resolve server 20, `Connect` by host and port can. **Unverified.** |
  | IFX | MT5 | **Unverified.** Symbols pass through in the broker's own spelling, so `SymbolList` returns `XAUUSD.ifx` as-is and our `symbol_map` logic carries over. |
  | Deriv, Trade245, FBS, HFM | MT5 (FBS and HFM also MT4) | **Unverified.** A real terminal can reach any server the desktop app can, so I'd expect them to work, but this must be tested. |

### Q3. Can our users reliably receive trades?

- **Market orders with SL/TP:** `OrderSend?symbol&operation=TMT5_ORDER_TYPE_BUY&volume&stopLoss&takeProfit&slippage&expertId&comment`.
  MT4 uses `operationType`, `stoploss`, `takeprofit` and `magicNumber`.
- **Pending orders:** buy/sell limit, buy/sell stop and stop-limit (`stopLimitPrice`), with
  `price`.
  - **Expiry** uses `expirationTimeType` (`GTC`, `DAY`, `SPECIFIED`, `SPECIFIED_DAY`) plus an
    ISO `expirationTime`.
  - Our `pending_expiry_seconds` maps to `SPECIFIED` with now + seconds.
  - The broker must allow that expiry mode, which `SymbolParamsMany.expirationMode` shows.
- **Pre-trade check:** `OrderCheck` (margin and validity, without sending) and `OrderCalcMargin`.
  These feed the margin check and the shrink-to-fit plan in the rejection report.
- **Latency:**
  - **Not published.** Terms §10 says it "depends partially on external internet routing and
    broker server responsiveness".
  - Each terminal has **2 parallel workers**. Timeouts are 15 s for account and position reads,
    30 s for everything else including orders, and 120 s by default for connect.
  - All servers are in Falkenstein, Germany (confirmed: `mt5.mrpc.pro` resolves to Hetzner,
    Falkenstein).
  - **Measure this in the bake-off.**
- **Uptime and SLA:** "We strive for 99.9% monthly uptime" (Terms §10). There are no service
  credits, and liability is capped at **fees paid in the last 3 months** (Terms §9). It is a
  best-effort target, not an SLA.
- **How broker refusals come back:**
  - Every reply is HTTP 200 with either `data` or `error`.
  - A trade result carries `returnedCode` and `returnedStringCode`. For example, `10009
    TRADE_RETCODE_DONE` means placed; `10019 NO_MONEY`, `10016 INVALID_STOPS`, `10018
    MARKET_CLOSED`, `10014 INVALID_VOLUME` and `10026` (autotrading disabled by server) are all
    machine-readable.
  - API failures have a stable `errorCode` plus `mqlErrorTradeCode` / `mqlErrorTradeDescription`
    and `properties` such as `RetryAfterSeconds`.
  - **This is the direct fix for our 56 detail-less `[BROKER_REJECTION]` rows.**
- **Reconnect behaviour:**
  - `CheckConnect` and `ConnectState` (`isConnected`, `isAlive`, `apiIsAlive`, `errorMessage`)
    report the connection, and the `OnConnectState` stream pushes changes.
  - `Reconnect?forceReconnection` "recreate[s the] terminal instance with the same id", with no
    password parameter.
  - The privacy policy says broker passwords are "encrypted at rest with AES-256 and held only in
    volatile container memory".
  - So **while a terminal exists, MetaRPC can recover it without us storing the password**. After
    `Disconnect`, we need the password again. Whether recovery survives a host restart or a broker
    password change is **unverified**.
- **Investor (read-only) password:**
  - It connects, but `AccountInfoInteger?propertyId=ACCOUNT_TRADE_ALLOWED` is false. Check this
    at link time and refuse with our existing `ACCOUNT_READ_ONLY`, instead of discovering it on
    the first signal.
- **"Autotrading disabled":**
  - The client-side switch is MetaRPC's job, because they run the terminal and the EA bridge.
  - The server-side case is detectable before a signal, via `ACCOUNT_TRADE_EXPERT` = false.
  - It also comes back as retcode `10026` if we skip the check.
  - Our 10 `AUTOTRADING_DISABLED` refusals were really wrong symbol names, which `SymbolList`
    resolves.
- **Gotchas that affect fill rate:**
  - **A timed-out order can still execute.** The docs say to reconcile before resending. Our
    magic number is fixed per robot (`MAGIC_PREFIX 77` + robot id), so reconcile on magic +
    symbol + side + volume + time window, and keep at most one order in flight per account.
  - **At most 5 orders queued per terminal** (`TOO_MANY_ORDERS_IN_QUEUE`, and that order is never
    executed). This matters for close-all, not for single signals.
  - **The first order on a new symbol waits for a quote**, up to `quotesWaitMs` (default 5 s).
    Pre-subscribe the robot's symbols.

### Q4. API coverage of our features

| We need | MetaRPC MT5 | MetaRPC MT4 | Notes |
|---|---|---|---|
| Symbol list | `SymbolList`, `SymbolParamsMany` | `Symbols` | `SymbolParamsMany` gives `volumeMin`, `volumeStep`, `volumeMax`, `tradeStopsLevel`, `tradeMode`, `expirationMode`, `fillingMode`: everything the lot-step and stops logic needs. Market Watch holds at most 50 symbols. |
| Balance, equity, currency | `AccountSummary` (`accountBalance`, `accountEquity`, `accountCurrency`, `accountCredit`, `accountLeverage`) | `AccountSummary` | — |
| Free margin | `AccountInfoDouble?propertyId=ACCOUNT_MARGIN_FREE` (also `ACCOUNT_MARGIN`, `ACCOUNT_MARGIN_LEVEL`) | in `AccountSummary` / account info, **unverified** | **Not in MT5 `AccountSummary`**, so `broker-account` needs 2–3 calls. |
| Open positions and pending orders | `OpenedOrders` (`positionInfos`, `openedOrders`), `OnTrade` stream | `OpenedOrders` | — |
| Close-all (voice kill switch) | **No single endpoint.** `OpenedOrders`, then `OrderClose?ticket` per position, then delete pending orders the same way | `OrderCloseDelete`, `OrderCloseBy` | With 5 queued and 2 workers per terminal, send in batches of up to 5 and re-read `OpenedOrders` until empty. 10 positions ≈ 2–3 rounds. |
| History (Flex Cards, daily target lock) | `PositionsHistory` (open/close time, prices, SL/TP, profit, commission, swap, fee, symbol, **magic**), `OrderHistory`, `TradeStats`, `EquityHistory` | `OrdersHistory`, `TradeStats` | Paged. `magic` keeps "ours vs manual" possible. **Today's P/L for the target lock = `PositionsHistory` since broker midnight + `TradeUnrealizedPL`.** Times are broker time with `…Utc` siblings. |
| Live quote | `SymbolInfoTick`, `OnSymbolTick` stream | `Quote`, `QuoteMany` | — |

### Q5. Multi-account

- **Yes, and it gets cheaper per account.** Each linked account is its own terminal, billed at
  the shared month-to-date tier, so every extra account lowers the rate for all of them.
- **What an extra account costs us:** **$3.00 → $2.00 → $1.50 → $1.25** a month as volume grows
  ($2.00 at today's size).
- **Suggested add-on price:** any add-on of about **$5 / R90 a month or more** has a healthy
  margin. Pricing is the owner's call.
- One API key covers every user, and there are no per-key or per-IP limits.

### Q6. Company risk

| | Finding | Source |
|---|---|---|
| Age | `mrpc.pro` was registered **2024-04-09** and its GitHub (`MetaRPC`) was created **2025-04-14**. The same operator ran `mtapi.online` (registered **2017-12-20**) and `mtapi.io` (**2022-09-02**). "Over a decade" is a marketing claim; about 8 years of continuous MetaTrader-API business is supported by the evidence. | RDAP lookup; GitHub API |
| Operator | Telegram `@timurila` is listed as the "engineering and operations lead", the same contact as mtapi.io. NuGet and Docker packages are published under `timurila`. It looks like **one engineer's business**. | [contacts](https://mrpc.pro/contacts); NuGet / Docker Hub |
| Legal entity | **None named.** The Terms are governed by "applicable international commercial law". The addresses are 1309 Coffeen Ave STE 14739, Sheridan WY (a well-known registered-agent / virtual-office address), 111 Fulham Palace Road, London, and Koh Phangan, Thailand. Domains are registered through Russian registrars (REG.RU, RU-CENTER). | Terms §12; contacts; RDAP |
| Reviews | **None found** for mrpc.pro or mtapi.io on Trustpilot or forums. The GitHub account has 25 repos and 4 followers. | searches 2026-10-21 |
| Support | Telegram `@mrpcio` / `@timurila`, and `admin@mrpc.pro`. No ticketing system and no stated response time. | Terms §14 |
| Credential security | Broker passwords are "encrypted at rest with AES-256 and held only in volatile container memory". TLS 1.3. Data deleted within 90 days of account closure. **But the REST `ConnectEx` / `Connect` calls carry the broker password in the URL query string** (GET only, per the OpenAPI file). HTTPS protects it in transit, but URLs end up in server and proxy logs. gRPC sends it in the message body instead. | Privacy §4, §6, §7; OpenAPI |
| Hosting | Hetzner, Falkenstein, Germany. One region and one provider; no failover is described. | contacts; DNS + ipinfo |
| If they shut down | Terminals stop, and **every NovaHost user stops receiving trades at once**. Nothing at the broker is affected: positions stay open with their SL/TP. Balance refunds are not promised (deposits are non-refundable), and there is no data-export or notice clause. | Terms §4, §11 |
| **Resale clause** | The Acceptable Use Policy forbids users to "**Resell, redistribute, or sublicense access to the Service without prior written consent**". NovaHost charges users for trading that runs through MetaRPC. MetaRPC markets to prop firms and brokers, so consent is probably obtainable, but **get it in writing before launch**. | Terms §5 |
| Prepaid cutoff | "If your balance is exhausted, running terminals are gracefully stopped." That is a platform-wide outage we would cause ourselves. Needs auto top-up plus an alert at, say, 10 days of runway. | Terms §4 |

**Mitigations:**
1. Build the provider-neutral bridge layer (phase 1 of the migration plan), so MetaApi can be
   switched on if MetaRPC disappears.
2. Keep a funded MetaApi account with a test terminal as a warm standby.
3. Add an auto top-up and runway alert.
4. Get written resale consent and a named contracting entity from `admin@mrpc.pro` before launch.

### Q7a. Migration mapping

The apps call our edge functions by name, so the function names and response shapes stay as they
are and **no app release is needed**. Store the MetaRPC terminal id as `metadata.bridge_account_id`
with `bridge_provider = 'metarpc'`.

| Our function / step | MetaCopier today | MetaRPC |
|---|---|---|
| `metacopier-connect`: server list | `GET /brokers/{mt4|mt5}/all` | `DemoAccount/FindCompanies` → `GetBrokerServersByBrokerName` via an always-on helper terminal; cache the results in a table |
| `metacopier-connect`: link | `POST /accounts`, then poll its state | `ConnectEx` (MT5 or MT4 host, `timeoutSeconds` about 60, `name` = licence id, `expiration=0`). Fall back to `Connect host+port` if `BROKER_SERVER_NOT_FOUND` and we know the access point. Then `AccountInfoInteger ACCOUNT_TRADE_ALLOWED` / `ACCOUNT_TRADE_EXPERT` to catch investor passwords and server-disabled autotrading at link time. |
| `metacopier-connect`: unlink / reclaim | `DELETE /accounts/{id}` | `Disconnect` (the meter stops) |
| `metacopier-execute` | `POST /accounts/{id}/positions` (`stopLoss`, `takeProfit`, `pendingExpirySeconds`) | `OrderSend` with `expertId` = our magic; `expirationTimeType=SPECIFIED` + `expirationTime` for pending orders; branch on `returnedCode`. On `COMMAND_TIMEOUT_EXCEEDED`, reconcile via `OpenedOrders` / `PositionsHistory` before any retry. On a dropped connection, `Reconnect` and retry once. |
| `broker-account` | `/information` | `AccountSummary` + `AccountInfoDouble ACCOUNT_MARGIN_FREE` (+ `ACCOUNT_MARGIN`), keeping `currency` with the figure |
| `broker-symbols`, `sync-symbol-config` | `/symbols` | `SymbolList` + `SymbolParamsMany` |
| `broker-quote` | `/quote/{symbol}` | `SymbolInfoTick` |
| `broker-positions` | `/positions` | `OpenedOrders` |
| `broker-history`, `broker-performance` | history endpoints | `PositionsHistory` (paged), `TradeStats` |
| Kill switch (new) | per-position deletes | `OpenedOrders` → `OrderClose` in batches of 5 → repeat until empty |
| Error mapping | bracket-token parsing | `BROKER_SERVER_NOT_FOUND`→`SERVER_NOT_SUPPORTED`; connect auth failure→`ACCOUNT_WRONG_CREDENTIALS` (**exact code unverified**); `TERMINAL_INSTANCE_NOT_FOUND`→reconnect; `10019`→`INSUFFICIENT_MARGIN`; `10016`→`INVALID_STOPS`; `10018`→`MARKET_CLOSED`; `10014`→`INVALID_VOLUME`; `10026`/`10027`→`AUTOTRADING_DISABLED`; `10017`→`ACCOUNT_READ_ONLY`; `10004`/`10015`/`10020`/`10021`→re-quote and retry once |
| Cost guardrails (new) | — | A daily job reads the balance and alerts below 10 days of runway; `Disconnect` licences that are expired or have been unlinked for more than N days |

Effort: about **2–3 weeks**, the same as the plan above. The provider-neutral layer is 4–6 days,
the MetaRPC adapter 4–6 days, and connection upkeep, top-up and helper terminal 2–3 days. Then a
1–2 week pilot on new links only. Existing MetaCopier users have to re-link once, because we never
stored their passwords. Delete each MetaCopier account as it moves, since MetaCopier bills
disconnected accounts until they are deleted.

### Q7b. One-week bake-off plan

**Setup (the owner does this; I did not sign up):**
1. Create a MetaRPC account and top up about $25. Ten terminals × 7 days ≈ 1,700 account-hours,
   about **$10**.
2. **Before spending more, email `admin@mrpc.pro`** asking for written resale consent, the
   contracting entity's name, and whether `Reconnect` survives host restarts.

**Test accounts** (one terminal each, connected the whole week):

| # | Broker / server | Type | Why |
|---|---|---|---|
| 1 | Exness MT5 real (`Exness-MT5Real…`) | live, minimum balance | largest share of users |
| 2 | Exness MT4 real | live | MT4 path |
| 3 | XM MT5 on **`XMGlobal-MT5 20`** | live | the server MetaCopier can't reach; test `ConnectEx`, then `Connect` by host and port |
| 4 | XM MT5 on another numbered server | live | control for #3 |
| 5 | IFX MT5 | live | `.ifx` symbol suffixes, plus the past "autotrading disabled" confusion |
| 6 | Deriv MT5 | demo is fine | coverage |
| 7 | Trade245 MT5 | demo is fine | coverage |
| 8 | FBS MT5 | demo is fine | coverage |
| 9 | HFM MT5 | demo is fine | coverage |
| 10 | Any account linked with its **investor password** | live | must be detected as read-only at link time |

Use the owner's or a volunteer user's own accounts with consent. Live accounts only ever trade the
broker's minimum lot with tight SL/TP.

**What to run and measure:**
- **Connect:** success and time-to-connected for each server. Also try a wrong password and a
  wrong server name; record the exact `errorCode`.
- **Order flow** (a small script on a schedule, through a test copy of the edge function so the
  Supabase → Germany hop is included):
  - 4 market orders a day per account, at minimum lot with SL/TP, spread across the Asian, London
    and New York sessions and one high-impact news release;
  - plus 1 buy-limit and 1 sell-stop with a 1-hour expiry;
  - close everything after 2 minutes.
  - That is about 28 orders per account, about 250 in total.
- **Deliberate refusals:** lot above maximum, stops inside the stops level, wrong symbol, a market
  order on a closed market (weekend), and an order on the investor-password account. Record
  `returnedCode`, `returnedStringCode` and `mqlErrorTradeDescription` for each.
- **Latency:** time from the edge function sending to the reply arriving for every order; report
  p50, p95 and maximum.
- **Connection health:** `CheckConnect` every 5 minutes (about 2,000 calls, far below the 300 per
  minute limit). Log every drop, how long it lasted, and whether `Reconnect` recovered it without
  a password. Watch the weekend broker-maintenance window in particular.
- **Kill switch:** open 6 positions on one account, then time `close-all` until `OpenedOrders` is
  empty.
- **History check:** `PositionsHistory` P/L for the week against the broker's own statement.
- **Billing:** dashboard charges against the expected $10.

**Pass / fail:**
- **Pass per broker:**
  - ≥ 95% of valid orders placed (`10009`), excluding deliberate refusals and closed markets;
  - connected ≥ 99% of trading hours;
  - every refusal has a specific code;
  - p95 latency ≤ 2 s.
- **Pass overall:** Exness, XM (including server 20) and IFX all pass, the investor-password
  account is detected, and billing matches within 10%.
- **A failing secondary broker** (Deriv, Trade245, FBS, HFM) is marked unsupported in the server
  picker; it does not fail the vendor.
- **Fail overall:** any of the top three brokers fails, or a connection drop needs a password to
  recover, or no written resale consent arrives. If MetaRPC fails, fall back to MetaApi.

### Not verified (MetaRPC)

- No account was created and nothing was connected, so every broker-coverage, latency and
  reconnect claim above comes from the documentation.
- Welcome-credit amount, the exact `errorCode` for wrong credentials, and MT4 free-margin fields.
- Whether `Reconnect` works after a MetaRPC host restart without the password.
- The operator's identity and legal entity. The link to mtapi.io is inferred from the shared
  Telegram contact and identical API style.
- Whether Supabase edge functions (Deno) can use MetaRPC's gRPC, which would keep the password out
  of the URL. REST works with plain `fetch`.
