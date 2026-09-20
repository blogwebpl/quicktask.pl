package pl.quicktask.app.items.presentation

import pl.quicktask.app.items.model.AttachmentLimitException
import pl.quicktask.app.items.model.FileIntegrityException
import pl.quicktask.app.items.model.ItemDecryptionException
import pl.quicktask.app.items.model.MissingSessionException
import pl.quicktask.app.items.model.UserKeysLockedException


import org.jetbrains.compose.resources.StringResource
import pl.quicktask.app.auth.model.ApiException
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.error_access_token_expired
import todo.shared.generated.resources.error_authentication_failed
import todo.shared.generated.resources.error_invalid_dpop_proof
import todo.shared.generated.resources.error_max_attachments
import todo.shared.generated.resources.error_user_keys_locked
import todo.shared.generated.resources.error_item_decryption
import todo.shared.generated.resources.error_file_integrity
import todo.shared.generated.resources.error_item_rate_limit

internal fun itemErrorResource(error: Throwable, fallback: StringResource): StringResource = when (error) {
    is MissingSessionException -> Res.string.error_access_token_expired
    is UserKeysLockedException -> Res.string.error_user_keys_locked
    is AttachmentLimitException -> Res.string.error_max_attachments
    is ItemDecryptionException -> Res.string.error_item_decryption
    is FileIntegrityException -> Res.string.error_file_integrity
    is ApiException -> when (error.code) {
        "ACCESS_TOKEN_EXPIRED" -> Res.string.error_access_token_expired
        "INVALID_DPOP_PROOF" -> Res.string.error_invalid_dpop_proof
        "AUTHENTICATION_FAILED" -> Res.string.error_authentication_failed
        else -> when (error.statusCode) {
            401, 498 -> Res.string.error_access_token_expired
            429 -> Res.string.error_item_rate_limit
            else -> fallback
        }
    }
    else -> fallback
}
