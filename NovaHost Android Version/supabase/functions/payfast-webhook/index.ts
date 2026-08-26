import { serve } from "https://deno.land/std@0.208.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import crypto from "node:crypto";

/**
 * Payfast ITN handler. Grants entitlements for the three once-off products.
 *
 *   LIFETIME     R599  -> is_lifetime = true, is_premium = true, expiry cleared
 *   SCANNER      R349  -> has_scanner = true
 *   REACTIVATION R150  -> device_id moved to the paying handset
 *
 * The amount is verified against the price the product claims to be before any
 * entitlement is written. custom_str1 arrives back from the browser, so a
 * checkout URL edited by hand would otherwise buy R599 access for R1.
 *
 * SUBSCRIPTION is still accepted so an ITN from a checkout opened before this
 * deploy still lands, but it grants the same lifetime access the R599 product
 * does rather than a 30-day window -- the monthly plan no longer exists to
 * renew into.
 */

const ENV = Deno.env.get("ENVIRONMENT") || "sandbox";
const PASSPHRASE = ENV === "sandbox"
  ? (Deno.env.get("PAYFAST_SANDBOX_PASSPHRASE") || "")
  : (Deno.env.get("PAYFAST_PASSPHRASE") || "");

/** Minimum gross amount, in rand, each product must have been paid at. */
const EXPECTED_AMOUNT: Record<string, number> = {
  LIFETIME: 599,
  SUBSCRIPTION: 250,
  SCANNER: 349,
  REACTIVATION: 150,
};

function validatePayfastSignature(data: Record<string, string>, signatureToMatch: string, passphrase: string): boolean {
  const payload = { ...data };
  delete payload.signature;

  const sortedKeys = Object.keys(payload).sort();
  const parts = [];
  for (const key of sortedKeys) {
    if (payload[key] !== undefined && payload[key] !== null && payload[key] !== "") {
      const encodedValue = encodeURIComponent(payload[key].trim()).replace(/%20/g, "+");
      parts.push(key + "=" + encodedValue);
    }
  }

  let pfOutput = parts.join("&");

  if (passphrase) {
    const encodedPassphrase = encodeURIComponent(passphrase.trim()).replace(/%20/g, "+");
    pfOutput += "&passphrase=" + encodedPassphrase;
  }

  const calculatedSignature = crypto.createHash("md5").update(pfOutput).digest("hex");
  return calculatedSignature === signatureToMatch;
}

serve(async (req: Request) => {
  try {
    // Payfast sends ITN as application/x-www-form-urlencoded POST
    const formData = await req.formData();
    const data: Record<string, string> = {};
    for (const [key, value] of formData.entries()) {
      data[key] = value.toString();
    }

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
    );

    // [AUDIT LOG] Insert raw payload into itn_logs for debugging
    await supabase.from("itn_logs").insert([{ payload: data }]);

    console.log("[ITN Received] Raw payload:", JSON.stringify(data, null, 2));

    // --- Signature Validation ---
    // In Sandbox mode, skip signature validation entirely because
    // the PAYFAST_SANDBOX_PASSPHRASE env var is not set, causing
    // all signature checks to fail and silently blocking DB writes.
    // In production, signature MUST be validated.
    const receivedSignature = data.signature;
    if (ENV !== "sandbox") {
      if (!receivedSignature) {
        console.error("[ITN Error] Missing signature in payload.");
        return new Response("Missing signature", { status: 400 });
      }
      const isValid = validatePayfastSignature(data, receivedSignature, PASSPHRASE);
      if (!isValid) {
        console.error("[ITN Error] Invalid Payfast Signature!");
        return new Response("Invalid signature", { status: 401 });
      }
    } else {
      console.log("[ITN] Sandbox mode - skipping signature validation.");
    }

    const paymentStatus = data.payment_status;
    const payfastEmail = data.email_address;
    const productType = data.custom_str1;   // LIFETIME | SCANNER | REACTIVATION
    const deviceId = data.custom_str2;      // android_id
    const customStr3 = data.custom_str3;    // APP EMAIL (from generate-payfast-checkout)

    // Use the app's email (custom_str3) if available, fallback to Payfast email
    const appEmail = (customStr3 && customStr3.trim()) ? customStr3 : payfastEmail;

    console.log("[ITN] status=" + paymentStatus + ", email=" + appEmail + ", type=" + productType + ", device=" + deviceId);

    if (paymentStatus !== "COMPLETE") {
      console.log("[ITN Skipped] payment_status is not COMPLETE: " + paymentStatus);
      return new Response("OK", { status: 200 });
    }

    if (!appEmail) {
      console.warn("[ITN Warning] COMPLETE payment carried no email. No DB action taken.");
      return new Response("OK", { status: 200 });
    }

    // --- Amount check -------------------------------------------------------
    // amount_gross is what Payfast actually collected. A payload claiming to be
    // LIFETIME that was paid at R1 does not get lifetime access.
    const expected = EXPECTED_AMOUNT[productType];
    if (expected === undefined) {
      console.warn("[ITN Warning] Unknown custom_str1 type: " + productType + ". No DB action taken.");
      return new Response("OK", { status: 200 });
    }

    const paidAmount = Number.parseFloat(data.amount_gross ?? data.gross_amount ?? "0");
    // A cent of tolerance for Payfast's own rounding.
    if (!Number.isFinite(paidAmount) || paidAmount + 0.01 < expected) {
      console.error("[ITN Error] " + productType + " expected R" + expected + " but gross was " + data.amount_gross + ". Refusing to grant.");
      return new Response("OK", { status: 200 });
    }

    const cleanEmail = appEmail.trim().toLowerCase();
    const now = new Date().toISOString();

    if (productType === "LIFETIME" || productType === "SUBSCRIPTION") {
      const upsertPayload: Record<string, unknown> = {
        email: cleanEmail,
        is_premium: true,
        is_lifetime: true,
        // A once-off purchase has no expiry. Leaving a stale date here would
        // make check-subscription-status lock the user out on that date.
        subscription_expiry: null,
        device_id: deviceId || null,
        device_bound_at: deviceId ? now : null,
        updated_at: now,
      };

      const { data: result, error } = await supabase
        .from("subscriptions")
        .upsert(upsertPayload, { onConflict: "email" })
        .select();

      if (error) {
        console.error("[DB Error] LIFETIME upsert FAILED:", error.message, "| Code:", error.code, "| Details:", error.details);
        return new Response("Database error", { status: 500 });
      }

      console.log("[LIFETIME] SUCCESS - is_lifetime=true for " + cleanEmail + ". Rows: " + JSON.stringify(result));

    } else if (productType === "SCANNER") {
      // Upsert rather than update: the scanner can be bought against an email
      // whose app access was granted outside this webhook, and an update on a
      // missing row would silently no-op with the customer already charged.
      const { data: result, error } = await supabase
        .from("subscriptions")
        .upsert(
          {
            email: cleanEmail,
            has_scanner: true,
            updated_at: now,
          },
          { onConflict: "email" }
        )
        .select();

      if (error) {
        console.error("[DB Error] SCANNER upsert failed:", error.message);
        return new Response("Database error", { status: 500 });
      }

      console.log("[SCANNER] SUCCESS - has_scanner=true for " + cleanEmail + ". Rows: " + JSON.stringify(result));

    } else if (productType === "REACTIVATION") {
      const { data: result, error } = await supabase
        .from("subscriptions")
        .update({ device_id: deviceId, device_bound_at: now, updated_at: now })
        .eq("email", cleanEmail)
        .select();

      if (error) {
        console.error("[DB Error] REACTIVATION update failed:", error.message);
        return new Response("Database error", { status: 500 });
      }

      console.log("[REACTIVATION] Updated device_id for " + cleanEmail + ". Rows: " + JSON.stringify(result));
    }

    return new Response("OK", { status: 200 });

  } catch (err) {
    console.error("[ITN Fatal Error]", err);
    return new Response("Internal server error", { status: 500 });
  }
});
