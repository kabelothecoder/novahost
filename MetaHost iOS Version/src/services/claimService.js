import { supabase } from '../utils/supabaseClient'
import { getDeviceId } from '../utils/deviceId'
import { authState } from './authService'

/**
 * The phone's half of the licence claim (request-claim-code).
 *
 * A paid handset asks for a 30-minute code, the user types it on their robot
 * provider's website, and that site redeems it through the NovaHost API. This
 * side then polls the code and hands back the key once it has been redeemed.
 *
 * Both calls answer refusals in the body, never as a thrown error, so the
 * screen can show the function's own sentence ("already claimed this month").
 */

/** Unwraps a non-2xx body the way activateLicenseKey does. */
async function invoke (body) {
  let { data, error } = await supabase.functions.invoke('request-claim-code', {
    body: { email: authState.email, deviceId: getDeviceId(), ...body },
  })
  if (error && !data && error.context && typeof error.context.json === 'function') {
    data = await error.context.json().catch(() => null)
  }
  return data
}

/**
 * @returns {Promise<{ok: true, code: string, expiresAt: string} | {ok: false, message: string}>}
 */
export async function requestClaimCode () {
  try {
    const data = await invoke({ action: 'issue' })
    if (data && data.success && data.code) {
      return { ok: true, code: data.code, expiresAt: data.expires_at }
    }
    return { ok: false, message: (data && data.error) || 'Could not get a code. Try again.' }
  } catch (err) {
    console.error('request-claim-code failed:', err)
    return { ok: false, message: 'Could not get a code. Check your connection.' }
  }
}

/**
 * @returns {Promise<{state: 'waiting'|'redeemed'|'expired'|'unknown'|'error', licenseKey?: string}>}
 */
export async function claimCodeStatus (code) {
  try {
    const data = await invoke({ action: 'status', code })
    if (!data || !data.success) return { state: 'error' }
    return { state: data.state, licenseKey: data.license_key }
  } catch {
    return { state: 'error' }
  }
}

/** ABCDEFGH → ABCD-EFGH, for reading off one screen and typing on another. */
export function formatClaimCode (code) {
  return String(code || '').replace(/(.{4})(?=.)/g, '$1-')
}
