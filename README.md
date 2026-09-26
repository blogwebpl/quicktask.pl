This is a Kotlin Multiplatform project targeting Android, iOS, Web, Desktop (JVM).

* [/iosApp](./iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
    folder is the appropriate location.

### Running the apps

On Windows, use `.\gradlew.bat`. If neither `JAVA_HOME` nor Java on `PATH` is available, the wrapper automatically uses `STUDIO_JDK` or Android Studio's bundled JDK from its standard system or per-user installation directory. No global environment change is required. Restricted automation environments may require approval to access that JDK and Gradle's caches; see [AGENTS.md](./AGENTS.md).

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :androidApp:assembleDebug`
- Desktop app:
  - Hot reload: `./gradlew :desktopApp:hotRun --auto`
  - Standard run: `./gradlew :desktopApp:run`
- Web app:
  - Wasm target (faster, modern browsers): `./gradlew :webApp:wasmJsBrowserDevelopmentRun`
  - JS target (slower, supports older browsers): `./gradlew :webApp:jsBrowserDevelopmentRun`
- iOS app: open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### Apple login

The iPhone/iPad app, including when run on an Apple Silicon Mac, calls the same
Rust `opaque-ke` wrapper as Android through its generated UniFFI Swift bindings.
The original source and dependency lock are included in
[`native/opaque-kmp`](native/opaque-kmp/README.md). `ContentView` supplies the
native Swift bridge to `MainViewController`, which injects it into authentication.
The OpenSSL provider supplies the remaining DPoP and encrypted-key operations.

Before opening/building `iosApp`, run once from the client root on a Mac with
Xcode selected, Python 3 and Rust/rustup installed:

```sh
bash scripts/build-apple-opaque.sh
```

This builds and checks `iosApp/Frameworks/OpaqueKmp.xcframework` for device and
simulator ARM64. Xcode is already configured to link, embed and sign it. Rebuild
the framework when its source or dependencies change. It is ignored by Git;
ordinary app builds consume the artifact without running Cargo. A verified
existing framework is reused, and a failed rebuild preserves the previous one.

Protocol tests and source provenance are documented in the native wrapper README.
`scripts/opaque-native-legacy.cjs` also checks a rebuilt Rust test executable
against the existing synthetic account and export key. With the adjacent server's
dependencies installed, run from the client root:

```sh
cargo +1.90.0 build --locked --manifest-path native/opaque-kmp/rust/Cargo.toml --features interop --bin interop
node scripts/opaque-native-legacy.cjs
```

Set `OPAQUE_INTEROP_BIN` if the executable is in a custom Cargo target directory.
On a Mac, run `./gradlew :shared:iosSimulatorArm64Test` for the shared adapter and
cryptography tests. The `iosAppNativeTests` Xcode scheme runs the Swift bridge
against the actual framework in a hosted iOS test app. For the command line, find
an ARM64 simulator UUID with `xcrun simctl list devices available`, then run:

```sh
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosAppNativeTests \
  -destination 'platform=iOS Simulator,id=<SIMULATOR_UUID>' test
```

Finally, run `iosApp` and check existing-account login and encrypted data,
registration, wrong passwords and cancellation. Rust/Node compatibility tests
and Kotlin compilation on Windows do not replace these Apple runtime checks.

### Android releases

To upload directly from Android Studio to Google Play **internal testing**, fill in the ignored `android-publish.properties` file with your existing upload key alias and passwords. Then sync Gradle, select **Wyslij do testow Google Play** in the run configuration selector and click Run. This builds, signs and uploads the release bundle; it does not publish to production. Credentials must never be committed or shared. The configuration file is Git-ignored but still follows any Dropbox syncing settings of this directory.

For a local check without uploading, run `./gradlew :androidApp:validatePlayUpload`. The upload task is `./gradlew :androidApp:publishReleaseBundle`.

Publishing credentials use project-relative paths: `secrets/upload.keystore` and `secrets/play-service-account.json`. Both `secrets/` and `android-publish.properties` are Git-ignored. On another machine, transfer these files securely together with the project; they are not included when cloning the repository. Dropbox may sync them, so restrict access to the shared folder.

During `publishReleaseBundle`, Gradle Play Publisher's `ResolutionStrategy.AUTO` reads the highest version code from Google Play and assigns the next available number before building the bundle. This works across machines and does not depend on their clocks. Do not publish simultaneously from multiple machines: Google Play does not reserve a number during the lookup, so concurrent uploads can conflict.

Use `./gradlew :androidApp:publishReleaseBundle` or the Android Studio run configuration for uploads. Ordinary offline builds (`assembleDebug` or `bundleRelease`) use the local baseline code `1190` and do not obtain a new Google Play version code; do not manually upload these bundles.

### Required Android updates

Release builds check Google Play at startup and on returning to the foreground using Play In-App Updates (immediate mode). Any newer version available to the current user's Play account and release track blocks access to the app. Cancelling or failing the update leaves a mandatory update screen with retry and Google Play buttons. If immediate updates are unavailable, the user can update through the store.

The required version code is saved locally, so restarting the app or losing connectivity cannot bypass an update already detected. The gate clears when that version or a newer one is installed. If the initial lookup fails or takes longer than 15 seconds and no required update is known, the app remains usable. Debug builds skip the check so local development does not require Google Play. This integration applies to Android only.

To verify end to end, install a release containing this mechanism from Google Play internal testing, then publish a higher version code to the same track and account. Check startup, cancellation, retry, returning from the store, restarting offline after detection, and successful installation. A local debug APK cannot validate the Play update flow. Existing installations must first receive the release containing this mechanism.

See [Google Play In-App Updates](https://developer.android.com/guide/playcore/in-app-updates/kotlin-java).

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :shared:testAndroidHostTest`
- Desktop tests: `./gradlew :shared:jvmTest`
- Web tests:
  - Wasm target: `./gradlew :shared:wasmJsTest`
  - JS target: `./gradlew :shared:jsTest`
- iOS tests: `./gradlew :shared:iosSimulatorArm64Test`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://kotlinlang.org/compose-multiplatform/),
[Kotlin/Wasm](https://kotl.in/wasm/)…

We would appreciate your feedback on Compose/Web and Kotlin/Wasm in the public Slack channel [#compose-web](https://slack-chats.kotlinlang.org/c/compose-web).
If you face any issues, please report them on [YouTrack](https://youtrack.jetbrains.com/newIssue?project=CMP).

### Atomic inbox updates

Deploy the backend supporting optional `addedFileIds` and `removedAttachmentIds`
fields on `PATCH /inbox/:itemId` before releasing this client. New files are
uploaded first; content and attachment references are then committed together.
The client does not fall back to the older multi-request update flow. An unknown
write outcome is reconciled by reading the server state, without repeating the
write.

Inbox lists combine an authoritative server snapshot with per-item pending
operations. Refreshes and sync events preserve pending operations; a failure
releases only the overlay owned by that operation.

The backend's PostgreSQL integration test is
`test/inbox-atomic.e2e-spec.ts`. Set `TEST_DATABASE_URL`, then run in `server`:
`npm run test:e2e -- --runInBand --runTestsByPath test/inbox-atomic.e2e-spec.ts`.
It creates and removes a unique `test_inbox_atomic_*` schema and does not use
application tables in the default schema. The database user needs permission to
create schemas; the existing `pgcrypto` extension must be available.
