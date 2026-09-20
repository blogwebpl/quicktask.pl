package pl.quicktask.app.auth.crypto

import dev.whyoleg.cryptography.CryptographyProvider

actual fun getCryptographyProvider(): CryptographyProvider = CryptographyProvider.Default
