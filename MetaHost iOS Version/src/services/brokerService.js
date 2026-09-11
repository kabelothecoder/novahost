/**
 * The broker link: connecting an MT4/MT5 account, and reading what it holds.
 *
 * ## What this replaces
 *
 * `TerminalView.doConnect()` posted to `test-broker-connection`. That endpoint
 * is a tombstone — it returns 410 with "test-broker-connection has been
 * retired. Broker linking runs through metacopier-connect." — so **every
 * connect attempt from this app failed**, and had done since the MetaAPI
 * bridge was removed. The screen was not half-built; it was pointed at a
 * corpse.
 *
 * Three further faults went with it, and each is worth naming because the fix
 * is not obvious from the call site:
 *
 *   1. It sent no `license_key`. The link is recorded on the LICENCE
 *      (`licenses.metadata.metacopier_account_id`) because app users
 *      authenticate by key and have no `auth.users` row. A connect request
 *      without a key has nothing to bind to, so even a working endpoint could
 *      not have completed the link.
 *   2. On success it wrote the broker **password** into device storage as
 *      `MH_TERMINAL_CONFIG`. Nothing ever read it back. A trading password
 *      sitting in localStorage is a liability with no compensating feature,
 *      and `metacopier-connect` does not accept the field on any later call —
 *      it forwards credentials to MetaCopier once, at link time, and never
 *      needs them again.
 *   3. The session list was seeded with a hardcoded row
 *      (`Exness-MT5Real / 84912347`). A user who had never connected anything
 *      opened the screen to a green LIVE pill against an account number that
 *      was not theirs.
 *
 * ## Why link state is read back rather than remembered
 *
 * The old screen kept its own `sessions` array in device storage, so the UI's
 * idea of "connected" was whatever this handset last did. The truth lives on
 * the licence and `license-status` reports it: whether an account is attached,
 * which server, and whether MetaCopier's session to the broker is actually up.
 * A phone that was reinstalled, or a licence linked from the Android handset,
 * both show correctly — neither could before.
 */

import { supabase } from '../utils/supabaseClient.ts'
import { secStorage } from '../utils/secStorage.js'
import { getDeviceId } from '../utils/deviceId.js'

/* ── Error copy ──────────────────────────────────────────────────────
   `metacopier-connect` sends real prose for the failures a user can act on
   — a rejected login names the password type and the server spelling — so
   the server's message wins wherever it sent one. These cover the codes
   that arrive bare. */
const CODE_HINTS = {
  LICENCE_UNKNOWN:    'This device is not activated. Enter your licence key first.',
  LICENCE_INACTIVE:   'This licence is not active.',
  LICENCE_EXPIRED:    'This licence has expired.',
  LINK_NOT_SAVED:     'Your broker accepted the login but the connection could not be saved. Try again — it is safe to retry.',
  NO_ACCOUNT_LINKED:  'No trading account is connected to this licence yet.',
  BROKER_UNREACHABLE: 'Could not reach your broker just now.',
  ACCOUNT_DISCONNECTED: 'Your trading account is not connected to the broker right now.',
}

function describe(data, fallback) {
  if (!data) return fallback
  if (data.error) return String(data.error)
  if (data.code && CODE_HINTS[data.code]) return CODE_HINTS[data.code]
  return data.code ? `Refused (${data.code}).` : fallback
}

/**
 * `invoke` throws a FunctionsHttpError on any non-2xx and hides the response
 * body — which is exactly where the useful refusal lives. The 400 that says
 * "check the trading password, not the investor password" would otherwise
 * reach the user as "Edge Function returned a non-2xx status code". Every
 * call here digs the body back out.
 */
async function bodyOf(error) {
  try { return await error?.context?.json?.() } catch { return null }
}

/** Every call here is licence-scoped. One place to fail when there is no key. */
function requireKey() {
  const key = secStorage.getItem('LICENSE_KEY')
  if (!key) throw Object.assign(new Error('No licence key on this device.'), { code: 'NO_LICENCE' })
  return key
}

/* ── Connect ─────────────────────────────────────────────────────────
   The password is a parameter and never a field on anything this module
   keeps. It is handed to the edge function and goes out of scope with the
   call. */

/**
 * Link an MT4/MT5 account to this licence.
 *
 * `accountType` matters more than it looks: `metacopier-connect` derives the
 * broker's symbol suffix from it (a micro book lists XAUUSD.m, not XAUUSD),
 * and an order naming a symbol the broker does not list is rejected outright.
 * Sending it is what stops a micro-account user having a robot that places
 * orders and fills none of them.
 *
 * Returns `{ ok, connecting, message, code }`. `connecting` is a real and
 * distinct outcome, not a soft failure: MetaCopier registers the account
 * immediately but the broker session can take up to a minute to come up, and
 * during it `metacopier-execute` holds orders rather than losing them. The UI
 * must show that as a wait, never as a green light.
 */
export async function linkBroker({ accountNumber, password, server, platform, accountType }) {
  let licenseKey
  try { licenseKey = requireKey() } catch (e) { return { ok: false, code: e.code, message: e.message } }

  try {
    const { data, error } = await supabase.functions.invoke('metacopier-connect', {
      body: {
        license_key: licenseKey,
        account_number: String(accountNumber || '').trim(),
        password: String(password || ''),
        server: String(server || '').trim(),
        platform: String(platform || 'MT5').toUpperCase(),
        account_type: accountType || undefined,
      },
    })

    if (error) {
      const body = await bodyOf(error)
      if (body) return { ok: false, code: body.code, message: describe(body, 'Connection failed.') }
      throw error
    }

    if (!data?.success) {
      return { ok: false, code: data?.code, message: describe(data, 'Connection failed.') }
    }

    return {
      ok: true,
      connecting: data.status !== 'connected',
      code: data.code,
      platform: data.platform,
      message: data.message || 'Trading account connected.',
    }
  } catch (err) {
    return { ok: false, message: err?.message || 'Could not reach the connection service.' }
  }
}

/* ── Read back ───────────────────────────────────────────────────────*/

/**
 * What the licence says about its broker link.
 *
 * Deliberately never returns the MetaCopier account id — `license-status`
 * does not send it, and the client has no use for it. Orders resolve the
 * account server-side from the licence, which is what stops a request being
 * pointed at somebody else's account.
 *
 * `linked` is tri-state on purpose. `null` means the status read itself
 * failed, which is UNKNOWN and not the same as disconnected: rendering a
 * network blip as "broker down" sends users to reconnect an account that is
 * perfectly fine.
 */
export async function loadLinkState() {
  let licenseKey
  try { licenseKey = requireKey() } catch { return { linked: false, reason: 'no_licence' } }

  try {
    const { data, error } = await supabase.functions.invoke('license-status', {
      body: { license_key: licenseKey, device_id: getDeviceId() },
    })
    if (error) throw error
    if (!data?.success) return { linked: null, reason: 'unavailable', message: data?.error }

    return {
      linked: Boolean(data.linked),
      /* Absent on licences linked before this was recorded; the documented
         reading of absent is "assume connected". Only an explicit
         'connecting' is a wait. */
      connecting: data.metacopier_status === 'connecting',
      server: data.broker_server || null,
      platform: data.platform || null,
      connectedAt: data.connected_at || null,
      allowedSymbols: Array.isArray(data.allowed_symbols) ? data.allowed_symbols.map(String) : [],
      reason: data.reason,
      message: data.message,
    }
  } catch (err) {
    return { linked: null, reason: 'unavailable', message: err?.message }
  }
}

/**
 * Balance, equity and the currency they are in.
 *
 * The currency is not decoration. This project's live accounts are ZAR: a
 * balance of R596 handed to a calculator that assumes dollars sizes every
 * position about eighteen times too large. Callers must render and size
 * against the pair, never the figure alone.
 */
export async function loadAccount() {
  let licenseKey
  try { licenseKey = requireKey() } catch (e) { return { ok: false, message: e.message } }

  try {
    const { data, error } = await supabase.functions.invoke('broker-account', {
      body: { license_key: licenseKey },
    })
    if (error) {
      const body = await bodyOf(error)
      if (body) return { ok: false, code: body.code, message: describe(body, 'Could not read your account.') }
      throw error
    }
    if (!data?.success) return { ok: false, code: data?.code, message: describe(data, 'Could not read your account.') }
    return { ok: true, ...data }
  } catch (err) {
    return { ok: false, message: err?.message || 'Could not reach the broker service.' }
  }
}

/**
 * Open positions on the linked account, with live profit.
 *
 * This is the answer to "did my trade actually happen". Nothing else in the
 * product could say so: `trade_logs` has no writer anywhere in the codebase
 * and zero rows, and `metacopier-execute` returns a ticket at the moment it
 * places an order and then never mentions it again. The broker's own open
 * position list is the only record that exists.
 */
export async function loadPositions() {
  let licenseKey
  try { licenseKey = requireKey() } catch (e) { return { ok: false, message: e.message, positions: [] } }

  try {
    const { data, error } = await supabase.functions.invoke('broker-positions', {
      body: { license_key: licenseKey },
    })
    if (error) {
      const body = await bodyOf(error)
      if (body) return { ok: false, code: body.code, message: describe(body, 'Could not read your open trades.'), positions: [] }
      throw error
    }
    if (!data?.success) {
      return { ok: false, code: data?.code, message: describe(data, 'Could not read your open trades.'), positions: [] }
    }
    return { ok: true, ...data }
  } catch (err) {
    return { ok: false, message: err?.message || 'Could not reach the broker service.', positions: [] }
  }
}

/**
 * Closed trades, newest first, plus the running equity curve built from them.
 *
 * The curve is computed on the server for the same reason the loss streak is:
 * the raw history is dozens of positions carrying prices, swaps and
 * commissions each, and shipping all of it to a phone to draw one line wastes
 * the response and puts the account's whole trading record on the device.
 */
export async function loadHistory({ limit } = {}) {
  let licenseKey
  try { licenseKey = requireKey() } catch (e) { return { ok: false, message: e.message, closed: [], curve: [] } }

  try {
    const { data, error } = await supabase.functions.invoke('broker-performance', {
      body: { license_key: licenseKey, limit },
    })
    if (error) {
      const body = await bodyOf(error)
      if (body) return { ok: false, code: body.code, message: describe(body, 'Could not read your trade history.'), closed: [], curve: [] }
      throw error
    }
    if (!data?.success) {
      return { ok: false, code: data?.code, message: describe(data, 'Could not read your trade history.'), closed: [], curve: [] }
    }
    return { ok: true, ...data }
  } catch (err) {
    return { ok: false, message: err?.message || 'Could not reach the broker service.', closed: [], curve: [] }
  }
}
