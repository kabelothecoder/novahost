# Wow features — shortlist (2026-10-21)

Rule for "wow": it has to be **visible in 5 seconds**, **shareable or demo-able in a TikTok**,
and **make someone pay** (new licence, add-on, or mentor fee). Ranked by sell-power ÷ effort.
"Have already" = building blocks that exist in the codebase today.

## Decided 2026-10-21
Building: Flex Cards (NO affiliate link / QR), Prop Firm section (EA check → auto, else manual
signal cards), News Predictions (Event Playbook — replaces News Shield), Daily target lock,
Voice kill switch, Morning briefing. Not now: Robot Arena, replays.
Prompts: 9–12 in `2026-10-claude-design-prompts.md`.

## Top tier — build next to voice + Command Core

1. **Flex Cards (shareable P/L)**
   One tap turns a winning day/week into an animated card: the robot's loop video, profit,
   win rate, robot name — exported as a 9:16 video for WhatsApp Status / TikTok / IG, with
   the user's **affiliate link baked in** as a QR + code.
   *Sells because:* every share is a free ad that pays the sharer commission.
   *Have already:* affiliate attribution, loop videos (coming), trade logs.

2. **Prop-Firm Guardian**
   A mode for funded/challenge accounts (FTMO-style): user enters the firm's rules (daily
   loss %, max drawdown, min days, news ban) and the robot enforces them — shrinks lots,
   pauses before breaching, and shows a live "challenge health" gauge on Command Core.
   *Sells because:* prop challenges are huge with SA traders and a blown challenge costs
   them real money. Sell as a paid add-on.
   *Have already:* executor guardrails, margin/budget checks, news feed.

3. **News Shield**
   Robot auto-pauses X minutes around high-impact events for its symbols and says so in
   voice ("NFP in 10 minutes — I'm standing down on Gold"). Shown as a shield on the HUD.
   *Sells because:* "it protected me from NFP" is the story people tell.
   *Have already:* `market-wire` FairEconomy feed (times are GMT).

4. **"Why did you take that trade?" replays**
   Every trade gets a 15-second AI replay: chart animates to entry, robot narrates the
   reason in its voice, then shows the outcome. Losses explained honestly.
   *Sells because:* trust — and it's the most demo-able thing in the app.
   *Have already:* signals with SL/TP, Fish Audio (coming), scan reasoning.

## Second tier

5. **Equity Guardian / daily target lock** — "Stop for the day at +R500 or −R300". Robot
   parks itself and celebrates/consoles by voice. Simple, loved, cheap to build.
6. **Voice kill-switch** — "Jarvis, close everything" → confirm card → closes all. Feels
   like Iron Man, and it's a real safety feature.
7. **Robot Arena (leaderboard)** — robots ranked by *verified* live results across all
   NovaHost users (no screenshots, from our own execution logs). Providers pay to be
   featured; buyers pick robots from it. Needs care: only show real, net-of-refusals data.
8. **Morning Briefing** — first open of the day: 30-second voice briefing from the robot
   (yesterday's result, today's events, what it's watching). Note: iOS PWA can't push
   notify, so this lives in-app on open.

## Not recommended now
- Social chat rooms / copy-other-users — moderation and regulatory load, low payoff.
- Crypto/NFT robot skins — distracts from trading results.

## Honest note
The biggest "wow" a buyer feels is **the trade actually going in**. At 35% / 16% fill
rates (see `2026-10-trade-rejection-report.md`) every feature above is undermined until
connection + symbol setup are fixed. Pair each launch with a fill-rate milestone.
