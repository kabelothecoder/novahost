import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

/**
 * The NovaHost API a mentor's website calls to swap a claim code for a licence.
 *
 *   POST /functions/v1/redeem-claim-code
 *   x-novahost-key: nhk_...            (from mentor-api-keys; server-side only)
 *   { "code": "ABCD-EFGH", "ea": "<robot code or name>", "plan": "<plan code, optional>" }
 *
 *   200 { success: true, license_key, robot, plan, expires_at }
 *   4xx { success: false, code, error }
 *
 * The code came from request-claim-code on a phone that has ALREADY PAID, so
 * every key minted here belongs to a paying user. The licence is minted on the
 * calling mentor's robot under the same approval and quota rules as
 * generate-license, and tagged `metadata.claimed_via = "claim_code"` so claims
 * can be counted per mentor.
 *
 * The phone polls its code and fills the key in by itself; returning it here
 * as well lets the website show it, for a user who is on another device.
 *
 * Public (no JWT). The API key is the credential, and it must only ever be
 * sent from the website's SERVER -- a key in browser JavaScript can be lifted
 * and used to burn the mentor's quota.
 */

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-novahost-key",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

/** Wrong codes one API key may send per hour before it is told to wait. */
const MAX_FAILED_PER_HOUR = 20;

const HOUR_MS = 60 * 60 * 1000;

async function sha256Hex(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest)).map((b) => b.toString(16).padStart(2, "0")).join("");
}

function randomSegment() {
  const arr = new Uint32Array(1);
  crypto.getRandomValues(arr);
  return arr[0].toString(36).slice(-4).toUpperCase().padStart(4, "0");
}

/** Same shape generate-license emits, so the app's 16-character field takes it. */
function generateLicenseKey(prefix: string) {
  return `${prefix}-${randomSegment()}-${randomSegment()}-${randomSegment()}`;
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS_HEADERS });

  const json = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), {
      status,
      headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
    });
  const fail = (status: number, code: string, error: string) => json({ success: false, code, error }, status);

  if (req.method !== "POST") return fail(405, "METHOD", "Use POST.");

  try {
    const novaHost = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
      { auth: { autoRefreshToken: false, persistSession: false } },
    );

    // ── The mentor ───────────────────────────────────────────────────────────
    const apiKey = (req.headers.get("x-novahost-key") ?? "").trim();
    if (!apiKey) return fail(401, "NO_API_KEY", "Send your NovaHost API key in the x-novahost-key header.");

    const { data: keyRow, error: keyErr } = await novaHost
      .from("mentor_api_keys")
      .select("id, mentor_id, failed_attempts, failed_window_at, revoked_at")
      .eq("key_hash", await sha256Hex(apiKey))
      .maybeSingle();

    if (keyErr) throw keyErr;
    if (!keyRow || keyRow.revoked_at) return fail(401, "BAD_API_KEY", "That API key is not valid.");

    const windowOpen = keyRow.failed_window_at &&
      Date.now() - new Date(keyRow.failed_window_at).getTime() < HOUR_MS;
    if (windowOpen && keyRow.failed_attempts >= MAX_FAILED_PER_HOUR) {
      return fail(429, "RATE_LIMITED", "Too many wrong codes. Try again in an hour.");
    }

    const { data: profile } = await novaHost
      .from("profiles")
      .select("approval_status, license_quota")
      .eq("id", keyRow.mentor_id)
      .maybeSingle();
    if (profile?.approval_status !== "approved") {
      return fail(403, "NOT_APPROVED", "This mentor account is pending approval.");
    }

    // ── The request ──────────────────────────────────────────────────────────
    const body = await req.json().catch(() => ({}));
    const code = String(body.code ?? "").toUpperCase().replace(/[^A-Z0-9]/g, "");
    const ea = String(body.ea ?? "").trim();
    const planWanted = String(body.plan ?? "").trim();

    if (!code) return fail(400, "NO_CODE", "Missing code.");
    if (!ea) return fail(400, "NO_ROBOT", "Missing ea (your robot's code or name).");

    // The robot, scoped to this mentor -- see generate-license for why the
    // lookup must never be unscoped.
    let { data: product } = await novaHost
      .from("expert_advisors")
      .select("id, code, name, display_name, symbols")
      .eq("user_id", keyRow.mentor_id)
      .eq("code", ea)
      .maybeSingle();
    if (!product) {
      ({ data: product } = await novaHost
        .from("expert_advisors")
        .select("id, code, name, display_name, symbols")
        .eq("user_id", keyRow.mentor_id)
        .ilike("name", ea)
        .maybeSingle());
    }
    if (!product) return fail(404, "ROBOT_NOT_FOUND", `No robot "${ea}" on this mentor account.`);

    const { data: plans } = await novaHost
      .from("plans")
      .select("id, code, name, duration_days, max_devices")
      .eq("product_id", product.id);

    const planRow = planWanted
      ? (plans ?? []).find((p) => p.code === planWanted || p.name?.toLowerCase() === planWanted.toLowerCase())
      : (plans?.length === 1 ? plans[0] : null);

    if (!planRow) {
      return fail(400, "PLAN_REQUIRED",
        planWanted
          ? `No plan "${planWanted}" on ${product.name}.`
          : `${product.name} has ${plans?.length ?? 0} plans; send "plan" with one of: ${(plans ?? []).map((p) => p.code).join(", ")}.`);
    }

    const quota = profile?.license_quota ?? null;
    if (quota !== null) {
      const { count: held } = await novaHost
        .from("licenses")
        .select("id", { count: "exact", head: true })
        .eq("user_id", keyRow.mentor_id);
      if ((held ?? 0) + 1 > quota) {
        return fail(403, "QUOTA", `You have used all ${quota} of your licence keys. Request more from an admin.`);
      }
    }

    // ── Claim the code (atomic) ──────────────────────────────────────────────
    // Marked redeemed BEFORE the licence exists, guarded on still being
    // unredeemed and in date, so two sites racing the same code cannot both
    // mint. Undone below if the mint fails.
    const nowIso = new Date().toISOString();
    const { data: claim, error: claimErr } = await novaHost
      .from("licence_claim_codes")
      .update({ redeemed_at: nowIso, redeemed_by: keyRow.mentor_id })
      .eq("code", code)
      .is("redeemed_at", null)
      .is("cancelled_at", null)
      .gt("expires_at", nowIso)
      .select("id, email")
      .maybeSingle();

    if (claimErr) throw claimErr;

    if (!claim) {
      await novaHost.from("mentor_api_keys").update({
        failed_attempts: windowOpen ? keyRow.failed_attempts + 1 : 1,
        failed_window_at: windowOpen ? keyRow.failed_window_at : nowIso,
      }).eq("id", keyRow.id);
      return fail(404, "CODE_INVALID", "That code is wrong, already used, or expired. Codes last 30 minutes.");
    }

    // ── Mint ─────────────────────────────────────────────────────────────────
    const prefix = `${planRow.code.slice(0, 2).toUpperCase()}${new Date().getFullYear().toString().slice(-2)}`;
    const symbols = Array.isArray(product.symbols) ? product.symbols : [];

    let licence: { id: string; license_key: string; expires_at: string | null } | null = null;
    let lastErr: unknown = null;
    for (let attempt = 0; attempt < 5 && !licence; attempt++) {
      const { data, error } = await novaHost
        .from("licenses")
        .insert({
          owner_id: keyRow.mentor_id,
          user_id: keyRow.mentor_id,
          product_id: product.id,
          ea_id: product.id,
          plan_id: planRow.id,
          license_key: generateLicenseKey(prefix),
          max_devices: planRow.max_devices,
          allowed_symbols: symbols,
          metadata: {
            username: claim.email,
            claimed_via: "claim_code",
            claim_code_id: claim.id,
            claimed_by_email: claim.email,
            expert_advisor_id: product.id,
            robot_id: product.id,
            robot_name: product.name,
            robot_code: product.code,
            plan_name: planRow.name,
            plan_code: planRow.code,
            display_name: product.display_name || product.name,
            symbols,
          },
        })
        .select("id, license_key, expires_at")
        .single();
      if (!error) licence = data;
      else {
        lastErr = error;
        if ((error as { code?: string }).code !== "23505") break;
      }
    }

    if (!licence) {
      console.error("redeem-claim-code: mint failed, releasing code", lastErr);
      await novaHost.from("licence_claim_codes")
        .update({ redeemed_at: null, redeemed_by: null })
        .eq("id", claim.id);
      return fail(500, "MINT_FAILED", "The licence could not be created. The code is still valid; try again.");
    }

    await novaHost.from("licence_claim_codes")
      .update({ license_id: licence.id, license_key: licence.license_key })
      .eq("id", claim.id);
    await novaHost.from("mentor_api_keys")
      .update({ last_used_at: nowIso, failed_attempts: 0, failed_window_at: null })
      .eq("id", keyRow.id);

    return json({
      success: true,
      license_key: licence.license_key,
      robot: product.display_name || product.name,
      plan: planRow.code,
      expires_at: licence.expires_at,
    });
  } catch (err) {
    console.error("redeem-claim-code error:", err);
    return fail(500, "SERVER_ERROR", "Something went wrong. Try again in a moment.");
  }
});
