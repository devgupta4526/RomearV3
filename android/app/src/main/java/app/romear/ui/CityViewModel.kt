package app.romear.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.romear.data.*
import java.io.IOException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONException
import org.json.JSONObject

data class Route(val page: String, val id: String = "")

class CityViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    val repository = CityRepository.get(application)
    val state = repository.state
    internal val auth = CityAuth(application)
    init {
        if (auth.deviceEnabled && !auth.hasSession && !auth.demoSelected && state.value.onboarded) {
            saved["page"] = "welcome"
            saved["entity"] = ""
            saved.remove<ArrayList<String>>("history")
        }
    }
    private var authInProgress = false
    internal var people = emptyList<CommunityPerson>()
        private set
    internal var conversations = emptyList<Conversation>()
        private set
    internal var thread = emptyMap<String, List<Message>>()
        private set
    internal var personalPlans = emptyList<Booking>()
        private set
    internal var sharedPools = emptyList<SharedPool>()
        private set
    internal var applicants = emptyList<PoolApplicant>()
        private set
    internal var clubMemberships = emptyList<ClubMembership>()
        private set
    internal var clubMembers = emptyMap<String, List<CommunityPerson>>()
        private set
    internal var publicFeed = emptyList<CommunityPost>()
        private set
    internal var cloudSavedPosts = emptyList<CommunityPost>()
        private set
    internal var authorPosts = emptyMap<String, List<CommunityPost>>()
        private set
    internal var postDetails: CommunityPost? = null
        private set
    internal var postComments = emptyList<PostComment>()
        private set
    internal var savedStamps = emptySet<String>()
        private set
    internal var eventDrafts = emptyList<LocalEvent>()
        private set
    internal var offerDrafts = emptyList<OfferDraft>()
        private set
    internal val postPhotos = object : android.util.LruCache<String, android.graphics.Bitmap>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: android.graphics.Bitmap) = value.byteCount
    }
    private var communityVersion = 0
    private val routeUpdates = MutableStateFlow(route)
    internal val communityRoutes = routeUpdates.asStateFlow()
    private val liveRoutes = setOf("explore", "people", "person", "shared", "connected", "search", "inbox",
        "chat", "plans", "pass", "booking", "notifications", "pools", "pool", "events", "approvals", "clubs", "club", "profile", "home", "post", "saved",
        "passport", "business", "create-event", "create-offer", "promote")

    internal val activeCommunity get() = auth.hasSession
    internal fun livePerson(id: String) = people.firstOrNull { it.item.id == id }?.item
        ?: conversations.firstOrNull { it.peer.id == id }?.peer
        ?: clubMembers.values.asSequence().flatten().firstOrNull { it.item.id == id }?.item

    internal suspend fun refreshCommunity(): Boolean {
        val screen = route
        val version = communityVersion
        if (!activeCommunity || authInProgress || screen.page !in liveRoutes) return false
        return try {
            readCommunity(screen, version)
        } catch (_: IOException) {
            false
        } catch (_: JSONException) {
            false
        }
    }

    private suspend fun readCommunity(screen: Route, version: Int, forcedEndpoint: String? = null): Boolean {
        val endpoint = forcedEndpoint ?: when (screen.page) {
            "passport", "business", "create-event", "create-offer", "promote" -> "library"
            "home", "compose" -> "posts"
            "saved" -> "posts?view=saved"
            "post" -> if (screen.id.length == 36) "posts/${screen.id}" else return false
            "person" -> when {
                screen.id.length != 36 -> "people"
                livePerson(screen.id) == null -> "people/${screen.id}"
                else -> "posts?author=${screen.id}"
            }
            "inbox" -> "inbox"
            "chat" -> when {
                CityData.clubs.any { it.id == screen.id } -> if (clubMemberships.any { it.id == screen.id && it.joined }) "clubs/${screen.id}/messages" else "clubs"
                screen.id.length != 36 -> return false
                livePerson(screen.id) == null -> "inbox"
                else -> "messages/${screen.id}"
            }
            "plans", "pass", "booking", "notifications" -> "plans"
            "pools", "pool", "create-pool", "events" -> "pools"
            "explore" -> when (draft("explore__category")) { "Pools" -> "pools"; "Posts" -> "posts"; else -> "people" }
            "approvals" -> "pool-requests"
            "clubs", "profile" -> "clubs"
            "club" -> if (draft("club_${screen.id}_tab") == "Members" &&
                clubMemberships.any { it.id == screen.id && it.joined }) "clubs/${screen.id}/members" else "clubs"
            else -> "people"
        }
        val result = auth.community("GET", endpoint)
        if (version != communityVersion || !activeCommunity) return false
        return when (endpoint) {
            "library" -> {
                val stamps = result.getJSONArray("stamps").let { array -> (0 until array.length()).map(array::getString).toSet() }
                val events = result.records("events") {
                    LocalEvent(it.getString("id"), it.getString("title"), it.getString("description"), it.getString("location"), it.getString("date"))
                }
                val offers = result.records("offers") {
                    OfferDraft(it.getString("id"), it.getString("title"), it.getString("description"), it.getString("date"), it.getInt("discount"))
                }
                (savedStamps != stamps || eventDrafts != events || offerDrafts != offers).also {
                    savedStamps = stamps; eventDrafts = events; offerDrafts = offers
                }
            }
            "posts" -> result.records("posts", JSONObject::communityPost).let { (publicFeed != it).also { _ -> publicFeed = it } }
            "posts?view=saved" -> result.records("posts", JSONObject::communityPost).let { (cloudSavedPosts != it).also { _ -> cloudSavedPosts = it } }
            "people" -> result.records("people") { CommunityPerson(it.person(it.getString("id")), it.getBoolean("following")) }
                .let { (people != it).also { _ -> people = it } }
            "inbox" -> {
                val rows = result.records("conversations") { Conversation(it.person(it.getString("peer")), it.message()) }
                val changed = conversations != rows
                conversations = rows
                readCommunity(screen, version, "clubs") || changed
            }
            "plans" -> result.records("plans", JSONObject::plan).let { (personalPlans != it).also { _ -> personalPlans = it } }
            "pools" -> result.records("pools", JSONObject::sharedPool).let { (sharedPools != it).also { _ -> sharedPools = it } }
            "pool-requests" -> result.records("requests", JSONObject::applicant).let { (applicants != it).also { _ -> applicants = it } }
            "clubs" -> result.records("clubs") { ClubMembership(it.getString("id"), it.getInt("members"), it.getBoolean("joined")) }
                .let { (clubMemberships != it).also { _ -> clubMemberships = it } }
            else -> if (endpoint.startsWith("people/")) {
                val person = result.getJSONObject("person")
                val entry = CommunityPerson(person.person(person.getString("id")), person.getBoolean("following"))
                people = people.filterNot { it.item.id == entry.item.id } + entry
                true
            } else if (endpoint.startsWith("posts?author=")) {
                val rows = result.records("posts", JSONObject::communityPost)
                (authorPosts[screen.id] != rows).also { authorPosts = mapOf(screen.id to rows) }
            } else if (endpoint.startsWith("posts/")) {
                val post = result.getJSONObject("post").communityPost()
                val comments = result.records("comments") { PostComment(it.getString("author"), it.getString("body")) }
                (postDetails != post || postComments != comments).also { postDetails = post; postComments = comments }
            } else if (endpoint.endsWith("/members")) {
                val members = result.records("people") { CommunityPerson(it.person(it.getString("id")), it.getBoolean("following")) }
                (clubMembers[screen.id] != members).also { clubMembers = mapOf(screen.id to members) }
            } else result.records("messages", JSONObject::message)
                .let { (thread[screen.id] != it).also { _ -> thread = mapOf(screen.id to it) } }
        }
    }

    internal fun updateCommunity(method: String, endpoint: String, body: JSONObject?, onError: (String) -> Unit, done: suspend () -> Unit) {
        val requestedFrom = route
        authenticate(onError) {
            communityVersion++
            community(method, endpoint, body)
            try {
                val projection = when {
                    endpoint.endsWith("/membership") -> "clubs"
                    endpoint.startsWith("following/") -> "people"
                    method == "DELETE" && endpoint.matches(Regex("posts/[a-f0-9-]{36}")) -> {
                        publicFeed = publicFeed.filterNot { "posts/${it.id}" == endpoint }
                        cloudSavedPosts = cloudSavedPosts.filterNot { "posts/${it.id}" == endpoint }
                        postDetails = null
                        "posts"
                    }
                    else -> null
                }
                readCommunity(requestedFrom, communityVersion, projection)
            } catch (_: IOException) {
                // The write succeeded. The foreground refresh will retry the read.
            } catch (_: JSONException) {
                // Keep the last successful local projection.
            }
            if (route == requestedFrom) done()
        }
    }

    internal fun authenticate(onError: (String) -> Unit, action: suspend CityAuth.() -> Unit) {
        if (authInProgress) return
        authInProgress = true
        val startingRoute = route
        viewModelScope.launch {
            try {
                auth.action()
            } catch (exception: IOException) {
                if (route == startingRoute) onError(exception.message ?: "Could not connect. Try again.")
            } catch (_: JSONException) {
                if (route == startingRoute) onError("The server response could not be read. Try again.")
            } finally {
                authInProgress = false
            }
        }
    }
    var route: Route
        get() = Route(saved["page"] ?: if (state.value.onboarded) "home" else "welcome", saved["entity"] ?: "")
        set(value) { saved["page"] = value.page; saved["entity"] = value.id; routeUpdates.value = value }
    private var history: ArrayList<String>
        get() = saved["history"] ?: arrayListOf()
        set(value) { saved["history"] = value }
    fun navigate(page: String, id: String = "", root: Boolean = false) {
        if (route == Route(page, id)) return
        history = if (root) arrayListOf() else ArrayList(history + "${route.page}|${route.id}")
        route = Route(page, id)
    }
    fun back(): Boolean {
        if (history.isEmpty()) return false
        val previous = history.last().split('|', limit = 2)
        history = ArrayList(history.dropLast(1))
        route = Route(previous[0], previous.getOrElse(1) { "" })
        return true
    }
    fun finishOnboarding() {
        repository.update { it.copy(onboarded = true) }
        navigate("home", root = true)
    }
    fun draft(key: String, default: String = ""): String = saved["draft_$key"] ?: default
    fun saveDraft(key: String, value: String) { saved["draft_$key"] = value }
    fun reset() {
        communityVersion++
        people = emptyList()
        conversations = emptyList()
        personalPlans = emptyList()
        sharedPools = emptyList()
        applicants = emptyList()
        clubMemberships = emptyList()
        clubMembers = emptyMap()
        publicFeed = emptyList()
        cloudSavedPosts = emptyList()
        authorPosts = emptyMap()
        postDetails = null
        postComments = emptyList()
        savedStamps = emptySet()
        eventDrafts = emptyList()
        offerDrafts = emptyList()
        postPhotos.evictAll()
        thread = emptyMap()
        viewModelScope.launch { auth.signOut() }
        saved.keys().toList().forEach { saved.remove<Any>(it) }
        route = Route("welcome")
        repository.reset()
    }
}
