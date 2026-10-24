import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

/**
 * GET ?ea=<expert_advisors.id> -> that robot's avatar as a real image.
 *
 * Mentor art lives in `expert_advisors.avatar_url`, mostly as base64 data URIs.
 * Mail clients (Gmail above all) strip data: images, so an email can only show
 * a robot's art through a plain https URL. This is that URL. Public on purpose:
 * the same art is already shown to anyone holding a licence for the robot.
 *
 * Deploy with --no-verify-jwt.
 */

const FALLBACK = 'https://app.novahost-ea.app/robot_avatar.png'
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

Deno.serve(async (req) => {
  const id = new URL(req.url).searchParams.get('ea') ?? ''
  if (!UUID.test(id)) return Response.redirect(FALLBACK, 302)

  try {
    const db = createClient(Deno.env.get('SUPABASE_URL')!, Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!)
    const { data } = await db.from('expert_advisors').select('avatar_url').eq('id', id).maybeSingle()
    const art = typeof data?.avatar_url === 'string' ? data.avatar_url.trim() : ''

    if (/^https?:\/\//i.test(art)) return Response.redirect(art, 302)

    const m = art.match(/^data:(image\/(?:png|jpe?g|gif|webp));base64,(.+)$/is)
    if (!m) return Response.redirect(FALLBACK, 302)

    const bytes = Uint8Array.from(atob(m[2].replace(/\s/g, '')), (c) => c.charCodeAt(0))
    return new Response(bytes, {
      headers: {
        'Content-Type': m[1].toLowerCase(),
        'Cache-Control': 'public, max-age=86400',
      },
    })
  } catch {
    return Response.redirect(FALLBACK, 302)
  }
})
