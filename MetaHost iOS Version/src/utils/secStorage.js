/**
 * secStorage.js
 *
 * Obfuscated wrapper over localStorage for the licence key and session state.
 *
 * A PWA has no Keychain. This is not encryption and does not pretend to be —
 * the router guard re-validates every credential against the server on boot
 * (see services/authService.js `initializeAuth`), and that is the real gate.
 * What this layer buys, over the plain-XOR scheme it replaced:
 *
 *   - Per-install keying. A random salt is minted once per browser and mixed
 *     into the key stream, so a blob lifted out of one install's localStorage
 *     and pasted into another's no longer decodes.
 *   - Tamper detection that FAILS CLOSED. Every value carries a salted digest
 *     of its own plaintext. Flip a byte of the stored base64 — or hand-write
 *     `LICENSE_VALID = true` — and the digest no longer matches, so getItem
 *     drops the value and returns null. The session then falls through to a
 *     fresh server check rather than trusting the edit.
 *
 * Values written by the old scheme do not carry a digest and will read back as
 * null once; they are re-written in the new format on the next setItem. There
 * are no production PWA users to migrate, so this is deliberate rather than
 * papered over.
 */

const PREFIX = 'MH_SEC_';
// Distinct namespace, stored raw. Still caught by clear() (it is PREFIX-scoped).
const SALT_KEY = PREFIX + '_salt';

let _saltBytes = null;

/** This install's salt as a byte array, minted and persisted on first use. */
function saltBytes() {
  if (_saltBytes) return _saltBytes;

  let hex = null;
  try { hex = localStorage.getItem(SALT_KEY); } catch (e) { /* private mode, etc. */ }

  if (!hex || !/^[0-9a-f]{32,}$/i.test(hex)) {
    const bytes = new Uint8Array(16);
    if (typeof crypto !== 'undefined' && crypto.getRandomValues) {
      crypto.getRandomValues(bytes);
    } else {
      for (let i = 0; i < bytes.length; i++) bytes[i] = Math.floor(Math.random() * 256);
    }
    hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
    try { localStorage.setItem(SALT_KEY, hex); } catch (e) { /* best effort */ }
  }

  _saltBytes = new Uint8Array(hex.length / 2);
  for (let i = 0; i < _saltBytes.length; i++) {
    _saltBytes[i] = parseInt(hex.substr(i * 2, 2), 16);
  }
  return _saltBytes;
}

const enc = new TextEncoder();
const dec = new TextDecoder();

/** Symmetric: the salt key stream XORed with the value, plus the old constant
 *  so a zero byte in the salt still moves the plaintext. */
function xorBytes(bytes) {
  const key = saltBytes();
  const out = new Uint8Array(bytes.length);
  for (let i = 0; i < bytes.length; i++) {
    out[i] = bytes[i] ^ key[i % key.length] ^ 0x2a;
  }
  return out;
}

function toB64(bytes) {
  let s = '';
  for (let i = 0; i < bytes.length; i++) s += String.fromCharCode(bytes[i]);
  return btoa(s);
}

function fromB64(b64) {
  const s = atob(b64);
  const out = new Uint8Array(s.length);
  for (let i = 0; i < s.length; i++) out[i] = s.charCodeAt(i);
  return out;
}

/** FNV-1a 32-bit over salt + plaintext, as 8 hex chars. Not a MAC — a
 *  tamper-evidence check whose key is the per-install salt. */
function digest(plain) {
  const src = enc.encode(
    Array.from(saltBytes(), (b) => b.toString(16).padStart(2, '0')).join('') + '\x1f' + plain
  );
  let h = 0x811c9dc5;
  for (let i = 0; i < src.length; i++) {
    h ^= src[i];
    h = Math.imul(h, 0x01000193);
  }
  return (h >>> 0).toString(16).padStart(8, '0');
}

export const secStorage = {
  setItem(key, value) {
    const valStr = typeof value === 'string' ? value : JSON.stringify(value);
    const framed = digest(valStr) + '|' + valStr;
    try {
      localStorage.setItem(PREFIX + key, toB64(xorBytes(enc.encode(framed))));
    } catch (e) {
      /* quota / disabled storage — nothing sensible to do here */
    }
  },

  getItem(key, isJson = false) {
    let raw = null;
    try { raw = localStorage.getItem(PREFIX + key); } catch (e) { return null; }
    if (!raw) return null;

    let framed;
    try {
      framed = dec.decode(xorBytes(fromB64(raw)));
    } catch (e) {
      secStorage.removeItem(key);
      return null;
    }

    // `<8 hex>|<plaintext>` — the separator sits at index 8 exactly.
    if (framed.charCodeAt(8) !== 0x7c /* | */) {
      secStorage.removeItem(key);
      return null;
    }
    const tag = framed.slice(0, 8);
    const plain = framed.slice(9);
    if (tag !== digest(plain)) {
      // Wrong install, or an edited value. Drop it and let the caller re-derive.
      secStorage.removeItem(key);
      return null;
    }

    if (isJson) {
      try { return JSON.parse(plain); } catch (e) { return null; }
    }
    return plain;
  },

  removeItem(key) {
    try { localStorage.removeItem(PREFIX + key); } catch (e) { /* ignore */ }
  },

  clear() {
    // Only MH_SEC_ keys — the salt included, which is the right behaviour: a
    // cleared session should not leave the next one decodable.
    try {
      Object.keys(localStorage).forEach((k) => {
        if (k.startsWith(PREFIX)) localStorage.removeItem(k);
      });
    } catch (e) { /* ignore */ }
    _saltBytes = null;
  },
};
