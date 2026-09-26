# Validation — 2026-09-26

The original wrapper was rebuilt with Rust 1.90.0 in local Ubuntu WSL, using
`Cargo.lock` and cached dependencies in offline mode. Build output was kept in a
temporary Linux directory because the Dropbox-mounted directory rejected cleanup
of a Cargo temporary archive. No filesystem permissions or original source files
were changed.

| Check | Result |
| --- | --- |
| Copied original inputs | All 18 files match `SOURCE-SHA256SUMS.txt` |
| Rust unit tests | 3 passed: secret clearing, concurrent single-use registration, cancelled login |
| Original Rust ↔ Serenity 1.1.0 interoperability suite | All 10 reported cases passed |
| Existing synthetic account | Login, unchanged export key, server finalization and single-use state passed via `scripts/opaque-native-legacy.cjs` |
| Apple build script | Bash syntax passed; non-macOS execution stops with the prerequisite message |
| Embedded framework verifier | Python syntax passed |
| Kotlin Apple adapter | Main and test source compilation passed for `iosArm64` and `iosSimulatorArm64` |
| Xcode project | Framework reference, linking, Embed & Sign, and required-artifact build check inspected |

The interoperability suite covers both registration/login directions, default
and custom identifiers, Unicode, stable export/session keys, wrong passwords,
modified identifiers, malformed messages, consumed states, unknown accounts and
modified server authentication. These tests used only synthetic data.

## Still requires a Mac

`OpaqueKmp.xcframework` was **not built** in this Windows environment. The
framework's Swift bindings and application bridge were not compiled or run here.
Native iOS tests, simulator/device login, Xcode linking and signing, and the iPad
application running on macOS remain unverified. Kotlin source compilation and
host protocol tests do not replace those checks.

Run `bash scripts/build-apple-opaque.sh` on a Mac, then build and test `iosApp` in
Xcode. The script verifies both actual framework slices, Rust static linkage and
Swift API imports before publishing the artifact.
