package app.romear.data

import android.content.SharedPreferences
import android.util.Log
import app.romear.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject

/** Sends the latest persisted snapshot independently of screen and activity lifecycles. */
internal class CityStateSync(private val prefs: SharedPreferences) {
    private val origin = BuildConfig.BACKEND_URL
    private val enabled = origin.startsWith("https://") ||
        (BuildConfig.DEBUG && origin.startsWith("http://127.0.0.1:"))
    private val signals = Channel<Unit>(Channel.CONFLATED)
    private val endpointPrefs = "sync:$origin:"

    init {
        if (enabled) CoroutineScope(Dispatchers.IO).launch {
            for (signal in signals) uploadPending()
        }
    }

    fun schedule() {
        if (enabled) signals.trySend(Unit)
    }

    private suspend fun uploadPending() {
        var retryMillis = 2_000L
        while (true) {
            // Read both the serialized state and its revision from one preferences snapshot.
            val values = prefs.all
            val revision = values["revision"] as? Long ?: return
            val acknowledged = values[endpointPrefs + "ack"] as? Long ?: 0
            if (revision <= acknowledged) return

            val result = try {
                upload(revision, values["state"] as? String)
            } catch (_: IOException) {
                false
            } catch (_: JSONException) {
                false
            }
            when (result) {
                true -> {
                    prefs.edit().putLong(endpointPrefs + "ack", revision).apply()
                    retryMillis = 2_000L
                }
                false -> {
                    delay(retryMillis)
                    retryMillis = (retryMillis * 2).coerceAtMost(300_000L)
                }
                null -> return
            }
        }
    }

    private fun upload(revision: Long, local: String?): Boolean? {
        val state = JSONObject(local ?: "{}")
        val cloudState = if (state.optBoolean("onboarded")) state else JSONObject()
        cloudState.optJSONArray("posts")?.let { posts ->
            for (index in 0 until posts.length()) {
                val post = posts.getJSONObject(index)
                val image = post.optString("image").removePrefix("video:")
                if (image.startsWith("content:") || image.startsWith("file:")) post.remove("image")
            }
        }
        val body = JSONObject().put("revision", revision).put("state", cloudState)
            .toString().toByteArray(Charsets.UTF_8)
        if (body.size > 128 * 1024) {
            Log.w("RomearSync", "State exceeds the cloud storage limit")
            return null
        }
        val tokenKey = endpointPrefs + "token"
        val token = prefs.getString(tokenKey, null) ?: ByteArray(32)
            .also(SecureRandom()::nextBytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
            .also { if (!prefs.edit().putString(tokenKey, it).commit()) return false }

        val connection = URL("$origin/v1/state").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "PUT"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 25_000
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(body.size)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.outputStream.use { it.write(body) }
            return when (val code = connection.responseCode) {
                200 -> connection.inputStream.bufferedReader(Charsets.UTF_8).use {
                    JSONObject(it.readText()).getLong("revision") >= revision
                }
                408, 429, in 500..599 -> false
                else -> {
                    Log.w("RomearSync", "State upload returned HTTP $code")
                    null
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}
