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

const ENV = Deno.env.get("ENVIRONMENT") || "sandbox";

let MERCHANT_ID: string;
let MERCHANT_KEY: string;
let PASSPHRASE: string;
let PAYFAST_URL: string;

if (ENV === "sandbox") {
  MERCHANT_ID = "10049163";
  MERCHANT_KEY = "v49ihv914w42y";
  PASSPHRASE = Deno.env.get("PAYFAST_SANDBOX_PASSPHRASE") || "";
  PAYFAST_URL = "https://sandbox.payfast.co.za/eng/process";
} else {
  MERCHANT_ID = Deno.env.get("PAYFAST_MERCHANT_ID") || "";
  MERCHANT_KEY = Deno.env.get("PAYFAST_MERCHANT_KEY") || "";
  PASSPHRASE = Deno.env.get("PAYFAST_PASSPHRASE") || "";
  PAYFAST_URL = "https://www.payfast.co.za/eng/process";
}

/** The price list. One place, so the app and the ITN can never disagree about it. */
const PRICES = {
  APP: { amount: "599.00", item_name: "NovaHost App Access (once-off)", custom_str1: "LIFETIME" },
  SCANNER: { amount: "349.00", item_name: "NovaHost AI Chart Scanner (once-off)", custom_str1: "SCANNER" },
  REACTIVATE: { amount: "150.00", item_name: "NovaHost Device Reactivation", custom_str1: "REACTIVATION" },
} as const;

type ProductKey = keyof typeof PRICES;

/**
 * Percent-encoding that matches PHP's `urlencode`, which is what Payfast hashes.
 *
 * This one character class is why nobody has ever completed an R599 or R349
 * purchase. `encodeURIComponent` leaves `! ' ( ) *` unescaped; PHP escapes all
 * five. Two of the three item names below contain "(once-off)", so the app and
 * the scanner were signed over `...Scanner+(once-off)` while the browser
 * submitted `...Scanner+%28once-off%29`, and Payfast answered every one of them
 * with "Generated signature does not match submitted signature".
 *
 * Payfast's own checkout page confirms it: signing with this encoder is
 * accepted, signing with the bare `encodeURIComponent` is refused.
 *
 * "NovaHost Device Reactivation" has no parentheses -- which is the entire
 * reason the R150 device moves are the only payments that have gone through
 * recently, and why this looked like a product problem rather than a bug.
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

  // Going live is four Supabase secrets, and three of them are easy to forget.
  // Without this guard a production deploy with a missing secret still builds a
  // perfectly well-formed checkout -- just with merchant_id="" -- and Payfast
  // answers with a generic error on ITS page, after the user has already left
  // the app. That reads as "the app is broken" to 250 people at once, and there
  // is nothing in our logs to say why. Fail here instead, loudly and by name.
  if (ENV !== "sandbox") {
    const missing = [
      !MERCHANT_ID && "PAYFAST_MERCHANT_ID",
      !MERCHANT_KEY && "PAYFAST_MERCHANT_KEY",
    ].filter(Boolean);

    if (missing.length > 0) {
      console.error(
        `[payfast] ENVIRONMENT=${ENV} but these secrets are unset: ${missing.join(", ")}`,
      );
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
  }

  try {
    const body = await req.json().catch(() => ({}));
    const email = String(body.email ?? "").trim();
    const androidId = String(body.android_id ?? body.deviceId ?? "").trim();
    // Defaults to APP so an older build that does not send `product` still gets
    // the app gate rather than a 400.
    const requested = String(body.product ?? "APP").trim().toUpperCase();

    if (!email || !androidId) {
      return json({ error: "Missing email or android_id" }, 400);
    }

    if (requested !== "APP" && requested !== "SCANNER") {
      return json({ error: `Unknown product "${requested}".` }, 400);
    }

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
    );

    const cleanEmail = email.toLowerCase();

    const { data: sub, error } = await supabase
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
      return json({ route: "ACTIVE_SAME_DEVICE", message: "App access is already active on this device." });
    } else if (paid && !deviceMatches) {
      product = "REACTIVATE";
      route = "ACTIVE_NEW_DEVICE";
    } else {
      product = "APP";
      route = "NEW_USER";
    }

    const price = PRICES[product];
    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;

    const payload: Record<string, string> = {
      merchant_id: MERCHANT_ID,
      merchant_key: MERCHANT_KEY,
      return_url: `${supabaseUrl}/functions/v1/payment-redirect?status=success`,
      cancel_url: `${supabaseUrl}/functions/v1/payment-redirect?status=cancel`,
      notify_url: `${supabaseUrl}/functions/v1/payfast-webhook`,
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

    payload.signature = generatePayfastSignature(payload, PASSPHRASE);

    // Built with the same encoder the signature was hashed with, rather than
    // through URLSearchParams. The two disagree on exactly the characters that
    // broke this -- URLSearchParams escapes parentheses, encodeURIComponent did
    // not -- and a checkout URL whose encoding differs from its own signature is
    // the failure this function just came out of. One encoder, both places.
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
