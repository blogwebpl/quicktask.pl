// Exercise the rebuilt Rust wrapper against the existing synthetic account, without regenerating it.
'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { spawn } = require('node:child_process');
const { createInterface } = require('node:readline');
const opaque = require(path.resolve(__dirname, '../../server/node_modules/@serenity-kit/opaque'));
const fixture = JSON.parse(fs.readFileSync(path.resolve(__dirname, '../shared/src/jvmTest/resources/opaque-legacy.json'), 'utf8'));
const executable = process.env.OPAQUE_INTEROP_BIN || path.resolve(__dirname,
    `../native/opaque-kmp/rust/target/debug/interop${process.platform === 'win32' ? '.exe' : ''}`);

async function main() {
    await opaque.ready;
    const child = spawn(executable, [], { stdio: ['pipe', 'pipe', 'pipe'] });
    child.stderr.resume(); // Protocol diagnostics must not become application logs.
    const lines = createInterface({ input: child.stdout });
    let waiting;
    let failed = false;
    const fail = () => { failed = true; waiting?.reject(new Error('Rust test process failed')); waiting = undefined; };
    child.on('error', fail);
    child.on('exit', fail);
    child.stdin.on('error', fail);
    lines.on('line', line => {
        const current = waiting;
        waiting = undefined;
        if (!current) return fail();
        try { current.resolve(JSON.parse(line)); } catch { current.reject(new Error('Invalid test response')); }
    });
    function rpc(input) {
        if (failed) return Promise.reject(new Error('Rust test process unavailable'));
        return new Promise((resolve, reject) => {
            const timeout = setTimeout(() => { waiting = undefined; reject(new Error('Rust test timed out')); }, 30_000);
            waiting = {
                resolve(value) { clearTimeout(timeout); resolve(value); },
                reject(error) { clearTimeout(timeout); reject(error); },
            };
            child.stdin.write(JSON.stringify(input) + '\n');
        });
    }
    try {
        const identifiers = { client: fixture.email, server: fixture.origin };
        const start = await rpc({ op: 'loginStart', password: fixture.password });
        assert.ok(!start.error && start.startLoginRequest, 'Native login starts');
        const response = opaque.server.startLogin({
            serverSetup: fixture.serverSetup, userIdentifier: fixture.email,
            registrationRecord: fixture.registrationRecord, startLoginRequest: start.startLoginRequest, identifiers,
        });
        const request = { op: 'loginFinish', password: fixture.password, state: start.state, response: response.loginResponse, identifiers };
        const finish = await rpc(request);
        assert.ok(!finish.error, 'Native login completes');
        assert.ok(finish.exportKey === fixture.exportKey, 'Existing export key is preserved');
        const server = opaque.server.finishLogin({ serverLoginState: response.serverLoginState, finishLoginRequest: finish.finishLoginRequest });
        assert.ok(finish.sessionKey === server.sessionKey, 'Server accepts native finalization');
        assert.ok((await rpc(request)).error, 'Native state is consumed');
        console.log('Rebuilt native OPAQUE: legacy account, export key, server finalization and single-use state passed.');
    } finally {
        lines.close();
        child.stdin.end();
        child.kill();
    }
}

main().catch(() => { console.error('Native OPAQUE legacy compatibility failed.'); process.exitCode = 1; });
