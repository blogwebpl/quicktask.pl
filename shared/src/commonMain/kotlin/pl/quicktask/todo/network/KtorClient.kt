package pl.quicktask.todo.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

val sharedHttpClient: HttpClient by lazy {
    createHttpClient()
}

fun createHttpClient(): HttpClient {
    return HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
        }
        install(HttpRequestRetry) {
            maxRetries = 3
            retryIf { _, response ->
                (response.status.value in 500..599)
            }
            exponentialDelay()
        }
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                },
            )
        }
    }
}
