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

function generatePayfastSignature(data: Record<string, string>, passphrase: string): string {
  const sortedKeys = Object.keys(data).sort();

  const parts = [];
  for (const key of sortedKeys) {
    if (data[key] !== undefined && data[key] !== null && data[key] !== "") {
      const encodedValue = encodeURIComponent(data[key].trim()).replace(/%20/g, "+");
      parts.push(`${key}=${encodedValue}`);
    }
  }

  let pfOutput = parts.join("&");

  if (passphrase) {
    const encodedPassphrase = encodeURIComponent(passphrase.trim()).replace(/%20/g, "+");
    pfOutput += `&passphrase=${encodedPassphrase}`;
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
      item_name: price.item_name,
      amount: price.amount,
      custom_str1: price.custom_str1,
      custom_str2: androidId,
      custom_str3: cleanEmail,
    };

    payload.signature = generatePayfastSignature(payload, PASSPHRASE);

    const finalUrl = new URL(PAYFAST_URL);
    for (const [key, value] of Object.entries(payload)) {
      finalUrl.searchParams.append(key, value);
    }

    return json({
      route,
      product,
      amount: price.amount,
      item_name: price.item_name,
      checkout_url: finalUrl.toString(),
    });

  } catch (err) {
    console.error("generate-payfast-checkout error:", err);
    return json({ error: "Internal server error" }, 500);
  }
});
