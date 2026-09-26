(() => {
    'use strict';

    // Only opaque handles cross the native bridge. Protocol state stays in this isolated world.
    const pending = new Map();
    const lifetimeMs = 120_000;
    const maximumPending = 32;
    const now = () => performance.now();

    function fail() {
        throw new Error('OPAQUE operation failed.');
    }

    function string(value, nonEmpty = true) {
        if (typeof value !== 'string' || (nonEmpty && value.length === 0)) fail();
        return value;
    }

    function remove(id) {
        const entry = pending.get(id);
        if (entry) {
            pending.delete(id);
            clearTimeout(entry.timer);
        }
        return entry;
    }

    function prune() {
        const time = now();
        for (const [id, entry] of pending) {
            if (time >= entry.expiresAt) remove(id);
        }
    }

    function reserve(id, type) {
        const entry = { type, state: null, expiresAt: now() + lifetimeMs, timer: null };
        entry.timer = setTimeout(() => {
            if (pending.get(id) === entry) remove(id);
        }, lifetimeMs);
        pending.set(id, entry);
        return entry;
    }

    function consume(id, type) {
        const entry = remove(string(id));
        if (!entry || !entry.state || entry.type !== type || now() >= entry.expiresAt) fail();
        return entry.state;
    }

    function identifiers(payload) {
        return { client: string(payload.email), server: string(payload.serverOrigin) };
    }

    globalThis.clearMindOpaque = async (operation, payload) => {
        try {
            if (!payload || typeof payload !== 'object' || Array.isArray(payload)) fail();
            prune();
            if (operation === 'discard') {
                remove(string(payload.stateId));
                return {};
            }

            const library = globalThis.serenityOpaque;
            if (!library || !library.client) fail();
            if (operation === 'startLogin' || operation === 'startRegistration') {
                const id = string(payload.stateId);
                const password = string(payload.password, false);
                prune();
                if (pending.has(id) || pending.size >= maximumPending) fail();
                const entry = reserve(id, operation === 'startLogin' ? 'login' : 'registration');
                try {
                    await library.ready;
                    // A cancelled request must not recreate a discarded state after initialization.
                    if (pending.get(id) !== entry || now() >= entry.expiresAt) fail();
                    if (operation === 'startLogin') {
                        const result = library.client.startLogin({ password });
                        const request = string(result.startLoginRequest);
                        entry.state = string(result.clientLoginState);
                        return { startLoginRequest: request, clientLoginState: id };
                    }
                    const result = library.client.startRegistration({ password });
                    const request = string(result.registrationRequest);
                    entry.state = string(result.clientRegistrationState);
                    return { registrationRequest: request, clientRegistrationState: id };
                } catch (_) {
                    if (pending.get(id) === entry) remove(id);
                    fail();
                }
            }

            if (operation === 'finishLogin') {
                const state = consume(payload.clientLoginState, 'login');
                await library.ready;
                const result = library.client.finishLogin({
                    password: string(payload.password, false),
                    clientLoginState: state,
                    loginResponse: string(payload.loginResponse),
                    identifiers: identifiers(payload),
                    keyStretching: 'memory-constrained',
                });
                if (!result) fail();
                return { finishLoginRequest: string(result.finishLoginRequest), exportKey: string(result.exportKey) };
            }

            if (operation === 'finishRegistration') {
                const state = consume(payload.clientRegistrationState, 'registration');
                await library.ready;
                const result = library.client.finishRegistration({
                    password: string(payload.password, false),
                    clientRegistrationState: state,
                    registrationResponse: string(payload.registrationResponse),
                    identifiers: identifiers(payload),
                    keyStretching: 'memory-constrained',
                });
                if (!result) fail();
                return { registrationRecord: string(result.registrationRecord), exportKey: string(result.exportKey) };
            }
            fail();
        } catch (_) {
            // Never forward library errors, passwords, identifiers or protocol messages to native logs.
            fail();
        }
    };
})();
