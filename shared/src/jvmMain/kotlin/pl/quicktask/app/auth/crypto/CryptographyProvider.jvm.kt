package pl.quicktask.app.auth.crypto

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.providers.jdk.JDK

actual fun getCryptographyProvider(): CryptographyProvider = CryptographyProvider.JDK
