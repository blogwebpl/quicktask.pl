// Test the shipped browser bundle and Apple bridge against the synthetic legacy account.
// Run from the client repository: node scripts/opaque-apple-compatibility.cjs
'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { webcrypto, randomUUID } = require('node:crypto');
const serverOpaque = require(path.resolve(__dirname, '../../server/node_modules/@serenity-kit/opaque'));
const root = path.resolve(__dirname, '..');
const fixture = JSON.parse(fs.readFileSync(path.join(root, 'shared/src/jvmTest/resources/opaque-legacy.json'), 'utf8'));
const bundle = fs.readFileSync(path.join(root, 'shared/src/commonMain/resources/opaque_bundle.js'), 'utf8');
const bridge = fs.readFileSync(path.join(root, 'shared/src/iosMain/composeResources/files/opaque/bridge.js'), 'utf8');

function browserRuntime() {
    let time = 0;
    let nextTimer = 1;
    const timers = new Map();
    const context = vm.createContext({
        crypto: webcrypto,
        TextEncoder,
        TextDecoder,
        atob,
        btoa,
        performance: { now: () => time },
        setTimeout(callback, delay) {
            const id = nextTimer++;
            timers.set(id, { callback, at: time + delay });
            return id;
        },
        clearTimeout(id) { timers.delete(id); },
    });
    // The bundle gets CommonJS exports only; Node's process, require and Buffer are absent.
    vm.runInContext(`(() => { const exports = {};\n${bundle}\nglobalThis.serenityOpaque = exports; })();`, context);
    vm.runInContext(bridge, context);
    assert.equal(vm.runInContext('typeof require + ":" + typeof Buffer + ":" + typeof process', context), 'undefined:undefined:undefined');
    return {
        async call(operation, payload) {
            // Transfer data as an object, as WKWebView.callAsyncJavaScript arguments do.
            const result = await context.clearMindOpaque(operation, structuredClone(payload));
            return JSON.parse(JSON.stringify(result));
        },
        advance(milliseconds, runTimers = true) {
            time += milliseconds;
            if (runTimers) {
                for (const [id, timer] of [...timers]) {
                    if (timer.at <= time) {
                        timers.delete(id);
                        timer.callback();
                    }
                }
            }
        },
        timerCount: () => timers.size,
        holdReady() {
            const ready = context.serenityOpaque.ready;
            let release;
            context.serenityOpaque.ready = ready.then(() => new Promise(resolve => { release = resolve; }));
            return async () => {
                await ready;
                // Allow the chained callback above to install its resolver.
                await Promise.resolve();
                release();
                context.serenityOpaque.ready = ready;
            };
        },
    };
}

function accountOptions(account = fixture) {
    return {
        serverSetup: account.serverSetup,
        userIdentifier: account.email,
        registrationRecord: account.registrationRecord,
        identifiers: { client: account.email, server: account.origin },
    };
}

async function beginLogin(runtime, account = fixture) {
    const stateId = randomUUID();
    const start = await runtime.call('startLogin', { password: account.password, stateId });
    assert.deepEqual(Object.keys(start).sort(), ['clientLoginState', 'startLoginRequest']);
    assert.equal(start.clientLoginState, stateId);
    const response = serverOpaque.server.startLogin({ ...accountOptions(account), startLoginRequest: start.startLoginRequest });
    return {
        response,
        payload: {
            password: account.password,
            clientLoginState: stateId,
            loginResponse: response.loginResponse,
            email: account.email,
            serverOrigin: account.origin,
        },
    };
}

async function reject(runtime, operation, payload, description) {
    await assert.rejects(runtime.call(operation, payload), error => error.message === 'OPAQUE operation failed.', description);
}

async function main() {
    await serverOpaque.ready;
    const runtime = browserRuntime();
    let checks = 0;

    const legacy = await beginLogin(runtime);
    const recovered = await runtime.call('finishLogin', legacy.payload);
    assert.deepEqual(Object.keys(recovered).sort(), ['exportKey', 'finishLoginRequest']);
    assert.equal(recovered.exportKey, fixture.exportKey, 'Existing export key must remain unchanged');
    assert.ok(serverOpaque.server.finishLogin({
        serverLoginState: legacy.response.serverLoginState,
        finishLoginRequest: recovered.finishLoginRequest,
    }).sessionKey);
    await reject(runtime, 'finishLogin', legacy.payload, 'Successful login state must be consumed');
    checks++;

    const unicode = {
        ...fixture,
        email: 'Zażółć+δοκιμή@example.test',
        password: 'Zażółć 🧠 漢字 </script> " \' \\ \n \u2028 \u2029 ${globalThis.pwned = true}',
        origin: 'https://compatibility.example.test',
    };
    const registrationId = randomUUID();
    const registration = await runtime.call('startRegistration', { password: unicode.password, stateId: registrationId });
    assert.deepEqual(Object.keys(registration).sort(), ['clientRegistrationState', 'registrationRequest']);
    assert.equal(registration.clientRegistrationState, registrationId);
    const registrationResponse = serverOpaque.server.createRegistrationResponse({
        serverSetup: unicode.serverSetup,
        userIdentifier: unicode.email,
        registrationRequest: registration.registrationRequest,
    });
    const registrationPayload = {
        password: unicode.password,
        clientRegistrationState: registrationId,
        registrationResponse: registrationResponse.registrationResponse,
        email: unicode.email,
        serverOrigin: unicode.origin,
    };
    const registered = await runtime.call('finishRegistration', registrationPayload);
    assert.deepEqual(Object.keys(registered).sort(), ['exportKey', 'registrationRecord']);
    unicode.registrationRecord = registered.registrationRecord;
    const unicodeLogin = await beginLogin(runtime, unicode);
    const unicodeFinish = await runtime.call('finishLogin', unicodeLogin.payload);
    assert.equal(unicodeFinish.exportKey, registered.exportKey, 'Unicode password and identifiers must round trip');
    assert.ok(serverOpaque.server.finishLogin({
        serverLoginState: unicodeLogin.response.serverLoginState,
        finishLoginRequest: unicodeFinish.finishLoginRequest,
    }).sessionKey);
    await reject(runtime, 'finishRegistration', registrationPayload, 'Registration state must be consumed');
    checks++;

    for (const [name, patch] of [
        ['wrong password', { password: 'Incorrect test password' }],
        ['wrong origin', { serverOrigin: 'https://different.example.test' }],
        ['wrong email', { email: 'different@example.test' }],
        ['malformed response', { loginResponse: 'not!base64' }],
        ['missing origin', { serverOrigin: undefined }],
    ]) {
        const login = await beginLogin(runtime);
        await reject(runtime, 'finishLogin', { ...login.payload, ...patch }, name);
        await reject(runtime, 'finishLogin', login.payload, 'Failed finish must consume its state');
        checks++;
    }

    const discarded = await beginLogin(runtime);
    assert.deepEqual(await runtime.call('discard', { stateId: discarded.payload.clientLoginState }), {});
    assert.deepEqual(await runtime.call('discard', { stateId: discarded.payload.clientLoginState }), {});
    await reject(runtime, 'finishLogin', discarded.payload, 'Discarded state must not be reusable');
    checks++;

    const releaseReady = runtime.holdReady();
    const cancelledId = randomUUID();
    const cancelledStart = reject(runtime, 'startLogin', {
        password: fixture.password, stateId: cancelledId,
    }, 'Discard during initialization must cancel the pending start');
    assert.equal(runtime.timerCount(), 1);
    await runtime.call('discard', { stateId: cancelledId });
    await releaseReady();
    await cancelledStart;
    assert.equal(runtime.timerCount(), 0, 'Initialization must not recreate a discarded state');
    checks++;

    const expired = await beginLogin(runtime);
    runtime.advance(120_000);
    assert.equal(runtime.timerCount(), 0, 'Expiry timers release abandoned states');
    await reject(runtime, 'finishLogin', expired.payload, 'Expired state must not finish');
    const suspended = await beginLogin(runtime);
    runtime.advance(120_000, false);
    await reject(runtime, 'finishLogin', suspended.payload, 'Expiry is checked even while timers are suspended');
    assert.equal(runtime.timerCount(), 0);
    checks++;

    const wrongType = await beginLogin(runtime);
    await reject(runtime, 'finishRegistration', {
        ...registrationPayload,
        clientRegistrationState: wrongType.payload.clientLoginState,
    }, 'Login state cannot finish a registration');
    await reject(runtime, 'finishLogin', wrongType.payload, 'Type mismatch must consume state');
    const wrongRegistrationId = randomUUID();
    await runtime.call('startRegistration', { password: fixture.password, stateId: wrongRegistrationId });
    await reject(runtime, 'finishLogin', {
        ...legacy.payload,
        clientLoginState: wrongRegistrationId,
    }, 'Registration state cannot finish a login');
    checks++;

    const ids = Array.from({ length: 32 }, () => randomUUID());
    await Promise.all(ids.map((stateId, index) => runtime.call(index % 2 ? 'startLogin' : 'startRegistration', {
        password: fixture.password, stateId,
    })));
    assert.equal(runtime.timerCount(), 32);
    await reject(runtime, 'startLogin', { password: fixture.password, stateId: randomUUID() }, 'Combined state count is bounded');
    await reject(runtime, 'startRegistration', { password: fixture.password, stateId: ids[0] }, 'Duplicate IDs cannot replace a state');
    await runtime.call('discard', { stateId: ids[0] });
    const replacementId = randomUUID();
    await runtime.call('startLogin', { password: fixture.password, stateId: replacementId });
    assert.equal(runtime.timerCount(), 32);
    runtime.advance(120_000);
    assert.equal(runtime.timerCount(), 0);
    await runtime.call('startLogin', { password: fixture.password, stateId: randomUUID() });
    runtime.advance(120_000);
    checks++;

    await reject(runtime, 'unknown', {}, 'Unknown operations are rejected');
    await reject(runtime, 'startLogin', { password: null, stateId: randomUUID() }, 'Invalid password type is rejected');
    await reject(runtime, 'startLogin', { password: fixture.password, stateId: '' }, 'Empty state handles are rejected');
    assert.equal(runtime.timerCount(), 0, 'Validation failures do not leak pending state');
    checks++;

    console.log(`Apple OPAQUE compatibility: ${checks} checks passed.`);
}

main().catch(() => {
    console.error('Apple OPAQUE compatibility check failed.');
    process.exitCode = 1;
});
