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
 * checkout URL edited by hand would otherwise buy full access for R1.
 *
 * SUBSCRIPTION is still accepted so an ITN from a checkout opened before this
 * deploy still lands, but it grants the same lifetime access the R599 product
 * does rather than a 30-day window -- the monthly plan no longer exists to
 * renew into.
 */

// Production only -- the sandbox branch and the ENVIRONMENT switch are gone.
// A passphrase mismatch (set on the Payfast account but unset here, or vice
// versa) fails every signature check below, so this must be the SAME value as
// generate-payfast-checkout signs with.
const PASSPHRASE = Deno.env.get("PAYFAST_PASSPHRASE") || "";

/**
 * Minimum gross, in rand, per product. A FLOOR (a cent more is fine, a cent
 * less is not). Matches generate-payfast-checkout.PRICES -- the CHARGED
 * R600/R350, not the displayed R599/R349.
 */
const EXPECTED_AMOUNT: Record<string, number> = {
  LIFETIME: 600,
  SUBSCRIPTION: 250,
  SCANNER: 350,
  REACTIVATION: 150,
};

const PAYFAST_HOST = "www.payfast.co.za";

// ── Move policy ────────────────────────────────────────────────────────────
// Keep identical to check-subscription-status, request-device-move and
// generate-payfast-checkout.
const MOVE_COOLDOWN_DAYS = 30;
const MOVE_LIMIT_PER_YEAR = 2;

const DAY_MS = 24 * 60 * 60 * 1000;

/**
 * A fresh device session. Rotating this is what evicts the old handset.
 *
 * `globalThis.crypto`, not the bare name: this module imports node:crypto for
 * the MD5 the Payfast signature needs, and that import shadows the Web Crypto
 * global for the whole file. Reaching for `crypto.randomUUID` here would be
 * reaching into Deno's node shim, whose surface is not the one this needs.
 */
function mintToken(): string {
  const uuid = () => globalThis.crypto.randomUUID().replace(/-/g, "");
  return uuid() + uuid();
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
    if (until > new Date()) return { eligible: false, reason: "cooldown", moves_used: used };
  }

  if (used >= MOVE_LIMIT_PER_YEAR) {
    return { eligible: false, reason: "limit_reached", moves_used: used };
  }

  return { eligible: true, reason: "ok", moves_used: used };
}

/**
 * Percent-encoding that matches PHP's `urlencode`, which is what Payfast hashes.
 *
 * `encodeURIComponent` leaves `! ' ( ) *` unescaped and PHP escapes all five.
 * The item_name arrives here carrying "(once-off)" for both paid products, so
 * an ITN validated with the bare encoder would be rejected as forged for
 * exactly the two purchases this webhook exists to grant.
 *
 * Kept identical to `payfastEncode` in generate-payfast-checkout. If one of
 * them changes, the other has to change with it.
 */
function payfastEncode(value: string): string {
  return encodeURIComponent(value.trim())
    .replace(/%20/g, "+")
    .replace(/[!'()*]/g, (c) => "%" + c.charCodeAt(0).toString(16).toUpperCase());
}

/**
 * Payfast ITN signature.
 *
 * Hashed in the order the fields arrived in the POST, not sorted -- the
 * alphabetical ordering belongs to Payfast's API signature format. [pairs] is
 * the ordered list straight off the form body, with `signature` itself removed.
 */
function validatePayfastSignature(
  pairs: Array<[string, string]>,
  signatureToMatch: string,
  passphrase: string
): boolean {
  // EVERY field Payfast sent is hashed, including the empty ones -- only
  // `signature` itself is dropped.
  //
  // This is the opposite of the outgoing checkout signature, where we build the
  // payload ourselves and omit blanks entirely. Here the payload is Payfast's,
  // and its ITN carries a dozen empty fields (m_payment_id, item_description,
  // custom_str4/5, custom_int1-5, name_first, name_last). Payfast's own PHP
  // sample walks the whole POST body, so `item_description=` is part of the
  // string it hashed. Skipping blanks produced a different string and therefore
  // a different digest, and every genuine callback was rejected as forged.
  //
  // Verified against a real sandbox ITN (pf_payment_id 3362362): keeping the
  // empties reproduces Payfast's 63f226684b82ff329e3df50c64d566dc exactly,
  // dropping them yields 2f88ccc7d3ad931aaa9d00b030e4a92e.
  const parts = [];
  for (const [key, value] of pairs) {
    if (key === "signature") continue;
    parts.push(key + "=" + payfastEncode(value ?? ""));
  }

  let pfOutput = parts.join("&");

  if (passphrase) {
    pfOutput += "&passphrase=" + payfastEncode(passphrase);
  }

  const calculatedSignature = crypto.createHash("md5").update(pfOutput).digest("hex");
  return calculatedSignature === signatureToMatch;
}

/**
 * Asks Payfast whether it actually sent this.
 *
 * The ITN payload goes back to Payfast verbatim and they answer VALID or
 * INVALID. This is the check that does not depend on getting the signature
 * recipe right, on holding the passphrase, or on recognising a source IP -- the
 * only party who can make this return VALID is Payfast, because only they hold
 * the transaction.
 *
 * It is the gate that closes the hole this endpoint used to have. The function
 * is public (it has to be; Payfast is not going to carry a JWT), and in sandbox
 * it skipped signature validation outright with a comment explaining that the
 * passphrase was unset. The result was that a hand-written form POST claiming
 * `payment_status=COMPLETE` and `custom_str1=LIFETIME` was indistinguishable
 * from a real R599 purchase.
 *
 * Failing to reach Payfast returns false, and the caller answers 500 so the ITN
 * is retried. An entitlement is not something to grant because a network call
 * timed out.
 */
async function confirmWithPayfast(pairs: Array<[string, string]>): Promise<boolean> {
  try {
    const body = new URLSearchParams();
    for (const [key, value] of pairs) body.append(key, value);

    const res = await fetch(`https://${PAYFAST_HOST}/eng/query/validate`, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: body.toString(),
    });

    if (!res.ok) {
      console.error("[ITN] Payfast validate returned HTTP " + res.status);
      return false;
    }

    const answer = (await res.text()).trim().toUpperCase();
    if (answer !== "VALID") {
      console.error("[ITN] Payfast did not confirm this payload: " + answer);
      return false;
    }
    return true;
  } catch (err) {
    console.error("[ITN] Payfast validate unreachable:", err);
    return false;
  }
}

serve(async (req: Request) => {
  try {
    // Payfast sends ITN as application/x-www-form-urlencoded POST
    const formData = await req.formData();
    // Kept as an ordered list as well as a map: the signature and the
    // confirmation postback both depend on the order Payfast sent the fields
    // in, and a map alone cannot be trusted to preserve it for numeric-looking
    // keys.
    const pairs: Array<[string, string]> = [];
    const data: Record<string, string> = {};
    for (const [key, value] of formData.entries()) {
      pairs.push([key, value.toString()]);
      data[key] = value.toString();
    }

    const novaHost = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
    );

    console.log("[ITN Received] Raw payload:", JSON.stringify(data, null, 2));

    // --- Authenticity: signature, then Payfast's own confirmation ------------
    // The previous build skipped this whole block when it thought it was in
    // sandbox -- and since ENVIRONMENT was never set, that meant it skipped
    // validation on the live public endpoint for every payload it ever
    // received. The sandbox path is gone now; this always runs.
    const receivedSignature = data.signature;
    if (!receivedSignature) {
      console.error("[ITN Error] Missing signature in payload.");
      return new Response("Missing signature", { status: 400 });
    }

    if (!validatePayfastSignature(pairs, receivedSignature, PASSPHRASE)) {
      console.error("[ITN Error] Signature did not match.");
      return new Response("Invalid signature", { status: 401 });
    }

    // The authoritative check. Answers "did Payfast send this", which is the
    // question a forged POST cannot pass however well formed it is.
    if (!await confirmWithPayfast(pairs)) {
      // 500 rather than 401: an unreachable Payfast is a retryable condition,
      // and their ITN queue will present this again. A 4xx would be read as
      // "delivered, stop trying" and lose a real payment.
      return new Response("Could not confirm with Payfast", { status: 500 });
    }

    // --- Replay: one grant per Payfast transaction ---------------------------
    // Payfast retries an ITN until it gets a 200, and the same delivery
    // arriving twice must not move a device binding twice.
    //
    // Checked BEFORE this payload is logged, and the log is written only after
    // both gates above have passed. `itn_logs` therefore contains authentic
    // Payfast deliveries and nothing else -- which is what makes it safe to use
    // as the replay ledger. Logging first, as this function used to, would let
    // a forged POST carrying a real pf_payment_id poison the ledger and make
    // the genuine retry look like a duplicate.
    const pfPaymentId = data.pf_payment_id;
    if (pfPaymentId) {
      const { data: seen } = await novaHost
        .from("itn_logs")
        .select("id")
        .eq("payload->>pf_payment_id", pfPaymentId)
        .limit(1);

      if (seen && seen.length > 0) {
        console.log("[ITN] pf_payment_id " + pfPaymentId + " already processed. Acknowledging without re-granting.");
        return new Response("OK", { status: 200 });
      }
    }

    // [AUDIT LOG] Confirmed-authentic payloads only.
    await novaHost.from("itn_logs").insert([{ payload: data }]);

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
        // Minted here so the buying handset is the live session from the
        // moment it lands, rather than at whatever point it next calls
        // check-subscription-status.
        token: deviceId ? mintToken() : null,
        token_issued_at: deviceId ? now : null,
        updated_at: now,
      };

      const { data: result, error } = await novaHost
        .from("subscriptions")
        .upsert(upsertPayload, { onConflict: "email" })
        .select();

      if (error) {
        console.error("[DB Error] LIFETIME upsert FAILED:", error.message, "| Code:", error.code, "| Details:", error.details);
        return new Response("Database error", { status: 500 });
      }

      console.log("[LIFETIME] SUCCESS - is_lifetime=true for " + cleanEmail + ". Rows: " + JSON.stringify(result));

      if (deviceId) {
        await novaHost.from("subscription_device_events").insert({
          email: cleanEmail,
          event: "bind",
          new_device_id: deviceId,
          amount: paidAmount,
          pf_payment_id: pfPaymentId ?? null,
          note: "App access purchased (LIFETIME).",
        });
      }

    } else if (productType === "SCANNER") {
      // Upsert rather than update: the scanner can be bought against an email
      // whose app access was granted outside this webhook, and an update on a
      // missing row would silently no-op with the customer already charged.
      const { data: result, error } = await novaHost
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
      if (!deviceId) {
        console.error("[ITN Error] REACTIVATION carried no device id. Nothing to move to.");
        return new Response("OK", { status: 200 });
      }

      const { data: existing, error: readErr } = await novaHost
        .from("subscriptions")
        .select("id, device_id, reactivation_count")
        .eq("email", cleanEmail)
        .maybeSingle();

      if (readErr) {
        console.error("[DB Error] REACTIVATION lookup failed:", readErr.message);
        return new Response("Database error", { status: 500 });
      }

      if (!existing) {
        console.error("[ITN Error] REACTIVATION for an email with no subscription: " + cleanEmail);
        return new Response("OK", { status: 200 });
      }

      // ---- The ticket ------------------------------------------------------
      // Proof, issued by request-device-move, that whoever paid can read the
      // mailbox. Consumed here so it is single-use.
      //
      // This is also the replay guard that pf_payment_id cannot be. Paying the
      // same (validly signed) checkout URL twice produces two DIFFERENT
      // payment ids, so the dedupe above lets both through; only the ticket
      // makes the second one a no-op.
      const { data: ticket, error: ticketErr } = await novaHost
        .from("device_move_tickets")
        .select("id")
        .eq("email", cleanEmail)
        .eq("target_device_id", deviceId)
        .not("verified_at", "is", null)
        .is("consumed_at", null)
        .order("created_at", { ascending: false })
        .limit(1)
        .maybeSingle();

      if (ticketErr) {
        console.error("[DB Error] REACTIVATION ticket lookup failed:", ticketErr.message);
        return new Response("Database error", { status: 500 });
      }

      if (!ticket) {
        // Money taken, licence NOT moved. Almost always a duplicate payment on
        // a re-used checkout URL. Loud, because it is a refund, and silence
        // here is a customer who paid and got nothing with no trace of why.
        console.error(
          "[REACTIVATION] REFUND REQUIRED -- no unconsumed verified move ticket for " +
          cleanEmail + " -> device " + deviceId + ", pf_payment_id=" + (pfPaymentId ?? "?") +
          ", amount=" + paidAmount
        );
        await novaHost.from("subscription_device_events").insert({
          email: cleanEmail,
          event: "revoke",
          old_device_id: existing.device_id,
          new_device_id: deviceId,
          amount: paidAmount,
          pf_payment_id: pfPaymentId ?? null,
          note: "PAID BUT NOT GRANTED: no valid move ticket (likely duplicate payment). Refund required.",
        });
        return new Response("OK", { status: 200 });
      }

      // Cooldown and cap are re-read here, but they do NOT block a move that
      // got this far. generate-payfast-checkout is what stops someone reaching
      // a checkout they cannot use; by this point the money is taken and the
      // ticket is genuine, so refusing would mean keeping R150 for nothing.
      // Anomalies are recorded instead, because the only way to be here is a
      // race with our own gate and that is worth seeing.
      const eligibility = await moveEligibility(novaHost, cleanEmail);
      if (!eligibility.eligible) {
        console.warn(
          "[REACTIVATION] Granting a move that failed the " + eligibility.reason +
          " check at ITN time for " + cleanEmail + ". Honoured because payment succeeded."
        );
      }

      // Rotating the token is the eviction. The handset this licence is moving
      // away from still holds the old value, so its next check-subscription-
      // status no longer matches -- and the device_id comparison locks it out
      // regardless.
      const { data: result, error } = await novaHost
        .from("subscriptions")
        .update({
          device_id: deviceId,
          device_bound_at: now,
          token: mintToken(),
          token_issued_at: now,
          reactivation_count: (existing.reactivation_count ?? 0) + 1,
          last_reactivated_at: now,
          updated_at: now,
        })
        .eq("id", existing.id)
        .select();

      if (error) {
        console.error("[DB Error] REACTIVATION update failed:", error.message);
        return new Response("Database error", { status: 500 });
      }

      await novaHost
        .from("device_move_tickets")
        .update({ consumed_at: now })
        .eq("id", ticket.id);

      await novaHost.from("subscription_device_events").insert({
        email: cleanEmail,
        event: "move",
        old_device_id: existing.device_id,
        new_device_id: deviceId,
        amount: paidAmount,
        pf_payment_id: pfPaymentId ?? null,
        note: eligibility.eligible ? null : `Granted despite ${eligibility.reason}.`,
      });

      console.log("[REACTIVATION] Moved " + cleanEmail + " from " + existing.device_id + " to " + deviceId + ". Rows: " + JSON.stringify(result));
    }

    return new Response("OK", { status: 200 });

  } catch (err) {
    console.error("[ITN Fatal Error]", err);
    return new Response("Internal server error", { status: 500 });
  }
});
