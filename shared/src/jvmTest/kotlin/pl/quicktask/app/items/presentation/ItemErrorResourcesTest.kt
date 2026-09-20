package pl.quicktask.app.items.presentation

import pl.quicktask.app.items.model.AttachmentLimitException
import pl.quicktask.app.items.model.FileIntegrityException
import pl.quicktask.app.items.model.ItemDecryptionException
import pl.quicktask.app.items.model.MissingSessionException
import pl.quicktask.app.items.model.UserKeysLockedException

import pl.quicktask.app.auth.model.ApiException
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.error_access_token_expired
import todo.shared.generated.resources.error_fetch_items
import todo.shared.generated.resources.error_file_integrity
import todo.shared.generated.resources.error_invalid_dpop_proof
import todo.shared.generated.resources.error_item_decryption
import todo.shared.generated.resources.error_item_rate_limit
import todo.shared.generated.resources.error_max_attachments
import todo.shared.generated.resources.error_user_keys_locked
import kotlin.test.Test
import kotlin.test.assertEquals

class ItemErrorResourcesTest {
    private val fallback = Res.string.error_fetch_items

    @Test
    fun knownErrorsUseSpecificResources() {
        assertEquals(Res.string.error_access_token_expired, itemErrorResource(MissingSessionException(), fallback))
        assertEquals(Res.string.error_user_keys_locked, itemErrorResource(UserKeysLockedException(), fallback))
        assertEquals(Res.string.error_max_attachments, itemErrorResource(AttachmentLimitException(), fallback))
        assertEquals(Res.string.error_item_decryption, itemErrorResource(ItemDecryptionException(Exception()), fallback))
        assertEquals(Res.string.error_file_integrity, itemErrorResource(FileIntegrityException(), fallback))
        assertEquals(Res.string.error_invalid_dpop_proof, itemErrorResource(ApiException("INVALID_DPOP_PROOF", 400, "detail"), fallback))
        assertEquals(Res.string.error_item_rate_limit, itemErrorResource(ApiException(null, 429, "detail"), fallback))
    }

    @Test
    fun unknownServerMessageIsNeverUsedAsUiText() {
        assertEquals(fallback, itemErrorResource(ApiException(null, 500, "sekretny tekst serwera"), fallback))
        assertEquals(fallback, itemErrorResource(IllegalStateException("diagnostic detail"), fallback))
    }
}
