package app.romear.ui

import android.content.Intent
import android.widget.Toast
import app.romear.MainActivity
import app.romear.R
import app.romear.data.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class CityScreens(val host: MainActivity) {
    val model get() = host.model
    val repo get() = model.repository
    val s get() = model.state.value
    val route get() = model.route
    val ui = CityUi(host)
    val body get() = host.binding.content
    fun go(page: String, id: String = "") = host.navigate(page, id)
    fun refresh() = host.render()
    fun toast(text: String) { Toast.makeText(host, text, Toast.LENGTH_SHORT).show() }
    fun draft(key: String, default: String = "") = model.draft("${route.page}_${route.id}_$key", default)
    fun save(key: String, value: String) = model.saveDraft("${route.page}_${route.id}_$key", value)
    val businessEvents get() = model.eventDrafts + s.events
    val passportStamps get() = if (model.activeCommunity) model.savedStamps else s.stamps
    val localEvents get() = businessEvents.map { CityItem(it.id, it.name, "${it.date} · Event draft", R.drawable.photo_market, Kind.EVENT, it.description, it.location, "Community") }
    fun findItem(id: String) = CityData.find(id) ?: localEvents.firstOrNull { it.id == id }
    val discoverPeople get() = if (model.activeCommunity) model.people.map { it.item } else CityData.people
    val joinedClubs get() = if (model.activeCommunity) model.clubMemberships.filter { it.joined }.map { it.id }.toSet() else s.joined
    fun findPerson(id: String) = model.livePerson(id) ?: CityData.people.firstOrNull { it.id == id }
    fun follows(id: String) = model.people.firstOrNull { it.item.id == id }?.following
        ?: model.clubMembers.values.asSequence().flatten().firstOrNull { it.item.id == id }?.following
        ?: (id in s.following)
    fun followPerson(id: String) {
        val following = follows(id)
        if (model.livePerson(id) != null) {
            model.updateCommunity(if (following) "DELETE" else "PUT", "following/$id", null, ::toast) {
                if (following) refresh() else go("connected", id)
            }
        } else {
            repo.toggle(id, "following")
            if (!following) go("connected", id)
        }
    }
    fun saveProfileField(change: (CityState) -> CityState) {
        if (model.activeCommunity) {
            model.authenticate({ message -> toast(message); refresh() }) {
                saveProfile(change(repo.state.value))
                repo.update(change)
            }
        } else repo.update(change)
    }
    fun choose(title: String, options: List<String>, action: (String) -> Unit) {
        MaterialAlertDialogBuilder(host).setTitle(title).setItems(options.toTypedArray()) { _, index -> action(options[index]) }.setNegativeButton("Cancel", null).show()
    }
    fun confirm(title: String, description: String, positive: String, action: () -> Unit) {
        MaterialAlertDialogBuilder(host).setTitle(title).setMessage(description).setNegativeButton("Keep", null).setPositiveButton(positive) { _, _ -> action() }.show()
    }
    fun share(text: String) { host.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share with…")) }
    fun itemCard(item: CityItem) {
        if (item.kind == Kind.POST) feedPost(item)
        else ui.photo(body, item.copy(subtitle = item.subtitle + if (item.subtitle.contains(item.location, true)) "" else "\n${item.location}"), item.id in s.saved, { repo.toggle(item.id, "saved") }, { open(item) })
    }
    fun open(item: CityItem) = go(when(item.kind) {
        Kind.CLUB -> "club"; Kind.EVENT -> "event"; Kind.VENUE -> "venue"; Kind.PERSON -> "person"; Kind.POST -> "post"; Kind.POOL -> "pool"; Kind.PLACE -> "place"
    }, item.id)
    fun render() {
        when (route.page) {
            "welcome", "signin", "otp", "city", "interests", "setup", "ready" -> onboarding()
            "home" -> home()
            "explore" -> explore()
            "search" -> search()
            "clubs" -> clubs()
            "club" -> club()
            "people" -> people()
            "person" -> person()
            "shared" -> shared()
            "connected" -> connected()
            "post" -> post()
            "compose" -> compose()
            "story" -> story()
            "reel" -> reelViewer()
            "saved" -> saved()
            "events", "venues" -> listing()
            "event", "venue", "place" -> experience()
            "booking" -> booking()
            "plans" -> plans()
            "pass" -> pass()
            "pools" -> pools()
            "pool" -> pool()
            "create-pool" -> createPool()
            "inbox" -> inbox()
            "chat" -> chat()
            "notifications" -> notifications()
            "profile" -> profile()
            "edit-profile" -> editProfile()
            "passport" -> passport()
            "map" -> map()
            "badges" -> badges()
            "timeline" -> timeline()
            "places" -> places()
            "recap" -> recap()
            "settings" -> settings()
            "premium" -> premium()
            "business" -> business()
            "approvals" -> approvals()
            "verification" -> verification()
            "create-event", "create-offer" -> businessEditor()
            "promote" -> promote()
            else -> ui.empty(body, "Let’s explore", "Find your next experience in the city.")
        }
    }
    fun menu() {
        val entries = listOf("Explore your city" to "explore", "My plans" to "plans", "Pools & activities" to "pools", "Saved places & posts" to "saved",
            "My passport" to "passport", "Business tools" to "business", "ROMEAR Premium" to "premium", "Settings" to "settings")
        choose("Your city, your way", entries.map { it.first }) { label -> go(entries.first { it.first == label }.second) }
    }
    companion object {
        val titles = mapOf("signin" to "Get started", "city" to "Your city", "interests" to "Your interests", "setup" to "Create profile", "ready" to "Welcome aboard",
            "search" to "Search", "club" to "Club", "people" to "Discover people", "person" to "ROMEAR", "shared" to "Shared interests", "connected" to "Connected",
            "post" to "Post", "compose" to "Create post", "saved" to "Saved", "story" to "Stories", "venues" to "Cafés & spaces", "event" to "Event details", "venue" to "Venue details",
            "booking" to "Plan your visit", "plans" to "My plans", "pass" to "Reservation", "pools" to "Pools", "pool" to "Pool details", "create-pool" to "Create pool",
            "chat" to "Conversation", "notifications" to "Notifications", "edit-profile" to "Edit profile", "passport" to "My Passport", "map" to "Experience Map",
            "badges" to "Badges & Achievements", "timeline" to "Activity Timeline", "places" to "Places Explored", "recap" to "Monthly Recap", "settings" to "Settings",
            "premium" to "ROMEAR Premium", "business" to "Business Tools", "approvals" to "Member Approvals", "verification" to "Verify your club",
            "create-event" to "Create event", "create-offer" to "Create offer", "promote" to "Promote your event", "place" to "Place details")
    }
}
