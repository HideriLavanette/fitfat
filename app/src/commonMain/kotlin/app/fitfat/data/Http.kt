package app.fitfat.data

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout

/** Один HTTP-клиент на приложение: движок подбирается платформой (OkHttp на Android, Darwin на iOS). */
object Http {
    val client: HttpClient by lazy {
        HttpClient {
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = 30_000
            }
        }
    }
}
