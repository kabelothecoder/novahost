import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}

const supabase = createClient(
  Deno.env.get('SUPABASE_URL') ?? '',
  Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
)

serve(async (req: Request) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    const body = await req.json()
    const { ea_id, license_key, signal_data } = body

    if (!ea_id || !license_key) {
      return new Response(JSON.stringify({ error: 'Missing credentials' }), { 
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      })
    }

    // Validate the license and EA ID in the database
    const { data: license, error: licenseError } = await supabase
      .from('licenses')
      .select('*')
      .eq('license_key', license_key)
      .eq('ea_id', ea_id)
      .single()

    if (licenseError || !license) {
      return new Response(JSON.stringify({ error: 'Invalid EA_ID or License Key' }), { 
        status: 401,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      })
    }

    // Deduplication check
    const signalId = signal_data?.signal_id || signal_data?.ticket
    if (signalId) {
        const { data: existing } = await supabase
            .from('signals')
            .select('id')
            .eq('signal_id', String(signalId))
            .maybeSingle()
        
        if (existing) {
            return new Response(JSON.stringify({ success: true, message: 'Duplicate signal ignored' }), {
                headers: { ...corsHeaders, 'Content-Type': 'application/json' },
            })
        }
    }

    // Log the incoming signal for auditing
    await supabase.from('signal_logs').insert([{
      license_id: license.id,
      ea_id: ea_id,
      raw_data: signal_data,
      status: 'received'
    }])

    // ALSO: Bridge to the 'signals' table so the Status Hub UI reacts in real-time
    if (signal_data && signal_data.pair) {
        await supabase.from('signals').insert([{
            pair: signal_data.pair,
            side: (signal_data.action || 'buy').toLowerCase(),
            sl: signal_data.sl,
            tp: signal_data.tp,
            status: 'received_webhook',
            signal_id: signalId ? String(signalId) : null,
            created_at: new Date().toISOString()
        }])
    }

    // --- Core Trade Execution Worker Queue Loop ---
    // Fetch all active client licenses/accounts for the given EA
    const { data: activeLicenses, error: activeLicensesError } = await supabase
      .from('licenses')
      .select('id, license_key, status, metaapi_account_id')
      .eq('ea_id', ea_id)
      .eq('status', 'active');

    if (activeLicensesError) {
      console.error("[Queue Error] Failed to fetch active licenses:", activeLicensesError.message);
    } else if (activeLicenses && activeLicenses.length > 0) {
      const metaApiToken = Deno.env.get("METAAPI_TOKEN");

      // Loop through all active trading accounts in the queue
      for (const userLicense of activeLicenses) {
        try {
          // If the metaapi_account_id is missing, treat as malformed configuration / credentials error
          if (!userLicense.metaapi_account_id) {
            throw new Error("Missing MetaAPI Account ID (broker connection is not provisioned)");
          }

          if (!metaApiToken) {
            throw new Error("Server misconfiguration: METAAPI_TOKEN env var is missing");
          }

          const accountId = userLicense.metaapi_account_id;
          const actionType = (signal_data.action || signal_data.type || 'BUY').toUpperCase() === 'BUY' ? 'ORDER_TYPE_BUY' : 'ORDER_TYPE_SELL';
          
          const tradePayload = {
            actionType,
            symbol: signal_data.pair,
            volume: Number(signal_data.lot || signal_data.volume || 0.01),
            stopLoss: signal_data.sl ? Number(signal_data.sl) : undefined,
            takeProfit: signal_data.tp ? Number(signal_data.tp) : undefined
          };

          console.log(`[Worker Queue] Dispatching trade to account ${accountId} for license ${userLicense.license_key}...`);
          
          const tradeResponse = await fetch(
            `https://mt-client-api-v1.agiliumtrade.agiliumtrade.ai/users/current/accounts/${accountId}/trade`,
            {
              method: "POST",
              headers: {
                "auth-token": metaApiToken,
                "Accept": "application/json",
                "Content-Type": "application/json"
              },
              body: JSON.stringify(tradePayload)
            }
          );

          if (!tradeResponse.ok) {
            const errorText = await tradeResponse.text();
            throw new Error(`MetaAPI returned status ${tradeResponse.status}: ${errorText}`);
          }

          const tradeResult = await tradeResponse.json();
          console.log(`[Worker Queue Success] License ${userLicense.license_key} executed trade successfully:`, tradeResult);

          // Log successful trade
          await supabase.from('signal_logs').insert([{
            license_id: userLicense.id,
            ea_id: ea_id,
            raw_data: { success: true, tradeResult, signal_data },
            status: 'executed'
          }]);

        } catch (queueErr: any) {
          // Isolate Failures: If a single user fails, catch and handle immediately
          console.error(`[Worker Queue Failure] Execution failed for license ${userLicense.license_key}:`, queueErr.message);

          // Mark that specific user account status as "FAILED_CREDENTIALS" in the database
          const { error: dbUpdateErr } = await supabase
            .from('licenses')
            .update({ status: 'FAILED_CREDENTIALS' })
            .eq('id', userLicense.id);

          if (dbUpdateErr) {
            console.error(`[Worker Queue Error] Failed to update license status: ${dbUpdateErr.message}`);
          } else {
            console.log(`[Worker Queue] Marked license ${userLicense.license_key} as FAILED_CREDENTIALS.`);
          }

          // Log failure
          await supabase.from('signal_logs').insert([{
            license_id: userLicense.id,
            ea_id: ea_id,
            raw_data: { success: false, error: queueErr.message, signal_data },
            status: 'failed'
          }]);
        }
      }
    }

    return new Response(JSON.stringify({ success: true, message: 'Webhook processed and queue dispatched' }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    })
  } catch (error) {
    console.error('[Fatal Webhook Error]', error);
    return new Response(JSON.stringify({ error: error.message }), { 
      status: 500,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' }
    })
  }
})
