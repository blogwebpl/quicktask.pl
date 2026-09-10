package pl.quicktask.todo.inbox

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.file.Files

@Composable
actual fun rememberFilePicker(
    onFilePicked: (InputFile?) -> Unit,
): () -> Unit {
    val scope = rememberCoroutineScope()
    return remember(scope) {
        {
            scope.launch {
                val inputFile = withContext(Dispatchers.IO) {
                    pickFileJvm()
                }
                onFilePicked(inputFile)
            }
        }
    }
}

private fun pickFileJvm(): InputFile? {
    return try {
        val fileDialog = FileDialog(null as Frame?, "Wybierz plik załącznika", FileDialog.LOAD)
        fileDialog.isVisible = true
        val directory = fileDialog.directory ?: return null
        val fileStr = fileDialog.file ?: return null
        val file = File(directory, fileStr)
        if (!file.exists() || !file.isFile) return null

        val bytes = file.readBytes()
        val mimeType = Files.probeContentType(file.toPath()) ?: "application/octet-stream"
        InputFile(fileName = file.name, mimeType = mimeType, bytes = bytes)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
