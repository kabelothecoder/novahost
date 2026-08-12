import { supabase } from "@/integrations/supabase/client";

export async function pushTradeLog(licenseKey: string, pair: string, action: string, pl: number) {
  try {
    const { error } = await supabase.from('trade_logs').insert({
      license_key: licenseKey,
      pair,
      action,
      pl, 
    });

    if (error) {
      console.error("Failed to push trade log:", error.message);
    }
  } catch (err) {
    console.error("Unexpected error pushing trade log:", err);
  }
}
