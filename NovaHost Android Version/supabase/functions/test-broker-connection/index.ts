import { serve } from "https://deno.land/std@0.208.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: CORS_HEADERS });
  }

  try {
    const body = await req.json().catch(() => ({}));
    const { account_id, password, server, platform, license_key } = body;

    console.log(`[BrokerConn] Request received: server=${server}, account=${account_id}, platform=${platform}, license=${license_key}`);

    if (!account_id || !server || !password || !license_key) {
      return new Response(
        JSON.stringify({ success: false, error: "Missing required fields (account_id, password, server, license_key)." }),
        { status: 400, headers: { "Content-Type": "application/json", ...CORS_HEADERS } }
      );
    }

    const metaApiToken = Deno.env.get("METAAPI_TOKEN");
    if (!metaApiToken) {
      console.error("[BrokerConn] METAAPI_TOKEN not configured.");
      return new Response(
        JSON.stringify({ success: false, error: "Server configuration error (MetaAPI token missing)." }),
        { status: 500, headers: { "Content-Type": "application/json", ...CORS_HEADERS } }
      );
    }

    const supabaseUrl = Deno.env.get('SUPABASE_URL');
    const supabaseServiceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');
    
    if (!supabaseUrl || !supabaseServiceKey) {
      console.error("[BrokerConn] Supabase configuration missing.");
      return new Response(
        JSON.stringify({ success: false, error: "Server configuration error (Supabase config missing)." }),
        { status: 500, headers: { "Content-Type": "application/json", ...CORS_HEADERS } }
      );
    }

    const supabase = createClient(supabaseUrl, supabaseServiceKey);

    // Decode password if it was base64 encoded by Android
    let plainPassword = password;
    try {
      plainPassword = atob(password).replace("MH_SALT_", "");
    } catch (e) {
      console.warn("[BrokerConn] Password decode skipped — possibly not base64 encoded.");
    }

    // Generate unique 32-character transaction ID
    const transactionId = crypto.randomUUID().replace(/-/g, '');

    const createPayload = {
      login: account_id,
      password: plainPassword,
      server: server,
      platform: platform || "mt5",
      name: "Nova Edge Cloud Terminal Container",
      magic: 0,
      type: "cloud-g2"
    };

    console.log(`[BrokerConn] Deploying cloud terminal for ${account_id}... transactionId=${transactionId}`);

    const createResponse = await fetch(
      "https://mt-provisioning-api-v1.agiliumtrade.agiliumtrade.ai/users/current/accounts",
      {
        method: "POST",
        headers: {
          "auth-token": metaApiToken,
          "Content-Type": "application/json",
          "Accept": "application/json",
          "transaction-id": transactionId
        },
        body: JSON.stringify(createPayload)
      }
    );

    if (!createResponse.ok) {
      const errorBody = await createResponse.text();
      console.error(`[BrokerConn] MetaAPI account creation failed: ${createResponse.status} — ${errorBody}`);
      return new Response(
        JSON.stringify({ success: false, error: `Provisioning failed: ${errorBody}` }),
        { status: 400, headers: { "Content-Type": "application/json", ...CORS_HEADERS } }
      );
    }

    const createdAccount = await createResponse.json();
    const metaApiAccountId = createdAccount._id || createdAccount.id;
    console.log(`[BrokerConn] Account created: ${metaApiAccountId}`);

    // Track this generated MetaApi account ID inside the user's corresponding column row in the licenses table
    const { error: dbError } = await supabase
      .from('licenses')
      .update({ 
        metaapi_account_id: metaApiAccountId,
        provisioning_status: 'DEPLOYING'
      })
      .eq('license_key', license_key);

    if (dbError) {
      console.error(`[BrokerConn] Failed to update license in DB: ${dbError.message}`);
      // Proceed anyway since MetaApi account was created
    }

    return new Response(
      JSON.stringify({
        success: true,
        message: "Deployment accepted",
        metaapi_account_id: metaApiAccountId,
        transaction_id: transactionId,
        status: "DEPLOYING"
      }),
      { status: 202, headers: { "Content-Type": "application/json", ...CORS_HEADERS } }
    );

  } catch (error: any) {
    console.error("[BrokerConn] Fatal Error:", error.message);
    return new Response(
      JSON.stringify({ success: false, error: `Internal server error: ${error.message}` }),
      { status: 500, headers: { "Content-Type": "application/json", ...CORS_HEADERS } }
    );
  }
});
