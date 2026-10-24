import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

/**
 * The AI Chart Scanner's vision pass, on Gemini.
 *
 * Takes a chart screenshot and returns the reading the confluence engine scores:
 * a direction, an entry and how to reach it, a stop, the pattern, the
 * higher-timeframe bias, the nearest structural level with its touch count, a
 * volatility measure, a per-timeframe read, and whether the chart is still
 * describing a live market at all.
 *
 * ## Why raw fetch and not an SDK
 *
 * Google's JS SDK pulls a large dependency tree through npm: into a Deno edge
 * function for what is one POST. The REST contract here is stable and small
 * enough to hold in one file, and an edge function's cold start is the user
 * waiting on a scan.
 *
 * ## What this replaced
 *
 * The original endpoint had three faults, any one of which alone stopped every
 * scan this product has ever attempted: it read `{ image }` where Android posts
 * `{ imageBase64 }`, its `OPENAI_API_KEY` was never set, and it asked the model
 * for `action` where the client parses `direction`. It also asked for none of
 * the fields the confluence engine actually scores, and hardcoded "you are an
 * SMC trader" into its prompt -- so every scan was a Smart Money read whether or
 * not that was how the user trades. [STRATEGY_BRIEFS] is the fix for the last
 * of those.
 */

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/models";

/**
 * Flash rather than Pro: this is one image and a bounded JSON answer, which is
 * what Flash is for, and it is the tier the free quota applies to.
 *
 * An env var so the model can be moved without a deploy -- and that turned out
 * to matter within a day. The first real call against this endpoint answered
 * `404: This model models/gemini-2.5-flash is no longer available to new users`,
 * because Google retires point releases on their own schedule and a key issued
 * after the cutoff simply cannot reach the old one. Overriding `GEMINI_MODEL`
 * moves it without touching this file.
 */
const MODEL = Deno.env.get("GEMINI_MODEL") || "gemini-3.6-flash";

/** Largest decoded image we will forward, in bytes. */
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;

/**
 * How many times one scan will ask Gemini before giving up.
 *
 * The free tier is the first traffic shed when a model is busy, so a 503
 * UNAVAILABLE here is routine rather than exceptional: seven of the eleven scan
 * failures in the day this was written were that, and every one was handed back
 * to the handset as "The scan did not complete. Try again." with no attempt made
 * to do exactly that. The user then retries by hand -- the same request a few
 * seconds later -- so the function may as well make it for them. "The scanner
 * never works" is, in a good share of cases, this.
 *
 * Three and no more: a scan is a person holding a phone waiting on an answer,
 * and a fourth attempt buys less than the wait costs.
 */
const GEMINI_ATTEMPTS = 3;

/** Floor between attempts, per retry. Google's own retryDelay wins if longer. */
const GEMINI_BACKOFF_MS = [700, 1800];

/**
 * Whether a failed Gemini call is worth repeating, and after how long.
 * Null means no -- answer the user now.
 *
 * 503 is capacity and clears on its own, so it is retried immediately. 429 is
 * quota, and the two quotas behind that status are not the same failure: a
 * per-minute limit refills inside the time someone would wait, a per-DAY limit
 * does not refill today at all. Google names which one ran out in `quotaId` and
 * how long to wait in `retryDelay`, so both are read rather than assumed --
 * sleeping through a retry against a daily cap only makes the refusal slower.
 */
function retryAfterMs(status: number, detail: string): number | null {
  if (status === 503) return 0;
  if (status !== 429) return null;
  if (/PerDay/i.test(detail)) return null;

  const named = detail.match(/"retryDelay"s*:s*"(d+(?:.d+)?)s"/);
  const delay = named ? Number(named[1]) * 1000 : 0;
  // Longer than anyone will hold a phone for. Refuse now and say so.
  return delay > 30_000 ? null : delay;
}

/**
 * How to read the chart, keyed by [ScanStrategy] on the handset.
 *
 * Server-side on purpose. The client sends the *name* and never the text, so
 * the reading instruction can be tuned without shipping an APK, and a tampered
 * client cannot rewrite the prompt this project is billed for.
 */
const STRATEGY_BRIEFS: Record<string, string> = {
  SMC:
    "Read this as a Smart Money Concepts trader. Look for order blocks, fair value gaps, " +
    "liquidity sweeps above highs or below lows, and a change of character that confirms the " +
    "shift. Enter from the zone price is returning to, not from where it broke.",
  PRICE_ACTION:
    "Read this as a pure price action trader. Work from horizontal support and resistance, the " +
    "candles printing at those levels, and rejection wicks. No indicators. The key level is the " +
    "one price has respected most often in the visible window.",
  // The entry is pinned to the sweep candle's 50% rather than left to the
  // model's judgement. A defined entry price is what makes the same setup size
  // the same way twice: stop distance drives lot size, which drives the blended
  // R:R that Guardrails blocks below 1.5. A freely chosen entry made that figure
  // wander between scans of near-identical charts.
  CRT:
    "Read this as a Candle Range Theory trader. The range of a completed higher-timeframe candle " +
    "is what matters -- its high and its low are the levels. The model runs in three candles: one " +
    "sets the range, the next sweeps one side of it to take the liquidity resting there, and the " +
    "third distributes toward the opposite side. The setup is a sweep that closes back INSIDE the " +
    "range. " +
    "Entry is the 50% of that sweep candle -- its equilibrium, halfway between its high and its " +
    "low -- placed as a LIMIT, because price has to come back to it. Do not enter at the sweep " +
    "candle's close. Stop goes just beyond the sweep's wick, and the target is the opposite end " +
    "of the range. " +
    "If price has already run past that 50% toward the target, the entry is gone: report it as a " +
    "missed setup with low confidence and say so in the narrative. Do not move the entry to " +
    "wherever price is now to make the setup fit. " +
    "Name the range high and low you are working from in entry_note. " +
    "A sweep that closes OUTSIDE the range is a breakout, not a CRT -- report that as low " +
    "confidence rather than forcing it into the model.",

  // Kept so a handset still on the previous build does not fall back to SMC
  // without saying so. Removed once those installs have updated.
  TREND:
    "Read this as a trend follower. Establish the dominant direction from the structure of highs " +
    "and lows, then find the pullback entry that resumes it. Do not take a counter-trend setup -- " +
    "if the only setup visible is against the trend, say so in the narrative and return low " +
    "confidence.",
  BREAKOUT:
    "Read this as a breakout trader. Find the consolidation -- range, triangle, flag -- and the " +
    "level that ends it. Prefer the retest entry after the break over chasing the candle that " +
    "broke it, and put the stop back inside the range.",
};

/**
 * Per-style rulebooks. The style used to reach the model as one sentence ("they
 * are scanning for a scalp trade") and changed nothing about how a chart was
 * read, so a scalp and a swing came back with the same stops and targets.
 * `minRR` is enforced after the read as well as asked for, because a model
 * asked for 2R will sometimes hand back 1.4R and call it fine.
 */
const STYLES: Record<string, { label: string; htf: string; ltf: string; minRR: number; brief: string }> = {
  scalp: {
    label: "scalp", htf: "H1", ltf: "M5", minRR: 1.5,
    brief:
      "Style: SCALP, held minutes to an hour. Take the trend from the H1 (or the highest timeframe " +
      "visible) and only trade with it. Time the entry on M5-M15 at a fresh sweep, an untested zone " +
      "or a retest -- never chase a candle that has already run. The stop sits about one average " +
      "candle range beyond the structure. The first target must be at least 1.5 times the risk. " +
      "If the M15/H1 trend is against the only setup, say so and return low confidence.",
  },
  day: {
    label: "day trade", htf: "H4", ltf: "M15", minRR: 2,
    brief:
      "Style: DAY TRADE, opened and closed inside one trading day. Take the bias from the H4. Work " +
      "from the previous day's high and low and the Asian session range as the key levels. Time the " +
      "entry on M15-H1. The target must be reachable today -- do not aim past the previous day's high " +
      "or low unless it has already broken. The first target must be at least 2 times the risk.",
  },
  swing: {
    label: "swing", htf: "D1", ltf: "H4", minRR: 2.5,
    brief:
      "Style: SWING, held for days. Only trade with the daily and weekly trend. Use daily and weekly " +
      "levels; time the entry on H4. The stop goes beyond a daily structure point, not inside it. " +
      "The first target must be at least 2.5 times the risk.",
  },
};
const styleFor = (mode: string) => {
  const m = mode.toLowerCase();
  if (m.startsWith("scalp")) return STYLES.scalp;
  if (m.startsWith("swing")) return STYLES.swing;
  return STYLES.day;
};

/**
 * Deriv's synthetic indices run 24/7 off a random number generator: no
 * sessions, no news, no weekend gap. Volatility indices (V75, V100, the 1s
 * variants) are supported. Boom/Crash and Step are not -- Boom/Crash spike in
 * one direction by design and Step moves in fixed increments, and none of the
 * strategy briefs reads either honestly.
 */
// Weltrade's FX Vol / SFX Vol are the same kind of random-walk index and are
// read the same way. Its directional and fixed-pattern products -- PainX and
// GainX (Boom/Crash-style jumps), FlipX, SwitchX, BreakX, TrendX, and the
// growth models PlusX, FiboX, QuadX -- move by rule, not structure, so they are
// refused with Boom, Crash and Step.
const VOLATILITY_RE = /\b(VOLATILITY\s*\d+|V\s?\d{2,3}|R_\d{2,3}|1HZ\d{2,3}V?|S?FX\s?VOL\w*)\b/i;
const UNSUPPORTED_SYNTHETIC_RE = /(BOOM|CRASH|\bSTEP\b|PAINX|GAINX|FLIPX|SWITCHX|BREAKX|TRENDX|PLUSX|FIBOX|QUADX)/i;
const UNSUPPORTED_MSG =
  "That index moves by fixed rules (spikes, flips or set growth), so the scanner can't read it. " +
  "Volatility indices like V75, V100 and FX Vol are supported.";

const SYNTHETIC_BRIEF =
  "Instrument: this is a synthetic volatility index (V75, V100, FX Vol and similar). If it is SFX Vol, expect a spike roughly every 30 minutes and keep the stop outside one. It trades 24/7 with no " +
  "sessions, no news and no fundamental drivers -- do not mention London, New York, Asia or news. " +
  "Size the stop from the candles themselves (average candle range), since pips and sessions do not " +
  "apply. Structure, ranges and sweeps still work the same way.";

const EXPLAIN_INSTRUCTION = `Explanations: write explain_normal and explain_pro. Both are read aloud by the app's robot voice, so write for the ear: short sentences, no bullet points, no symbols like "@" or "->", prices written as numbers.
explain_normal is for a beginner, about 60 words, four beats in order: what you saw, the plan (buy or sell, where to enter, where the stop goes), where the target is, and what would cancel the idea. No jargon at all -- not "order block", "FVG", "liquidity", "CHoCH", "BOS", "CRT". Say "price swept below the recent low and came back" instead.
explain_pro is for an experienced trader, about 60 words, and may use the strategy's own vocabulary and timeframe names.
Never mention any person, mentor or robot provider. If the setup is weak or missed, say so plainly in both.`;

/**
 * The exact shape the engine consumes.
 *
 * Gemini's `responseSchema` is an OpenAPI 3.0 subset, and it differs from the
 * JSON Schema dialect in two ways that matter here:
 *
 *  - **No `additionalProperties`.** Sending it is rejected. Object shapes are
 *    closed by listing every property and requiring them, which this does.
 *  - **No union types.** `type: ["string", "null"]` is invalid; a nullable
 *    field is `type: "string"` plus `nullable: true`.
 *
 * `propertyOrdering` is not decoration -- Gemini generates fields in the order
 * given, and putting `chart_readable` first means the model decides whether it
 * can read the chart before it commits to a direction, rather than justifying a
 * direction it has already written.
 */
const RESPONSE_SCHEMA = {
  type: "object",
  properties: {
    chart_readable: {
      type: "boolean",
      description:
        "False when the image is not a readable price chart -- a screenshot of something else, " +
        "an unreadably small crop, or a chart with no visible price axis.",
    },
    unreadable_reason: {
      type: "string",
      nullable: true,
      description: "One short sentence for the user when chart_readable is false. Null otherwise.",
    },
    symbol_on_chart: {
      type: "string",
      nullable: true,
      description:
        "The instrument name printed on the chart itself, verbatim (e.g. 'XAUUSD', 'GBPJPY.m'). " +
        "Null if no symbol label is visible. Do not infer it from the price level.",
    },
    timeframe_on_chart: {
      type: "string",
      nullable: true,
      description: "The timeframe printed on the chart (e.g. 'M15', 'H4', '1D'). Null if not visible.",
    },
    market_closed: {
      type: "boolean",
      description:
        "True when the chart itself says the market is shut -- MetaTrader prints 'Market closed' " +
        "beside the symbol label, TradingView prints 'Market closed' or 'Post-market'. Report what " +
        "is written on the image; do not infer it from the date or from quiet candles.",
    },
    last_candle_time: {
      type: "string",
      nullable: true,
      description:
        "The time axis label at or immediately under the RIGHTMOST candle, copied verbatim " +
        "(e.g. '11 Sep 22:15', '2025.09.11 14:45'). Null if the time axis is cropped or illegible.",
    },
    last_candle_iso: {
      type: "string",
      nullable: true,
      description:
        "last_candle_time normalised to 'YYYY-MM-DDTHH:MM', in the chart's own clock. The axis " +
        "usually omits the year -- use the year given in the request, and if that would place the " +
        "date in the future, use the year before. Null whenever last_candle_time is null or " +
        "carries no date you can pin down.",
    },
    direction: { type: "string", enum: ["BUY", "SELL"] },
    confidence: {
      type: "number",
      description: "0-100. How strongly the visible evidence supports this direction.",
    },
    entry: { type: "number", description: "Entry price, in the chart's own price scale." },
    entry_type: {
      type: "string",
      enum: ["MARKET", "LIMIT", "STOP"],
      description:
        "How the entry should be reached. MARKET when the setup is live at the current price. " +
        "LIMIT when waiting for price to come BACK to the entry (a retest, a pullback into a " +
        "zone). STOP when waiting for price to BREAK THROUGH the entry (a breakout above " +
        "resistance, a breakdown below support). Judge it from where the entry sits relative to " +
        "the last candle on the chart.",
    },
    entry_note: {
      type: "string",
      description:
        "One short sentence on why the entry sits where it does, e.g. 'waiting for the retest of " +
        "the broken 1.0850 level'. Shown to the trader beside the order type.",
    },
    sl: { type: "number", description: "Stop loss. Below entry for a BUY, above entry for a SELL." },
    tp: { type: "number", description: "First take profit. Above entry for a BUY, below for a SELL." },
    patterns: {
      type: "array",
      items: { type: "string" },
      description: "Named structures actually visible, most significant first. Empty if none are.",
    },
    trend_bias: {
      type: "string",
      enum: ["BULLISH", "BEARISH", "NEUTRAL"],
      description:
        "Dominant bias of the highest timeframe legible on this chart. NEUTRAL is a real answer " +
        "for a range -- do not pick a side to be helpful.",
    },
    trend_timeframe: {
      type: "string",
      description: "Which timeframe trend_bias describes, e.g. 'H4'. 'higher-timeframe' if unnamed.",
    },
    key_level: {
      type: "number",
      description: "The nearest structural level to entry -- the support or resistance being traded against.",
    },
    key_level_touches: {
      type: "integer",
      description:
        "How many times price visibly reacted at key_level in the window shown. Count what is on " +
        "screen; do not estimate history that is scrolled out of view.",
    },
    average_candle_range: {
      type: "number",
      nullable: true,
      description:
        "Typical high-to-low range of one candle over roughly the last 14 candles, in the chart's " +
        "own price scale -- not pips, not a percentage. Null if candles are not distinguishable.",
    },
    timeframes: {
      type: "array",
      description: "One row per timeframe you can actually read. One honest row beats four invented ones.",
      items: {
        type: "object",
        properties: {
          timeframe: { type: "string" },
          bias: { type: "string", enum: ["BULLISH", "BEARISH", "NEUTRAL"] },
          note: { type: "string", description: "One short line of evidence for this row." },
        },
        required: ["timeframe", "bias", "note"],
        propertyOrdering: ["timeframe", "bias", "note"],
      },
    },
    narrative: {
      type: "string",
      description:
        "Two or three plain sentences: what the chart shows, why this direction, and the clearest " +
        "argument against it. Written for the trader, not about the model.",
    },
    htf_symbol_on_chart: {
      type: "string",
      nullable: true,
      description: "Symbol printed on the HIGHER-timeframe screenshot, verbatim. Null if only one image was sent or none is visible.",
    },
    htf_timeframe_on_chart: {
      type: "string",
      nullable: true,
      description: "Timeframe printed on the HIGHER-timeframe screenshot. Null if only one image was sent or none is visible.",
    },
    explain_normal: { type: "string", description: "Plain-language spoken explanation, see instructions." },
    explain_pro: { type: "string", description: "Trader-language spoken explanation, see instructions." },
  },
  required: [
    "chart_readable", "unreadable_reason", "symbol_on_chart", "timeframe_on_chart",
    "market_closed", "last_candle_time", "last_candle_iso",
    "direction", "confidence", "entry", "entry_type", "entry_note", "sl", "tp", "patterns",
    "trend_bias", "trend_timeframe", "key_level", "key_level_touches",
    "average_candle_range", "timeframes", "narrative",
    "htf_symbol_on_chart", "htf_timeframe_on_chart", "explain_normal", "explain_pro",
  ],
  propertyOrdering: [
    "chart_readable", "unreadable_reason", "symbol_on_chart", "timeframe_on_chart",
    "market_closed", "last_candle_time", "last_candle_iso",
    "direction", "confidence", "entry", "entry_type", "entry_note", "sl", "tp", "patterns",
    "trend_bias", "trend_timeframe", "key_level", "key_level_touches",
    "average_candle_range", "timeframes", "narrative",
    "htf_symbol_on_chart", "htf_timeframe_on_chart", "explain_normal", "explain_pro",
  ],
};

const BASE_INSTRUCTION = `You read trading chart screenshots for a mobile app that sizes a real position from your answer.

Read what is on the chart. Do not supply what is not there.

Prices: every number you return must be read off the chart's own price axis and be in its scale. A gold chart is around four figures; EURUSD is around one. If you cannot read the axis, the chart is not readable -- say so rather than guessing a plausible-looking number.

Direction and bracket: the stop goes on the invalidation side of the structure you are trading, not at a round distance. For a BUY, sl < entry < tp. For a SELL, sl > entry > tp. Never return a bracket that fails that ordering.

Entry type: this is the order the app will actually place, so be deliberate. Compare your entry to the last candle on the chart. If price has to come BACK DOWN to a buy entry, or BACK UP to a sell entry, that is LIMIT -- a retest, a pullback into a zone. If price has to BREAK THROUGH the entry to trigger it, that is STOP -- a breakout above resistance, a breakdown below support. Only say MARKET if the setup is live right now at the current candle. Do not default to MARKET because it is simplest; a market fill on a retest setup enters the trade thirty pips from the level the stop was measured against.

Key level: pick the level the entry is actually leaning on, and count the touches you can see in the visible window. A level with one touch is a level with one touch -- report it, do not round it up to make the setup look better.

Trend bias: report the bias of the highest timeframe legible here. NEUTRAL is a real and common answer. A range is not secretly bullish.

Timeframes: return a row only for a timeframe you can genuinely read. If the screenshot is a single H1 chart, that is one row.

Volatility: average_candle_range is the typical high-to-low distance of one candle over roughly the last 14, in the chart's price units. Measure it off the candles you can see. If they are too compressed to measure, return null -- the app treats null as unknown and scores it as unverified, which is correct. A number you did not measure is worse than no number, because it will be scored as if it were measured.

How old is this chart: read the time axis label under the rightmost candle and copy it into last_candle_time exactly as printed, then normalise it into last_candle_iso. Also report market_closed from whatever the terminal has written on the image -- MetaTrader puts "Market closed" under the symbol name when the book is shut. This matters more than anything else on the chart: the app places a live order from your answer, and a setup read off a screenshot from last week is a setup that resolved days ago. Both fields are observations, not judgements -- if the time axis is cropped out, say null rather than estimating from the candles.

Confidence: what the visible evidence supports. A clean structure at a well-tested level is high. A guess from a noisy crop is low, and saying so is more useful than a confident number that is wrong.

If the image is not a readable price chart at all, set chart_readable false, give one sentence in unreadable_reason, and fill the remaining fields with any valid values -- they will be discarded.`;

interface Reading {
  chart_readable: boolean;
  unreadable_reason: string | null;
  symbol_on_chart: string | null;
  timeframe_on_chart: string | null;
  market_closed: boolean;
  last_candle_time: string | null;
  last_candle_iso: string | null;
  direction: "BUY" | "SELL";
  confidence: number;
  entry: number;
  entry_type: "MARKET" | "LIMIT" | "STOP";
  entry_note: string;
  sl: number;
  tp: number;
  patterns: string[];
  trend_bias: "BULLISH" | "BEARISH" | "NEUTRAL";
  trend_timeframe: string;
  key_level: number;
  key_level_touches: number;
  average_candle_range: number | null;
  timeframes: Array<{ timeframe: string; bias: string; note: string }>;
  narrative: string;
  htf_symbol_on_chart: string | null;
  htf_timeframe_on_chart: string | null;
  explain_normal: string;
  explain_pro: string;
}

/**
 * A minute count as a trader would say it out loud.
 *
 * Rounded hard on purpose. The input is an age derived from a time axis read off
 * an image, against a chart clock whose timezone is unknown -- "about 3 days" is
 * the whole of what it supports, and "4,412 minutes" would dress that up as a
 * measurement.
 */
function describeAge(minutes: number): string {
  if (minutes < 90) return `${minutes} minutes`;
  const hours = Math.round(minutes / 60);
  if (hours < 36) return `${hours} hours`;
  return `${Math.round(hours / 24)} days`;
}

/**
 * Splits `data:image/png;base64,AAAA` into its media type and payload.
 *
 * Accepts a bare base64 string too. The Android client sends the full data URL
 * and the old function then prefixed it a second time, producing
 * `data:image/jpeg;base64,data:image/jpeg;base64,...` -- so both forms are
 * handled here rather than relying on either side to change first.
 *
 * The media type is read from the URL rather than assumed to be JPEG: the picker
 * hands back whatever the user screenshotted, which on Android is usually PNG.
 */
function decodeImage(raw: string): { mediaType: string; data: string } | null {
  const value = raw.trim();

  const match = value.match(/^data:([a-zA-Z0-9/+.-]+);base64,(.*)$/s);
  const mediaType = match ? match[1].toLowerCase() : "image/jpeg";
  const data = match ? match[2] : value;

  if (!data) return null;
  if (!["image/jpeg", "image/png", "image/gif", "image/webp"].includes(mediaType)) return null;
  // Base64 inflates by 4/3; compare against the decoded size.
  if ((data.length * 3) / 4 > MAX_IMAGE_BYTES) return null;

  return { mediaType, data };
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: CORS_HEADERS });
  }

  const json = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), {
      status,
      headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
    });

  try {
    // Both names accepted. GEMINI_API_KEY is the documented one; GOOGLE_API_KEY
    // is what Google's own tooling exports, and having the scanner refuse to
    // start over which of two synonyms was typed helps nobody.
    //
    // Recovered from the deployed v25, which had this and the repo did not --
    // deploying the repo copy over it would have taken the fallback out from
    // under a project that may be running on the second name.
    const apiKey = Deno.env.get("GEMINI_API_KEY") || Deno.env.get("GOOGLE_API_KEY");
    if (!apiKey) {
      // Named explicitly, and the visible secret names are logged with it. The
      // predecessor's equivalent failure surfaced as "Incorrect API key
      // provided: undefined", which reads like a broken account rather than an
      // unset server secret -- and gave nobody a way to tell a missing secret
      // from a misspelled one. That diagnostic immediately earned itself: the
      // key had been saved as "Gemini flash", a name no environment variable
      // can have.
      const visible = Object.keys(Deno.env.toObject())
        .filter((k) => !k.startsWith("SUPABASE_") && !k.startsWith("SB_") && !k.startsWith("DENO_"))
        .join(", ");
      console.error(
        "[analyze-chart] GEMINI_API_KEY is not set. Custom secrets visible to this function: " +
        (visible || "(none)")
      );
      return json({ error: "The scanner is not configured on the server yet.", code: "NOT_CONFIGURED" }, 503);
    }

    const body = await req.json().catch(() => ({}));

    // `image` is accepted as an alias so a request built against the old
    // contract still works. `imageBase64` is what the Android client sends.
    const rawImage = String(body.imageBase64 ?? body.image ?? "");
    if (!rawImage) {
      return json({ error: "No chart image was included in the request." }, 400);
    }

    const image = decodeImage(rawImage);
    if (!image) {
      return json(
        { error: "That image could not be read. Use a PNG or JPEG screenshot under 5 MB." },
        400
      );
    }

    const pair = String(body.pair ?? "").trim();
    const mode = String(body.mode ?? body.trading_mode ?? "").trim();
    const strategyKey = String(body.strategy ?? "SMC").trim().toUpperCase();
    const strategyBrief = STRATEGY_BRIEFS[strategyKey] ?? STRATEGY_BRIEFS.SMC;
    const style = styleFor(mode || "day");

    // Optional higher-timeframe screenshot. CRT cannot be read from one chart --
    // the range candle lives on the higher timeframe and the sweep on the lower
    // -- so it is required there and merely encouraged everywhere else.
    const rawHtf = String(body.htfImageBase64 ?? "");
    const htfImage = rawHtf ? decodeImage(rawHtf) : null;
    if (rawHtf && !htfImage) {
      return json({ error: "The higher-timeframe image could not be read. Use a PNG or JPEG under 5 MB." }, 400);
    }
    // Only builds that can send a second chart say so; an older Android build
    // scanning CRT keeps working on one chart (capped confidence) until it updates.
    if (strategyKey === "CRT" && !htfImage && body.htf_capable === true) {
      return json({
        error: `CRT needs two screenshots: the ${style.htf} chart for the range and the ${style.ltf} chart for the entry.`,
        code: "NEED_HTF",
      }, 400);
    }
    if (UNSUPPORTED_SYNTHETIC_RE.test(pair)) {
      return json({ error: UNSUPPORTED_MSG, code: "UNSUPPORTED_SYMBOL" }, 422);
    }
    const syntheticByPair = VOLATILITY_RE.test(pair);

    // ---- Entitlement --------------------------------------------------------
    // The scanner is a separate R349 purchase and this endpoint spends quota on
    // every call. Checked here rather than trusted from the app: the only
    // credential the client holds is the anon key, which ships inside the APK,
    // so a gate enforced only in the UI is a gate anyone can walk around.
    const email = String(body.email ?? "").trim().toLowerCase();
    const deviceId = String(body.android_id ?? body.deviceId ?? "").trim();

    if (!email || !deviceId) {
      return json({ error: "Sign in with the email you bought the scanner with.", code: "NO_IDENTITY" }, 401);
    }

    const novaHost = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""
    );

    const { data: sub, error: subErr } = await novaHost
      .from("subscriptions")
      .select("has_scanner, device_id, is_premium, is_lifetime, subscription_expiry, scan_credits, free_scan_month")
      .eq("email", email)
      .maybeSingle();

    if (subErr) {
      console.error("[analyze-chart] entitlement lookup failed:", subErr.message);
      return json({ error: "Could not check your scanner access. Try again." }, 503);
    }

    // Three ways in: the old R349 unlock (unlimited), the one free scan a month
    // that active app access carries, or a bought credit (SCANS20). Checked
    // here so the Gemini call is never made for someone with nothing to
    // spend; the actual spend happens only after a reading comes back, so a
    // failed scan costs nothing.
    const appActive = sub?.is_lifetime === true || (sub?.is_premium === true &&
      (!sub?.subscription_expiry || new Date(sub.subscription_expiry) > new Date()));
    const freeAvailable = appActive && sub?.free_scan_month !== new Date().toISOString().slice(0, 7);
    const canScan = sub?.has_scanner === true || freeAvailable || (sub?.scan_credits ?? 0) > 0;

    if (!canScan) {
      return json({
        error: sub?.has_scanner === false && (sub?.scan_credits ?? 0) === 0 && appActive
          ? "You've used this month's free scan. Buy 20 scans to keep going."
          : "The AI chart scanner is not unlocked on this email.",
        code: "SCANNER_LOCKED",
      }, 403);
    }

    // Same one-email-one-device rule check-subscription-status applies. Fails
    // closed: an unbound row is not yet a licence to spend.
    if (sub?.device_id && sub.device_id !== deviceId) {
      return json({ error: "The scanner is active on another device.", code: "DEVICE_MISMATCH" }, 403);
    }

    // ---- Read the chart -----------------------------------------------------
    // The model has no clock, so it is given one. Without it `last_candle_iso`
    // cannot resolve a year -- a MetaTrader time axis prints "11 Sep 22:15" and
    // nothing else -- and a chart from last December would date itself to this
    // one.
    const nowUtc = new Date();
    const context = [
      pair ? `The trader has selected ${pair} in the app.` : null,
      `They are scanning for a ${style.label} trade.`,
      htfImage
        ? `Two screenshots are attached. The FIRST is the higher timeframe (expected ${style.htf}) -- ` +
          `read trend, range and key levels from it. The SECOND is the entry timeframe (expected ` +
          `${style.ltf}) -- time the entry, stop and every other single-chart field from it. ` +
          `Report the first image's symbol and timeframe in htf_symbol_on_chart and htf_timeframe_on_chart.`
        : `Only one screenshot is attached; set htf_symbol_on_chart and htf_timeframe_on_chart to null.`,
      "If the chart is a Deriv Volatility index (V75, V100 and the like), apply the synthetic-index rules.",
      `The current time in UTC is ${nowUtc.toISOString().slice(0, 16)} ` +
        `(year ${nowUtc.getUTCFullYear()}). Use that year for last_candle_iso ` +
        `unless doing so would date the chart in the future.`,
      "Read this chart.",
      pair
        ? `If the symbol printed on the chart is not ${pair}, still report what the chart says in ` +
          `symbol_on_chart -- the mismatch is checked separately and matters.`
        : null,
    ].filter(Boolean).join(" ");

    const geminiRequest: RequestInit = {
      method: "POST",
      headers: {
        // Header rather than ?key= in the URL: query strings end up in access
        // logs and proxy caches, and this one is a credential.
        "x-goog-api-key": apiKey,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        // The strategy rides on the system instruction so it frames the whole
        // read, rather than arriving as one more sentence in the user turn.
        systemInstruction: {
          parts: [{
            text: [BASE_INSTRUCTION, strategyBrief, style.brief, SYNTHETIC_BRIEF, EXPLAIN_INSTRUCTION].join("\n\n"),
          }],
        },
        contents: [
          {
            role: "user",
            parts: [
              ...(htfImage ? [{ inline_data: { mime_type: htfImage.mediaType, data: htfImage.data } }] : []),
              { inline_data: { mime_type: image.mediaType, data: image.data } },
              { text: context },
            ],
          },
        ],
        generationConfig: {
          responseMimeType: "application/json",
          responseSchema: RESPONSE_SCHEMA,
          // Chart reading is not a creative task. The same screenshot should
          // produce the same levels twice.
          temperature: 0.2,
        },
      }),
    };

    // Retried rather than surfaced -- see GEMINI_ATTEMPTS. `detail` outlives the
    // loop because the refusal written below is shaped from the LAST failure.
    let geminiResponse!: Response;
    let detail = "";

    for (let attempt = 1; attempt <= GEMINI_ATTEMPTS; attempt++) {
      geminiResponse = await fetch(`${GEMINI_BASE}/${MODEL}:generateContent`, geminiRequest);
      if (geminiResponse.ok) break;

      detail = await geminiResponse.text();
      console.error(
        `[analyze-chart] Gemini ${geminiResponse.status} ` +
        `(attempt ${attempt}/${GEMINI_ATTEMPTS}): ${detail}`
      );

      const wait = retryAfterMs(geminiResponse.status, detail);
      if (wait === null || attempt === GEMINI_ATTEMPTS) break;

      await new Promise((resolve) =>
        setTimeout(resolve, Math.max(wait, GEMINI_BACKOFF_MS[attempt - 1] ?? 1800))
      );
    }

    if (!geminiResponse.ok) {
      // A busy minute and a spent day arrive as the same status and are not the
      // same failure. "Give it a minute" against an exhausted DAILY quota walks
      // a paying user into the same wall every minute until the quota rolls
      // over -- which is how a billing setting on a Google project turns into
      // "the scanner never works" in a support queue.
      if (geminiResponse.status === 429) {
        const daily = /PerDay/i.test(detail);
        if (daily) {
          console.error(
            "[analyze-chart] DAILY Gemini quota exhausted. This key is on the FREE TIER: " +
            "20 requests per day for the whole project, shared by every paying scanner user. " +
            "Enable billing on the Google AI project -- no code change can move this ceiling."
          );
        }
        return json(
          {
            error: daily
              ? "The scanner has hit a daily limit on our side, not on your account. Please try again later."
              : "The scanner is busy right now. Give it a minute and scan again.",
            code: daily ? "QUOTA_EXHAUSTED" : "RATE_LIMITED",
          },
          429
        );
      }

      // A retired or misspelled model is a configuration fault, not a transient
      // one, and "try again" is actively wrong advice for it -- retrying cannot
      // help and the user would keep doing it. Named separately so the fix
      // (override GEMINI_MODEL) is discoverable from the message.
      if (geminiResponse.status === 404) {
        console.error(`[analyze-chart] model "${MODEL}" was refused; set GEMINI_MODEL to a current one`);
        return json(
          { error: "The scanner's model is out of date. This needs fixing on the server.", code: "MODEL_UNAVAILABLE" },
          503
        );
      }

      return json({ error: "The scan did not complete. Try again.", code: "UPSTREAM_ERROR" }, 502);
    }

    const payload = await geminiResponse.json();

    // A safety block arrives as a 200 with no candidate. Checked before reading
    // content so it cannot be mistaken for a parse failure and retried into the
    // same wall.
    const blockReason = payload?.promptFeedback?.blockReason;
    if (blockReason) {
      console.warn("[analyze-chart] blocked:", blockReason);
      return json({ error: "That image could not be analysed. Try a different screenshot." }, 422);
    }

    const text = payload?.candidates?.[0]?.content?.parts?.[0]?.text;
    if (!text) {
      console.error("[analyze-chart] no text part:", JSON.stringify(payload).slice(0, 400));
      return json({ error: "The scan did not complete. Try again." }, 502);
    }

    const reading = JSON.parse(text) as Reading;

    if (!reading.chart_readable) {
      return json(
        {
          error: reading.unreadable_reason || "That image is not a readable price chart.",
          code: "NOT_A_CHART",
        },
        422
      );
    }

    // ---- Bracket sanity -----------------------------------------------------
    // The schema guarantees the fields exist and are numbers; it cannot
    // guarantee they are on the right sides of each other. A stop above entry on
    // a BUY sizes to a negative distance and the plan screen renders nonsense
    // around it, so it is refused here rather than shipped downstream.
    const wrongWayRound = reading.direction === "BUY"
      ? !(reading.sl < reading.entry)
      : !(reading.sl > reading.entry);

    if (wrongWayRound) {
      console.error(
        `[analyze-chart] rejected bracket: ${reading.direction} entry=${reading.entry} sl=${reading.sl}`
      );
      return json(
        { error: "The scan produced an inconsistent stop. Try again with a clearer chart.", code: "BAD_BRACKET" },
        422
      );
    }

    // ---- Is this chart still current? ---------------------------------------
    // The reason this endpoint exists is to price a live order, and nothing
    // upstream of here could tell a screenshot taken a minute ago from one taken
    // last week. A scan of a three-day-old gold chart returned a confident SELL
    // that scored MODERATE and armed the execute control; price had rallied
    // ninety dollars in the interval. The reading was not wrong about the chart.
    // It was answering a question about a market that had moved on.
    //
    // Two independent signals, because each one alone has a hole: the terminal's
    // own "Market closed" banner is exact but absent on a weekday screenshot,
    // and the time axis is always there but has to survive being read off an
    // image.
    //
    // The threshold is deliberately coarse. A MetaTrader chart runs on BROKER
    // server time, typically UTC+2 or +3, and the axis never says so -- so an
    // hour-by-hour comparison against UTC would call fresh charts stale all
    // morning. Twelve hours clears any broker offset in use and still catches
    // the case that matters, which is a chart from another session entirely.
    const STALE_AFTER_MINUTES = 12 * 60;

    let ageMinutes: number | null = null;
    if (reading.last_candle_iso) {
      // Normalised rather than trusted: the field is asked for as
      // `YYYY-MM-DDTHH:MM` and comes back as that most of the time, but a space
      // separator and a trailing `:00Z` are both within a model's idea of
      // following the instruction. Taking the first sixteen characters and
      // supplying the rest accepts every one of those spellings and rejects
      // anything shorter.
      const iso = reading.last_candle_iso.trim().replace(" ", "T").slice(0, 16);
      // `Date.parse` on a bare `YYYY-MM-DDTHH:MM` reads as UTC, which is the
      // assumption documented above rather than an oversight.
      const at = iso.length === 16 ? Date.parse(iso + ":00Z") : NaN;
      if (Number.isFinite(at)) {
        // Negative means the chart's clock is ahead of UTC -- a broker offset,
        // not a chart from the future. Floored at zero so it never reads as
        // "fresh by minus three hours".
        ageMinutes = Math.max(0, Math.round((nowUtc.getTime() - at) / 60_000));
      }
    }

    const tooOld = ageMinutes != null && ageMinutes > STALE_AFTER_MINUTES;
    const staleReason = reading.market_closed
      ? "This chart was taken with the market closed."
      : tooOld
        ? `The last candle on this chart is about ${describeAge(ageMinutes!)} old.`
        : null;

    // ---- Style and instrument checks ---------------------------------------
    // Each one blocks the execute control the same way a stale chart does: the
    // reading is still shown, the order is not armed.
    const blocks: string[] = [];
    const chartSymbol = `${reading.symbol_on_chart ?? ""} ${reading.htf_symbol_on_chart ?? ""}`;
    if (UNSUPPORTED_SYNTHETIC_RE.test(chartSymbol)) {
      return json({ error: UNSUPPORTED_MSG, code: "UNSUPPORTED_SYMBOL" }, 422);
    }
    const synthetic = syntheticByPair || VOLATILITY_RE.test(chartSymbol);

    const norm = (s: string | null) => String(s ?? "").toUpperCase().replace(/[^A-Z0-9]/g, "");
    if (htfImage && reading.symbol_on_chart && reading.htf_symbol_on_chart &&
        !norm(reading.symbol_on_chart).includes(norm(reading.htf_symbol_on_chart).slice(0, 6)) &&
        !norm(reading.htf_symbol_on_chart).includes(norm(reading.symbol_on_chart).slice(0, 6))) {
      blocks.push(`The two screenshots show different symbols (${reading.htf_symbol_on_chart} and ${reading.symbol_on_chart}).`);
    }
    if (htfImage && reading.timeframe_on_chart && reading.htf_timeframe_on_chart &&
        norm(reading.timeframe_on_chart) === norm(reading.htf_timeframe_on_chart)) {
      blocks.push(`Both screenshots are ${reading.timeframe_on_chart}. Send the ${style.htf} chart and the ${style.ltf} chart.`);
    }

    const risk = Math.abs(reading.entry - reading.sl);
    const rr = risk > 0 ? Math.abs(reading.tp - reading.entry) / risk : 0;
    if (rr < style.minRR) {
      blocks.push(`The target is only ${rr.toFixed(1)}R. A ${style.label} needs at least ${style.minRR}R.`);
    }

    // Scalps live on London and New York liquidity. Synthetics have no sessions.
    const utcHour = nowUtc.getUTCHours();
    const weekend = [0, 6].includes(nowUtc.getUTCDay());
    if (style === STYLES.scalp && !synthetic && (weekend || utcHour < 7 || utcHour >= 21)) {
      blocks.push("Scalps only run during the London and New York sessions (07:00-21:00 UTC, weekdays).");
    }

    // One chart is a weaker read than two. Confidence is capped and the app is
    // told why, so it can ask for the higher timeframe next time.
    if (!htfImage && reading.confidence > 70) reading.confidence = 70;

    const usage = payload?.usageMetadata ?? {};
    console.log(
      `[analyze-chart] ${pair || "?"} ${strategyKey} ${reading.direction} ${reading.entry_type} ` +
      `entry=${reading.entry} sl=${reading.sl} bias=${reading.trend_bias} ` +
      `touches=${reading.key_level_touches} atr=${reading.average_candle_range} ` +
      `age=${ageMinutes ?? "?"}m closed=${reading.market_closed} stale=${!!staleReason} ` +
      `in=${usage.promptTokenCount} out=${usage.candidatesTokenCount}`
    );

    // Spent only now that there is a reading to hand back. Locked in SQL, so
    // two scans racing for the last credit cannot both take it -- the loser
    // still gets its result (the Gemini call is already paid for) and the
    // anomaly is logged.
    let scansLeft: number | null = null;
    const { data: spent, error: spendErr } = await novaHost.rpc("spend_scan", {
      p_email: email,
      p_free_ok: freeAvailable,
    });
    if (spendErr) console.error("[analyze-chart] spend_scan failed:", spendErr.message);
    else if (!spent) console.warn("[analyze-chart] scan returned with nothing left to spend for " + email);
    if (spent !== "unlimited") {
      const { data: after } = await novaHost
        .from("subscriptions").select("scan_credits").eq("email", email).maybeSingle();
      scansLeft = after?.scan_credits ?? 0;
    }

    return json({
      /** What this scan used ('unlimited' | 'free' | 'credit') and what is left. */
      scan_source: spent ?? null,
      scan_credits: scansLeft,
      ...reading,
      // Mirrored so a client build that reads either name still parses this.
      action: reading.direction,
      strategy: strategyKey,
      model: MODEL,
      /** True when this reading must not arm an execute control. */
      stale: !!staleReason,
      stale_reason: staleReason,
      /** Null when the time axis could not be read. See the note above. */
      chart_age_minutes: ageMinutes,
      style: style.label,
      min_rr: style.minRR,
      rr: Math.round(rr * 10) / 10,
      synthetic,
      two_charts: !!htfImage,
      /** Asks the app to suggest the higher-timeframe chart next time. */
      htf_suggested: !htfImage ? style.htf : null,
      /** True when the reading must not arm execute, for a reason other than age. */
      blocked: blocks.length > 0,
      block_reasons: blocks,
    });

  } catch (err) {
    console.error("[analyze-chart] failed:", err);
    return json({ error: (err as Error).message ?? "The scan did not complete." }, 500);
  }
});
