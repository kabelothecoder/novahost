import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

/**
 * The AI Chart Scanner's vision pass, on Gemini.
 *
 * Takes a chart screenshot and returns the reading the confluence engine scores:
 * a direction, an entry and a stop, the pattern, the higher-timeframe bias, the
 * nearest structural level with its touch count, a volatility measure, and a
 * per-timeframe read.
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
  },
  required: [
    "chart_readable", "unreadable_reason", "symbol_on_chart", "timeframe_on_chart",
    "direction", "confidence", "entry", "entry_type", "entry_note", "sl", "tp", "patterns",
    "trend_bias", "trend_timeframe", "key_level", "key_level_touches",
    "average_candle_range", "timeframes", "narrative",
  ],
  propertyOrdering: [
    "chart_readable", "unreadable_reason", "symbol_on_chart", "timeframe_on_chart",
    "direction", "confidence", "entry", "entry_type", "entry_note", "sl", "tp", "patterns",
    "trend_bias", "trend_timeframe", "key_level", "key_level_touches",
    "average_candle_range", "timeframes", "narrative",
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

Confidence: what the visible evidence supports. A clean structure at a well-tested level is high. A guess from a noisy crop is low, and saying so is more useful than a confident number that is wrong.

If the image is not a readable price chart at all, set chart_readable false, give one sentence in unreadable_reason, and fill the remaining fields with any valid values -- they will be discarded.`;

interface Reading {
  chart_readable: boolean;
  unreadable_reason: string | null;
  symbol_on_chart: string | null;
  timeframe_on_chart: string | null;
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
    const apiKey = Deno.env.get("GEMINI_API_KEY");
    if (!apiKey) {
      // Named explicitly. The predecessor's equivalent failure surfaced to the
      // user as "Incorrect API key provided: undefined", which reads like a
      // problem with their account rather than an unset server secret.
      console.error("[analyze-chart] GEMINI_API_KEY is not set on this project.");
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

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""
    );

    const { data: sub, error: subErr } = await supabase
      .from("subscriptions")
      .select("has_scanner, device_id")
      .eq("email", email)
      .maybeSingle();

    if (subErr) {
      console.error("[analyze-chart] entitlement lookup failed:", subErr.message);
      return json({ error: "Could not check your scanner access. Try again." }, 503);
    }

    if (!sub?.has_scanner) {
      return json({ error: "The AI chart scanner is not unlocked on this email.", code: "SCANNER_LOCKED" }, 403);
    }

    // Same one-email-one-device rule check-subscription-status applies. Fails
    // closed: an unbound row is not yet a licence to spend.
    if (sub.device_id && sub.device_id !== deviceId) {
      return json({ error: "The scanner is active on another device.", code: "DEVICE_MISMATCH" }, 403);
    }

    // ---- Read the chart -----------------------------------------------------
    const context = [
      pair ? `The trader has selected ${pair} in the app.` : null,
      mode ? `They are scanning for a ${mode.toLowerCase()} trade.` : null,
      "Read this chart.",
      pair
        ? `If the symbol printed on the chart is not ${pair}, still report what the chart says in ` +
          `symbol_on_chart -- the mismatch is checked separately and matters.`
        : null,
    ].filter(Boolean).join(" ");

    const geminiResponse = await fetch(`${GEMINI_BASE}/${MODEL}:generateContent`, {
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
          parts: [{ text: BASE_INSTRUCTION + "\n\n" + strategyBrief }],
        },
        contents: [
          {
            role: "user",
            parts: [
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
    });

    if (!geminiResponse.ok) {
      const detail = await geminiResponse.text();
      console.error(`[analyze-chart] Gemini ${geminiResponse.status}: ${detail}`);

      // 429 is the free tier's shape of failure, not a bug, and it is the one
      // the user can act on -- waiting works. Named so it does not read as a
      // broken scanner.
      if (geminiResponse.status === 429) {
        return json(
          { error: "The scanner is busy right now. Give it a minute and scan again.", code: "RATE_LIMITED" },
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

    const usage = payload?.usageMetadata ?? {};
    console.log(
      `[analyze-chart] ${pair || "?"} ${strategyKey} ${reading.direction} ${reading.entry_type} ` +
      `entry=${reading.entry} sl=${reading.sl} bias=${reading.trend_bias} ` +
      `touches=${reading.key_level_touches} atr=${reading.average_candle_range} ` +
      `in=${usage.promptTokenCount} out=${usage.candidatesTokenCount}`
    );

    return json({
      ...reading,
      // Mirrored so a client build that reads either name still parses this.
      action: reading.direction,
      strategy: strategyKey,
      model: MODEL,
    });

  } catch (err) {
    console.error("[analyze-chart] failed:", err);
    return json({ error: (err as Error).message ?? "The scan did not complete." }, 500);
  }
});
