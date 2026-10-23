const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}

/**
 * Retired. This was a one-off maintenance utility (never part of the
 * product) that reclaimed MetaCopier accounts no non-revoked licence
 * referenced. Its one approved run, on 2026-09-22, found the project
 * already down to 8 live accounts with none orphaned -- the ~150-account
 * figure behind the trial-cap error was never in the list this function
 * scanned (MetaCopier's list endpoint excludes soft-deleted accounts; only
 * a lookup by known id sees them). Nothing left for it to do.
 *
 * It has no licence scoping -- unlike every other function in this project,
 * it acted on the WHOLE MetaCopier project rather than one caller's own
 * account -- so it does not stay live the way broker-connect and
 * broker-diagnostics safely do. Same tombstone pattern brokerService.js
 * documents for test-broker-connection: the slug stays, every call refused.
 */
Deno.serve(async (req: Request) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: CORS_HEADERS })
  }
  return new Response(
    JSON.stringify({ success: false, error: 'admin-reclaim-orphans has been retired after its one approved run.' }),
    { status: 410, headers: { 'Content-Type': 'application/json', ...CORS_HEADERS } },
  )
})
