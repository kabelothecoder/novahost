import "jsr:@supabase/functions-js/edge-runtime.d.ts";

// TOMBSTONE -- 2026-09-02
//
// This endpoint is retired. It was the MetaAPI-era signal dispatcher, replaced
// by `broadcast-signal` (mentor fan-out) and `claim-signals` (device poll).
//
// It never actually dispatched anything: past the database insert it only
// console.log'd the terminal it was supposedly sending to. What it did do was
// accept a broker password in `terminal.password`, base64-decode it, and log
// the server and account id alongside it.
//
// It also wrote `side` and `signal_id`, columns the live `signals` table does
// not have, so its one real side effect had been throwing regardless.
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
        "dispatch-signal has been retired. Signals run through broadcast-signal and claim-signals.",
    }),
    {
      status: 410,
      headers: { "Content-Type": "application/json" },
    },
  )
);
