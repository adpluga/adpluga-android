package com.adpluga.client

import com.adpluga.config.Constants
import com.adpluga.consent.ConsentStore
import com.adpluga.errors.AdPlugaError
import com.adpluga.logger.AdPlugaLogger
import com.adpluga.model.AdPlugaJson
import com.adpluga.model.FeaturesDto
import com.adpluga.model.FeaturesView
import com.adpluga.model.ServeResponse
import com.adpluga.model.ServeResponseDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

internal class HttpTransport(
    private val publisherKey: String,
    private val endpoint: String,
    private val consent: ConsentStore,
    private val clock: () -> Long = System::currentTimeMillis,
    clientProvider: () -> OkHttpClient = ::defaultClient,
) : AutoCloseable {

    private val client: OkHttpClient = clientProvider()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun serve(slotId: String, format: String?, userHash: String?): ServeResponse? =
        withContext(Dispatchers.IO) {
            val url = endpoint.toHttpUrl().newBuilder().apply {
                addPathSegments("v1/serve")
                addQueryParameter("slot", slotId)
                if (!format.isNullOrBlank()) addQueryParameter("format", format)
                if (!userHash.isNullOrBlank()) addQueryParameter("user_hash", userHash)
                if (!consent.state.isPersonalized) addQueryParameter("non_personalized", "true")
            }.build()
            val request = buildGet(url.toString(), Constants.NETWORK_SERVE_TIMEOUT_MS)
            val body = executeWithRetry(request, isSensitive = true) ?: return@withContext null
            AdPlugaJson.decodeFromString(ServeResponseDto.serializer(), body).toModel()
        }

    suspend fun fetchFeatures(etag: String?): FeaturesResult = withContext(Dispatchers.IO) {
        val url = endpoint.toHttpUrl().newBuilder()
            .addPathSegments("v1/features")
            .build()
        val builder = Request.Builder()
            .url(url)
            .get()
            .header(Constants.KEY_HEADER, publisherKey)
            .header(Constants.PLATFORM_HEADER, Constants.SDK_PLATFORM)
            .header(Constants.VERSION_HEADER, Constants.SDK_VERSION)
        if (!etag.isNullOrBlank()) builder.header("If-None-Match", etag)
        val request = builder.build()
        val call = client.newCall(request).withTimeout(Constants.NETWORK_TRACK_TIMEOUT_MS)
        val response = call.await()
        response.use {
            when (response.code) {
                304 -> FeaturesResult.NotModified
                200 -> {
                    val payload = response.body?.string().orEmpty()
                    val dto = AdPlugaJson.decodeFromString(FeaturesDto.serializer(), payload)
                    FeaturesResult.Updated(
                        view = FeaturesView(dto.flags, dto.sdkMinVersion),
                        etag = response.header("ETag"),
                    )
                }
                426 -> throw AdPlugaError.UpgradeRequired(
                    response.header(Constants.UPGRADE_HEADER) ?: "unknown"
                )
                in 400..599 -> throw AdPlugaError.Network(response.code, response.message)
                else -> throw AdPlugaError.Network(response.code, response.message)
            }
        }
    }

    suspend fun postTrack(path: String, token: String, payload: Map<String, String>) {
        withContext(Dispatchers.IO) {
            val url = endpoint.toHttpUrl().newBuilder()
                .addPathSegments(path.trimStart('/'))
                .build()
            val body: RequestBody = buildJsonObject {
                put("token", JsonPrimitive(token))
                payload.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
            }.toString().toRequestBody(jsonMediaType)
            val req = buildPost(url.toString(), body, Constants.NETWORK_TRACK_TIMEOUT_MS)
            executeWithRetry(req, isSensitive = false)
        }
    }

    suspend fun beacon(url: String) {
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .header(Constants.KEY_HEADER, publisherKey)
                    .header(Constants.PLATFORM_HEADER, Constants.SDK_PLATFORM)
                    .header(Constants.VERSION_HEADER, Constants.SDK_VERSION)
                    .build()
                client.newCall(request).withTimeout(Constants.NETWORK_TRACK_TIMEOUT_MS)
                    .await().use { /* drain and close */ }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                AdPlugaLogger.debug("beacon failed url=$url", t)
            }
        }
    }

    suspend fun postTelemetry(body: String) {
        withContext(Dispatchers.IO) {
            val url = endpoint.toHttpUrl().newBuilder()
                .addPathSegments("v1/sdk/telemetry")
                .build()
            val req = buildPost(url.toString(), body.toRequestBody(jsonMediaType), Constants.NETWORK_TRACK_TIMEOUT_MS)
            try {
                executeWithRetry(req, isSensitive = false)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                AdPlugaLogger.debug("telemetry post failed", t)
            }
        }
    }

    private suspend fun executeWithRetry(request: Request, isSensitive: Boolean): String? {
        var attempt = 0
        while (true) {
            val call = client.newCall(request).withTimeout(callTimeoutOf(request))
            val response = try {
                call.await()
            } catch (io: IOException) {
                if (attempt < Constants.NETWORK_RETRY_MAX_ATTEMPTS) {
                    delay(backoffMs(attempt))
                    attempt++
                    continue
                }
                if (isSensitive) return null
                throw AdPlugaError.Network(0, "io", io)
            }
            response.use {
                val code = response.code
                if (code == 426) {
                    throw AdPlugaError.UpgradeRequired(
                        response.header(Constants.UPGRADE_HEADER) ?: "unknown"
                    )
                }
                if (code in 200..299) {
                    return response.body?.string().orEmpty()
                }
                if (shouldRetry(code) && attempt < Constants.NETWORK_RETRY_MAX_ATTEMPTS) {
                    delay(backoffMs(attempt))
                    attempt++
                    return@use
                }
                if (isSensitive) return null
                throw AdPlugaError.Network(code, response.message)
            }
        }
    }

    private fun shouldRetry(code: Int): Boolean =
        code == 408 || code == 429 || code in 500..599

    private fun backoffMs(attempt: Int): Long {
        val exp = Constants.NETWORK_RETRY_BASE_BACKOFF_MS shl attempt
        val jitter = Random.nextLong(0, Constants.NETWORK_RETRY_BASE_BACKOFF_MS)
        return exp + jitter
    }

    private fun buildGet(url: String, timeoutMs: Long): Request =
        Request.Builder()
            .url(url)
            .get()
            .header(Constants.KEY_HEADER, publisherKey)
            .header(Constants.PLATFORM_HEADER, Constants.SDK_PLATFORM)
            .header(Constants.VERSION_HEADER, Constants.SDK_VERSION)
            .tag(TimeoutTag::class.java, TimeoutTag(timeoutMs))
            .build()

    private fun buildPost(url: String, body: RequestBody, timeoutMs: Long): Request =
        Request.Builder()
            .url(url)
            .post(body)
            .header(Constants.KEY_HEADER, publisherKey)
            .header(Constants.PLATFORM_HEADER, Constants.SDK_PLATFORM)
            .header(Constants.VERSION_HEADER, Constants.SDK_VERSION)
            .header("Content-Type", "application/json; charset=utf-8")
            .tag(TimeoutTag::class.java, TimeoutTag(timeoutMs))
            .build()

    private fun callTimeoutOf(request: Request): Long =
        request.tag(TimeoutTag::class.java)?.timeoutMs ?: Constants.NETWORK_TRACK_TIMEOUT_MS

    override fun close() {
        try {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
            client.cache?.close()
        } catch (_: Throwable) {
        }
    }

    private data class TimeoutTag(val timeoutMs: Long)

    internal sealed class FeaturesResult {
        object NotModified : FeaturesResult()
        data class Updated(val view: FeaturesView, val etag: String?) : FeaturesResult()
    }

    private companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .callTimeout(Constants.NETWORK_TRACK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .connectTimeout(Constants.NETWORK_SERVE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(Constants.NETWORK_TRACK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }
}

private fun Call.withTimeout(timeoutMs: Long): Call {
    timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS)
    return this
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            cont.resume(response)
        }
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isCancelled) return
            cont.resumeWithException(e)
        }
    })
    cont.invokeOnCancellation {
        try { cancel() } catch (_: Throwable) {}
    }
}
