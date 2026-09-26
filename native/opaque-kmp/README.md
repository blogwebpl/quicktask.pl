# Native OPAQUE wrapper

This is the original `opaque-kmp` 0.1.0 wrapper used to produce this client's
Android AAR and desktop libraries. It calls `opaque-ke`; it does not implement
the protocol or its cryptographic primitives. The Rust sources, dependency lock,
toolchain declaration, generated Swift/C bindings and original interoperability
tests were copied without modification.

## Provenance

Source delivery: `2026-09-07/files-pasted-by-the-user-stw/outputs/opaque-kmp`,
recovered from the user's local Codex documents. The sibling archive was named
`opaque-kmp-sources.zip`. `SOURCE-SHA256SUMS.txt` records every original file
included here. The original delivery also contains `opaque-kmp-android.zip`.

Its Android AAR has SHA-256
`4b13657b9c6403812c5fa17850de4f0f5c6cc99565bf48df4800b15be4b80241`, exactly
matching `shared/libs/opaque-kmp.aar`. Both macOS libraries and the Windows
library in the delivery also match this client's existing resources byte for byte.

The pinned dependencies are `opaque-ke` 4.0.1, UniFFI 0.29.4, Argon2 0.5.3 and
Rust 1.90.0. `rust/Cargo.lock` supplies all transitive versions and checksums.
Keep the lock and generated bindings together; changing the protocol dependencies
requires repeating the compatibility tests and regenerating bindings.

The cipher suite uses Ristretto255, TripleDH/SHA-512 and the existing Serenity
`memory-constrained` profile: Argon2id v0x13, 65536 KiB, three iterations, four
lanes and a 16-byte zero salt. Explicit client/server identifiers remain UTF-8
bytes. Passwords receive no Unicode normalization. None of these settings changed.

## Build for Apple

On a Mac with Xcode selected, Python 3 and Rust installed, run from the client root:

```sh
bash scripts/build-apple-opaque.sh
```

This one-time prerequisite creates
`iosApp/Frameworks/OpaqueKmp.xcframework` with iOS device ARM64 and simulator ARM64
slices. The iPad application running on an Apple Silicon Mac uses the device
slice. The script installs the pinned Rust toolchain/targets when needed, runs
Rust unit tests, regenerates Swift bindings in ignored staging and explicitly
links the Rust static archive into each dynamic framework. It checks architecture,
Apple platform, absence of a Rust dylib dependency and Swift API imports.

A fingerprint of source, build script and tool versions permits reuse of a
verified existing framework. Frameworks are staged before publication; a failed
build preserves the previous artifact. Build staging and any previous artifact
remain under ignored `build` directories for inspection. Ordinary Xcode app builds
consume the finished XCFramework and do not run Cargo.

The original delivery had never built its iOS framework. A successful Rust test
on Windows/Linux does not establish Swift, iOS device, simulator or signed-app
compatibility. Complete the Mac build and app login tests before distributing it.

## Protocol tests

The original tests use only synthetic accounts and the pinned Serenity 1.1.0
package. From this directory:

```sh
cargo test --locked --manifest-path rust/Cargo.toml --lib
cargo build --locked --manifest-path rust/Cargo.toml --features interop --bin interop
npm ci --prefix tests
node tests/interop.mjs
```

`OPAQUE_INTEROP_BIN` selects a Rust test executable in a different build directory.
The suite checks registration/login in both directions with Serenity, stable
export keys, matching session keys, default/custom identifiers, Unicode, wrong
passwords, corrupted messages and consumed states. No production account is used.
`tests/BindingSmoke.swift` is typechecked against both built Apple slices.

UniFFI generates a Swift API for Apple and a JVM API for Android. The app's Swift
host injects its native implementation into the shared Kotlin interface; the
generated pure Swift classes are not imported directly by Kotlin/Native.
