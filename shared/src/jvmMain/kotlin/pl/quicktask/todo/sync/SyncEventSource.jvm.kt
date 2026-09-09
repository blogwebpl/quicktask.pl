package pl.quicktask.todo.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

class OkHttpSyncEventSource(
    private val client: OkHttpClient = defaultOkHttpClient,
) : SyncEventSource {

    override suspend fun connectAndListen(
        url: String,
        accessToken: String,
        dpopProof: String,
        onEvent: (SyncEvent) -> Unit,
    ): SyncConnectionResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .header("Authorization", "DPoP $accessToken")
            .header("DPoP", dpopProof)
            .build()

        val call = client.newCall(request)

        val completionHandle = coroutineContext.job.invokeOnCompletion {
            call.cancel()
        }

        try {
            val response = call.execute()
            if (!response.isSuccessful) {
                val code = response.code
                val bodyText = response.body?.string() ?: ""
                response.close()
                return@withContext SyncConnectionResult.HttpError(code, bodyText)
            }

            val body = response.body
                ?: return@withContext SyncConnectionResult.HttpError(500, "Empty response body")

            val parser = SyncEventParser()

            body.source().use { source ->
                while (coroutineContext.isActive && !source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    val events = parser.feedLine(line)
                    for (event in events) {
                        onEvent(event)
                    }
                }
            }
            response.close()
            SyncConnectionResult.Completed
        } catch (e: Exception) {
            if (e is CancellationException || coroutineContext.job.isCancelled) {
                SyncConnectionResult.Completed
            } else {
                SyncConnectionResult.NetworkError(e)
            }
        } finally {
            completionHandle.dispose()
        }
    }

    companion object {
        private val defaultOkHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()
        }
    }
}

actual fun createSyncEventSource(): SyncEventSource = OkHttpSyncEventSource()
