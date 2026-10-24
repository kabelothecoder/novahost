import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

/**
 * A mentor's API keys for redeem-claim-code. Called from the portal with the
 * mentor's own session.
 *
 *   { action: "list" }                      -> { keys: [{ id, key_prefix, label, created_at, last_used_at }] }
 *   { action: "create", label?: string }    -> { key: "nhk_...", id }   shown ONCE, only the hash is kept
 *   { action: "revoke", id: string }        -> { success: true }
 */

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

/** Live keys one mentor may hold at once. */
const MAX_LIVE_KEYS = 5;

async function sha256Hex(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest)).map((b) => b.toString(16).padStart(2, "0")).join("");
}

function mintKey(): string {
  const bytes = new Uint8Array(24);
  crypto.getRandomValues(bytes);
  return "nhk_" + Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS_HEADERS });

  const json = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), {
      status,
      headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
    });

  try {
    const novaHost = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
      { auth: { autoRefreshToken: false, persistSession: false } },
    );

    const jwt = (req.headers.get("Authorization") ?? "").replace("Bearer ", "");
    const { data: { user } } = await novaHost.auth.getUser(jwt);
    if (!user) return json({ error: "Unauthorized" }, 401);

    const { data: profile } = await novaHost
      .from("profiles").select("approval_status").eq("id", user.id).maybeSingle();
    if (profile?.approval_status !== "approved") {
      return json({ error: "Your account is pending approval." }, 403);
    }

    const body = await req.json().catch(() => ({}));
    const action = String(body.action ?? "list");

    if (action === "list") {
      const { data, error } = await novaHost
        .from("mentor_api_keys")
        .select("id, key_prefix, label, created_at, last_used_at")
        .eq("mentor_id", user.id)
        .is("revoked_at", null)
        .order("created_at", { ascending: false });
      if (error) throw error;
      return json({ keys: data ?? [] });
    }

    if (action === "create") {
      const { count } = await novaHost
        .from("mentor_api_keys")
        .select("id", { count: "exact", head: true })
        .eq("mentor_id", user.id)
        .is("revoked_at", null);
      if ((count ?? 0) >= MAX_LIVE_KEYS) {
        return json({ error: `You already have ${MAX_LIVE_KEYS} keys. Revoke one first.` }, 400);
      }

      const key = mintKey();
      const { data, error } = await novaHost
        .from("mentor_api_keys")
        .insert({
          mentor_id: user.id,
          key_hash: await sha256Hex(key),
          key_prefix: key.slice(0, 10),
          label: body.label ? String(body.label).slice(0, 60) : null,
        })
        .select("id")
        .single();
      if (error) throw error;
      return json({ key, id: data.id });
    }

    if (action === "revoke") {
      const { error } = await novaHost
        .from("mentor_api_keys")
        .update({ revoked_at: new Date().toISOString() })
        .eq("id", String(body.id ?? ""))
        .eq("mentor_id", user.id);
      if (error) throw error;
      return json({ success: true });
    }

    return json({ error: `Unknown action "${action}".` }, 400);
  } catch (err) {
    console.error("mentor-api-keys error:", err);
    return json({ error: "Something went wrong." }, 500);
  }
});
