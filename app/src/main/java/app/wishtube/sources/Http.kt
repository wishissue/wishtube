package app.wishtube.sources

import app.wishtube.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object Http {
    const val MAX_RESPONSE_SIZE = 5L * 1024 * 1024 // 5 MB
    val USER_AGENT = "WishTube/${BuildConfig.VERSION_NAME} (+https://github.com/wishissue/wishtube; TODO_OWNER_EMAIL)"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    fun url(base: String, vararg params: Pair<String, String>): String {
        val b = base.toHttpUrl().newBuilder()
        params.forEach { b.addQueryParameter(it.first, it.second) }
        return b.build().toString()
    }

    suspend fun getJson(url: String): JsonElement = exec(Request.Builder().url(url))

    suspend fun postJson(url: String, body: String): JsonElement =
        exec(Request.Builder().url(url).post(body.toRequestBody("application/json".toMediaType())))

    private suspend fun exec(b: Request.Builder): JsonElement {
        val isGet = b.build().method == "GET"
        val req = b.header("User-Agent", USER_AGENT).build()

        var attempts = 0
        val maxAttempts = if (isGet) 3 else 1

        while (true) {
            attempts++
            try {
                val resp = client.newCall(req).await()
                if (isGet && attempts < maxAttempts && isRetryableStatus(resp.code)) {
                    val backoff = getRetryBackoffMs(resp, attempts)
                    resp.close()
                    delay(backoff)
                    continue
                }
                return withContext(Dispatchers.IO) {
                    resp.use {
                        if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
                        val content = readResponseBodyString(it)
                        json.parseToJsonElement(content)
                    }
                }
            } catch (e: IOException) {
                if (isGet && attempts < maxAttempts) {
                    delay(200L * attempts)
                    continue
                } else {
                    throw e
                }
            }
        }
    }

    private fun isRetryableStatus(code: Int): Boolean =
        code == 429 || code == 502 || code == 503 || code == 504

    private fun getRetryBackoffMs(resp: Response, attempt: Int): Long {
        val retryAfter = resp.header("Retry-After")
        if (!retryAfter.isNullOrBlank()) {
            val seconds = retryAfter.toLongOrNull()
            if (seconds != null && seconds > 0) {
                return (seconds * 1000L).coerceAtMost(2000L)
            }
        }
        return 200L * attempt
    }

    fun readResponseBodyString(response: Response): String {
        val body = response.body ?: return ""
        val contentLength = body.contentLength()
        if (contentLength > MAX_RESPONSE_SIZE) {
            throw IOException("Response body exceeded 5 MB limit ($contentLength bytes)")
        }
        val source = body.source()
        source.request(MAX_RESPONSE_SIZE + 1)
        if (source.buffer.size > MAX_RESPONSE_SIZE) {
            throw IOException("Response body exceeded 5 MB limit")
        }
        return body.string()
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { if (cont.isActive) cont.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) { cont.resume(response) }
    })
}

fun JsonElement?.obj(): JsonObject? = this as? JsonObject
fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull
fun JsonElement?.long(): Long? = (this as? JsonPrimitive)?.longOrNull
fun JsonElement?.strList(): List<String> = (this as? JsonArray)?.mapNotNull { it.str() } ?: emptyList()
