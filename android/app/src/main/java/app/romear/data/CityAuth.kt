package app.romear.data

import android.content.Context
import app.romear.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONArray
import org.json.JSONObject

internal class CityAuth(context: Context) {
    private val prefs = context.getSharedPreferences("romear_auth", Context.MODE_PRIVATE)
    private val tokenKey = "session:${BuildConfig.BACKEND_URL}"
    val enabled = BuildConfig.EMAIL_AUTH &&
        (BuildConfig.BACKEND_URL.startsWith("https://") || BuildConfig.DEBUG)
    val hasSession get() = enabled && prefs.contains(tokenKey)

    suspend fun requestCode(email: String): String =
        send("POST", "challenges", JSONObject().put("email", email)).getString("challenge")

    suspend fun verify(challenge: String, code: String): CityState? {
        val result = send("POST", "verify", JSONObject().put("challenge", challenge).put("code", code))
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
        prefs.edit().putString(tokenKey, result.getString("token")).apply()
        return profile
    }

    suspend fun saveProfile(profile: CityState) {
        val token = prefs.getString(tokenKey, null) ?: throw IOException("Sign in to save your profile")
        val body = JSONObject()
            .put("name", profile.name)
            .put("username", profile.username)
            .put("bio", profile.bio)
            .put("city", profile.city)
            .put("interests", JSONArray(profile.interests.toList()))
        send("PUT", "profile", body, token)
    }

    suspend fun signOut() {
        val token = prefs.getString(tokenKey, null) ?: return
        prefs.edit().remove(tokenKey).apply()
        try {
            send("DELETE", "session", token = token)
        } catch (_: IOException) {
            // The local session has already been cleared.
        }
    }

    private suspend fun send(method: String, endpoint: String, body: JSONObject? = null, token: String? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val request = URL("${BuildConfig.BACKEND_URL}/v1/auth/$endpoint").openConnection() as HttpURLConnection
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
                    401 -> throw IOException(if (endpoint == "verify") "Code is invalid or expired" else "Sign in again to continue")
                    422 -> throw IOException("Check the email address or verification code")
                    429 -> throw IOException("Too many code requests. Try again later.")
                    else -> throw IOException("Email verification is unavailable right now")
                }
            } catch (exception: JSONException) {
                throw IOException("Email verification is unavailable right now", exception)
            } finally {
                request.disconnect()
            }
        }
}
