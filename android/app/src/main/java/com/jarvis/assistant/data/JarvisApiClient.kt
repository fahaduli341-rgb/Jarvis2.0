package com.jarvis.assistant.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

data class JarvisBackendReply(
    val replyText: String,
    val action: PendingPhoneAction? = null
)

sealed class ApiOutcome {
    data class Success(val data: JarvisBackendReply) : ApiOutcome()
    data class Failure(val userMessage: String) : ApiOutcome()
}

class JarvisApiClient(private val settings: SecureSettingsManager) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun sendTurn(
        userText: String,
        history: List<ChatTurn>,
        deviceLabel: String = "Redmi A5"
    ): ApiOutcome = withContext(Dispatchers.IO) {
        val baseUrl = settings.backendUrl
        if (!baseUrl.startsWith("https://") && !baseUrl.startsWith("http://localhost") && !baseUrl.startsWith("http://10.0.2.2")) {
            return@withContext ApiOutcome.Failure(
                "Secure HTTPS backend URL is required. Please check your JARVIS settings."
            )
        }

        val payload = JSONObject().apply {
            put("message", userText)
            put("device", deviceLabel)
            put("history", JSONArray().apply {
                history.takeLast(12).forEach { turn ->
                    put(JSONObject().apply {
                        put("role", turn.role)
                        put("text", turn.text)
                    })
                }
            })
        }

        val requestBuilder = Request.Builder()
            .url("$baseUrl/api/jarvis/turn")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "application/json")

        val token = settings.accessToken
        if (token.isNotEmpty()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val serverMsg = runCatching {
                        JSONObject(rawBody).optString("error")
                    }.getOrNull().orEmpty()
                    val msg = when (response.code) {
                        401, 403 -> "Authentication failed on the JARVIS backend. Check your access token."
                        429 -> "Rate limit reached on the AI backend. Please wait a moment and try again."
                        else -> serverMsg.ifBlank { "Backend returned HTTP ${response.code}." }
                    }
                    return@withContext ApiOutcome.Failure(msg)
                }

                val json = JSONObject(rawBody)
                val replyText = json.optString("reply").trim()
                if (replyText.isEmpty()) {
                    return@withContext ApiOutcome.Failure("Received an empty response from JARVIS.")
                }

                val actionJson = json.optJSONObject("action")
                val parsedAction = actionJson?.let { parseAction(it) }

                ApiOutcome.Success(
                    JarvisBackendReply(
                        replyText = replyText,
                        action = parsedAction
                    )
                )
            }
        } catch (_: IOException) {
            ApiOutcome.Failure(
                "I cannot reach the JARVIS server right now. Please check your mobile data or Wi-Fi."
            )
        } catch (_: Exception) {
            ApiOutcome.Failure(
                "An unexpected error occurred while processing that request."
            )
        }
    }

    private fun parseAction(obj: JSONObject): PendingPhoneAction? {
        val rawType = obj.optString("type").uppercase()
        val type = runCatching { ActionType.valueOf(rawType) }.getOrNull() ?: return null
        val target = obj.optString("target").trim()
        val summary = obj.optString("summary").trim().ifBlank { "Execute $rawType" }
        val hour = if (obj.has("hour") && !obj.isNull("hour")) obj.optInt("hour") else null
        val minute = if (obj.has("minute") && !obj.isNull("minute")) obj.optInt("minute") else null
        val sensitive = obj.optBoolean("sensitive", true)

        return PendingPhoneAction(
            id = UUID.randomUUID().toString(),
            type = type,
            target = target,
            summary = summary,
            hour = hour,
            minute = minute,
            sensitive = sensitive
        )
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
