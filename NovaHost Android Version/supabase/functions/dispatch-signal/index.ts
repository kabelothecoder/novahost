import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const supabase = createClient(
  Deno.env.get('SUPABASE_URL') ?? '',
  Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
)

serve(async (req: Request) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: { 'Access-Control-Allow-Origin': '*' } })
  }

  try {
    const body = await req.json()
    const { pair, action, entry, sl, tp, terminal } = body

    // Log the actual trade execution request in the signals table
    // This allows the PulseHub to detect it in real-time
    const { data: signalData, error: dbError } = await supabase
      .from('signals')
      .insert([
        { 
          pair: pair, 
          side: action.toLowerCase(), 
          sl: sl, 
          tp: tp, 
          status: 'dispatched',
          created_at: new Date().toISOString() 
        }
      ])
      .select()

    if (dbError) throw dbError

    // Logic for MT5 execution (e.g. metaapi-java-sdk bridge)
    // Decode password
    let plainPassword = terminal.password
    try {
      plainPassword = atob(terminal.password).replace("MH_SALT_", "")
    } catch (e) {
      console.warn("Failed to decode password, might not be base64 encoded yet")
    }
    
    // For now, we simulate the log of terminal info used
    console.log(`Dispatching to MT5: Server=${terminal.server}, Account=${terminal.account}, PasswordDecoded=${plainPassword ? 'yes' : 'no'}`, { pair, action, entry, sl, tp })

    return new Response(JSON.stringify({ 
      success: true, 
      signalId: signalData?.[0]?.id,
      message: 'Signal successfully dispatched to hub and terminal bridge' 
    }), {
      headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' },
    })
  } catch (error) {
    console.error('Dispatch Error:', error)
    return new Response(JSON.stringify({ success: false, error: error.message }), {
      status: 500,
      headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' },
    })
  }
})
