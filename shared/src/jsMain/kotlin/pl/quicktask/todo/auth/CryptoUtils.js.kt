package pl.quicktask.todo.auth

import dev.whyoleg.cryptography.CryptographyProvider

actual fun getCryptographyProvider(): CryptographyProvider = CryptographyProvider.Default
