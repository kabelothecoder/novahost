import "jsr:@supabase/functions-js/edge-runtime.d.ts";

// TOMBSTONE -- 2026-09-02
//
// This endpoint is retired. It was the MetaAPI-era inbound signal webhook,
// replaced by `broadcast-signal` (mentor fan-out) and `claim-signals` (device
// poll).
//
// It was deployed verify_jwt:false and authenticated on an `ea_id` plus
// `license_key` pair in the body, then inserted straight into `signals` with
// the service role. Paired with the licence-key exposure closed on the same
// day -- any signed-in account could read every key out of `licenses` -- that
// made it an unauthenticated path for injecting trade signals at somebody
// else's subscribers.
//
// It also wrote `side` and `signal_id`, columns the live `signals` table does
// not have, so every insert it attempted had been throwing regardless.
//
// No caller in the Android app, the mentor portal, or any other edge function.
// Left deployed rather than deleted so a straggler fails loudly with 410
// instead of 404-ing into a retry loop.

Deno.serve(() =>
  new Response(
    JSON.stringify({
      success: false,
      code: "ENDPOINT_RETIRED",
      error:
        "webhook-handler has been retired. Signals run through broadcast-signal and claim-signals.",
    }),
    {
      status: 410,
      headers: { "Content-Type": "application/json" },
    },
  )
);
