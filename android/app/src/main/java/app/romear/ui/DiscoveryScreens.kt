package app.romear.ui

import android.view.Gravity
import androidx.core.view.setPadding
import android.widget.LinearLayout
import app.romear.R
import app.romear.data.*

fun CityScreens.explore() {
    ui.heading(body, "Explore your city", "Discover clubs, events, cafés, people\nand stories around you.")
    val category = draft("category", "For you")
    val categories = listOf("For you" to R.drawable.ic_compass, "Clubs" to R.drawable.ic_users,
        "Events" to R.drawable.ic_calendar, "Pools" to R.drawable.ic_run, "People" to R.drawable.ic_user,
        "Cafés" to R.drawable.ic_coffee, "Posts" to R.drawable.ic_photo)
    val rail = ui.row()
    categories.forEach { (label, icon) ->
        val selected = label == category
        rail.addView(ui.column().apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ui.dp(68), ui.dp(76)).apply { marginEnd = ui.dp(7) }
            background = ui.shape(if (selected) ui.forest else ui.sand, 16)
            addView(ui.icon(icon, label).apply {
                layoutParams = LinearLayout.LayoutParams(ui.dp(36), ui.dp(36))
                setPadding(ui.dp(7))
                imageTintList = android.content.res.ColorStateList.valueOf(if (selected) android.graphics.Color.WHITE else ui.rust)
            })
            addView(ui.text(label, 12, true, if (selected) android.graphics.Color.WHITE else ui.ink).apply { gravity = Gravity.CENTER })
            contentDescription = label
            isSelected = selected
            setOnClickListener { save("category", label); refresh() }
        })
    }
    body.addView(android.widget.HorizontalScrollView(host).apply {
        isHorizontalScrollBarEnabled = false
        addView(rail, android.widget.FrameLayout.LayoutParams(-2, -2))
        post { smoothScrollTo((categories.indexOfFirst { it.first == category } * ui.dp(75) - width / 3).coerceAtLeast(0), 0) }
    })
    ui.gap(body, 16)
    ui.action(body, "Search your city", "Find a person, club or activity", R.drawable.ic_search) { go("search") }
    when (category) {
        "Clubs" -> {
            ui.section(body, "Trending clubs")
            CityData.clubs.take(3).forEachIndexed { index, club ->
                itemCard(club)
                val activity = listOf(
                    "Sunday long run" to "Members are planning an easy riverside run followed by coffee.",
                    "Café meetup" to "Members are picking their next independent café to try together.",
                    "Project demo night" to "Local makers are sharing prototypes and looking for feedback."
                )[index]
                ui.action(body, activity.first, activity.second, R.drawable.ic_users) { go("club", club.id) }
            }
            ui.section(body, "Sponsored")
            ui.action(body, "Riverside Café", "Sample promotion · Meet over coffee by the river", R.drawable.ic_coffee) { go("venue", "riverside") }
        }
        "Pools" -> {
            ui.section(body, "Small pools")
            if (model.activeCommunity) model.sharedPools.filterNot { it.mine }.forEach { itemCard(it.item) }
            else CityData.pools.forEach { itemCard(it) }
            ui.button(body, "Create a pool") { go("create-pool") }
        }
        "Events" -> CityData.events.forEach { itemCard(it) }
        "People" -> discoverPeople.forEach { resultRow(it) }
        "Cafés" -> CityData.venues.forEach { itemCard(it) }
        "Posts" -> CityData.posts.forEach { feedPost(it) }
        else -> {
            ui.section(body, "Coming up"); itemCard(CityData.events.first())
            ui.section(body, "Small plans nearby"); itemCard(CityData.pools.first())
            ui.section(body, "Trending community"); itemCard(CityData.clubs.first())
        }
    }
}

fun CityScreens.search() {
    val query = ui.input(body, "Search clubs, people, cafés…", draft("query")) { save("query", it) }
    val filter = draft("filter", "All")
    ui.chips(body, listOf("All", "Clubs", "People", "Events", "Cafés"), filter) { save("filter", it); refresh() }
    ui.button(body, "Search", false, R.drawable.ic_search) { save("query", query.text.toString()); refresh() }
    val pool = when (filter) { "Clubs" -> CityData.clubs; "People" -> discoverPeople; "Events" -> CityData.events; "Cafés" -> CityData.venues; else -> CityData.clubs + discoverPeople + CityData.events + CityData.venues }
    val terms = draft("query").lowercase().trim().split(Regex("\\s+")).filter { it.isNotBlank() && it !in setOf("near", "me", "in", "rome", "clubs", "club") }
    val result = pool.filter { item -> terms.all { term -> "${item.title} ${item.subtitle} ${item.description} ${item.category}".contains(term, true) } }
    ui.note(body, "${result.size} results · Rome reference collection")
    if (result.isEmpty()) ui.empty(body, "Nothing here just yet", "Try a name, an interest, or a shorter search.")
    result.forEach { resultRow(it) }
}

private fun CityScreens.resultRow(item: CityItem) {
    ui.card(body, 12) { box ->
        box.addView(ui.row().apply {
            addView(if (item.kind == Kind.PERSON) ui.avatar(item.image, 68) else ui.image(item.image, 72).apply { layoutParams = LinearLayout.LayoutParams(ui.dp(78), ui.dp(72)) })
            addView(ui.column().apply {
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = ui.dp(12) }
                addView(ui.text(item.title, 17, true)); addView(ui.text(item.subtitle, 13, color = ui.muted))
            })
            addView(ui.icon(R.drawable.ic_arrow, "Open ${item.title}") { open(item) })
        })
    }.setOnClickListener { open(item) }
}

fun CityScreens.clubs() {
    ui.heading(body, "Discover Clubs", "Clubs for your interests and city life.")
    val filter = draft("filter", if (route.id == "joined") "Joined" else "For you")
    ui.chips(body, listOf("For you", "Joined", "Popular"), filter) { save("filter", it); refresh() }
    val directory = CityData.clubs.map { club ->
        if (model.activeCommunity) {
            val count = model.clubMemberships.firstOrNull { it.id == club.id }?.members ?: 0
            club.copy(subtitle = "${club.category} · $count members", members = count)
        } else club
    }
    val clubs = when (filter) { "Joined" -> directory.filter { it.id in joinedClubs }; "Popular" -> directory.sortedByDescending { it.members }; else -> directory }
    if (clubs.isEmpty()) ui.empty(body, "Your people are out there", "Join a club to see it here.")
    clubs.forEach { itemCard(it) }
    ui.action(body, "Pools & community activities", "Make a small plan. Find good company.", R.drawable.ic_users) { go("pools") }
    ui.button(body, "Verify a club", false, R.drawable.ic_shield) { go("verification") }
}

fun CityScreens.club() {
    val club = CityData.clubs.firstOrNull { it.id == route.id } ?: return
    val live = model.activeCommunity
    ui.hero(body, club, "A COMMUNITY FOR YOUR CITY")
    if (live) {
        val count = model.clubMemberships.firstOrNull { it.id == club.id }?.members ?: 0
        ui.note(body, "$count ROMEAR members · ${club.category}")
    } else ui.socialProof(body, "${club.members} people, one shared interest", "${club.category} · ${club.location}", 4, 6, false)
    if (!live && isMockClubVerified(club.id)) {
        body.addView(ui.text("✓  Verified club", 14, true, ui.forest).apply {
            contentDescription = "Verified club, sample status. Tap for details."
            minHeight = ui.dp(48)
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { toast("Sample verified club · See verification details in Overview") }
        })
    }
    body.addView(ui.text(club.description)); ui.gap(body, 18)
    ui.chips(body, listOf(club.category, "Community", "Rome"), club.category) { }
    val joined = club.id in joinedClubs
    ui.button(body, if (joined) "Joined · Leave club" else "Join Club", !joined, R.drawable.ic_users) {
        fun changeMembership() {
            if (live) model.updateCommunity(if (joined) "DELETE" else "PUT", "clubs/${club.id}/membership", null, ::toast) { refresh() }
            else { repo.toggle(club.id, "joined"); if (!joined) toast("Added to your clubs on this device") }
        }
        if (joined) confirm("Leave ${club.title}?", if (live) "This also removes access to the club chat." else "You can join again at any time in this local demo.", "Leave") { changeMembership() }
        else changeMembership()
    }
    val tab = draft("tab", "Overview")
    ui.chips(body, listOf("Overview", "Events", "Posts", "Members"), tab) { save("tab", it); refresh() }
    when(tab) {
        "Overview", "Events" -> {
              if (tab == "Overview") clubAbout(club)
            ui.section(body, "Upcoming together")
            itemCard(if (club.category == "Running") CityData.events[1] else CityData.events[2])
            if (tab == "Overview") ui.action(body, "Community guidelines", "Be kind. Show up. Make room for everyone.", R.drawable.ic_shield) {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(host).setTitle("A better community, together").setMessage("Respect everyone’s pace and privacy. Keep plans accurate. Ask before sharing photos of others. For this offline demo, reporting and moderation are not connected.").setPositiveButton("Got it", null).show()
            }
        }
        "Posts" -> CityData.posts.filter { if (club.category == "Running") it.category == "Running" else it.category == "Cafés" }.forEach { itemCard(it) }
        "Members" -> if (live) {
            if (joined) model.clubMembers[club.id].orEmpty().forEach { resultRow(it.item) }
            else ui.empty(body, "Meet your club", "Join to view the member directory.")
        } else CityData.people.forEach { resultRow(it) }
    }
    ui.button(body, "Share club", false, R.drawable.ic_share) { share("Discover ${club.title} on ROMEAR — ${club.description}") }
}

fun CityScreens.people() {
    ui.heading(body, "Discover People", "Meet people with similar interests\nin your city.")
    val filter = draft("filter", "For you")
    ui.chips(body, listOf("For you", "Following", "Mutual clubs"), filter) { save("filter", it); refresh() }
    val people = if (filter == "Following") discoverPeople.filter { follows(it.id) } else discoverPeople
    if (people.isEmpty()) ui.empty(body, "Make the first connection", "Follow someone from the discovery collection.")
    people.forEach { person ->
        body.addView(ui.row().apply {
            addView(ui.avatar(person.image, 64))
            addView(ui.column().apply {
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = ui.dp(14) }
                addView(ui.text(person.title, 21, true)); addView(ui.text(person.subtitle, 14)); addView(ui.text(person.location, 12, color = ui.muted))
                setOnClickListener { go("person", person.id) }
            })
            addView(ui.icon(if (follows(person.id)) R.drawable.ic_check else R.drawable.ic_plus, "${if (follows(person.id)) "Unfollow" else "Follow"} ${person.title}") {
                followPerson(person.id)
            })
        })
        ui.divider(body)
    }
}

fun CityScreens.person() {
    val p = findPerson(route.id) ?: return
    val live = model.livePerson(p.id) != null
    body.addView(ui.image(R.drawable.photo_river, 160)); ui.gap(body, 14)
    body.addView(ui.avatar(p.image, 96).apply { setPadding(ui.dp(4)); background = ui.shape(ui.cream, 60); layoutParams = LinearLayout.LayoutParams(ui.dp(96), ui.dp(96)).apply { topMargin = -ui.dp(48); marginStart = ui.dp(16) } }); ui.gap(body, 12)
    ui.heading(body, p.title, "@${if (live) p.category else p.title.lowercase().replace(" ", "")}")
    if (!live && publicPersonDetails(p.id).verified) body.addView(ui.text("✓  Verified profile", 14, true, ui.forest).apply {
        minHeight = ui.dp(48); gravity = Gravity.CENTER_VERTICAL
        contentDescription = "Verified profile, sample status"
    })
    if (!live) ui.stats(body, listOf("128" to "Experiences", "24" to "Clubs", "56" to "Events"))
    body.addView(ui.text(p.description)); ui.gap(body, 16)
    ui.note(body, if (live) p.location else "Demo profile · Rome")
    ui.button(body, if (follows(p.id)) "Following" else "Follow", !follows(p.id)) {
        followPerson(p.id)
    }
    ui.button(body, "Message", false, R.drawable.ic_chat) { go("chat", p.id) }
    ui.action(body, "You share similar interests", "See what you could do together", R.drawable.ic_users) { go("shared", p.id) }
    val tab = draft("profile-tab", "About")
    ui.chips(body, listOf("About", "Activity", "Clubs", "Events"), tab) { save("profile-tab", it); refresh() }
    if (live) {
        if (tab == "About") ui.note(body, p.subtitle)
        else ui.empty(body, "Nothing shared here yet", "This member’s public ${tab.lowercase()} will appear here.")
        return
    }
    when (tab) {
        "About" -> { publicPosts(p.id); publicPersonAbout(p) }
        "Activity" -> {
            ui.section(body, "Public activity")
            publicPosts(p.id)
        }
        "Clubs" -> publicPersonDetails(p.id).clubs.forEach { id -> CityData.clubs.firstOrNull { it.id == id }?.let { itemCard(it) } }
        "Events" -> { ui.section(body, "Interested in"); itemCard(if (p.id == "aarav" || p.id == "kabir") CityData.events[1] else CityData.events[2]) }
    }
}

fun CityScreens.shared() {
    val person = findPerson(route.id) ?: return
    ui.heading(body, "You Share\nSimilar Interests", "Great potential to connect and do more together.")
    body.addView(ui.row().apply { gravity = Gravity.CENTER; addView(ui.avatar(R.drawable.avatar_aarav, 100)); addView(ui.icon(R.drawable.ic_users, "Shared interests")); addView(ui.avatar(person.image, 100)) })
    ui.gap(body, 24)
    ui.section(body, "You both like")
    val shared = s.interests.filter { interest -> person.subtitle.contains(interest, true) }
    if (shared.isNotEmpty()) ui.chips(body, shared, shared.first()) { }
    else ui.note(body, "Try something new together: cafés, running, and city walks.")
    ui.action(body, "Discover mutual communities", "Find clubs that bring you together", R.drawable.ic_users) { go("clubs") }
    ui.action(body, "Upcoming events", "Turn a conversation into a plan", R.drawable.ic_calendar) { go("events") }
    ui.action(body, "Meet at a café", "Find a table and take it from there", R.drawable.ic_coffee) { go("venues") }
}

fun CityScreens.connected() {
    val p = findPerson(route.id) ?: return
    ui.gap(body, 24)
    body.addView(ui.icon(R.drawable.ic_check, "Following").apply { layoutParams = LinearLayout.LayoutParams(ui.dp(80), ui.dp(80)).apply { gravity = Gravity.CENTER_HORIZONTAL }; background = ui.shape(ui.sand, 40) })
    ui.gap(body, 24)
    ui.heading(body, "You’re now connected!", if (model.livePerson(p.id) != null) "You’re following ${p.title}." else "You’re following ${p.title} in this local demo.")
    ui.button(body, "Message", icon = R.drawable.ic_chat) { go("chat", p.id) }
    ui.button(body, "View profile", false) { go("person", p.id) }
    ui.divider(body); ui.section(body, "Plan something together?")
    ui.action(body, "Join an upcoming event", "Sunday Long Run", R.drawable.ic_calendar) { go("event", "sunday-run") }
    ui.action(body, "Suggest a club", "City Runners Club", R.drawable.ic_users) { go("club", "runners") }
    ui.action(body, "Meet at a café", "Riverside Café", R.drawable.ic_coffee) { go("venue", "riverside") }
}
