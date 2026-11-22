package app.romear.data

import app.romear.R
import java.time.OffsetDateTime
import org.json.JSONObject

internal data class CommunityPerson(val item: CityItem, val following: Boolean)
internal data class Conversation(val peer: CityItem, val preview: Message)
internal data class ClubMembership(val id: String, val members: Int, val joined: Boolean)
internal data class SharedPool(val pool: LocalPool, val mine: Boolean, val status: String, val joined: Int) {
    val item get() = CityItem(
        pool.id, pool.name, "${pool.date} · ${pool.time} · $joined of ${pool.capacity} joined",
        R.drawable.photo_park, Kind.POOL, pool.description, pool.location,
    )
}
internal data class PoolApplicant(val pool: String, val account: String, val title: String, val name: String, val status: String)
internal data class CommunityPost(
    val id: String, val owner: String, val author: String, val body: String, val category: String,
    val city: String, val photo: Boolean, val sample: String?, val mine: Boolean, val following: Boolean,
    val liked: Boolean, val saved: Boolean, val likes: Int, val comments: Int,
) {
    val item get() = CityItem(id, body.take(70), author, R.drawable.photo_river, Kind.POST, body, city, category)
}
internal data class PostComment(val author: String, val body: String)
internal data class OfferDraft(val id: String, val title: String, val description: String, val date: String, val discount: Int)

internal fun JSONObject.communityPost() = CommunityPost(
    getString("id"), getString("owner_id"), getString("author"), getString("body"), getString("category"),
    getString("city"), getBoolean("has_photo"), optString("sample").takeUnless { isNull("sample") },
    getBoolean("mine"), getBoolean("following"), getBoolean("liked"), getBoolean("saved"), getInt("likes"), getInt("comments"),
)

internal fun <T> JSONObject.records(key: String, read: (JSONObject) -> T): List<T> {
    val rows = getJSONArray(key)
    return (0 until rows.length()).map { read(rows.getJSONObject(it)) }
}

internal fun JSONObject.person(id: String): CityItem {
    val profile = getJSONObject("profile")
    val interests = profile.getJSONArray("interests")
    return CityItem(
        id, profile.getString("name"),
        (0 until interests.length()).joinToString(" · ") { interests.getString(it) },
        R.drawable.ic_user, Kind.PERSON, profile.getString("bio"), profile.getString("city"),
        profile.getString("username"),
    )
}

internal fun JSONObject.message() = Message(
    getString("body"), getBoolean("mine"),
    OffsetDateTime.parse(getString("sent_at")).toInstant().toEpochMilli(),
    optString("author").takeIf(String::isNotBlank),
)

internal fun JSONObject.plan() = Booking(
    getString("id"), getString("item_id"), getString("day"), getString("time_label"),
    getInt("guests"), getBoolean("cancelled"),
)

internal fun JSONObject.sharedPool() = SharedPool(
    LocalPool(getString("id"), getString("title"), getString("location"), getString("day"),
        getInt("capacity"), getString("description"), getString("time_label")),
    getBoolean("mine"), if (isNull("status")) "" else getString("status"), getInt("joined"),
)

internal fun JSONObject.applicant() = PoolApplicant(
    getString("pool_id"), getString("account_id"), getString("title"), getString("name"), getString("status"),
)
