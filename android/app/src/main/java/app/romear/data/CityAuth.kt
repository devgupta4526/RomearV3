package app.romear.data

import android.content.Context
import app.romear.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONException
import org.json.JSONArray
import org.json.JSONObject

internal class CityAuth(context: Context) {
    private val prefs = context.getSharedPreferences("romear_auth", Context.MODE_PRIVATE)
    private val tokenKey = "session:${BuildConfig.BACKEND_URL}"
    private val deviceKey = "device:${BuildConfig.BACKEND_URL}"
    private val demoKey = "demo:${BuildConfig.BACKEND_URL}"
    private val networkAllowed = BuildConfig.BACKEND_URL.startsWith("https://") || BuildConfig.DEBUG
    private val sessionLock = Mutex()
    val enabled = BuildConfig.EMAIL_AUTH && networkAllowed
    val deviceEnabled = BuildConfig.DEVICE_AUTH && networkAllowed
    val demoSelected get() = prefs.getBoolean(demoKey, false)
    val hasSession get() = (enabled || deviceEnabled) && !demoSelected && prefs.contains(tokenKey)

    fun chooseDemo() {
        prefs.edit().putBoolean(demoKey, true).apply()
    }

    suspend fun requestCode(email: String): String =
        send("POST", "challenges", JSONObject().put("email", email)).getString("challenge")

    suspend fun verify(challenge: String, code: String): CityState? {
        val result = send("POST", "verify", JSONObject().put("challenge", challenge).put("code", code))
        return rememberSession(result)
    }

    suspend fun registerDevice(): CityState? = sessionLock.withLock {
        rememberSession(exchangeDevice())
    }

    private suspend fun exchangeDevice(): JSONObject {
        val key = prefs.getString(deviceKey, null) ?: withContext(Dispatchers.IO) {
            ByteArray(32).also(SecureRandom()::nextBytes)
                .joinToString("") { "%02x".format(it.toInt() and 255) }
                .also { if (!prefs.edit().putString(deviceKey, it).commit()) throw IOException("Could not save device access") }
        }
        return send("POST", "device", token = key)
    }

    private fun rememberSession(result: JSONObject): CityState? {
        val profile = result.optJSONObject("profile")?.let {
            val interests = it.getJSONArray("interests")
            CityState(
                onboarded = true,
                name = it.getString("name"),
                username = it.getString("username"),
                bio = it.getString("bio"),
                city = it.getString("city"),
                interests = (0 until interests.length()).map(interests::getString).toSet(),
            )
        }
        prefs.edit().putString(tokenKey, result.getString("token")).remove(demoKey).apply()
        return profile
    }

    suspend fun saveProfile(profile: CityState, discoverable: Boolean? = null) {
        val body = JSONObject()
            .put("name", profile.name)
            .put("username", profile.username)
            .put("bio", profile.bio)
            .put("city", profile.city)
            .put("interests", JSONArray(profile.interests.toList()))
        discoverable?.let { body.put("discoverable", it) }
        authorized("PUT", "profile", body, "auth")
    }

    suspend fun community(method: String, path: String, body: JSONObject? = null): JSONObject =
        authorized(method, path, body, "community")

    private suspend fun authorized(method: String, path: String, body: JSONObject?, namespace: String): JSONObject {
        val token = prefs.getString(tokenKey, null) ?: throw IOException("Sign in to continue")
        try {
            return send(method, path, body, token, namespace = namespace)
        } catch (expired: SessionExpired) {
            if (!deviceEnabled) throw expired
            val replacement = sessionLock.withLock {
                val current = prefs.getString(tokenKey, null) ?: throw expired
                if (current == token) rememberSession(exchangeDevice())
                prefs.getString(tokenKey, null) ?: throw expired
            }
            return send(method, path, body, replacement, namespace = namespace)
        }
    }

    suspend fun signOut() {
        val token = sessionLock.withLock {
            prefs.getString(tokenKey, null).also { prefs.edit().remove(tokenKey).remove(demoKey).apply() }
        } ?: return
        try {
            send("DELETE", "session", token = token)
        } catch (_: IOException) {
            // The local session has already been cleared.
        }
    }

    private suspend fun send(method: String, endpoint: String, body: JSONObject? = null, token: String? = null, namespace: String = "auth"): JSONObject =
        withContext(Dispatchers.IO) {
            val request = URL("${BuildConfig.BACKEND_URL}/v1/$namespace/$endpoint").openConnection() as HttpURLConnection
            try {
                request.requestMethod = method
                request.connectTimeout = 10_000
                request.readTimeout = 75_000
                request.instanceFollowRedirects = false
                token?.let { request.setRequestProperty("Authorization", "Bearer $it") }
                body?.let {
                    val bytes = it.toString().toByteArray(Charsets.UTF_8)
                    request.doOutput = true
                    request.setFixedLengthStreamingMode(bytes.size)
                    request.setRequestProperty("Content-Type", "application/json")
                    request.outputStream.use { stream -> stream.write(bytes) }
                }
                when (request.responseCode) {
                    200, 201 -> request.inputStream.bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
                    204 -> JSONObject()
                    401 -> if (endpoint == "verify") throw IOException("Code is invalid or expired") else throw SessionExpired()
                    403, 404, 409 -> {
                        val detail = request.errorStream?.bufferedReader(Charsets.UTF_8)?.use { JSONObject(it.readText()).optString("detail") }
                        throw IOException(detail?.takeIf(String::isNotBlank) ?: "This action is unavailable")
                    }
                    422 -> throw IOException(if (namespace == "auth") "Check the email address or verification code" else "Check the details and try again")
                    429 -> throw IOException("Too many requests. Try again later.")
                    else -> throw IOException(if (namespace == "auth") "Email verification is unavailable right now" else "ROMEAR is unavailable right now")
                }
            } catch (exception: JSONException) {
                throw IOException("Email verification is unavailable right now", exception)
            } finally {
                request.disconnect()
            }
        }

    private class SessionExpired : IOException("Sign in again to continue")
}
