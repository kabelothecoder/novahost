/**
 * The fleet: every robot this handset holds a licence for.
 *
 * ## Why this exists
 *
 * Home's Robot List was a list of one. `ensureSeeded()` built a single row
 * out of whatever `validate-license` had last written to secStorage, "Connect
 * new robot" raised a toast saying it needed a new licence key, and picking a
 * row changed `activeEaId` and nothing else — the name, the art, the accent
 * and the allowed symbols all stayed on the robot the key had bound. A list
 * that cannot list and a switcher that cannot switch.
 *
 * Everything needed to fix it was already deployed. `my-licenses` answers
 * "which licences is this DEVICE bound to", which is the right question: an
 * install holds a mentor-issued key and the anon key with no NovaHost auth
 * session, so an email- or uid-scoped query returns nothing (and RLS on
 * `licenses` has no anon policy, so it could never have returned anything).
 * It unions `device_activations` with the legacy `licenses.metadata.device_id`
 * so installs from before that split are not dropped, and it filters out
 * suspended and expired keys — a switcher must only offer rows the user can
 * actually switch to.
 *
 * ## Switching is a re-validation, not a local swap
 *
 * `selectRobot` runs `validate-license` for the chosen key. That is what makes
 * the swap real: the same function that seeds identity on first activation
 * re-seeds it here, so ROBOT_NAME, AVATAR_URL, ALLOWED_SYMBOLS and the
 * mentor's accent all move together and PairsView, the backdrop, the hero and
 * the signal filter all follow. It also re-checks status and device binding at
 * the moment of the switch, so a key revoked since the list was fetched fails
 * where the user can see it rather than silently trading on a dead licence.
 *
 * The cost is one round trip per switch, which is also what buys the full art:
 * `my-licenses` strips base64 avatars out of list rows (a four-robot drawer
 * was an 8.8 MB response, re-fetched on every resume), and `validate-license`
 * returns the selected robot's blob once.
 */

import { supabase } from '../utils/supabaseClient.ts'
import { getDeviceId } from '../utils/deviceId.js'

const FLEET_TIMEOUT_MS = 12000

/** Same shape the rest of the services use: an abortable invoke. */
async function invoke(fn, body) {
  const controller = new AbortController()
  let timedOut = false
  const timer = setTimeout(() => { timedOut = true; controller.abort() }, FLEET_TIMEOUT_MS)

  try {
    let { data, error } = await supabase.functions.invoke(fn, { body, signal: controller.signal })

    if (timedOut) return { ok: false, message: 'That took too long. Check your connection.' }

    /* supabase-js v2 hands a non-2xx back as a FunctionsHttpError with `data`
       null and the body on `error.context`. Without this unwrap a 400 that
       explains itself reads as "Edge Function returned a non-2xx status". */
    if (error && !data && error.context && typeof error.context.json === 'function') {
      try { data = await error.context.json(); error = null } catch { /* genuinely no body */ }
    }

    if (error && !data) return { ok: false, message: 'Could not reach the licence server.' }
    return { ok: true, data }
  } catch (err) {
    if (timedOut) return { ok: false, message: 'That took too long. Check your connection.' }
    return { ok: false, message: err?.message || 'Something went wrong.' }
  } finally {
    clearTimeout(timer)
  }
}

/**
 * Normalises one `my-licenses` row into the shape the UI draws.
 *
 * `id` is the LICENCE id, not the EA id. Two licences can point at the same
 * expert advisor — a mentor reissuing a key, or a user holding both a trial
 * and a full seat — and keying the list on the EA would collapse those into
 * one row that switches to whichever key happened to sort first.
 *
 * `avatar` stays null rather than falling back here. The widget decides what
 * to draw when a robot has no usable art, and `hasArt` is the distinction it
 * needs: "no art" and "art that is a 3 MB data URI the list endpoint stripped"
 * look identical in this field and should not look identical on screen.
 */
function normalise(row) {
  const ea = row.expert_advisors || {}
  return {
    id: row.id,
    key: row.license_key,
    name: ea.display_name || ea.name || 'NovaHost AI',
    avatar: ea.avatar_url || null,
    hasArt: ea.has_art === true || Boolean(ea.avatar_url),
    accent: ea.accent_color || null,
    symbols: row.allowed_symbols || ea.symbols || [],
    expiresAt: row.expires_at || null,
  }
}

/**
 * Every usable licence on this device.
 *
 * An empty list is a legitimate answer — a handset whose key was activated
 * before `device_activations` existed and whose licence row carries no
 * metadata device id is real, and it is not an error. The caller decides
 * whether to fall back to the locally-seeded single robot.
 */
export async function loadFleet() {
  const res = await invoke('my-licenses', { deviceId: getDeviceId() })
  if (!res.ok) return { ok: false, message: res.message, robots: [] }

  const data = res.data
  if (!data || data.success !== true) {
    return { ok: false, message: (data && data.error) || 'Could not read your licences.', robots: [] }
  }

  return { ok: true, robots: (data.licenses || []).map(normalise) }
}
