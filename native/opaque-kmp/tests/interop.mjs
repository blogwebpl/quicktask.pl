import * as opaque from '@serenity-kit/opaque';
import { spawn } from 'node:child_process';
import { createInterface } from 'node:readline';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';

await opaque.ready;
const exe = process.env.OPAQUE_INTEROP_BIN ?? fileURLToPath(new URL(
  `../rust/target/debug/interop${process.platform === 'win32' ? '.exe' : ''}`, import.meta.url));
const child = spawn(exe, [], { stdio: ['pipe', 'pipe', 'pipe'] });
const queue = [];
let failure;
function fail(error) { failure = error; while (queue.length) queue.shift().reject(error); }
child.on('error', () => fail(new Error('Cannot start Rust test bridge')));
child.on('exit', code => fail(new Error(`Rust test bridge exited (${code})`)));
// Do not forward stdout/stderr: protocol traffic contains test key material.
child.stderr.resume();
createInterface({ input: child.stdout }).on('line', line => {
  const pending = queue.shift();
  if (!pending) return fail(new Error('Unexpected bridge response'));
  try { pending.resolve(JSON.parse(line)); }
  catch { pending.reject(new Error('Invalid bridge response')); }
});
async function rpc(value) {
  if (failure) throw failure;
  return Promise.race([
    new Promise((resolve, reject) => {
      queue.push({ resolve, reject });
      child.stdin.write(JSON.stringify(value) + '\n');
    }),
    new Promise((_, reject) => { const t = setTimeout(() => reject(new Error('Bridge timeout')), 120_000); t.unref(); }),
  ]);
}
const eqSecret = (a, b, message) => assert.ok(a === b, message);
const password = 'test-only-Zażółć-🔐-\u0000-password';
const userIdentifier = 'test-user';
const serverSetup = opaque.server.createSetup();
const serverStaticPublicKey = opaque.server.getPublicKey(serverSetup);
const reports = [];
async function rustRegister(identifiers) {
  const start = await rpc({ op: 'registrationStart', password });
  assert.ok(!start.error, 'registration start');
  assert.equal(Buffer.from(start.registrationRequest, 'base64url').length, 32);
  const { registrationResponse } = opaque.server.createRegistrationResponse({
    serverSetup, userIdentifier, registrationRequest: start.registrationRequest,
  });
  const finish = await rpc({ op: 'registrationFinish', password, state: start.state, response: registrationResponse, identifiers });
  assert.ok(!finish.error, 'registration finish');
  eqSecret(finish.serverStaticPublicKey, serverStaticPublicKey, 'registration server key');
  const reuse = await rpc({ op: 'registrationFinish', password, state: start.state, response: registrationResponse });
  assert.ok(reuse.error, 'registration state cannot be reused');
  return finish;
}
async function rustLogin(record, identifiers, loginPassword = password, expectedFailure = false) {
  const start = await rpc({ op: 'loginStart', password: loginPassword });
  const server = opaque.server.startLogin({ serverSetup, userIdentifier,
    registrationRecord: record.registrationRecord, startLoginRequest: start.startLoginRequest, identifiers });
  const finish = await rpc({ op: 'loginFinish', password: loginPassword, state: start.state, response: server.loginResponse, identifiers });
  if (expectedFailure) { assert.ok(finish.error, 'invalid login must fail'); return; }
  assert.ok(!finish.error, 'Rust login succeeds');
  const done = opaque.server.finishLogin({ serverLoginState: server.serverLoginState, finishLoginRequest: finish.finishLoginRequest });
  eqSecret(done.sessionKey, finish.sessionKey, 'Rust/Serenity session keys match');
  eqSecret(record.exportKey, finish.exportKey, 'export key remains stable');
  eqSecret(serverStaticPublicKey, finish.serverStaticPublicKey, 'login server key');
  const reuse = await rpc({ op: 'loginFinish', password, state: start.state, response: server.loginResponse });
  assert.ok(reuse.error, 'login state cannot be reused');
}
try {
  for (const identifiers of [undefined, { client: 'żółw@example.test', server: 'api.example.test' }]) {
    const record = await rustRegister(identifiers);
    await rustLogin(record, identifiers);
    reports.push(`PASS Rust registration → Rust login → Serenity server (${identifiers ? 'custom' : 'default'} identifiers)`);

    const jsStart = opaque.client.startLogin({ password });
    const server = opaque.server.startLogin({ serverSetup, userIdentifier, registrationRecord: record.registrationRecord,
      startLoginRequest: jsStart.startLoginRequest, identifiers });
    const jsFinish = opaque.client.finishLogin({ password, clientLoginState: jsStart.clientLoginState,
      loginResponse: server.loginResponse, identifiers, keyStretching: 'memory-constrained' });
    assert.ok(jsFinish, 'Serenity client accepts Rust registration');
    const done = opaque.server.finishLogin({ serverLoginState: server.serverLoginState, finishLoginRequest: jsFinish.finishLoginRequest });
    eqSecret(done.sessionKey, jsFinish.sessionKey, 'Serenity session keys match');
    eqSecret(record.exportKey, jsFinish.exportKey, 'cross-client export keys match');
    reports.push('PASS Rust registration → Serenity client login');

    const jsReg = opaque.client.startRegistration({ password });
    const response = opaque.server.createRegistrationResponse({ serverSetup, userIdentifier, registrationRequest: jsReg.registrationRequest });
    const jsRecord = opaque.client.finishRegistration({ password, clientRegistrationState: jsReg.clientRegistrationState,
      registrationResponse: response.registrationResponse, identifiers, keyStretching: 'memory-constrained' });
    await rustLogin(jsRecord, identifiers);
    reports.push('PASS Serenity registration → Rust client login');
    await rustLogin(record, identifiers, 'wrong-password', true);
    await rustLogin(record, { client: 'different', server: 'different' }, password, true);
    reports.push('PASS wrong password and mismatched identifiers rejected');
  }
  for (const phase of ['registration', 'login']) {
    const start = await rpc({ op: `${phase}Start`, password });
    const malformed = await rpc({ op: `${phase}Finish`, password, state: start.state, response: 'AA' });
    assert.ok(malformed.error, 'malformed message rejected');
    const reused = await rpc({ op: `${phase}Finish`, password, state: start.state, response: 'AA' });
    assert.ok(reused.error, 'failed finish consumes state');
  }
  const unknownStart = await rpc({ op: 'loginStart', password });
  const unknownServer = opaque.server.startLogin({ serverSetup, userIdentifier: 'unknown-user',
    registrationRecord: null, startLoginRequest: unknownStart.startLoginRequest });
  const unknown = await rpc({ op: 'loginFinish', password, state: unknownStart.state, response: unknownServer.loginResponse });
  assert.ok(unknown.error, 'unknown user rejected');
  const record = await rustRegister();
  const tamperStart = await rpc({ op: 'loginStart', password });
  const tamperServer = opaque.server.startLogin({ serverSetup, userIdentifier,
    registrationRecord: record.registrationRecord, startLoginRequest: tamperStart.startLoginRequest });
  const modifiedResponse = Buffer.from(tamperServer.loginResponse, 'base64url');
  modifiedResponse[modifiedResponse.length - 1] ^= 1;
  const tampered = await rpc({ op: 'loginFinish', password, state: tamperStart.state,
    response: modifiedResponse.toString('base64url') });
  assert.ok(tampered.error, 'modified server authentication rejected');
  reports.push('PASS malformed messages and consumed states rejected');
  reports.push('PASS unknown user and modified server authentication rejected');
  console.log(reports.join('\n'));
} finally {
  child.stdin.end();
  child.kill();
}
