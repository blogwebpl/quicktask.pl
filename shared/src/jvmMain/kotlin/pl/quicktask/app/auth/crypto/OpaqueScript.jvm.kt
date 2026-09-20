package pl.quicktask.app.auth.crypto

import java.io.InputStreamReader

object OpaqueScript {
    val POLYFILLS = """
        var window = globalThis;
        if (typeof globalThis.window === 'undefined') {
            globalThis.window = globalThis;
        }
        if (typeof globalThis.OPAQUE_RESULTS === 'undefined') {
            globalThis.OPAQUE_RESULTS = {};
        }
        var b64chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=';
        if (typeof globalThis.atob === 'undefined') {
            globalThis.atob = function(input) {
                var str = String(input).replace(/=+$/, '');
                var output = '';
                if (str.length % 4 === 1) {
                    throw new Error("'atob' failed: The string to be decoded is not correctly encoded.");
                }
                for (
                    var bc = 0, bs, buffer, idx = 0;
                    buffer = str.charAt(idx++);
                    ~buffer && (bs = bc % 4 ? bs * 64 + buffer : buffer,
                        bc++ % 4) ? output += String.fromCharCode(255 & bs >> (-2 * bc & 6)) : 0
                ) {
                    buffer = b64chars.indexOf(buffer);
                }
                return output;
            };
        }
        if (typeof globalThis.btoa === 'undefined') {
            globalThis.btoa = function(input) {
                var str = String(input);
                var output = '';
                for (
                    var block, charCode, idx = 0, map = b64chars;
                    str.charAt(idx | 0) || (map = '=', idx % 1);
                    output += map.charAt(63 & block >> 8 - idx % 1 * 8)
                ) {
                    charCode = str.charCodeAt(idx += 3 / 4);
                    if (charCode > 255) {
                        throw new Error("'btoa' failed: The string to be encoded contains characters outside of the Latin1 range.");
                    }
                    block = block << 8 | charCode;
                }
                return output;
            };
        }
        if (typeof globalThis.crypto === 'undefined') {
            globalThis.crypto = {};
        }
        if (typeof globalThis.crypto.getRandomValues === 'undefined') {
            globalThis.crypto.getRandomValues = function(array) {
                for (var i = 0; i < array.length; i++) {
                    array[i] = Math.floor(Math.random() * 256);
                }
                return array;
            };
        }
        if (typeof TextEncoder === 'undefined') {
            globalThis.TextEncoder = function TextEncoder() {};
            globalThis.TextEncoder.prototype.encode = function(str) {
                var buf = new Uint8Array(str.length);
                for (var i = 0; i < str.length; i++) {
                    buf[i] = str.charCodeAt(i) & 0xff;
                }
                return buf;
            };
        }
        if (typeof TextDecoder === 'undefined') {
            globalThis.TextDecoder = function TextDecoder() {};
            globalThis.TextDecoder.prototype.decode = function(bytes) {
                var str = "";
                var arr = new Uint8Array(bytes);
                for (var i = 0; i < arr.length; i++) {
                    str += String.fromCharCode(arr[i]);
                }
                return str;
            };
        }

        var exports = {};
        var module = { exports: exports };
        var global = globalThis;
    """.trimIndent()

    val BUNDLE: String by lazy {
        val stream = OpaqueScript::class.java.getResourceAsStream("/opaque_bundle.js")
            ?: OpaqueScript::class.java.classLoader?.getResourceAsStream("opaque_bundle.js")
            ?: error("opaque_bundle.js resource not found")
        InputStreamReader(stream, Charsets.UTF_8).use { it.readText() }
    }
}
