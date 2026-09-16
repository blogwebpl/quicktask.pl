package pl.quicktask.app.inbox

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberFilePicker(
    onFilePicked: (InputFile?) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            val inputFile = readInputFileFromUri(context, uri)
            onFilePicked(inputFile)
        } else {
            onFilePicked(null)
        }
    }
    return remember(launcher) {
        { launcher.launch("*/*") }
    }
}

private fun readInputFileFromUri(context: Context, uri: Uri): InputFile? {
    return try {
        val contentResolver = context.contentResolver
        var fileName = "attachment"
        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"

        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    val name = cursor.getString(nameIndex)
                    if (!name.isNullOrBlank()) {
                        fileName = name
                    }
                }
            }
        }

        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        InputFile(fileName = fileName, mimeType = mimeType, bytes = bytes)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
