import "jsr:@supabase/functions-js/edge-runtime.d.ts";

// TOMBSTONE -- 2026-09-02
//
// This endpoint is retired, and it was the most dangerous of the four retired
// with it. It minted real licence keys.
//
// It was deployed verify_jwt:false and guarded only by an `x-partner-secret`
// header compared against `Deno.env.get('PARTNER_SECRET') ?? 'dev-partner-secret'`.
// PARTNER_SECRET was never set on this project, so the literal string
// 'dev-partner-secret' -- sitting in the source -- was the live credential:
// anyone sending that header reached the service-role call to
// generate_license_secure and could mint unlimited free licences straight past
// the R599 paygate.
//
// Verified from outside before retiring: 'dev-partner-secret' returned 400
// (past the guard, into the RPC) while a wrong secret returned 401. The only
// reason it was not already being exploited is an unrelated accident -- the
// function passed p_email/p_mentor_id/p_robot_id, which does not match the
// real signature generate_license_secure(text,text,text,int). That is a typo,
// not a defence.
//
// Note that the RPC lockdown of 2026-09-02 did NOT cover this path: revoking
// anon and PUBLIC execute does nothing to a function called with the service
// role, which is what this did.
//
// This function had no local copy in the repo before this tombstone -- it
// existed only as a deployed artifact, which is how it went unreviewed.
//
// Left deployed rather than deleted so a straggler fails loudly with 410
// instead of 404-ing into a retry loop.

Deno.serve(() =>
  new Response(
    JSON.stringify({
      success: false,
      code: "ENDPOINT_RETIRED",
      error:
        "new-gen-fulfillment has been retired. Licence issuing runs through generate-license.",
    }),
    {
      status: 410,
      headers: { "Content-Type": "application/json" },
    },
  )
);
