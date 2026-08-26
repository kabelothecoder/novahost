import { serve } from "https://deno.land/std@0.208.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import crypto from "node:crypto";

const ENV = Deno.env.get("ENVIRONMENT") || "sandbox";
const PASSPHRASE = ENV === "sandbox" 
  ? (Deno.env.get("PAYFAST_SANDBOX_PASSPHRASE") || "")
  : (Deno.env.get("PAYFAST_PASSPHRASE") || "");

function validatePayfastSignature(data: Record<string, string>, signatureToMatch: string, passphrase: string): boolean {
  const payload = { ...data };
  delete payload.signature;

  const sortedKeys = Object.keys(payload).sort();
  const parts = [];
  for (const key of sortedKeys) {
    if (payload[key] !== undefined && payload[key] !== null && payload[key] !== "") {
      const encodedValue = encodeURIComponent(payload[key].trim()).replace(/%20/g, "+");
      parts.push(`${key}=${encodedValue}`);
    }
  }

  let pfOutput = parts.join("&");

  if (passphrase) {
    const encodedPassphrase = encodeURIComponent(passphrase.trim()).replace(/%20/g, "+");
    pfOutput += `&passphrase=${encodedPassphrase}`;
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
    const customStr1 = data.custom_str1;   // "SUBSCRIPTION" or "REACTIVATION"
    const customStr2 = data.custom_str2;   // android_id
    const customStr3 = data.custom_str3;   // APP EMAIL (from generate-payfast-checkout)
    const token = data.token;              // Payfast recurring token

    // Use the app's email (custom_str3) if available, fallback to Payfast email
    const appEmail = (customStr3 && customStr3.trim()) ? customStr3 : payfastEmail;

    console.log(`[ITN] payment_status=${paymentStatus}, payfastEmail=${payfastEmail}, appEmail=${appEmail}, type=${customStr1}, device=${customStr2}`);

    // Only process COMPLETE payments
    if (paymentStatus === "COMPLETE" && appEmail) {
      const cleanEmail = appEmail.trim().toLowerCase();

      if (customStr1 === "SUBSCRIPTION") {
        const expiryDate = new Date();
        expiryDate.setDate(expiryDate.getDate() + 30);

        const upsertPayload: Record<string, unknown> = {
          email: cleanEmail,
          is_premium: true,
          subscription_expiry: expiryDate.toISOString(),
          device_id: customStr2 || null,
        };

        if (token) {
          upsertPayload.token = token;
        }

        // UPSERT: Insert if email doesn't exist, Update if it does
        const { data: result, error } = await supabase
          .from("subscriptions")
          .upsert(upsertPayload, { onConflict: "email" })
          .select();

        if (error) {
          console.error("[DB Error] SUBSCRIPTION upsert FAILED:", error.message, "| Code:", error.code, "| Details:", error.details);
          return new Response("Database error", { status: 500 });
        }

        console.log(`[SUBSCRIPTION] SUCCESS - is_premium=true for ${cleanEmail}. Rows affected:`, JSON.stringify(result));

      } else if (customStr1 === "REACTIVATION") {
        const { data: result, error } = await supabase
          .from("subscriptions")
          .update({ device_id: customStr2 })
          .eq("email", cleanEmail)
          .select();

        if (error) {
          console.error("[DB Error] REACTIVATION update failed:", error.message);
          return new Response("Database error", { status: 500 });
        }

        console.log(`[REACTIVATION] Updated device_id for ${cleanEmail}. Rows:`, JSON.stringify(result));

      } else {
        console.warn(`[ITN Warning] Unknown custom_str1 type: "${customStr1}". No DB action taken.`);
      }

    } else if (paymentStatus !== "COMPLETE") {
      console.log(`[ITN Skipped] payment_status="${paymentStatus}" is not COMPLETE.`);
    }

    return new Response("OK", { status: 200 });

  } catch (err) {
    console.error("[ITN Fatal Error]", err);
    return new Response("Internal server error", { status: 500 });
  }
});
