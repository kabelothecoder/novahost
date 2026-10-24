import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

/**
 * The phone's half of the iOS licence claim.
 *
 *   action "issue"   (default)  hand this paid handset a 30-minute code. A
 *                               still-live code is returned again rather than
 *                               replaced, so tapping twice does not break the
 *                               code the user is halfway through typing.
 *   action "status"            has that code been redeemed yet? Once it has,
 *                               the answer carries the licence key, and the app
 *                               validates it like a typed one.
 *
 * Only a paid, device-matched email may ask. That is the whole point of the
 * flow: a key is minted for someone who has already paid, never before.
 *
 * Public (no JWT): the app authenticates by email + device, the same pair
 * check-subscription-status trusts.
 */

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

/** How long a code may be typed into a mentor's site. */
const CODE_TTL_MINUTES = 30;

/** Licences one email may claim by code in a rolling 30 days. */
const MAX_CLAIMS_PER_30_DAYS = 1;

/** Codes one email may be issued per hour, live or not. */
const MAX_ISSUES_PER_HOUR = 6;

/** No 0/O, 1/I/L: a code is read off one screen and typed on another. */
const ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
const CODE_LENGTH = 8;

const DAY_MS = 24 * 60 * 60 * 1000;

function newCode(): string {
  const bytes = new Uint8Array(CODE_LENGTH);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (b) => ALPHABET[b % ALPHABET.length]).join("");
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS_HEADERS });

  const json = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), {
      status,
      headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
    });

  try {
    const body = await req.json().catch(() => ({}));
    const email = String(body.email ?? "").trim().toLowerCase();
    const deviceId = String(body.deviceId ?? body.android_id ?? "").trim();
    const action = String(body.action ?? "issue").trim().toLowerCase();

    if (!email || !deviceId || deviceId === "UNKNOWN_DEVICE") {
      return json({ success: false, error: "Email and device id are required." }, 400);
    }

    const novaHost = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    // ── Paid, and on this handset ────────────────────────────────────────────
    const { data: sub, error: subErr } = await novaHost
      .from("subscriptions")
      .select("is_premium, is_lifetime, subscription_expiry, device_id")
      .eq("email", email)
      .maybeSingle();

    if (subErr) throw subErr;

    const active = sub?.is_lifetime === true || (sub?.is_premium === true &&
      (!sub?.subscription_expiry || new Date(sub.subscription_expiry) > new Date()));

    if (!active) {
      return json({ success: false, code: "NOT_PAID", error: "Pay for NovaHost first, then request a code." }, 403);
    }
    if (sub!.device_id && sub!.device_id !== deviceId) {
      return json({ success: false, code: "DEVICE_MISMATCH", error: "This purchase is active on another device." }, 403);
    }

    const nowIso = new Date().toISOString();

    // ── Status ───────────────────────────────────────────────────────────────
    if (action === "status") {
      const code = String(body.code ?? "").toUpperCase().replace(/[^A-Z0-9]/g, "");
      if (!code) return json({ success: false, error: "No code to check." }, 400);

      const { data: row, error } = await novaHost
        .from("licence_claim_codes")
        .select("expires_at, redeemed_at, license_key, cancelled_at")
        .eq("email", email)
        .eq("device_id", deviceId)
        .eq("code", code)
        .order("created_at", { ascending: false })
        .limit(1)
        .maybeSingle();

      if (error) throw error;
      if (!row) return json({ success: true, state: "unknown" });

      if (row.redeemed_at) {
        return json({ success: true, state: "redeemed", license_key: row.license_key });
      }
      if (row.cancelled_at || new Date(row.expires_at) < new Date()) {
        return json({ success: true, state: "expired" });
      }
      return json({ success: true, state: "waiting", expires_at: row.expires_at });
    }

    if (action !== "issue") return json({ success: false, error: `Unknown action "${action}".` }, 400);

    // ── Issue ────────────────────────────────────────────────────────────────
    // One claimed licence per 30 days per email. Without it a single R349
    // payment could be walked round every mentor's site for a stack of keys.
    const { count: claimed, error: claimedErr } = await novaHost
      .from("licence_claim_codes")
      .select("id", { count: "exact", head: true })
      .eq("email", email)
      .not("redeemed_at", "is", null)
      .gte("redeemed_at", new Date(Date.now() - 30 * DAY_MS).toISOString());

    if (claimedErr) throw claimedErr;
    if ((claimed ?? 0) >= MAX_CLAIMS_PER_30_DAYS) {
      return json({
        success: false,
        code: "CLAIM_LIMIT",
        error: "You've already claimed a licence this month. Enter that key, or contact support.",
      }, 429);
    }

    // The same live code again, rather than a new one.
    const { data: live, error: liveErr } = await novaHost
      .from("licence_claim_codes")
      .select("code, expires_at")
      .eq("email", email)
      .eq("device_id", deviceId)
      .is("redeemed_at", null)
      .is("cancelled_at", null)
      .gt("expires_at", nowIso)
      .order("created_at", { ascending: false })
      .limit(1)
      .maybeSingle();

    if (liveErr) throw liveErr;
    if (live) return json({ success: true, code: live.code, expires_at: live.expires_at });

    const { count: recent, error: recentErr } = await novaHost
      .from("licence_claim_codes")
      .select("id", { count: "exact", head: true })
      .eq("email", email)
      .gte("created_at", new Date(Date.now() - 60 * 60 * 1000).toISOString());

    if (recentErr) throw recentErr;
    if ((recent ?? 0) >= MAX_ISSUES_PER_HOUR) {
      return json({ success: false, code: "RATE_LIMITED", error: "Too many codes requested. Try again in an hour." }, 429);
    }

    const expiresAt = new Date(Date.now() + CODE_TTL_MINUTES * 60 * 1000).toISOString();

    // The live-code unique index is the collision guard; a clash is retried.
    for (let attempt = 0; attempt < 5; attempt++) {
      const code = newCode();
      const { error } = await novaHost.from("licence_claim_codes").insert({
        code,
        email,
        device_id: deviceId,
        expires_at: expiresAt,
      });
      if (!error) return json({ success: true, code, expires_at: expiresAt });
      if (error.code !== "23505") throw error;
    }

    return json({ success: false, error: "Could not make a code. Try again." }, 500);
  } catch (err) {
    console.error("request-claim-code error:", err);
    return json({ success: false, error: "Something went wrong. Try again in a moment." }, 500);
  }
});
