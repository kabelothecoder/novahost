import "jsr:@supabase/functions-js/edge-runtime.d.ts";

// TOMBSTONE -- 2026-09-02
//
// This endpoint is retired. It was the MetaAPI-era broker link test, replaced
// by `metacopier-connect`, and it depended on METAAPI_TOKEN -- an integration
// this product no longer uses.
//
// It was deployed verify_jwt:false, so it was callable by anyone, and it took a
// broker `password` in the request body, base64-decoded it, and logged the
// server, account id and licence key to the function console on every call.
// Broker credentials do not belong in a public endpoint or in log output.
//
// The only caller anywhere was the iOS build, which is out of launch scope.
// Android reaches broker linking through metacopier-connect.
//
// Left deployed rather than deleted so a straggler fails loudly with 410
// instead of 404-ing into a retry loop.

Deno.serve(() =>
  new Response(
    JSON.stringify({
      success: false,
      code: "ENDPOINT_RETIRED",
      error:
        "test-broker-connection has been retired. Broker linking runs through metacopier-connect.",
    }),
    {
      status: 410,
      headers: { "Content-Type": "application/json" },
    },
  )
);
