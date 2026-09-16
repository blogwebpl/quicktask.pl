package pl.quicktask.app.inbox

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import js.buffer.ArrayBuffer
import js.typedarrays.Int8Array
import web.dom.document
import web.events.EventHandler
import web.file.File
import web.file.FileReader
import web.html.HTMLInputElement

@Composable
actual fun rememberFilePicker(
    onFilePicked: (InputFile?) -> Unit,
): () -> Unit {
    return remember {
        {
            try {
                val input = document.createElement("input") as HTMLInputElement
                input.asDynamic().type = "file"
                input.onchange = EventHandler {
                    val files = input.files
                    if (files != null && files.length > 0) {
                        val file = files.item(0) as File
                        readFileJs(file, onFilePicked)
                    } else {
                        onFilePicked(null)
                    }
                }
                input.click()
            } catch (_: Throwable) {
                onFilePicked(null)
            }
        }
    }
}

private fun readFileJs(file: File, onFilePicked: (InputFile?) -> Unit) {
    val reader = FileReader()
    reader.onload = EventHandler {
        val result = reader.result
        if (result != null) {
            val arrayBuffer = result as? ArrayBuffer
            if (arrayBuffer != null) {
                val int8Array = Int8Array(arrayBuffer)
                val bytes = ByteArray(int8Array.length) { i -> int8Array[i] }
                val mimeType = if (file.type.isNotBlank()) file.type else "application/octet-stream"
                onFilePicked(InputFile(fileName = file.name, mimeType = mimeType, bytes = bytes))
            } else {
                onFilePicked(null)
            }
        } else {
            onFilePicked(null)
        }
    }
    reader.onerror = EventHandler {
        onFilePicked(null)
    }
    reader.readAsArrayBuffer(file)
}
