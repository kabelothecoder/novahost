# NovaHost — Wednesday 2026-08-12
### Copy-paste prompts for VS Code. Deadline: tomorrow.

---

## Part 1 — "Slave accounts" explained (read this first, 3 min)

Bad industry terminology, simple idea. Ignore the word "slave" — it just means **follower**.

```
        THABO (a mentor)
        trades XAUUSD on his own MT5 terminal
                 │
                 ▼
        ┌─────────────────┐
        │  MASTER ACCOUNT │   ← Thabo's own broker account
        └────────┬────────┘
                 │  MetaCopier watches this account 24/7.
                 │  The instant a trade opens here, it copies it.
                 ▼
   ┌─────────┬─────────┬─────────┐
   ▼         ▼         ▼         ▼
 SLAVE     SLAVE     SLAVE     SLAVE      ← your app users' broker accounts
 (Sipho)   (Naledi)  (Ann)     (Jay)
```

- **Master** = the account trades are copied **FROM**. One per mentor. It's the mentor's *own* real MT5 account.
- **Slave** = an account trades are copied **TO**. One per paying app user.

When Thabo buys 0.01 XAUUSD, MetaCopier opens that same trade on all four users' accounts in under a second. Lot sizes scale automatically to each account's balance — Sipho with R2,000 gets a smaller position than Jay with R50,000.

**Why this matters for you:** MetaCopier does the hard part — latency, reconnections, lot scaling, partial closes — that you'd otherwise write and debug yourself. That's the whole reason you moved off MetaAPI.

### How this maps onto YOUR model

| Your words | MetaCopier calls it |
|---|---|
| Mentor's trading account | **Master** |
| App user who bought a license key | **Slave** / follower |
| A mentor's bot ("Gold Sniper") | A **strategy** or copier group |
| User enters license key + connects broker | Attaching that user's account as a slave of that mentor's master |

**Multi-mentor rule:** each mentor gets **their own master**, and their license holders attach only to *that* master. Thabo's subscribers must never receive Lerato's trades. That separation is the thing your whole business depends on being correct.

---

## Part 2 — What I fixed this morning, and what I got wrong yesterday

**I got T3 wrong.** I hardcoded your email as the only account allowed to broadcast, because I could only see one mentor. In your real model that blocks mentor #2 on their first day.

**Now corrected and deployed** (`broadcast-signal` v6). Authorization is now by **ownership**, not identity:

- Your schema already had this — `expert_advisors.user_id` records which mentor created each bot
- A mentor may only broadcast to bots **they own**
- "ALL" now means "all of *my* bots", never "every bot on the platform"

That last one was a real multi-tenancy bug: `ea_id: "MASTER_OVERRIDE"` would have sent one mentor's trade calls to **every user of every other mentor**. Verified closed — unauthenticated 401, valid-but-not-a-mentor 403.

---

## Part 3 — Today's blocks

**First, tell me: did T5 (the MetaCopier master→slave dry run) happen yesterday?** Everything below assumes it didn't. If it did, skip straight to W2.

---

### ▶ W1 — MetaCopier dry run 🎯 DO THIS BEFORE ANYTHING ELSE

> **Why first:** it needs zero code. If a trade copies from master to slave today, you have a demonstrable product tomorrow *even if every other block fails*. This is the rent-safety checkpoint and it has now slipped one day. Do not let it slip twice.

Mostly clicking, not coding:

1. MetaCopier dashboard → confirm project active
2. Add your **MT5 account as master**
3. Add **one demo account as slave**
4. Link them: master → slave
5. Place a manual **0.01 lot** trade on the master
6. **Watch the slave account.**

```
I'm doing the MetaCopier master → slave dry run. Walk me through verifying each
step, and tell me the 3 most common reasons a copier link silently fails to copy,
so I can diagnose fast if the trade doesn't appear on the slave.
```

**Green when:** the trade appears on the slave. **Stop and tell me the moment it does** — that changes what we build next.

---

### ▶ W2 — Decide: copy-trading or signal-execution?

> **Why this matters more than any code today:** you currently have *two half-built architectures* and only one day. Picking one and deleting the other is the highest-value decision left.

| | **A. Copy-trading (MetaCopier)** | **B. Signal execution (portal form)** |
|---|---|---|
| Mentor does | Trades on their own MT5 | Fills in pair/side/SL/TP in portal |
| Code needed | Almost none — config | `metacopier-execute` + per-user account mgmt |
| Works tomorrow? | **Yes, if W1 passes** | Risky |
| Chart scanner | Unaffected | Unaffected |

**My recommendation: ship A tomorrow, keep B for later.** Your portal's Quick Trade already writes to `signals` — that becomes a *notification* feed ("your mentor just bought XAUUSD") while MetaCopier does the actual execution. You get a working product tomorrow instead of a broken one.

```
Read NOVAHOST_WEDNESDAY.md Part 3 W2. I'm choosing architecture [A or B].
Tell me exactly which files and edge functions become dead code under that choice,
and which ones I still need. Don't delete anything yet — just show me the list.
```

---

### ▶ W3 — Scope signals to the right users (multi-tenant correctness)

> **What you're learning:** *multi-tenancy* — many customers sharing one system, each seeing only their own data. Get this wrong and Thabo's students receive Lerato's trades. It's the #1 way SaaS products leak data between customers.

The backend now stamps every signal with its `ea_id`. The Android app must **filter on it** — a user's license is tied to one bot (`licenses.ea_id`), so they should only ever act on signals for that bot.

```
In the Android app, NovaHostPulseService subscribes to the Supabase Realtime
'signals' channel. Right now it acts on every signal it receives.

Each user's license is tied to a specific bot via licenses.ea_id. Signals now carry
an ea_id in the payload.

Use plan mode. Show me how to: (1) store the user's ea_id at license activation,
(2) ignore any realtime signal whose ea_id doesn't match. Explain how I can TEST
that a signal for a different ea_id is correctly ignored.
```

**Green when:** a signal with a foreign `ea_id` is provably ignored.

---

### ▶ W4 — Chart scanner image picker

> Independent of everything above — safe to do if W1–W3 stall. `analyze-chart` is already deployed and live.

```
Wire up the AI chart scanner in the Android app: image picker → base64 → the
deployed analyze-chart edge function → render the returned levels.

Use plan mode. Follow the Premium Light theme in CLAUDE.md. Handle the failure
cases explicitly: no image selected, oversized image, function timeout, malformed
response — each with a user-facing message, not a crash.
```

---

## Part 4 — Two habits, since you're learning

**1. Authorize by ownership, not identity.** "Is this Kabelo?" doesn't scale — it broke the moment you told me there'd be many mentors. "Does this person own the thing they're touching?" works for 1 mentor or 10,000. That's the single most reusable idea from this whole build.

**2. A green toast is not proof.** Your Quick Trade said "Signal Broadcasted" for months while every insert threw. Always verify one layer deeper than the UI — check the actual row in the actual table.

---

## Still open (not done, don't forget)

- `execute-trade-v2`, `dispatch-signal`, `webhook-handler` are still `verify_jwt: false`. `execute-trade-v2` places **real orders** — it needs the same ownership treatment. Depends on W2's outcome.
- `ADMIN_BROADCAST_KEY` is now unused. Leave it; harmless.
- Three near-identical `CLAUDE.md` files describe a system that doesn't match reality. Collapse to one *after* launch.
