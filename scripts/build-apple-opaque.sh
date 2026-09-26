#!/usr/bin/env bash
set -euo pipefail

if [[ "$(uname -s)" != Darwin ]]; then
  echo 'Building OpaqueKmp.xcframework requires macOS with Xcode.' >&2
  exit 1
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd -P)"
SOURCE="$ROOT/native/opaque-kmp"
DESTINATION="$ROOT/iosApp/Frameworks/OpaqueKmp.xcframework"
BUILD="$SOURCE/build/apple"
STAGING_PARENT="$ROOT/iosApp/Frameworks/build"
TOOLCHAIN=1.90.0

for tool in cargo rustup xcrun xcodebuild python3 shasum; do
  command -v "$tool" >/dev/null || { echo "Required tool is missing: $tool" >&2; exit 1; }
done
[[ -f "$SOURCE/rust/Cargo.lock" ]] || { echo 'Vendored OPAQUE source is missing.' >&2; exit 1; }
[[ ! -L "$DESTINATION" ]] || { echo 'Refusing to replace a symlink at the framework destination.' >&2; exit 1; }
[[ ! -e "$DESTINATION" || -d "$DESTINATION" ]] || { echo 'Framework destination must be a directory.' >&2; exit 1; }
mkdir -p "$BUILD" "$STAGING_PARENT"
LOCK="$BUILD/build.lock"
mkdir "$LOCK" 2>/dev/null || { echo "Another build may be running; lock: $LOCK" >&2; exit 1; }
STAGE=''
BACKUP=''
PUBLISHED=false
cleanup() {
  local status=$?
  if [[ "$PUBLISHED" != true && -n "$BACKUP" && -d "$BACKUP" && ! -e "$DESTINATION" ]]; then
    mv "$BACKUP" "$DESTINATION" || echo "Restore the previous framework from $BACKUP" >&2
  fi
  rmdir "$LOCK" 2>/dev/null || true
  if [[ $status -ne 0 && -n "$STAGE" ]]; then
    echo "Build failed; staging files retained at $STAGE" >&2
  fi
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

if ! rustup run "$TOOLCHAIN" rustc --version >/dev/null 2>&1; then
  rustup toolchain install "$TOOLCHAIN" --profile minimal
fi
cd "$SOURCE/rust"
HOST="$(rustup run "$TOOLCHAIN" rustc -vV | sed -n 's/^host: //p')"
[[ "$HOST" == *-apple-darwin ]] || { echo 'The Rust host toolchain must target macOS.' >&2; exit 1; }
DEVICE_SDK="$(xcrun --sdk iphoneos --show-sdk-path)"
SIMULATOR_SDK="$(xcrun --sdk iphonesimulator --show-sdk-path)"

FINGERPRINT="$(
  {
    shasum -a 256 "$ROOT/scripts/build-apple-opaque.sh" "$SOURCE/rust-toolchain.toml" \
      "$SOURCE/rust/Cargo.toml" "$SOURCE/rust/Cargo.lock" "$SOURCE/rust/uniffi.toml" \
      "$SOURCE/tests/BindingSmoke.swift"
    find "$SOURCE/rust/src" -type f -name '*.rs' -print | LC_ALL=C sort | while IFS= read -r file; do
      shasum -a 256 "$file"
    done
    rustup run "$TOOLCHAIN" rustc -vV
    xcodebuild -version
    xcrun swiftc --version
    xcrun --sdk iphoneos --show-sdk-version
    xcrun --sdk iphonesimulator --show-sdk-version
  } | shasum -a 256 | cut -d ' ' -f 1
)"

verify_framework() {
  python3 - "$1" "$SOURCE/tests/BindingSmoke.swift" <<'PY'
import pathlib
import plistlib
import re
import subprocess
import sys

root = pathlib.Path(sys.argv[1]).resolve()
smoke = pathlib.Path(sys.argv[2])
info = plistlib.loads((root / 'Info.plist').read_bytes())
libraries = info.get('AvailableLibraries', [])
if len(libraries) != 2:
    raise SystemExit('Expected exactly the iOS device and simulator slices')
seen = set()
for library in libraries:
    variant = library.get('SupportedPlatformVariant', 'device')
    if library.get('SupportedPlatform') != 'ios' or library.get('SupportedArchitectures') != ['arm64']:
        raise SystemExit('Unexpected XCFramework platform or architecture')
    if variant not in ('device', 'simulator') or variant in seen:
        raise SystemExit('Missing or duplicate XCFramework slice')
    seen.add(variant)
    framework = (root / library['LibraryIdentifier'] / library['LibraryPath']).resolve()
    if root not in framework.parents or framework.name != 'OpaqueKmp.framework':
        raise SystemExit('Invalid framework path')
    binary = framework / 'OpaqueKmp'
    sdk = 'iphoneos' if variant == 'device' else 'iphonesimulator'
    target = 'arm64-apple-ios13.0' + ('-simulator' if variant == 'simulator' else '')
    expected_platform = 'IOS' if variant == 'device' else 'IOSSIMULATOR'
    def run(*args):
        return subprocess.run(args, check=True, text=True, capture_output=True).stdout
    if run('xcrun', 'lipo', '-archs', str(binary)).strip() != 'arm64':
        raise SystemExit('Framework binary has an unexpected architecture')
    build_commands = run('xcrun', 'vtool', '-show-build', str(binary))
    if not re.search(r'platform\s+' + expected_platform + r'\s', build_commands):
        raise SystemExit('Framework binary has an unexpected Apple platform')
    dependencies = run('xcrun', 'otool', '-L', str(binary))
    if 'libopaque_kmp' in dependencies:
        raise SystemExit('Framework must embed Rust statically, without a Rust dylib dependency')
    module = framework / 'Modules' / 'OpaqueKmp.swiftmodule'
    if not list(module.glob('*.swiftinterface')):
        raise SystemExit('Framework Swift interface is missing')
    sdk_path = run('xcrun', '--sdk', sdk, '--show-sdk-path').strip()
    subprocess.run(['xcrun', '--sdk', sdk, 'swiftc', '-typecheck', str(smoke),
                    '-F', str(framework.parent), '-target', target, '-sdk', sdk_path], check=True)
print('Verified device/simulator ARM64, embedded Rust, and Swift API imports.')
PY
}

if [[ -f "$DESTINATION/.clearmind-build-fingerprint" ]] && \
   [[ "$(cat "$DESTINATION/.clearmind-build-fingerprint")" == "$FINGERPRINT" ]]; then
  verify_framework "$DESTINATION"
  echo "OpaqueKmp.xcframework is current: $DESTINATION"
  exit 0
fi

# All disposable build products stay in ignored build directories. Existing outputs
# remain intact until the new XCFramework and both Swift imports have been verified.
STAGE="$(mktemp -d "$STAGING_PARENT/opaque-stage.XXXXXX")"
export CARGO_TARGET_DIR="$BUILD/cargo-target"
rustup target add --toolchain "$TOOLCHAIN" aarch64-apple-ios aarch64-apple-ios-sim
cargo +"$TOOLCHAIN" test --locked --lib --target "$HOST"
cargo +"$TOOLCHAIN" build --locked --lib --bin uniffi-bindgen --features bindgen --target "$HOST"
mkdir -p "$STAGE/bindings"
"$CARGO_TARGET_DIR/$HOST/debug/uniffi-bindgen" generate \
  --library "$CARGO_TARGET_DIR/$HOST/debug/libopaque_kmp.dylib" --language swift \
  --config "$SOURCE/rust/uniffi.toml" --out-dir "$STAGE/bindings" --no-format

build_slice() {
  local rust_target="$1" sdk="$2" sdk_path="$3" swift_target="$4" module_target="$5" slice="$6"
  local framework="$STAGE/$slice/OpaqueKmp.framework"
  IPHONEOS_DEPLOYMENT_TARGET=13.0 SDKROOT="$sdk_path" \
    cargo +"$TOOLCHAIN" build --locked --release --lib --target "$rust_target"
  local archive="$CARGO_TARGET_DIR/$rust_target/release/libopaque_kmp.a"
  [[ -f "$archive" ]] || { echo "Rust static archive is missing: $archive" >&2; exit 1; }
  mkdir -p "$framework/Headers" "$framework/Modules/OpaqueKmp.swiftmodule"
  cp "$STAGE/bindings/OpaqueKmpFFI.h" "$framework/Headers/"
  printf '#include "OpaqueKmpFFI.h"\n' > "$framework/Headers/OpaqueKmp.h"
  cat > "$framework/Modules/module.modulemap" <<'MAP'
framework module OpaqueKmp {
  umbrella header "OpaqueKmp.h"
  export *
  module * { export * }
}
MAP
  cat > "$framework/Info.plist" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
<key>CFBundleIdentifier</key><string>com.opaquekmp.OpaqueKmp</string>
<key>CFBundleName</key><string>OpaqueKmp</string>
<key>CFBundleExecutable</key><string>OpaqueKmp</string>
<key>CFBundlePackageType</key><string>FMWK</string>
<key>CFBundleVersion</key><string>1</string>
<key>CFBundleShortVersionString</key><string>0.1.0</string>
<key>MinimumOSVersion</key><string>13.0</string>
</dict></plist>
PLIST
  xcrun --sdk "$sdk" swiftc "$STAGE/bindings/OpaqueKmp.swift" "$archive" \
    -module-name OpaqueKmp -parse-as-library -emit-library -emit-module \
    -enable-library-evolution -import-underlying-module \
    -F "$STAGE/$slice" -sdk "$sdk_path" -target "$swift_target" -O \
    -framework Security -liconv \
    -Xlinker -install_name -Xlinker '@rpath/OpaqueKmp.framework/OpaqueKmp' \
    -emit-module-path "$framework/Modules/OpaqueKmp.swiftmodule/$module_target.swiftmodule" \
    -emit-module-interface-path "$framework/Modules/OpaqueKmp.swiftmodule/$module_target.swiftinterface" \
    -o "$framework/OpaqueKmp"
}

build_slice aarch64-apple-ios iphoneos "$DEVICE_SDK" arm64-apple-ios13.0 arm64-apple-ios device
build_slice aarch64-apple-ios-sim iphonesimulator "$SIMULATOR_SDK" arm64-apple-ios13.0-simulator arm64-apple-ios-simulator simulator
CANDIDATE="$STAGE/OpaqueKmp.xcframework"
xcodebuild -create-xcframework -framework "$STAGE/device/OpaqueKmp.framework" \
  -framework "$STAGE/simulator/OpaqueKmp.framework" -output "$CANDIDATE"
verify_framework "$CANDIDATE"
printf '%s\n' "$FINGERPRINT" > "$CANDIDATE/.clearmind-build-fingerprint"

# The candidate and destination share a parent filesystem. Publishing uses renames;
# the EXIT trap restores the previous artifact if publication is interrupted.
if [[ -e "$DESTINATION" ]]; then
  BACKUP="$STAGE/previous-OpaqueKmp.xcframework"
  mv "$DESTINATION" "$BACKUP"
fi
mv "$CANDIDATE" "$DESTINATION"
PUBLISHED=true
echo "Built and verified: $DESTINATION"
if [[ -n "$BACKUP" ]]; then
  echo "Previous framework retained at: $BACKUP"
fi
