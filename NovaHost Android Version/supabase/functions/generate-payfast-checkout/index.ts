import { serve } from "https://deno.land/std@0.208.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import crypto from "node:crypto";

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

function generatePayfastSignature(data: Record<string, string>, passphrase: string): string {
  // 1. Sort the object by keys alphabetically
  const sortedKeys = Object.keys(data).sort();
  
  // 2. URI encode values and build query string
  const parts = [];
  for (const key of sortedKeys) {
    if (data[key] !== undefined && data[key] !== null && data[key] !== "") {
      const encodedValue = encodeURIComponent(data[key].trim()).replace(/%20/g, "+");
      parts.push(`${key}=${encodedValue}`);
    }
  }

  let pfOutput = parts.join("&");

  // 3. Append passphrase if it exists
  if (passphrase) {
    const encodedPassphrase = encodeURIComponent(passphrase.trim()).replace(/%20/g, "+");
    pfOutput += `&passphrase=${encodedPassphrase}`;
  }

  // 4. MD5 Hash
  return crypto.createHash("md5").update(pfOutput).digest("hex");
}

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: CORS_HEADERS });
  }

  try {
    const { email, android_id } = await req.json();

    if (!email || !android_id) {
      return new Response(
        JSON.stringify({ error: "Missing email or android_id" }),
        { status: 400, headers: { ...CORS_HEADERS, "Content-Type": "application/json" } }
      );
    }

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
    );

    const { data: sub, error } = await supabase
      .from("subscriptions")
      .select("id, email, is_premium, device_id")
      .eq("email", email.trim().toLowerCase())
      .maybeSingle();

    if (error) {
        console.error("Database error:", error);
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;

    const payload: Record<string, string> = {
      merchant_id: MERCHANT_ID,
      merchant_key: MERCHANT_KEY,
      return_url: `${supabaseUrl}/functions/v1/payment-redirect?status=success`,
      cancel_url: `${supabaseUrl}/functions/v1/payment-redirect?status=cancel`,
      notify_url: `${supabaseUrl}/functions/v1/payfast-webhook`,
      email_address: email,
      item_name: "",
      amount: "",
      custom_str1: "",
      custom_str2: android_id,
      custom_str3: email
    };

    // Route A: New/Expired User
    if (!sub || !sub.is_premium) {
      payload.item_name = "Nova Edge Monthly Subscription";
      payload.amount = "250.00";
      payload.custom_str1 = "SUBSCRIPTION";
      
      // Recurring parameters
      payload.subscription_type = "1";
      payload.billing_date = new Date().toISOString().split("T")[0];
      payload.recurring_amount = "250.00";
      payload.frequency = "3"; // Monthly
      payload.cycles = "0"; // Indefinite
    } 
    // Route B: Active User, New Device Reactivation
    else if (sub.is_premium && sub.device_id !== android_id) {
      payload.item_name = "Nova Edge Device Reactivation";
      payload.amount = "150.00";
      payload.custom_str1 = "REACTIVATION";
      // Omit recurring parameters for once-off payment
    } else {
        // User is active and device matches
        return new Response(
            JSON.stringify({ message: "Subscription already active on this device.", route: "ACTIVE_SAME_DEVICE" }),
            { status: 200, headers: { ...CORS_HEADERS, "Content-Type": "application/json" } }
        );
    }

    // Generate Signature
    const signature = generatePayfastSignature(payload, PASSPHRASE);
    payload.signature = signature;

    // Construct the final URL
    const finalUrl = new URL(PAYFAST_URL);
    for (const [key, value] of Object.entries(payload)) {
      finalUrl.searchParams.append(key, value);
    }

    return new Response(
      JSON.stringify({ 
          route: payload.custom_str1 === "SUBSCRIPTION" ? "NEW_USER" : "ACTIVE_NEW_DEVICE",
          checkout_url: finalUrl.toString() 
      }),
      { headers: { ...CORS_HEADERS, "Content-Type": "application/json" } }
    );

  } catch (err) {
    console.error("generate-payfast-checkout error:", err);
    return new Response(
      JSON.stringify({ error: "Internal server error" }),
      { status: 500, headers: { ...CORS_HEADERS, "Content-Type": "application/json" } }
    );
  }
});
