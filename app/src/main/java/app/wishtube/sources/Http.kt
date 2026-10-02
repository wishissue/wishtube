package app.wishtube.sources

import kotlinx.coroutines.Dispatchers
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
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
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
        val req = b.header("User-Agent", "OpenVideo/0.1 (local-first client)").build()
        val resp = client.newCall(req).await()
        return withContext(Dispatchers.IO) {
            resp.use {
                if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
                json.parseToJsonElement(it.body?.string().orEmpty())
            }
        }
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
