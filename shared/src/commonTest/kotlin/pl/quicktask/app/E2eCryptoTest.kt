package pl.quicktask.app

import pl.quicktask.app.auth.UserKeyMaterialDto
import kotlin.test.Test
import kotlin.test.assertEquals

class CommonE2eCryptoTest {

    @Test
    fun testUserKeyMaterialDtoSerialization() {
        val dto = UserKeyMaterialDto(
            publicKey = "pubKey",
            encryptedPrivateKey = "encPrivKey",
            privateKeyNonce = "nonce",
            encryptionVersion = 1,
        )
        assertEquals("pubKey", dto.publicKey)
        assertEquals("encPrivKey", dto.encryptedPrivateKey)
        assertEquals("nonce", dto.privateKeyNonce)
        assertEquals(1, dto.encryptionVersion)
    }
}
