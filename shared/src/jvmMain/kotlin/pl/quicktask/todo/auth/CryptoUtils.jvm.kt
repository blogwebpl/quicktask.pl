package pl.quicktask.todo.auth

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.providers.jdk.JDK

actual fun getCryptographyProvider(): CryptographyProvider = CryptographyProvider.JDK
