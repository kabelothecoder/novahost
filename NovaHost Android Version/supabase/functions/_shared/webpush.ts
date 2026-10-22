/**
 * Web Push, on Web Crypto alone.
 *
 * Implements VAPID (RFC 8292) and the aes128gcm payload encryption of RFC 8291
 * directly, rather than through `web-push`, which leans on Node's crypto and
 * is not something to discover half-works inside the edge runtime while real
 * users are waiting for a trade alert.
 *
 * ## Keys
 *
 * The VAPID keypair lives in `internal_secrets` (service role only), not in a
 * function secret. It is generated on first use by whichever function asks
 * first, so the private key never passes through a terminal, a chat or a
 * dashboard. Changing it would invalidate every subscription on every phone,
 * so it is created once and only ever read after that.
 */
import type { SupabaseClient } from 'https://esm.sh/@supabase/supabase-js@2'

const enc = new TextEncoder()

/** Bytes backed by a plain ArrayBuffer, which is what Web Crypto and fetch accept. */
type Bytes = Uint8Array<ArrayBuffer>

export function b64url(bytes: Uint8Array): string {
  let s = ''
  for (const b of bytes) s += String.fromCharCode(b)
  return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

export function fromB64url(s: string): Bytes {
  const pad = '='.repeat((4 - (s.length % 4)) % 4)
  const bin = atob((s + pad).replace(/-/g, '+').replace(/_/g, '/'))
  return Uint8Array.from(bin, (c) => c.charCodeAt(0))
}

function concat(...parts: Uint8Array[]): Bytes {
  const out = new Uint8Array(parts.reduce((n, p) => n + p.length, 0))
  let o = 0
  for (const p of parts) { out.set(p, o); o += p.length }
  return out
}

export interface Vapid {
  publicKey: string            // base64url, 65-byte uncompressed point
  privateKey: CryptoKey        // ECDSA P-256, for signing the JWT
}

let cached: Vapid | null = null

/** Read the keypair, creating it if this project has never had one. */
export async function getVapid(db: SupabaseClient): Promise<Vapid> {
  if (cached) return cached

  const read = async () => {
    const { data } = await db
      .from('internal_secrets')
      .select('name, secret')
      .in('name', ['vapid_public_key', 'vapid_private_jwk'])
    const m = new Map((data ?? []).map((r: { name: string; secret: string }) => [r.name, r.secret]))
    return { pub: m.get('vapid_public_key'), jwk: m.get('vapid_private_jwk') }
  }

  let { pub, jwk } = await read()

  if (!pub || !jwk) {
    const pair = await crypto.subtle.generateKey(
      { name: 'ECDSA', namedCurve: 'P-256' }, true, ['sign', 'verify'],
    ) as CryptoKeyPair
    const rawPub = new Uint8Array(await crypto.subtle.exportKey('raw', pair.publicKey))
    const privJwk = await crypto.subtle.exportKey('jwk', pair.privateKey)

    /* `ignoreDuplicates`: if two functions race here, the first insert wins
       and both then read back the same pair. Never overwrite one. */
    await db.from('internal_secrets').upsert(
      [
        { name: 'vapid_public_key', secret: b64url(rawPub) },
        { name: 'vapid_private_jwk', secret: JSON.stringify(privJwk) },
      ],
      { onConflict: 'name', ignoreDuplicates: true },
    )
    ;({ pub, jwk } = await read())
    if (!pub || !jwk) throw new Error('VAPID keys could not be stored')
  }

  const privateKey = await crypto.subtle.importKey(
    'jwk', JSON.parse(jwk), { name: 'ECDSA', namedCurve: 'P-256' }, false, ['sign'],
  )
  cached = { publicKey: pub, privateKey }
  return cached
}

async function vapidHeader(endpoint: string, vapid: Vapid, subject: string): Promise<string> {
  const aud = new URL(endpoint).origin
  const header = b64url(enc.encode(JSON.stringify({ typ: 'JWT', alg: 'ES256' })))
  const claims = b64url(enc.encode(JSON.stringify({
    aud,
    exp: Math.floor(Date.now() / 1000) + 12 * 3600,
    sub: subject,
  })))
  const input = `${header}.${claims}`
  // Web Crypto returns ECDSA signatures as raw r||s, which is what JWS wants.
  const sig = new Uint8Array(await crypto.subtle.sign(
    { name: 'ECDSA', hash: 'SHA-256' }, vapid.privateKey, enc.encode(input),
  ))
  return `vapid t=${input}.${b64url(sig)}, k=${vapid.publicKey}`
}

async function hkdf(salt: Bytes, ikm: Bytes, info: Bytes, bytes: number) {
  const key = await crypto.subtle.importKey('raw', ikm, 'HKDF', false, ['deriveBits'])
  return new Uint8Array(await crypto.subtle.deriveBits(
    { name: 'HKDF', hash: 'SHA-256', salt, info }, key, bytes * 8,
  ))
}

/** RFC 8291: encrypt `payload` for one subscription, as an aes128gcm body. */
export async function encrypt(payload: Uint8Array, p256dh: string, auth: string): Promise<Bytes> {
  const uaPublic = fromB64url(p256dh)
  const authSecret = fromB64url(auth)

  const local = await crypto.subtle.generateKey(
    { name: 'ECDH', namedCurve: 'P-256' }, true, ['deriveBits'],
  ) as CryptoKeyPair
  const asPublic = new Uint8Array(await crypto.subtle.exportKey('raw', local.publicKey))

  const uaKey = await crypto.subtle.importKey(
    'raw', uaPublic, { name: 'ECDH', namedCurve: 'P-256' }, false, [],
  )
  const ecdh = new Uint8Array(await crypto.subtle.deriveBits(
    { name: 'ECDH', public: uaKey }, local.privateKey, 256,
  ))

  const ikm = await hkdf(
    authSecret, ecdh,
    concat(enc.encode('WebPush: info\0'), uaPublic, asPublic), 32,
  )
  const salt = crypto.getRandomValues(new Uint8Array(16))
  const cek = await hkdf(salt, ikm, enc.encode('Content-Encoding: aes128gcm\0'), 16)
  const nonce = await hkdf(salt, ikm, enc.encode('Content-Encoding: nonce\0'), 12)

  const key = await crypto.subtle.importKey('raw', cek, 'AES-GCM', false, ['encrypt'])
  // One record, so the delimiter is 0x02 ("last record") and there is no padding.
  const cipher = new Uint8Array(await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv: nonce }, key, concat(payload, new Uint8Array([2])),
  ))

  const rs = new Uint8Array(4)
  new DataView(rs.buffer).setUint32(0, 4096)
  return concat(salt, rs, new Uint8Array([asPublic.length]), asPublic, cipher)
}

export interface PushTarget { endpoint: string; p256dh: string; auth: string }

export interface PushResult {
  ok: boolean
  status: number
  /** The endpoint no longer exists; stop sending to it. */
  gone: boolean
  error?: string
}

export async function sendWebPush(
  target: PushTarget,
  message: Record<string, unknown>,
  vapid: Vapid,
  opts: { subject: string; ttl?: number; urgency?: 'normal' | 'high'; topic?: string },
): Promise<PushResult> {
  try {
    const body = await encrypt(enc.encode(JSON.stringify(message)), target.p256dh, target.auth)
    const headers: Record<string, string> = {
      Authorization: await vapidHeader(target.endpoint, vapid, opts.subject),
      'Content-Encoding': 'aes128gcm',
      'Content-Type': 'application/octet-stream',
      TTL: String(opts.ttl ?? 900),
      Urgency: opts.urgency ?? 'high',
    }
    // A Topic replaces an undelivered earlier push with the same topic.
    if (opts.topic) headers.Topic = opts.topic.replace(/[^A-Za-z0-9_-]/g, '').slice(0, 32)

    const res = await fetch(target.endpoint, {
      method: 'POST', headers, body, signal: AbortSignal.timeout(10_000),
    })
    const gone = res.status === 404 || res.status === 410
    if (res.ok) return { ok: true, status: res.status, gone: false }
    const text = (await res.text().catch(() => '')).slice(0, 300)
    return { ok: false, status: res.status, gone, error: text || res.statusText }
  } catch (e) {
    return { ok: false, status: 0, gone: false, error: (e as Error).message }
  }
}
