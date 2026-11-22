package app.romear.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class Booking(val id: String, val itemId: String, val date: String, val time: String, val guests: Int, val cancelled: Boolean = false)
data class Message(val text: String, val mine: Boolean, val time: Long = System.currentTimeMillis())
data class LocalPost(val id: String, val text: String, val category: String, val image: String? = null)
data class LocalPool(val id: String, val name: String, val location: String, val date: String, val capacity: Int, val description: String = "", val time: String = "")
data class LocalEvent(val id: String, val name: String, val description: String, val location: String, val date: String)
data class CityState(
    val onboarded: Boolean = false,
    val name: String = "Aarav Mehta", val username: String = "aarav.romear",
    val bio: String = "Exploring cafés, events and communities across the city.",
    val city: String = "Rome", val interests: Set<String> = setOf("Running", "Cafés", "Travel"),
    val saved: Set<String> = emptySet(), val liked: Set<String> = emptySet(),
    val joined: Set<String> = emptySet(), val following: Set<String> = emptySet(),
    val requested: Set<String> = emptySet(), val bookings: List<Booking> = emptyList(),
    val messages: Map<String, List<Message>> = emptyMap(), val posts: List<LocalPost> = emptyList(),
    val pools: List<LocalPool> = emptyList(), val stamps: Set<String> = setOf("Rome"),
    val notifications: Boolean = true, val mapVisible: Boolean = true,
    val approved: Set<String> = emptySet(), val declined: Set<String> = emptySet(),
    val offers: List<String> = emptyList(), val events: List<LocalEvent> = emptyList(),
)

class CityRepository private constructor(context: Context) {
    companion object {
        private var instance: CityRepository? = null

        @Synchronized
        fun get(context: Context): CityRepository =
            instance ?: CityRepository(context.applicationContext).also { instance = it }
    }

    private val prefs = context.getSharedPreferences("romear_local_v1", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(read())
    val state = mutable.asStateFlow()
    private val sync = CityStateSync(prefs)

    init {
        if (mutable.value.onboarded && !prefs.contains("revision")) {
            prefs.edit().putLong("revision", 1).apply()
        }
        sync.schedule()
    }
    private fun JSONArray.strings() = (0 until length()).map { getString(it) }.toSet()
    private fun JSONObject.set(key: String) = optJSONArray(key)?.strings().orEmpty()
    private fun read(): CityState = try {
        val j = JSONObject(prefs.getString("state", "{}") ?: "{}")
        val bookings = j.optJSONArray("bookings") ?: JSONArray()
        val messages = j.optJSONObject("messages") ?: JSONObject()
        val posts = j.optJSONArray("posts") ?: JSONArray()
        val pools = j.optJSONArray("pools") ?: JSONArray()
        val events = j.optJSONArray("events") ?: JSONArray()
        CityState(
            onboarded = j.optBoolean("onboarded"), name = j.optString("name", "Aarav Mehta"),
            username = j.optString("username", "aarav.romear"), bio = j.optString("bio", "Exploring cafés, events and communities across the city."),
            city = j.optString("city", "Rome"), interests = if (j.has("interests")) j.set("interests") else setOf("Running", "Cafés", "Travel"),
            saved = j.set("saved"), liked = j.set("liked"), joined = j.set("joined"), following = j.set("following"), requested = j.set("requested"),
            bookings = (0 until bookings.length()).map { bookings.getJSONObject(it).let { b -> Booking(b.getString("id"), b.getString("item"), b.getString("date"), b.getString("time"), b.getInt("guests"), b.optBoolean("cancelled")) } },
            messages = messages.keys().asSequence().associateWith { key -> messages.getJSONArray(key).let { a -> (0 until a.length()).map { a.getJSONObject(it).let { m -> Message(m.getString("text"), m.getBoolean("mine"), m.getLong("time")) } } } },
            posts = (0 until posts.length()).map { posts.getJSONObject(it).let { p -> LocalPost(p.getString("id"), p.getString("text"), p.getString("category"), p.optString("image").takeIf(String::isNotBlank)) } },
            pools = (0 until pools.length()).map { pools.getJSONObject(it).let { p -> LocalPool(p.getString("id"), p.getString("name"), p.getString("location"), p.getString("date"), p.getInt("capacity"), p.optString("description"), p.optString("time")) } },
            stamps = if (j.has("stamps")) j.set("stamps") else setOf("Rome"),
            notifications = j.optBoolean("notifications", true), mapVisible = j.optBoolean("mapVisible", true),
            approved = j.set("approved"), declined = j.set("declined"), offers = j.set("offers").toList(),
            events = (0 until events.length()).map { events.getJSONObject(it).let { e -> LocalEvent(e.getString("id"), e.getString("name"), e.getString("description"), e.getString("location"), e.getString("date")) } },
        )
    } catch (_: Exception) { CityState() }

    fun update(transform: (CityState) -> CityState) {
        val s = transform(mutable.value)
        val j = JSONObject().apply {
            put("onboarded", s.onboarded); put("name", s.name); put("username", s.username); put("bio", s.bio); put("city", s.city)
            listOf("interests" to s.interests, "saved" to s.saved, "liked" to s.liked, "joined" to s.joined,
                "following" to s.following, "requested" to s.requested, "stamps" to s.stamps,
                "approved" to s.approved, "declined" to s.declined, "offers" to s.offers).forEach { (k, v) -> put(k, JSONArray(v.toList())) }
            put("notifications", s.notifications); put("mapVisible", s.mapVisible)
            put("bookings", JSONArray(s.bookings.map { b -> JSONObject().put("id", b.id).put("item", b.itemId).put("date", b.date).put("time", b.time).put("guests", b.guests).put("cancelled", b.cancelled) }))
            put("messages", JSONObject().apply { s.messages.forEach { (key, list) -> put(key, JSONArray(list.map { m -> JSONObject().put("text", m.text).put("mine", m.mine).put("time", m.time) })) } })
            put("posts", JSONArray(s.posts.map { p -> JSONObject().put("id", p.id).put("text", p.text).put("category", p.category).put("image", p.image ?: "") }))
            put("pools", JSONArray(s.pools.map { p -> JSONObject().put("id", p.id).put("name", p.name).put("location", p.location).put("date", p.date).put("capacity", p.capacity).put("description", p.description).put("time", p.time) }))
            put("events", JSONArray(s.events.map { e -> JSONObject().put("id", e.id).put("name", e.name).put("description", e.description).put("location", e.location).put("date", e.date) }))
        }
        val editor = prefs.edit().putString("state", j.toString())
        if (s.onboarded) {
            editor.putLong("revision", prefs.getLong("revision", 0) + 1)
        }
        editor.apply()
        mutable.value = s
        if (s.onboarded) sync.schedule()
    }
    fun toggle(id: String, collection: String) = update { s ->
        fun Set<String>.flip() = if (id in this) this - id else this + id
        when (collection) {
            "liked" -> s.copy(liked = s.liked.flip())
            "joined" -> s.copy(joined = s.joined.flip())
            "following" -> s.copy(following = s.following.flip())
            "requested" -> s.copy(requested = s.requested.flip())
            else -> s.copy(saved = s.saved.flip())
        }
    }
    fun book(itemId: String, date: String, time: String, guests: Int): String {
        val id = UUID.randomUUID().toString().take(8).uppercase()
        update { it.copy(bookings = it.bookings + Booking(id, itemId, date, time, guests)) }
        return id
    }
    fun send(key: String, text: String) = update { s -> s.copy(messages = s.messages + (key to (s.messages[key].orEmpty() + Message(text.trim(), true)))) }
    fun reset() {
        val editor = prefs.edit().remove("state")
        if (prefs.contains("revision")) {
            editor.putLong("revision", prefs.getLong("revision", 0) + 1)
        }
        editor.apply()
        mutable.value = CityState()
        sync.schedule()
    }
}
