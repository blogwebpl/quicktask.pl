// Test-only bridge. All identities and server setup are synthetic.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const opaque = require(path.resolve(__dirname, '../../server/node_modules/@serenity-kit/opaque'));
const fixturePath = path.resolve(__dirname, '../shared/src/jvmTest/resources/opaque-legacy.json');
const b64 = b => Buffer.from(b).toString('base64');
function encrypt(key, data) {
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
  return Buffer.concat([iv, cipher.update(data), cipher.final(), cipher.getAuthTag()]);
}
(async () => {
  await opaque.ready;
  const input = JSON.parse(fs.readFileSync(0, 'utf8'));
  if (input.operation === 'generate') {
    const email = 'compatibility@example.test', password = 'Test-only OPAQUE compatibility 2026!', origin = 'https://compatibility.example.test';
    const serverSetup = opaque.server.createSetup();
    const identifiers = { client: email, server: origin };
    const start = opaque.client.startRegistration({ password });
    const response = opaque.server.createRegistrationResponse({ serverSetup, userIdentifier: email, registrationRequest: start.registrationRequest });
    const registered = opaque.client.finishRegistration({ ...start, ...response, password, identifiers, keyStretching: 'memory-constrained' });
    const pair = crypto.generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
    const privateKey = pair.privateKey.export({ type: 'pkcs8', format: 'der' });
    const wrapKey = Buffer.from(crypto.hkdfSync('sha256', Buffer.from(registered.exportKey, 'base64url'), Buffer.alloc(0), 'clearmind-user-private-key-v1', 32));
    const wrapped = encrypt(wrapKey, privateKey);
    const fileKey = crypto.randomBytes(32);
    const plaintext = 'Existing encrypted attachment — zażółć gęślą';
    const fixture = { email, password, origin, serverSetup, ...registered,
      publicKey: b64(pair.publicKey.export({ type: 'spki', format: 'der' })),
      privateKey: b64(privateKey), privateKeyNonce: b64(wrapped.subarray(0, 12)), encryptedPrivateKey: b64(wrapped.subarray(12)),
      fileKey: b64(fileKey), ciphertext: b64(encrypt(fileKey, Buffer.from(plaintext))), plaintext };
    fs.mkdirSync(path.dirname(fixturePath), { recursive: true });
    fs.writeFileSync(fixturePath, JSON.stringify(fixture, null, 2) + '\n');
    return;
  }
  const fixture = JSON.parse(fs.readFileSync(fixturePath, 'utf8'));
  const identifiers = { client: fixture.email, server: fixture.origin };
  if (input.operation === 'start') {
    console.log(JSON.stringify(opaque.server.startLogin({ serverSetup: fixture.serverSetup, userIdentifier: fixture.email, registrationRecord: input.record || fixture.registrationRecord, startLoginRequest: input.request, identifiers })));
  } else if (input.operation === 'registration') {
    console.log(JSON.stringify(opaque.server.createRegistrationResponse({ serverSetup: fixture.serverSetup, userIdentifier: fixture.email, registrationRequest: input.request })));
  } else if (input.operation === 'finish') {
    const result = opaque.server.finishLogin({ serverLoginState: input.state, finishLoginRequest: input.request, identifiers });
    console.log(JSON.stringify({ success: !!result.sessionKey }));
  } else if (input.operation === 'verify-js') {
    const start = opaque.client.startLogin({ password: fixture.password });
    const response = opaque.server.startLogin({ serverSetup: fixture.serverSetup, userIdentifier: fixture.email, registrationRecord: fixture.registrationRecord, startLoginRequest: start.startLoginRequest, identifiers });
    const finish = opaque.client.finishLogin({ ...start, loginResponse: response.loginResponse, password: fixture.password, identifiers, keyStretching: 'memory-constrained' });
    if (!finish || finish.exportKey !== fixture.exportKey) throw new Error('Legacy export key changed');
    opaque.server.finishLogin({ serverLoginState: response.serverLoginState, finishLoginRequest: finish.finishLoginRequest, identifiers });
    console.log(JSON.stringify({ success: true }));
  } else throw new Error('Unknown operation');
})().catch(() => { console.error('OPAQUE compatibility check failed'); process.exitCode = 1; });
