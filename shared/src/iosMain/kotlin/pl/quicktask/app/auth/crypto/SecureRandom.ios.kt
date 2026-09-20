@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package pl.quicktask.app.auth.crypto

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

actual fun secureRandomBytes(size: Int): ByteArray {
    require(size >= 0)
    return ByteArray(size).also { bytes ->
        if (size > 0) bytes.usePinned { check(SecRandomCopyBytes(kSecRandomDefault, size.toULong(), it.addressOf(0)) == 0) }
    }
}
