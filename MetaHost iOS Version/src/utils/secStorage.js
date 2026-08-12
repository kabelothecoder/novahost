/**
 * secStorage.js
 * 
 * Simple obfuscation wrapper for localStorage to obscure sensitive 
 * license keys from casual inspection in DevTools.
 */

const PREFIX = 'MH_SEC_';

// Simple XOR + Base64 obfuscation
function obfuscate(str) {
  if (!str) return '';
  const xor = str.split('').map(char => 
    String.fromCharCode(char.charCodeAt(0) ^ 42)
  ).join('');
  return btoa(xor);
}

function deobfuscate(b64) {
  if (!b64) return null;
  try {
    const xor = atob(b64);
    return xor.split('').map(char => 
      String.fromCharCode(char.charCodeAt(0) ^ 42)
    ).join('');
  } catch (e) {
    return null;
  }
}

export const secStorage = {
  setItem(key, value) {
    const valStr = typeof value === 'string' ? value : JSON.stringify(value);
    localStorage.setItem(PREFIX + key, obfuscate(valStr));
  },

  getItem(key, isJson = false) {
    const raw = localStorage.getItem(PREFIX + key);
    if (!raw) return null;
    const decoded = deobfuscate(raw);
    if (!decoded) return null;
    if (isJson) {
      try { return JSON.parse(decoded); } catch (e) { return null; }
    }
    return decoded;
  },

  removeItem(key) {
    localStorage.removeItem(PREFIX + key);
  },

  clear() {
    // Only clear MH keys
    Object.keys(localStorage).forEach(k => {
      if (k.startsWith(PREFIX)) localStorage.removeItem(k);
    });
  }
};
