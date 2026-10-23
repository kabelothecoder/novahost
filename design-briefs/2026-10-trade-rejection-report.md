# Why trades don't go in — live data, pulled 2026-10-21

Source: NovaHost Supabase (`signal_logs` = handset path, `signal_executions` = server-side
path, `provider_refusals` = MetaCopier connect refusals). Dry runs excluded.

## Headline

| Path | Placed | Refused | Fill rate |
|---|---|---|---|
| Handset (`signal_logs`, last 30 days) | 139 | 254 | **35%** |
| Server-side (`signal_executions`, since 09-12) | 27 | 140 | **16%** |

Target: 95%. The gap is overwhelmingly **setup and connection**, not the market.

## Handset path — refusal reasons (last 30 days)

| Reason | Count | Licences | Bucket |
|---|---|---|---|
| BROKER_REJECTED | 65 | 10 | broker (see breakdown) |
| NO_ACCOUNT_LINKED | 49 | 8 | connection |
| ACCOUNT_DISCONNECTED | 42 | 13 | connection |
| POSITION_CAP | 24 | 3 | **our own rule** |
| INSUFFICIENT_MARGIN | 23 | 9 | margin |
| ACCOUNT_CONNECTING | 22 | 1 | connection |
| AUTOTRADING_DISABLED | 10 | 2 | wrong symbol name (fixed in executor v32) |
| ACCOUNT_WRONG_CREDENTIALS | 8 | 1 | connection |
| ACCOUNT_READ_ONLY | 5 | 1 | investor password used |
| ACCOUNT_WAS_DELETED | 4 | 1 | connection |

BROKER_REJECTED breakdown (all time): 56 generic `[BROKER_REJECTION]` with no detail,
11 "Invalid price", 9 "Autotrading disabled", 7 "Symbol not found", 4 "not enough money".

## Server-side path — refusal reasons

| Reason | Count |
|---|---|
| **NO_SIZE_CONFIGURED** | **73** — no lot size set for the symbol (symbol setup UX) |
| ACCOUNT_DISCONNECTED | 15 |
| BROKER_REJECTED | 15 |
| INSUFFICIENT_MARGIN | 10 |
| ACCOUNT_WAS_DELETED | 9 |
| ACCOUNT_WRONG_CREDENTIALS | 8 |
| POSITION_CAP | 6 |
| AUTOTRADING_DISABLED | 4 |

## Upstream — users who can't even connect

`metacopier-connect` refused **1,514** connect attempts between 2026-10-04 and today with
`ACCOUNT_LIMIT_PER_PROJECT_REACHED_TRIAL (allowed=10, needed=160, funding=75.00)`.
New users can't link a broker at all, so they never receive a single trade. This is still
happening today, and no code change fixes it. The MetaCopier project needs funding / a
plan upgrade.

## What gets us toward 95%

1. **Fund/upgrade MetaCopier, or switch provider**. This unblocks every new user. Decision
   2026-10-21: not funding yet; research alternative broker bridges first.
2. **Symbol wizard sets a default size automatically**. That kills NO_SIZE_CONFIGURED (44% of
   server-side refusals) and "Symbol not found" / "Autotrading disabled" (wrong names).
3. **Auto-reconnect + push the user to fix a dropped link** before a signal arrives
   (connection bucket is about half of all handset refusals).
4. **Replace POSITION_CAP with a margin check** (decided 2026-10-21): open trades never
   block a new one while free margin is healthy.
5. **Shrink-to-fit on low margin** (decided): shrink the lot to what the free margin
   supports instead of refusing; refuse only below the broker's minimum lot.
6. **Capture the real broker message** for the 56 generic `[BROKER_REJECTION]` rows so we
   can see what they actually are.
7. **Invalid price (11)**: re-quote and retry once at market instead of sending a stale price.
