import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}

/**
 * Every licence activated on one handset, with the robot behind each.
 *
 * ## Why this is a function and not a PostgREST query
 *
 * The app used to read `licenses` directly, filtered on `user_email`. That
 * failed twice over. There is no `user_email` column -- it is `owner_email` --
 * so PostgREST answered 400 and the client's bare catch turned the error into
 * an empty list. And even spelled correctly it could never have worked: an
 * install holds a mentor-issued key and the anon key, with no Supabase auth
 * session, and RLS on `licenses` has no `anon` policy. `auth.uid()` is null, so
 * every row is filtered out. The drawer was structurally incapable of showing
 * anything.
 *
 * Email was the wrong key regardless. `owner_email` is null on most rows, and a
 * key-only user never supplies an email at all -- they type a licence key.
 *
 * So the device is the key, the same identity `validate-license` already binds
 * against, and the lookup runs service-role behind a function like every other
 * device-facing endpoint here.
 *
 * ## Two registries, deliberately
 *
 * `validate-license` now writes both `device_activations` and the legacy
 * `licenses.metadata.device_id`. Installs activated before that change exist
 * only in metadata, so this unions the two. Dropping the metadata path would
 * empty the drawer for every user already in the field.
 */
/**
 * Strips robot art out of a list row.
 *
 * Mentors upload avatars through the portal and they land in
 * `expert_advisors.avatar_url` as base64 data URIs -- 216KB to 3.0MB each. A
 * drawer listing four robots was therefore an 8.8MB response, re-fetched on
 * every app resume. On mobile data that is not a slow list, it is a broken one.
 *
 * A 40dp row does not need a 3MB JPEG. Real URLs pass through (the image loader
 * fetches and caches those itself); data URIs are dropped and flagged, and the
 * row renders the local placeholder. The selected robot still gets its full art
 * -- picking one re-runs `validate-license` for that key, which returns the
 * blob once instead of once per robot per resume.
 *
 * The proper fix is upstream: the portal should put art in Supabase Storage and
 * store a URL. Until it does, this keeps the drawer usable.
 */
function slim(license: Record<string, unknown>) {
  const ea = license.expert_advisors as Record<string, unknown> | null
  if (!ea) return license

  const art = typeof ea.avatar_url === 'string' ? ea.avatar_url : null
  const isUrl = !!art && /^https?:\/\//i.test(art)

  return {
    ...license,
    expert_advisors: {
      ...ea,
      avatar_url: isUrl ? art : null,
      // So the row can say "this robot has art, it just isn't inline" rather
      // than looking like a robot nobody bothered to brand.
      has_art: !!art,
      // Same treatment: background video urls are the other blob-shaped column.
      background_video_url:
        typeof ea.background_video_url === 'string' &&
        /^https?:\/\//i.test(ea.background_video_url)
          ? ea.background_video_url
          : null,
      // tts_script is free text a mentor typed and is never read from a list
      // row -- it is fetched with the rest of the identity on selection.
      tts_script: undefined,
    },
  }
}

Deno.serve(async (req: Request) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: CORS_HEADERS })
  }

  const json = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), {
      status,
      headers: { 'Content-Type': 'application/json', ...CORS_HEADERS },
    })

  try {
    const supabase = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
    )

    const body = await req.json().catch(() => ({}))
    const rawDevice = body.android_id ?? body.device_id ?? body.deviceId
    const deviceId = rawDevice ? String(rawDevice).trim() : ''

    if (!deviceId) {
      return json({ success: false, error: 'A device id is required.' }, 400)
    }

    // ---- Which licences is this handset bound to? --------------------------
    const ids = new Set<string>()

    const { data: seats, error: seatErr } = await supabase
      .from('device_activations')
      .select('license_id')
      .eq('device_id', deviceId)
      .eq('status', 'active')

    if (seatErr) throw seatErr
    for (const s of seats ?? []) if (s.license_id) ids.add(s.license_id as string)

    // Legacy binding. Kept until every install has re-activated at least once.
    const { data: legacy, error: legacyErr } = await supabase
      .from('licenses')
      .select('id')
      .eq('metadata->>device_id', deviceId)

    if (legacyErr) throw legacyErr
    for (const l of legacy ?? []) ids.add(l.id as string)

    if (ids.size === 0) {
      // Not an error. A handset with no keys yet is the ordinary first-run
      // state, and the drawer needs to tell those two cases apart -- "you have
      // no keys" and "we could not ask" are different sentences.
      return json({ success: true, licenses: [] })
    }

    // ---- The licences themselves, with their robots ------------------------
    const { data: licenses, error: licErr } = await supabase
      .from('licenses')
      .select(`
        id,
        license_key,
        ea_id,
        status,
        owner_email,
        allowed_symbols,
        expires_at,
        expert_advisors:expert_advisors!licenses_ea_id_fkey(
          id,
          name,
          display_name,
          avatar_url,
          accent_color,
          background_video_url,
          tts_script,
          symbols
        )
      `)
      .in('id', Array.from(ids))
      .order('created_at', { ascending: false })

    if (licErr) throw licErr

    const now = new Date()

    // Suspended and expired keys are dropped rather than listed greyed out.
    // The drawer is a switcher: everything in it must be something the user can
    // actually switch to, or tapping a row fails with no explanation.
    const usable = (licenses ?? []).filter((l: Record<string, unknown>) => {
      if (l.status !== 'active') return false
      const exp = l.expires_at as string | null
      return !exp || new Date(exp) >= now
    })

    return json({ success: true, licenses: usable.map(slim) })

  } catch (err) {
    console.error('[my-licenses] fatal:', err)
    return json({ success: false, error: (err as Error).message }, 500)
  }
})
