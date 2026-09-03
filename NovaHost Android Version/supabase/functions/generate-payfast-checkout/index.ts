import { serve } from "https://deno.land/std@0.208.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import crypto from "node:crypto";

/**
 * Builds a Payfast checkout URL for one of the app's three once-off purchases.
 *
 *   APP         R599  lifetime access to the app        -> subscriptions.is_lifetime
 *   SCANNER     R349  the AI chart scanner              -> subscriptions.has_scanner
 *   REACTIVATE  R150  move a paid email to a new device -> subscriptions.device_id
 *
 * Nothing here is recurring any more. The R250/month subscription this function
 * used to sell is gone: every product is a single payment, so the recurring
 * parameters (subscription_type / frequency / cycles) are never sent. Sending
 * them on a once-off is what makes Payfast set up a billing token, and a token
 * against a lifetime purchase bills a customer who has already paid in full.
 *
 * The entitlement *check* is not here -- that is check-subscription-status.
 * This function only ever answers "what would it cost and where do they pay".
 */

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

// Production only. The sandbox branch, its hardcoded test merchant, and the
// ENVIRONMENT switch are gone -- this endpoint talks to live Payfast and
// nothing else. The `PAYFAST_NOT_CONFIGURED` guard below is what stands in for
// the old sandbox fallback: a missing secret fails loudly rather than quietly
// signing a checkout with an empty merchant_id.
const MERCHANT_ID = Deno.env.get("PAYFAST_MERCHANT_ID") || "";
const MERCHANT_KEY = Deno.env.get("PAYFAST_MERCHANT_KEY") || "";
const PASSPHRASE = Deno.env.get("PAYFAST_PASSPHRASE") || "";
const PAYFAST_URL = "https://www.payfast.co.za/eng/process";

/** Charged amounts -- R1 over the displayed R599/R349 (marketing price is the
 * round number; collected price absorbs the card fee). The app shows its own
 * R599/R349 labels from PaywallOverlay.kt, not these. Keep in sync with
 * payfast-webhook.EXPECTED_AMOUNT. */
const PRICES = {
  APP: { amount: "600.00", item_name: "NovaHost App Access", custom_str1: "LIFETIME" },
  SCANNER: { amount: "350.00", item_name: "NovaHost AI Chart Scanner", custom_str1: "SCANNER" },
  REACTIVATE: { amount: "150.00", item_name: "NovaHost Device Reactivation", custom_str1: "REACTIVATION" },
} as const;

type ProductKey = keyof typeof PRICES;

// ── Move policy ────────────────────────────────────────────────────────────
//
// Keep identical to check-subscription-status, request-device-move and
// payfast-webhook.
//
// Enforced here so the user never reaches a Payfast page for a move the webhook
// is going to refuse. Enforced AGAIN in the webhook because everything on this
// page comes back through the browser, and the only copy of the rule that
// matters is the one running while the money is in the room.
const MOVE_COOLDOWN_DAYS = 30;
const MOVE_LIMIT_PER_YEAR = 2;

const DAY_MS = 24 * 60 * 60 * 1000;

/** Wrong codes tolerated on one ticket before it is dead. */
const MAX_CODE_ATTEMPTS = 5;

/**
 * SHA-256 of a move code, to compare against the stored hash.
 *
 * `globalThis.crypto.subtle`, not `crypto.subtle`: this module imports
 * node:crypto at the top for the MD5 the Payfast signature needs, and that
 * import shadows the Web Crypto global for the whole file. `crypto.subtle`
 * here would resolve against Deno's node shim rather than Web Crypto, and a
 * missing `.subtle` there is a 500 on every device move -- the one code path
 * that has no way to retry into a working state.
 */
async function sha256Hex(value: string): Promise<string> {
  const digest = await globalThis.crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

async function moveEligibility(
  // deno-lint-ignore no-explicit-any
  novaHost: any,
  email: string,
) {
  const since = new Date(Date.now() - 365 * DAY_MS).toISOString();

  const { data: moves, error } = await novaHost
    .from("subscription_device_events")
    .select("created_at")
    .eq("email", email)
    .eq("event", "move")
    .gte("created_at", since)
    .order("created_at", { ascending: false });

  if (error) throw error;

  const used = moves?.length ?? 0;
  const lastAt = moves?.[0]?.created_at ? new Date(moves[0].created_at) : null;

  if (lastAt) {
    const until = new Date(lastAt.getTime() + MOVE_COOLDOWN_DAYS * DAY_MS);
    if (until > new Date()) {
      return { eligible: false, reason: "cooldown" as const, available_at: until.toISOString(), moves_used: used, moves_allowed: MOVE_LIMIT_PER_YEAR };
    }
  }

  if (used >= MOVE_LIMIT_PER_YEAR) {
    const oldest = moves?.[moves.length - 1]?.created_at;
    return {
      eligible: false,
      reason: "limit_reached" as const,
      available_at: oldest ? new Date(new Date(oldest).getTime() + 365 * DAY_MS).toISOString() : null,
      moves_used: used,
      moves_allowed: MOVE_LIMIT_PER_YEAR,
    };
  }

  return { eligible: true, reason: "ok" as const, available_at: null, moves_used: used, moves_allowed: MOVE_LIMIT_PER_YEAR };
}

/**
 * Percent-encoding that matches PHP's `urlencode`, which is what Payfast hashes.
 *
 * `encodeURIComponent` leaves `! ' ( ) *` unescaped; PHP escapes all five, so
 * this re-escapes them, and the outgoing URL query is built with this SAME
 * function so the signature string and the query never disagree on a character.
 *
 * The parentheses were still a live blocker on PRODUCTION: an item_name of
 * "NovaHost App Access (once-off)" -- signed as `%28once-off%29` and submitted
 * the same -- was answered with 400 "Generated signature does not match
 * submitted signature", while the identical checkout with the parens dropped
 * went straight through. Sandbox had accepted both. So the item_names are kept
 * parenthesis-free (belt) and this encoder escapes parens anyway (braces).
 */
function payfastEncode(value: string): string {
  return encodeURIComponent(value.trim())
    .replace(/%20/g, "+")
    .replace(/[!'()*]/g, (c) => "%" + c.charCodeAt(0).toString(16).toUpperCase());
}

/**
 * Payfast checkout signature.
 *
 * The pairs are hashed in the order they appear in Payfast's attributes
 * description -- merchant_id, merchant_key, return_url, cancel_url, notify_url,
 * ... email_address, ... amount, item_name, ... custom_str1..5 -- which is the
 * order [payload] below is declared in. JavaScript preserves string-key
 * insertion order, so `Object.keys` reproduces it exactly.
 *
 * NOT sorted. Payfast's docs are explicit that the alphabetical ordering
 * belongs to their API signature format and must not be used here. The sandbox
 * turns out to normalise the order before hashing and accepts either, but the
 * documented order is what production is entitled to expect and it costs
 * nothing to send.
 */
function generatePayfastSignature(data: Record<string, string>, passphrase: string): string {
  const parts = [];
  for (const key of Object.keys(data)) {
    if (data[key] !== undefined && data[key] !== null && data[key] !== "") {
      parts.push(`${key}=${payfastEncode(data[key])}`);
    }
  }

  let pfOutput = parts.join("&");

  if (passphrase) {
    pfOutput += `&passphrase=${payfastEncode(passphrase)}`;
  }

  return crypto.createHash("md5").update(pfOutput).digest("hex");
}

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: CORS_HEADERS });
  }

  const json = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), {
      status,
      headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
    });

  // The old sandbox fallback is gone, so a missing merchant secret can no
  // longer be papered over with test credentials -- it has to fail here.
  // Without this guard the checkout still builds, just with merchant_id="",
  // and Payfast answers with a generic error on ITS page after the user has
  // already left the app. That reads as "the app is broken" to everyone at
  // once, with nothing in our logs to say why. Fail loudly and by name.
  const missing = [
    !MERCHANT_ID && "PAYFAST_MERCHANT_ID",
    !MERCHANT_KEY && "PAYFAST_MERCHANT_KEY",
  ].filter(Boolean);

  if (missing.length > 0) {
    console.error(`[payfast] these secrets are unset: ${missing.join(", ")}`);
    return json(
      {
        error: "Payments are not configured on the server. Please contact support.",
        code: "PAYFAST_NOT_CONFIGURED",
        missing,
      },
      503,
    );
  }

  // Not fatal: Payfast passphrases are optional, and an account that has none
  // must sign with none. But an account that HAS one and is missing it here
  // fails every signature, so make the choice visible in the logs.
  if (!PASSPHRASE) {
    console.warn(
      "[payfast] PAYFAST_PASSPHRASE is unset -- signing without a passphrase. " +
        "If a passphrase is set on the Payfast account, every payment will be " +
        "refused with a signature mismatch.",
    );
  }

  try {
    const body = await req.json().catch(() => ({}));
    const email = String(body.email ?? "").trim();
    const androidId = String(body.android_id ?? body.deviceId ?? "").trim();
    // Defaults to APP so an older build that does not send `product` still gets
    // the app gate rather than a 400.
    const requested = String(body.product ?? "APP").trim().toUpperCase();
    // The six digits from request-device-move's email. Only a move needs one.
    const moveCode = String(body.move_code ?? "").trim();

    if (!email || !androidId) {
      return json({ error: "Missing email or android_id" }, 400);
    }

    // A handset that cannot name itself must not be able to buy a binding.
    if (androidId === "UNKNOWN_DEVICE") {
      return json({ error: "This device could not be identified." }, 400);
    }

    if (requested !== "APP" && requested !== "SCANNER" && requested !== "REACTIVATE") {
      return json({ error: `Unknown product "${requested}".` }, 400);
    }

    const novaHost = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
    );

    const cleanEmail = email.toLowerCase();

    const { data: sub, error } = await novaHost
      .from("subscriptions")
      .select("id, email, is_premium, is_lifetime, has_scanner, device_id")
      .eq("email", cleanEmail)
      .maybeSingle();

    if (error) {
      console.error("Database error:", error);
    }

    const paid = sub?.is_lifetime === true || sub?.is_premium === true;
    const deviceMatches = !sub?.device_id || sub.device_id === androidId;

    // ---- Decide which product this request actually needs ------------------
    let product: ProductKey;
    let route: string;

    if (requested === "SCANNER") {
      if (sub?.has_scanner === true && deviceMatches) {
        return json({ route: "SCANNER_OWNED", message: "The scanner is already unlocked for this email." });
      }
      // A scanner purchase on a handset that does not hold the app licence is
      // a dead end -- they would pay and still hit the app lock. Say so rather
      // than taking the money.
      if (paid && !deviceMatches) {
        return json({ route: "ACTIVE_NEW_DEVICE_BLOCKED", message: "This email is active on another device. Move it across before buying the scanner." });
      }
      product = "SCANNER";
      route = "SCANNER_CHECKOUT";
    } else if (paid && deviceMatches) {
      // Covers an explicit REACTIVATE request too -- there is nothing to move.
      return json({ route: "ACTIVE_SAME_DEVICE", message: "App access is already active on this device." });
    } else if (paid && !deviceMatches) {
      // ---- The R150 move ---------------------------------------------------
      // Refusals here are 200s carrying a `route`, not errors. The app reads
      // `route` to choose its copy, and an `error` would collapse all of these
      // into one "that purchase could not be started".
      const eligibility = await moveEligibility(novaHost, cleanEmail);

      if (!eligibility.eligible) {
        return json({
          route: eligibility.reason === "cooldown" ? "MOVE_COOLDOWN" : "MOVE_LIMIT_REACHED",
          move: eligibility,
          message: eligibility.reason === "cooldown"
            ? "This licence was moved recently. It can be moved again after the cooldown."
            : `This licence has already been moved ${eligibility.moves_used} times in the last year. Contact support to move it again.`,
        });
      }

      // Proof of mailbox control. Without it, R150 evicts a stranger.
      if (!moveCode) {
        return json({
          route: "MOVE_CODE_REQUIRED",
          move: eligibility,
          message: "We'll email a 6-digit code to confirm this licence is yours.",
        });
      }

      const { data: ticket, error: ticketErr } = await novaHost
        .from("device_move_tickets")
        .select("id, code_hash, attempts, expires_at")
        .eq("email", cleanEmail)
        .eq("target_device_id", androidId)
        .is("consumed_at", null)
        .gt("expires_at", new Date().toISOString())
        .order("created_at", { ascending: false })
        .limit(1)
        .maybeSingle();

      if (ticketErr) throw ticketErr;

      if (!ticket) {
        return json({
          route: "MOVE_CODE_REQUIRED",
          move: eligibility,
          message: "That code has expired. Request a new one.",
        });
      }

      if (ticket.attempts >= MAX_CODE_ATTEMPTS) {
        return json({
          route: "MOVE_CODE_REQUIRED",
          move: eligibility,
          message: "Too many wrong codes. Request a new one.",
        });
      }

      if (await sha256Hex(moveCode) !== ticket.code_hash) {
        await novaHost
          .from("device_move_tickets")
          .update({ attempts: ticket.attempts + 1 })
          .eq("id", ticket.id);

        return json({
          route: "MOVE_CODE_INVALID",
          move: eligibility,
          message: `That code is not right. ${MAX_CODE_ATTEMPTS - ticket.attempts - 1} tries left.`,
        });
      }

      // Verified, not consumed. The webhook consumes it when the money lands,
      // so abandoning the Payfast page and coming back does not cost a new code.
      await novaHost
        .from("device_move_tickets")
        .update({ verified_at: new Date().toISOString() })
        .eq("id", ticket.id);

      product = "REACTIVATE";
      route = "ACTIVE_NEW_DEVICE";
    } else {
      product = "APP";
      route = "NEW_USER";
    }

    const price = PRICES[product];
    const novaHostUrl = Deno.env.get("SUPABASE_URL")!;

    const payload: Record<string, string> = {
      merchant_id: MERCHANT_ID,
      merchant_key: MERCHANT_KEY,
      return_url: `${novaHostUrl}/functions/v1/payment-redirect?status=success`,
      cancel_url: `${novaHostUrl}/functions/v1/payment-redirect?status=cancel`,
      notify_url: `${novaHostUrl}/functions/v1/payfast-webhook`,
      email_address: email,
      // amount BEFORE item_name. This object's declaration order IS the
      // signature order (see generatePayfastSignature), and Payfast's
      // attributes description puts the transaction amount ahead of its name.
      amount: price.amount,
      item_name: price.item_name,
      custom_str1: price.custom_str1,
      custom_str2: androidId,
      custom_str3: cleanEmail,
    };

    // Built with the same encoder the signature was hashed with, rather than
    // through URLSearchParams. The two disagree on exactly the characters that
    // broke this -- URLSearchParams escapes parentheses, encodeURIComponent did
    // not -- and a checkout URL whose encoding differs from its own signature is
    // the failure this function just came out of. One encoder, both places.
    payload.signature = generatePayfastSignature(payload, PASSPHRASE);
    const query = Object.entries(payload)
      .map(([key, value]) => `${key}=${payfastEncode(value)}`)
      .join("&");

    return json({
      route,
      product,
      amount: price.amount,
      item_name: price.item_name,
      checkout_url: `${PAYFAST_URL}?${query}`,
    });

  } catch (err) {
    console.error("generate-payfast-checkout error:", err);
    return json({ error: "Internal server error" }, 500);
  }
});
