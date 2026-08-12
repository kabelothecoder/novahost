
3import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

function toCode(name: string) {
  return name
    .trim()
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, "_")
    .replace(/^_|_$/g, "");
}

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response(null, { headers: corsHeaders });

  try {
    const SUPABASE_URL = Deno.env.get('SUPABASE_URL');
    const SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');
    if (!SUPABASE_URL || !SERVICE_ROLE_KEY) {
      return new Response(JSON.stringify({ error: 'Server not configured. Missing SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY.' }), { status: 500, headers: { 'Content-Type': 'application/json', ...corsHeaders } });
    }

    // Admin client for writes (bypasses RLS)
    const admin = createClient(SUPABASE_URL, SERVICE_ROLE_KEY, { global: { fetch } });

    // Require signed-in user (verify_jwt = true by default)
    const supabaseAuth = createClient(SUPABASE_URL, Deno.env.get('SUPABASE_ANON_KEY')!, {
      global: { fetch },
      auth: {
        autoRefreshToken: false,
        persistSession: false,
        detectSessionInUrl: false
      }
    });
    
    const authHeader = req.headers.get('Authorization');
    if (!authHeader) {
      console.error('manage-eas: Missing Authorization header');
      return new Response(JSON.stringify({ error: 'Unauthorized - No auth header' }), { status: 401, headers: { 'Content-Type': 'application/json', ...corsHeaders } });
    }

    const { data: userData, error: authErr } = await supabaseAuth.auth.getUser(authHeader.replace('Bearer ', ''));
    if (authErr || !userData?.user) {
      console.error('manage-eas: Auth failed', { authErr, hasUser: !!userData?.user });
      return new Response(JSON.stringify({ error: 'Unauthorized', details: authErr?.message }), { status: 401, headers: { 'Content-Type': 'application/json', ...corsHeaders } });
    }

    const { action, name } = await req.json().catch(() => ({}));

    if (action !== 'create') {
      return new Response(JSON.stringify({ error: 'Unknown or missing action' }), { status: 400, headers: { 'Content-Type': 'application/json', ...corsHeaders } });
    }

    if (!name || typeof name !== 'string' || !name.trim()) {
      return new Response(JSON.stringify({ error: 'Missing EA name' }), { status: 400, headers: { 'Content-Type': 'application/json', ...corsHeaders } });
    }

    const code = toCode(name);

    // Upsert product by code
    const existing = await admin
      .from('expert_advisors')
      .select('id, code, name')
      .eq('code', code)
      .maybeSingle();

    let product = existing.data;
    if (!product) {
      const { data: created, error: createErr } = await admin
        .from('expert_advisors')
        .insert({ code, name, user_id: userData.user.id })
        .select('id, code, name')
        .maybeSingle();

      if (createErr) {
        return new Response(JSON.stringify({ error: `Failed to create product: ${createErr.message}` }), { status: 500, headers: { 'Content-Type': 'application/json', ...corsHeaders } });
      }
      product = created!;
    }

    // Ensure default Lifetime plan exists
    const lifetimeCode = 'LIFETIME';
    const lifetime = await admin
      .from('plans')
      .select('id, code, name, duration_days, max_devices')
      .eq('product_id', product.id)
      .eq('code', lifetimeCode)
      .maybeSingle();

    let plan = lifetime.data;
    if (!plan) {
      const { data: createdPlan, error: planErr } = await admin
        .from('plans')
        .insert({
          product_id: product.id,
          code: lifetimeCode,
          name: 'Lifetime',
          duration_days: null,
          max_devices: 1,
        })
        .select('id, code, name, duration_days, max_devices')
        .maybeSingle();

      if (planErr) {
        return new Response(JSON.stringify({ error: `Failed to create default plan: ${planErr.message}` }), { status: 500, headers: { 'Content-Type': 'application/json', ...corsHeaders } });
      }
      plan = createdPlan!;
    }

    return new Response(JSON.stringify({ product, defaultPlan: plan }), { status: 201, headers: { 'Content-Type': 'application/json', ...corsHeaders } });
  } catch (e) {
    return new Response(JSON.stringify({ error: 'Unexpected error', details: String(e) }), { status: 500, headers: { 'Content-Type': 'application/json', ...corsHeaders } });
  }
});
