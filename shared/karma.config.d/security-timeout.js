// Argon2-based full OPAQUE flows can exceed Mocha's two-second default on Wasm.
config.client = config.client || {};
config.client.mocha = Object.assign({}, config.client.mocha, { timeout: 30000 });
