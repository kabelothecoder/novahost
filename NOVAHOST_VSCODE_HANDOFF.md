# Nova Edge — VS Code Working Doc
### Your prompt generator + teacher for the Tue→Thu build

Companion to [`Nova_Edge_Launch_Plan.md`](Nova_Edge_Launch_Plan.md). That one is *what and why*. This one is *what to type*.

**Session 1: Tuesday 2026-08-11, 16:00–17:00 SAST.**

---

## 🔐 STOP — read this before you paste anything

**Never paste an API key, password, or token into a chat window.** Not to me, not to any AI. Once it's in a transcript it's out of your control. Everything below tells you to put keys in files *you* control, with your own hands.

If you ever catch yourself about to paste a secret into chat: don't. Put it in the file, then tell me *"key is in place"*. That's all I need.

### Where every secret goes

| Secret | Where it goes | How | Why there |
|---|---|---|---|
| **MetaCopier API key** | Supabase **Edge Function Secrets** | Dashboard → Project Settings → Edge Functions → Secrets → `METACOPIER_API_KEY` | **Server-side only.** Project-level key = full trading rights on every account. In an APK, anyone who decompiles it can trade your users' money. |
| **`ADMIN_BROADCAST_KEY`** | Supabase Edge Function Secrets | Same screen. Generate: `openssl rand -hex 32` | This is the P0-3 fix. Replaces the `dev-secret-key` default. |
| **`METAAPI_TOKEN`** | Supabase Edge Function Secrets | Same screen (probably already set) | Powers `test-broker-connection` |
| **Supabase URL + anon key** | `Nova Edge Android Version/local.properties` | Already there ✅ | Gitignored; injected via `BuildConfig`. Anon key is *designed* to be public — RLS is what protects you. |
| **Portal env** | `MetaHost Portal/lumin-dash/.env` | Already there ✅ | Gitignored and untracked — I verified. |

**The one rule that matters:** in Vite, any variable starting `VITE_` gets **compiled into the public JS bundle**. `SUPABASE_SERVICE_ROLE_KEY` in your `.env` has no `VITE_` prefix, so it stays server-side. **Never** rename it to `VITE_SUPABASE_SERVICE_ROLE_KEY`. That single prefix is the difference between a secret and a public broadcast.

---

## 🎮 Install these first (10 min, pays for itself today)

### 1. Supabase agent skills — do this one
```bash
npx skills add supabase/agent-skills
```
Teaches your Claude Code the correct patterns for RLS, migrations, and edge functions. You're about to write a migration and an edge function today. This is the highest-value 60 seconds of your week.

### 2. Stop the BOM from ever coming back
Create `.gitattributes` in each repo root:
```
* text=auto eol=lf
*.gradle text eol=lf
*.kt text eol=lf
```
And in VS Code settings (`Ctrl+,` → search "encoding"): set **Files: Encoding** to `utf8` — **not** `utf8bom`. The BOM that broke your build came from an editor saving as UTF-8-BOM. This is the fix.

### 3. Slash commands you'll actually use
| Command | When | Why for you specifically |
|---|---|---|
| `/security-review` | Before every push | You have a live `dev-secret-key` hole. Run it after T3. |
| `/code-review` | After each block | Catches the N+1s and missing error handling before your users do |
| **Plan mode** (`Shift+Tab` twice) | Before any multi-file change | Claude explores and proposes *before* editing. As a vibe coder this is your single best habit — you review a plan instead of unwinding 12 bad edits. |

### 4. Build a MetaCopier skill (Wednesday, not today)
You already have the pattern: `MetaHost iOS Version/.agents/skills/metaapi-javascript-sdk/`. That's a full SDK skill with docs and examples. Do the same for MetaCopier and Claude stops guessing at their API:
```
/skill-creator
```
Point it at `https://api.metacopier.io/rest/api/documentation/v3/api-docs`. Wednesday-morning task, ~15 min, saves hours of wrong endpoints.

### 5. One CLAUDE.md at the root
You have three, all identical, all describing a system that doesn't match reality (they claim the signal pipeline works — it never has). Wednesday: collapse to one accurate root file. Wrong context is worse than no context.

---

## ⏱️ TODAY 16:00–17:00 — copy-paste prompts

Work them in order. Each ends somewhere verifiable. **Don't start a block until the one before it is green.**

---

### ▶ T2 — Fix the signals schema (20 min)

> **What you're learning:** why a schema mismatch fails *silently*. Postgres rejects the insert, the edge function catches it, returns 200-ish, and the UI says "Signal Broadcasted" — while nothing was written. Your UI has been lying to you for months. Lesson: **success in the UI is not success in the database.** Always verify at the data layer.

```
Read Nova_Edge_Launch_Plan.md section 1.2 P0-2.

The live `signals` table has columns: id, ea_id, pair, type, price, sl, tp, lot,
status, created_at. But broadcast-signal, dispatch-signal, execute-trade-v2 and
webhook-handler all insert `side` and `signal_id`, which don't exist. Every insert
throws. Also signal_logs.license_id is uuid but execute-trade-v2 passes a text
license_key.

Use plan mode. Propose a migration that adds `side` and `signal_id` to `signals`
(keeping `type` for back-compat) and resolves the license_id type problem. Show me
the SQL and which of the 4 functions change before applying anything.

Then apply it to project epulmnfbxjmaimefhofp and prove it worked by inserting a
test row through broadcast-signal and reading it back.
```

**Green when:** a row appears in `signals` that you inserted via the function, not by hand.

---

### ▶ T3 — Close the security hole (20 min) ⚠️ do not skip

> **What you're learning:** the difference between authentication (*who are you*) and a shared secret (*do you know the password*). `broadcast-signal` uses a shared secret, ships it in public JavaScript, **and** falls back to a default when unset. Three failures stacked. The fix is to make the client prove identity (JWT) instead of knowing a string.

```
Fix P0-3 from Nova_Edge_Launch_Plan.md.

Currently: broadcast-signal is deployed with verify_jwt:false, its only auth is the
x-novaedge-key header compared to ADMIN_BROADCAST_KEY which DEFAULTS to the literal
string 'dev-secret-key' — and src/pages/QuickTrade.tsx:107 hardcodes that same string
in client-side React, so it ships in the public bundle.

I have set a real ADMIN_BROADCAST_KEY in Supabase secrets.

Use plan mode. I want:
1. The 'dev-secret-key' fallback default REMOVED — fail closed if the env var is missing
2. The hardcoded key gone from QuickTrade.tsx
3. Mentor identity verified via Supabase JWT rather than a shared string

Then prove it: show me a call with no key returning 403, and the portal still working.
```

**Green when:** wrong/absent key → 403, and Quick Trade still broadcasts.
**Then run `/security-review`.**

---

### ▶ T4 — Get it on your phone (10 min)

> **What you're learning:** emulators lie. The overlay service, hardware device ID and deep links all behave differently on real hardware. Your license gate binds to a *physical* device UUID — an emulator can't validate that path.

```
The debug APK built successfully. Help me install it on my physical Android device
over USB and walk the flow: splash → onboarding → license gate → paygate → home.

Tell me what to check at each screen and what "correct" looks like, so I can spot
a wrong result instead of assuming it's fine.
```

**Green when:** you reach Home on a real phone.

---

### ▶ T5 — The MetaCopier dry run (10 min) 🎯 the one that matters

> **What you're learning:** de-risking by *configuration before code*. Flow 3 needs zero code — MetaCopier copies master→slave natively. If this works today, you have a demonstrable product even if Wednesday collapses. Professionals always find the no-code proof first.

**This is mostly clicking in the MetaCopier dashboard, not coding:**

1. Log into MetaCopier → confirm your project is active
2. Add your **MT5 master** account (`POST /accounts` equivalent in the UI)
3. Add **one demo slave** account
4. Create the copier link: master → slave
5. Place a manual 0.01 lot trade on the master
6. **Watch the slave.**

```
I'm doing the MetaCopier Flow 3 dry run — master → slave copy with no code.
Walk me through what to verify at each step, and tell me the 3 most common
reasons a copier link fails to copy so I can diagnose fast if the trade
doesn't appear on the slave.
```

**Green when:** the trade appears on the slave account. **This is the rent-safety checkpoint.** If it copies, you have a product.

---

## 🙋 What I need from you

Answer as you go — don't front-load it. Values in files, **never in chat**.

| # | Question | Answer format |
|---|---|---|
| 1 | ✅ MetaCopier account — **confirmed** | done |
| 2 | Is `METACOPIER_API_KEY` in Supabase secrets yet? | just say *"key is in place"* |
| 3 | Which **broker + demo account** for master and slave? | broker name only — no logins |
| 4 | Is `METAAPI_TOKEN` still active/paid? | yes / no / unsure |
| 5 | Paygate price + billing period? | e.g. "R499/month" |
| 6 | Is the mentor MT5 desktop terminal *your* machine, on Thursday? | yes / no |
| 7 | Signals migration: add columns (my rec) or rename in 4 functions? | A or B |
| 8 | Delete the two `*.bom-backup` files? | yes / keep till Thu |

---

## 📌 Session close — 17:00

Before you stop, write down:
- Which blocks went green
- Where exactly you got stuck (file + line, not "it broke")
- The **one** thing blocking tomorrow

Wednesday is the heavy day (`metacopier-execute`, the image picker, both live flows). Starting it with a clear head beats starting it at 2am.

---

## 🧠 Three habits worth more than any prompt

1. **Plan mode before multi-file changes.** `Shift+Tab` twice. Review the plan, not the wreckage.
2. **Verify at the data layer.** Green toast ≠ row in table. That exact gap hid P0-2 for months.
3. **Ask "how would I know if this were broken?"** before calling anything done. `MetaAPIManager.synchronize()` logs `">> Connected to trade server"` and connects to nothing. It *looks* right in the UI. Someone shipped it believing it worked.
