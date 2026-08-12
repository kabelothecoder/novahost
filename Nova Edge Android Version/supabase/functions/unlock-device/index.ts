// supabase/functions/unlock-device/index.ts
// @description Webhook endpoint called by Paystack upon successful R150 reactivation payment.
// Updates the subscriptions table with the new android_id.

import { serve } from "https://deno.land/std@0.208.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import * as crypto from "https://deno.land/std@0.208.0/crypto/mod.ts";

serve(async (req: Request) => {
  try {
    // 1. Verify Paystack Signature (Crucial for security)
    const signature = req.headers.get("x-paystack-signature");
    if (!signature) {
      return new Response("Missing signature", { status: 401 });
    }

    const bodyText = await req.text();
    const secretKey = Deno.env.get("PAYSTACK_SECRET_KEY")!;
    
    // HMAC SHA512 validation
    const key = await crypto.crypto.subtle.importKey(
      "raw",
      new TextEncoder().encode(secretKey),
      { name: "HMAC", hash: "SHA-512" },
      false,
      ["sign"]
    );
    const signatureBuffer = await crypto.crypto.subtle.sign(
      "HMAC",
      key,
      new TextEncoder().encode(bodyText)
    );
    const hashHex = Array.from(new Uint8Array(signatureBuffer))
      .map(b => b.toString(16).padStart(2, '0'))
      .join('');

    if (hashHex !== signature) {
      return new Response("Invalid signature", { status: 401 });
    }

    // 2. Parse payload
    const event = JSON.parse(bodyText);

    // Only care about successful charges
    if (event.event === "charge.success") {
      const email = event.data.customer.email;
      // We pass the new android_id via Paystack metadata during checkout
      const newDeviceId = event.data.metadata?.android_id;

      if (email && newDeviceId) {
        const supabase = createClient(
          Deno.env.get("SUPABASE_URL")!,
          Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
        );

        // Update the subscription's device_id
        await supabase
          .from("subscriptions")
          .update({ device_id: newDeviceId })
          .eq("email", email.trim().toLowerCase());
          
        console.log(`Unlocked device for ${email} -> ${newDeviceId}`);
      }
    }

    return new Response("ok", { status: 200 });

  } catch (err) {
    console.error("unlock-device webhook error:", err);
    return new Response("Internal server error", { status: 500 });
  }
});
