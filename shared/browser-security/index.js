// Browser-only, non-exportable key storage. No password or OPAQUE export key is persisted.
const DB = 'clearmind-dpop-v2';
let heldLock;
const b64 = bytes => btoa(Array.from(new Uint8Array(bytes), n => String.fromCharCode(n)).join(''));
const bytes = text => Uint8Array.from(atob(text), c => c.charCodeAt(0));
const url64 = data => b64(data).replaceAll('+', '-').replaceAll('/', '_').replaceAll('=', '');
const json64 = value => url64(new TextEncoder().encode(JSON.stringify(value)));
async function dbOperation(mode, action, e2e = false) {
  const db = await new Promise((resolve, reject) => {
    const request = indexedDB.open(e2e ? 'clearmind-device-keys' : DB, 1);
    request.onupgradeneeded = () => request.result.createObjectStore(e2e ? 'user-key' : 'keys', e2e ? { keyPath: 'id' } : undefined);
    request.onsuccess = () => resolve(request.result);
    request.onerror = request.onblocked = () => reject(new Error('Secure key storage unavailable'));
  });
  try { return await new Promise((resolve, reject) => {
    const transaction = db.transaction(e2e ? 'user-key' : 'keys', mode);
    const request = action(transaction.objectStore(e2e ? 'user-key' : 'keys'));
    transaction.oncomplete = () => resolve(request.result ?? null);
    transaction.onerror = transaction.onabort = () => reject(new Error('Secure key storage failed'));
  }); } finally { db.close(); }
}
const read = key => dbOperation('readonly', store => store.get(key === 'e2e' ? 'current' : key), key === 'e2e');
const put = (key, value) => dbOperation('readwrite', store => key === 'e2e' ? store.put(value) : store.put(value, key), key === 'e2e');
const remove = key => dbOperation('readwrite', store => store.delete(key === 'e2e' ? 'current' : key), key === 'e2e');
async function acquire() {
  if (!navigator.locks) throw new Error('Web Locks are required for browser sessions');
  await new Promise((resolve, reject) => {
    navigator.locks.request('clearmind-browser-session-v2', async () => {
      await new Promise(release => { heldLock = release; resolve(); });
    }).catch(reject);
  });
}
async function key(create) {
  let pair = await read('current');
  if (!pair && create) {
    pair = await crypto.subtle.generateKey({ name: 'ECDSA', namedCurve: 'P-256' }, false, ['sign', 'verify']);
    await put('current', pair);
  }
  if (!pair || pair.privateKey.extractable) throw new Error('Session key missing; sign in again');
  return pair;
}
exports.origin = () => location.origin;
exports.rememberedEmail = () => localStorage.getItem('clearmind.browser.email.v2') || '';
exports.epoch = () => localStorage.getItem('clearmind.browser.epoch.v2') || '';
exports.rememberUnlock = enabled => {
  if (enabled) localStorage.setItem('clearmind.rememberUnlock.v2', 'yes');
  else { localStorage.removeItem('clearmind.rememberUnlock.v2'); void remove('e2e').catch(() => {}); }
};
exports.call = async (operation, text) => {
  const input = JSON.parse(text);
  switch (operation) {
    case 'oauthTicket': {
      const params = new URLSearchParams(location.hash.slice(1));
      const ticket = params.get('oauth_ticket') || '';
      if (ticket) history.replaceState(null, '', location.pathname + location.search);
      return JSON.stringify({ ticket, error: location.pathname === '/auth/error' });
    }
    case 'oauthStart': {
      if (input.provider !== 'google' && input.provider !== 'apple') throw new Error('Unknown OAuth provider');
      location.assign('/auth/' + input.provider + '/start');
      return '{}';
    }
    case 'acquire': await acquire(); return '{}';
    case 'release': if (heldLock) { heldLock(); heldLock = null; } return '{}';
    case 'migrate':
      for (const name of ['clearmind.session', 'clearmind.dpop.privateJwk', 'clearmind.accessToken', 'clearmind.refreshToken', 'clearmind.userEmail', 'clearmind.dpop.privateKey', 'clearmind.dpop.publicKey', 'clearmind.keycache.email', 'clearmind.keycache.publicKey', 'clearmind.keycache.privateKey']) localStorage.removeItem(name);
      if (localStorage.getItem('clearmind.browser.migrated.v2') !== 'yes') {
        localStorage.removeItem('clearmind.browser.email.v2');
        await remove('e2e');
        localStorage.setItem('clearmind.browser.migrated.v2', 'yes');
      }
      return '{}';
    case 'email': localStorage.setItem('clearmind.browser.email.v2', input.email); localStorage.setItem('clearmind.browser.epoch.v2', crypto.randomUUID()); return '{}';
    case 'clear':
      localStorage.removeItem('clearmind.browser.email.v2');
      localStorage.setItem('clearmind.browser.epoch.v2', crypto.randomUUID());
      localStorage.removeItem('clearmind.rememberUnlock.v2');
      await Promise.allSettled([remove('current'), remove('e2e')]); return '{}';
    case 'proof': {
      const pair = await key(input.create);
      const jwk = await crypto.subtle.exportKey('jwk', pair.publicKey);
      const url = new URL(input.url); url.search = ''; url.hash = '';
      const payload = { jti: crypto.randomUUID(), htm: input.method.toUpperCase(), htu: url.toString(), iat: Math.floor(Date.now() / 1000) };
      if (input.token) payload.ath = url64(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(input.token)));
      const unsigned = json64({ typ: 'dpop+jwt', alg: 'ES256', jwk }) + '.' + json64(payload);
      return JSON.stringify({ proof: unsigned + '.' + url64(await crypto.subtle.sign({ name: 'ECDSA', hash: 'SHA-256' }, pair.privateKey, new TextEncoder().encode(unsigned))) });
    }
    case 'cache': {
      if (localStorage.getItem('clearmind.rememberUnlock.v2') !== 'yes') { await remove('e2e'); return '{}'; }
      const privateKey = await crypto.subtle.importKey('pkcs8', bytes(input.privateKey), { name: 'ECDH', namedCurve: 'P-256' }, false, ['deriveBits']);
      const publicKeySpki = bytes(input.publicKey).buffer;
      const publicKey = await crypto.subtle.importKey('spki', publicKeySpki, { name: 'ECDH', namedCurve: 'P-256' }, false, []);
      await put('e2e', { id: 'current', privateKey, publicKey, publicKeySpki, email: input.email });
      return '{}';
    }
    case 'load': {
      if (localStorage.getItem('clearmind.rememberUnlock.v2') !== 'yes') return '{}';
      const record = await read('e2e');
      return JSON.stringify(record && record.email === input.email && record.privateKey.extractable === false ? { publicKey: b64(record.publicKeySpki) } : {});
    }
    case 'derive': {
      const record = await read('e2e');
      if (!record || record.email !== input.email) throw new Error('Locked key');
      const publicKey = await crypto.subtle.importKey('spki', bytes(input.publicKey), { name: 'ECDH', namedCurve: 'P-256' }, false, []);
      return JSON.stringify({ secret: b64(await crypto.subtle.deriveBits({ name: 'ECDH', public: publicKey }, record.privateKey, 256)) });
    }
    case 'clearCache': await remove('e2e'); return '{}';
    default: throw new Error('Unknown browser security operation');
  }
};
