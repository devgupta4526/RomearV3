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
    private var communityVersion = 0
    private val routeUpdates = MutableStateFlow(route)
    internal val communityRoutes = routeUpdates.asStateFlow()
    private val liveRoutes = setOf("explore", "people", "person", "shared", "connected", "search", "inbox",
        "chat", "plans", "pass", "booking", "notifications", "pools", "pool", "events", "approvals", "clubs", "club", "profile")

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
            "inbox" -> "inbox"
            "chat" -> when {
                CityData.clubs.any { it.id == screen.id } -> if (clubMemberships.any { it.id == screen.id && it.joined }) "clubs/${screen.id}/messages" else "clubs"
                screen.id.length != 36 -> return false
                livePerson(screen.id) == null -> "inbox"
                else -> "messages/${screen.id}"
            }
            "plans", "pass", "booking", "notifications" -> "plans"
            "pools", "pool", "create-pool", "events" -> "pools"
            "explore" -> if (draft("explore__category") == "Pools") "pools" else "people"
            "approvals" -> "pool-requests"
            "clubs", "profile" -> "clubs"
            "club" -> if (draft("club_${screen.id}_tab") == "Members" &&
                clubMemberships.any { it.id == screen.id && it.joined }) "clubs/${screen.id}/members" else "clubs"
            else -> "people"
        }
        val result = auth.community("GET", endpoint)
        if (version != communityVersion || !activeCommunity) return false
        return when (endpoint) {
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
            else -> if (endpoint.endsWith("/members")) {
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
                readCommunity(requestedFrom, communityVersion, "clubs".takeIf { endpoint.endsWith("/membership") })
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
        thread = emptyMap()
        viewModelScope.launch { auth.signOut() }
        saved.keys().toList().forEach { saved.remove<Any>(it) }
        route = Route("welcome")
        repository.reset()
    }
}
