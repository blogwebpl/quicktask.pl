package pl.quicktask.app.auth

import dev.whyoleg.cryptography.CryptographyProvider

actual fun getCryptographyProvider(): CryptographyProvider = CryptographyProvider.Default
