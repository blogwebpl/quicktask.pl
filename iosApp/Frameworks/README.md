# OPAQUE framework

From the client repository on a Mac with Xcode and Rust 1.90.0, run:

```sh
bash scripts/build-apple-opaque.sh
```

This creates `OpaqueKmp.xcframework` here from the original, pinned Android Rust
wrapper in `native/opaque-kmp`. Xcode already links, embeds and signs it for the
app. The generated framework is ignored by Git; build it once on each Mac, or
copy the verified artifact from your library build. Ordinary app builds do not
run Cargo. Rebuild it after changing the wrapper or its bindings.
