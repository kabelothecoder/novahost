/**
 * RETIRED. Kept only as a tombstone; delete the function from the Supabase
 * dashboard and then delete this directory.
 *
 * This was the Paystack-era R150 reactivation webhook, from before the move ran
 * through Payfast. It survived the switch unnoticed and was still deployed and
 * publicly reachable.
 *
 * What it did was a bare:
 *
 *     update subscriptions set device_id = <metadata.android_id> where email = ...
 *
 * -- no cooldown, no yearly cap, no move ticket, no ledger entry, and no token
 * rotation, so the handset the licence moved away from kept a session that
 * still matched. It is a complete bypass of every rule in
 * generate-payfast-checkout and payfast-webhook, reachable by anyone who could
 * produce a valid Paystack HMAC.
 *
 * It is inert today only because PAYSTACK_SECRET_KEY is unset, which makes the
 * key import throw before it reaches the database. "Inert because a secret is
 * missing" is not a security control -- setting that secret for any reason
 * would quietly re-open the hole -- so the body is gone rather than disabled.
 */
Deno.serve(() =>
  new Response(
    JSON.stringify({
      error: 'This endpoint has been retired. Device moves run through request-device-move and payfast-webhook.',
    }),
    { status: 410, headers: { 'Content-Type': 'application/json' } },
  )
)
