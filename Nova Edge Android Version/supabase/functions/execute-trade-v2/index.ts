import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const supabase = createClient(
  Deno.env.get('SUPABASE_URL') ?? '',
  Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
)

serve(async (req: Request) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: CORS_HEADERS })
  }

  try {
    const body = await req.json()
    const { pair, type, volume, sl, tp, terminal, license_key } = body

    if (!terminal || !terminal.account) {
        return new Response(JSON.stringify({ success: false, error: "Missing terminal configuration." }), {
            status: 400,
            headers: { 'Content-Type': 'application/json', ...CORS_HEADERS },
        })
    }

    const accountId = terminal.account;

    // Decode password if needed
    let plainPassword = terminal.password;
    try {
      if (terminal.password) {
          plainPassword = atob(terminal.password).replace("MH_SALT_", "")
      }
    } catch (e) {
      console.warn("Failed to decode password")
    }

    const metaApiToken = Deno.env.get("METAAPI_TOKEN");
    if (!metaApiToken) {
        console.error("METAAPI_TOKEN is not configured.");
        return new Response(JSON.stringify({ success: false, error: "Server misconfiguration" }), {
            status: 500,
            headers: { 'Content-Type': 'application/json', ...CORS_HEADERS },
        })
    }

    console.log(`[Order Execution Start] Account=${accountId}, Pair=${pair}, Type=${type}, Vol=${volume}`);

    // --- STEP 1: CheckRisk() - Validate Margin Availability ---
    const accInfoResponse = await fetch(`https://mt-client-api-v1.agiliumtrade.agiliumtrade.ai/users/current/accounts/${accountId}/accountInformation`, {
        method: "GET",
        headers: {
            "auth-token": metaApiToken,
            "Accept": "application/json"
        }
    });

    if (!accInfoResponse.ok) {
        const errorText = await accInfoResponse.text();
        console.error(`[CheckRisk Error] Failed to fetch account info: ${errorText}`);
        return new Response(JSON.stringify({ success: false, error: "Could not verify account margin." }), {
            status: 400,
            headers: { 'Content-Type': 'application/json', ...CORS_HEADERS },
        });
    }

    const accInfo = await accInfoResponse.json();
    const marginFree = accInfo.freeMargin || 0;
    const balance = accInfo.balance || 0;

    // Daily loss limit simple guardrail (prevent trading if balance is extremely low or no free margin)
    if (marginFree < 5.0) {
        console.error(`[CheckRisk Error] Insufficient free margin: ${marginFree}`);
        return new Response(JSON.stringify({ success: false, error: "Insufficient free margin to execute trade." }), {
            status: 400,
            headers: { 'Content-Type': 'application/json', ...CORS_HEADERS },
        });
    }

    // --- STEP 2: OrderSend() - Dispatch Trade ---
    const actionType = type.toUpperCase() === 'BUY' ? 'ORDER_TYPE_BUY' : 'ORDER_TYPE_SELL';
    
    const tradePayload = {
        actionType: actionType,
        symbol: pair,
        volume: Number(volume),
        stopLoss: sl ? Number(sl) : undefined,
        takeProfit: tp ? Number(tp) : undefined
    };

    const tradeResponse = await fetch(`https://mt-client-api-v1.agiliumtrade.agiliumtrade.ai/users/current/accounts/${accountId}/trade`, {
        method: "POST",
        headers: {
            "auth-token": metaApiToken,
            "Accept": "application/json",
            "Content-Type": "application/json"
        },
        body: JSON.stringify(tradePayload)
    });

    if (!tradeResponse.ok) {
        const errorText = await tradeResponse.text();
        // --- STEP 3: Error Logging (GetLastError / Print equivalent) ---
        console.error(`[OrderSend Error] Execution failed for ${pair}. Code: ${tradeResponse.status}, Reason: ${errorText}`);
        
        // Log to database
        // license_id is a uuid FK to licenses.id — license_key is the raw text key,
        // not a uuid, so it belongs in the license_key column, not license_id.
        await supabase.from('signal_logs').insert([{
            license_key: license_key,
            ea_id: accountId,
            raw_data: { error: errorText, payload: tradePayload },
            status: 'failed'
        }]);

        return new Response(JSON.stringify({ 
            success: false, 
            message: "Trade execution failed.",
            details: errorText
        }), {
            status: 400,
            headers: { 'Content-Type': 'application/json', ...CORS_HEADERS },
        });
    }

    const tradeResult = await tradeResponse.json();
    console.log(`[OrderSend Success] Executed ${actionType} on ${pair}. Result:`, tradeResult);

    // Update signal status in the database
    await supabase.from('signals').insert([{
        pair: pair,
        side: type.toLowerCase(),
        sl: sl,
        tp: tp,
        status: 'executed',
        signal_id: tradeResult.orderId || null,
        created_at: new Date().toISOString()
    }]);

    return new Response(JSON.stringify({ 
        success: true, 
        message: `Trade executed successfully. Order ID: ${tradeResult.orderId}`
    }), {
        status: 200,
        headers: { 'Content-Type': 'application/json', ...CORS_HEADERS },
    });

  } catch (error) {
    console.error('[Fatal Error] Trade Execution Exception:', error.message)
    return new Response(JSON.stringify({ success: false, error: error.message }), {
      status: 500,
      headers: { 'Content-Type': 'application/json', ...CORS_HEADERS },
    })
  }
})
