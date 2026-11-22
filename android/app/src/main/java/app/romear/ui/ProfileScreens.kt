package app.romear.ui

import android.graphics.Color
import androidx.core.view.setPadding
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import app.romear.R
import app.romear.data.*
import com.google.android.material.switchmaterial.SwitchMaterial

private val CityScreens.xp get() = s.joined.size * 25 + s.posts.size * 20 + s.stamps.size * 10 + s.bookings.count { !it.cancelled } * 15
private val CityScreens.level get() = 1 + xp / 100

fun CityScreens.profile() {
    body.addView(ui.image(R.drawable.photo_river, 150))
    body.addView(ui.avatar(R.drawable.avatar_aarav, 96).apply { setPadding(ui.dp(4)); background = ui.shape(ui.cream, 60); layoutParams = LinearLayout.LayoutParams(ui.dp(96), ui.dp(96)).apply { topMargin = -ui.dp(42); marginStart = ui.dp(16) } }); ui.gap(body, 14)
    body.addView(ui.text(s.name, 30, true)); body.addView(ui.text("@${s.username}", 17, color = ui.muted)); ui.gap(body, 8)
    body.addView(ui.text("City Explorer · Level 6", 16, true)); ui.gap(body, 22)
    ui.stats(body, listOf("128" to "Experiences", "24" to "Clubs", "56" to "Events", "6" to "Cities"))
    ownerProfileInformation()
    ui.section(body, "Highlights")
    body.addView(ui.row().apply {
        listOf(R.drawable.photo_colosseum, R.drawable.photo_river, R.drawable.photo_cafe).forEachIndexed { index, image ->
            addView(ui.image(image, 105).apply {
                layoutParams = LinearLayout.LayoutParams(0, ui.dp(105), 1f).apply { if (index < 2) marginEnd = ui.dp(8) }
                contentDescription = "Open your city passport"; setOnClickListener { go("passport") }
            })
        }
    })
    ui.gap(body, 20); ui.button(body, "Edit Profile") { go("edit-profile") }
    ui.section(body, "Recent public posts")
    if (s.posts.isEmpty()) ui.action(body, "Share a moment", "Your posts will appear here", R.drawable.ic_photo) { go("compose") }
    s.posts.takeLast(3).asReversed().forEach { post ->
        ui.card(body) { card ->
            card.addView(ui.text(s.name, 16, true))
            ui.gap(card, 8)
            post.image?.let { addPostMedia(card, it); ui.gap(card, 8) }
            card.addView(ui.text(post.text, 15))
        }
    }
    ui.action(body, "My Passport", "Your city story, collected", R.drawable.ic_ticket) { go("passport") }
    ui.action(body, "Experience map", "Explore the illustrated Rome map", R.drawable.ic_pin) { go("map") }
    ui.action(body, "Badges & achievements", "$xp local exploration points", R.drawable.ic_star) { go("badges") }
    ui.action(body, "Activity timeline", "The little things add up", R.drawable.ic_calendar) { go("timeline") }
    ui.action(body, "My clubs", "${s.joined.size} communities", R.drawable.ic_users) { go("clubs", "joined") }
    ui.action(body, "Places to explore", "Build your next city chapter", R.drawable.ic_compass) { go("places") }
    ui.action(body, "My progress", "A recap of your local activity", R.drawable.ic_star) { go("recap") }
}

fun CityScreens.editProfile() {
    ui.heading(body, "Edit Profile")
    val name = ui.input(body, "Name", draft("name", s.name)) { save("name", it) }
    val username = ui.input(body, "Username", draft("username", s.username)) { save("username", it) }
    val bio = ui.input(body, "About you", draft("bio", s.bio), true) { save("bio", it) }
    ui.button(body, "Save profile") {
        val n = name.text.toString().trim(); val u = username.text.toString().trim().removePrefix("@")
        when {
            n.length !in 2..50 -> name.error = "Use 2–50 characters"
            !u.matches(Regex("[a-zA-Z0-9_.]{3,24}")) -> username.error = "Use 3–24 letters, numbers, dots or underscores"
            bio.text.toString().length > 160 -> bio.error = "Keep your bio under 160 characters"
            else -> {
                val about = bio.text.toString().trim()
                if (model.auth.hasSession) {
                    model.authenticate({ toast(it) }) {
                        saveProfile(repo.state.value.copy(name = n, username = u, bio = about))
                        if (route.page == "edit-profile") {
                            repo.update { it.copy(name = n, username = u, bio = about) }
                            go("profile")
                        }
                    }
                } else {
                    repo.update { it.copy(name = n, username = u, bio = about) }
                    go("profile")
                }
            }
        }
    }
    ui.section(body, "Your interests")
    val all = listOf("Running", "Cafés", "Travel", "Books", "Tech", "Culture", "Music", "Outdoors")
    all.forEach { interest ->
        body.addView(SwitchMaterial(host).apply {
            setShowText(false); textOn = ""; textOff = ""
            thumbTintList = android.content.res.ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(ui.forest, ui.muted))
            trackTintList = android.content.res.ColorStateList.valueOf(ui.sand)
            text = interest; isChecked = interest in s.interests; minHeight = ui.dp(48)
            setOnCheckedChangeListener { _, enabled -> repo.update { state -> state.copy(interests = if (enabled) state.interests + interest else state.interests - interest) } }
        })
    }
}

fun CityScreens.passport() {
    ui.heading(body, "My Passport")
    body.addView((if (s.name == "Aarav Mehta") ui.image(R.drawable.passport_cover, 300) else PassportCoverView(host, s.name, 6)).apply {
        layoutParams = LinearLayout.LayoutParams(-1, ui.dp(300)).apply { bottomMargin = ui.dp(24) }
        contentDescription = "ROMEAR City Passport for ${s.name}, City Explorer, Level $level"
    })
    ui.stats(body, listOf("128" to "Verified Experiences", "6" to "Cities Visited", "42" to "Places Explored"))
    ui.section(body, "Passport Stamps", "${s.stamps.size} cities") { }
    val cities = listOf("Rome" to R.drawable.photo_rome, "Milan" to R.drawable.photo_milan, "Florence" to R.drawable.photo_florence,
        "Naples" to R.drawable.photo_naples, "Bologna" to R.drawable.photo_bologna, "Venice" to R.drawable.photo_venice)
    cities.chunked(3).forEach { three ->
        body.addView(ui.row().apply {
            three.forEach { (city, image) -> addView(ui.column().apply {
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = ui.dp(6); bottomMargin = ui.dp(16) }
                alpha = 1f
                addView(ui.image(image, 78)); addView(ui.text(city, 14, true).apply { gravity = Gravity.CENTER })
                setOnClickListener { if (city in s.stamps) confirm("Remove $city stamp?", "This is a self-reported stamp on your device.", "Remove") { repo.update { it.copy(stamps = it.stamps - city) } } else repo.update { it.copy(stamps = it.stamps + city) } }
            }) }
        })
    }
    ui.button(body, "Add Passport Stamp", icon = R.drawable.ic_plus) {
        val available = cities.map { it.first }.filterNot { it in s.stamps }
        if (available.isEmpty()) toast("You’ve collected every city in this demo")
        else choose("Where have you explored?", available) { city -> repo.update { it.copy(stamps = it.stamps + city) } }
    }
    ui.note(body, "Stamps are self-reported local keepsakes. They are not verified travel records.")
}

fun CityScreens.map() {
    ui.heading(body, "Experience Map", "An illustrated guide to Rome.")
    val tab = draft("filter", "All")
    ui.chips(body, listOf("All", "Places", "Cafés", "Events"), tab) { save("filter", it); refresh() }
    if (s.mapVisible) {
        body.addView(ui.image(R.drawable.experience_map, 450).apply { scaleType = android.widget.ImageView.ScaleType.FIT_CENTER; contentDescription = "Illustrated reference map of Rome with sample experience pins" })
        ui.gap(body, 16)
        ui.note(body, "Illustrated reference artwork with sample pins. It is not an interactive map, a location history, or live navigation.")
    } else ui.note(body, "The illustrated map is hidden in your privacy preferences.")
    val items = when(tab) { "Cafés" -> CityData.venues; "Events" -> CityData.events; "Places" -> CityData.places; else -> CityData.places + CityData.events }
    items.forEach { item -> ui.action(body, item.title, item.location, R.drawable.ic_pin) { open(item) } }
}

fun CityScreens.badges() {
    ui.gap(body, 12)
    ui.card(body) { box ->
        box.addView(ui.row().apply {
            addView(ui.image(R.drawable.badge_insider, 82).apply { layoutParams = LinearLayout.LayoutParams(ui.dp(82), ui.dp(82)) })
            addView(ui.column().apply {
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = ui.dp(16) }
                addView(ui.text("Level 6", 28, true)); addView(ui.text("City Explorer", 20, true))
                ui.gap(this, 10)
                addView(ProgressBar(host, null, android.R.attr.progressBarStyleHorizontal).apply { max = 3000; progress = 2450; progressTintList = android.content.res.ColorStateList.valueOf(ui.forest) })
                addView(ui.text("2,450 / 3,000 XP", 14, color = ui.muted))
            })
        })
    }
    ui.section(body, "City Explorer Badges", "See All") { toast("All city badges are shown") }
    val badges = listOf(Triple("Café Trail", "(10 cafés)", R.drawable.badge_cafe), Triple("Event Goer", "(10 events)", R.drawable.badge_event), Triple("Riverside Runner", "", R.drawable.badge_runner), Triple("Historic Explorer", "", R.drawable.badge_history), Triple("Community Builder", "", R.drawable.badge_community), Triple("City Insider", "(Seasonal)", R.drawable.badge_insider))
    badges.chunked(3).forEach { row ->
        body.addView(ui.row().apply {
            row.forEach { (title, subtitle, image) -> addView(ui.column().apply {
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = ui.dp(8); bottomMargin = ui.dp(24) }
                addView(ui.image(image, 91).apply { scaleType = android.widget.ImageView.ScaleType.FIT_CENTER })
                addView(ui.text(title, 16, true).apply { gravity = Gravity.CENTER })
                if (subtitle.isNotBlank()) addView(ui.text(subtitle, 13).apply { gravity = Gravity.CENTER })
                setOnClickListener { toast(title) }
            }) }
        })
    }
    ui.section(body, "Recent Achievements")
    ui.action(body, "50 Verified Experiences", "+500 XP", R.drawable.ic_check) { toast("50 Verified Experiences") }
    ui.action(body, "5 Events in a Month", "+250 XP", R.drawable.ic_calendar) { toast("5 Events in a Month") }
}

fun CityScreens.timeline() {
    ui.heading(body, "Activity Timeline")
    val filter = draft("filter", "All")
    ui.chips(body, listOf("All", "Plans", "Clubs", "Posts"), filter) { save("filter", it); refresh() }
    ui.note(body, "Activity on this device · Grouped by type")
    if (filter in listOf("All", "Plans")) s.bookings.reversed().forEach { booking ->
        ui.action(body, if (booking.cancelled) "Cancelled a local plan" else "Made a local plan", "${findItem(booking.itemId)?.title.orEmpty()} · ${booking.date}", R.drawable.ic_calendar) { go("pass", booking.id) }
    }
    if (filter in listOf("All", "Clubs")) s.joined.forEach { id -> ui.action(body, "Joined a club", CityData.find(id)?.title.orEmpty(), R.drawable.ic_users) { go("club", id) } }
    if (filter in listOf("All", "Posts")) s.posts.reversed().forEach { post -> ui.card(body) { box -> box.addView(ui.text("Shared a city story", 18, true)); box.addView(ui.text(post.text, 15)) } }
    if (s.bookings.isEmpty() && s.joined.isEmpty() && s.posts.isEmpty()) ui.empty(body, "The first page is yours", "Join a club, share a post or make a plan to begin.")
    ui.button(body, "Add a moment", false, R.drawable.ic_plus) { go("compose") }
}

fun CityScreens.places() {
    ui.heading(body, "Places Explored", "Start a collection of your city favourites.")
    CityData.places.forEach { itemCard(it) }
}

fun CityScreens.recap() {
    ui.heading(body, "Monthly Recap", "Your activity on this device, so far.")
    ui.stats(body, listOf(xp.toString() to "Local XP", level.toString() to "Level", s.stamps.size.toString() to "Cities"))
    ui.section(body, "Your moments")
    ui.action(body, "${s.posts.size} city stories", "The little things you shared", R.drawable.ic_photo) { go("home") }
    ui.action(body, "${s.joined.size} communities", "People with something in common", R.drawable.ic_users) { go("clubs") }
    ui.action(body, "${s.bookings.count { !it.cancelled }} plans", "A reason to step outside", R.drawable.ic_calendar) { go("plans") }
    ui.action(body, "${s.saved.size} saved discoveries", "For another good day", R.drawable.ic_bookmark) { go("saved") }
    ui.button(body, "Find your next experience") { go("explore") }
}

fun CityScreens.settings() {
    ui.heading(body, "Settings")
    ui.action(body, "Edit profile", "Name, bio and interests", R.drawable.ic_user) { go("edit-profile") }
    ui.action(body, "Home city", s.city, R.drawable.ic_pin) {
        choose("Choose your city", listOf("Rome", "Milan", "Florence", "Naples", "Bologna", "Venice")) { city -> repo.update { it.copy(city = city) } }
    }
    ui.section(body, "Privacy")
    body.addView(SwitchMaterial(host).apply {
            setShowText(false); textOn = ""; textOff = ""
            thumbTintList = android.content.res.ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(ui.forest, ui.muted))
            trackTintList = android.content.res.ColorStateList.valueOf(ui.sand)
        text = "Show illustrated experience map"; isChecked = s.mapVisible; minHeight = ui.dp(56)
        setOnCheckedChangeListener { _, value -> repo.update { it.copy(mapVisible = value) } }
    })
    ui.note(body, "This offline app does not request your location or publish your profile. All messages, plans and activity stay on this device.")
    ui.action(body, "ROMEAR Premium", "Preview the membership", R.drawable.ic_crown) { go("premium") }
    ui.action(body, "Business tools", "A preview for organisers and venues", R.drawable.ic_users) { go("business") }
    ui.divider(body)
    ui.button(body, "Reset local demo data", false) {
        confirm("Start fresh?", "This permanently removes this app’s local profile, posts, messages, stamps and reservations from this device.", "Reset data") {
            model.reset(); host.navigate("welcome", root = true)
        }
    }
    ui.note(body, "ROMEAR 0.1.0 · Native Android preview\nNo connected authentication, billing, live bookings or moderation service.")
}
