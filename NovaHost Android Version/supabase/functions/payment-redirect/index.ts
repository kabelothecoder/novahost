import { serve } from "https://deno.land/std@0.208.0/http/server.ts";

serve((req) => {
  const url = new URL(req.url);
  const status = url.searchParams.get("status") || "success";
  
  // Payfast strictly requires return_url and cancel_url to be https://
  // It does not accept custom URI schemes like metahost://
  // This function acts as a bridge: Payfast redirects here via HTTPS,
  // and this function immediately redirects the browser to the deep link.
  
  return new Response(null, {
    status: 302,
    headers: {
      Location: `metahost://payment/${status}`
    }
  });
});
