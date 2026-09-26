package pl.quicktask.app.auth.crypto

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.providers.openssl3.Openssl3

actual fun getCryptographyProvider(): CryptographyProvider = CryptographyProvider.Openssl3
