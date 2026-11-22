package app.romear.ui

import app.romear.R
import app.romear.data.CityData
import app.romear.data.CityItem

data class PublicPersonDetails(val since: String, val about: String, val lookingFor: String, val languages: String, val clubs: List<String>, val verified: Boolean)

fun publicPersonDetails(id: String) = when (id) {
    "aarav" -> PublicPersonDetails("March 2021", "I moved to Rome for its energy and stayed for the people. Most weekends start with a riverside run and end with a new café. I founded City Runners Club to make finding good company a little easier.", "Running buddies, café discoveries, and relaxed weekend adventures.", "English · Hindi · Italian", listOf("runners", "cafe-circle", "urban"), true)
    "riya" -> PublicPersonDetails("September 2022", "A reader, art enthusiast, and collector of independent cafés. I founded Riverside Café Society to bring people together around good conversations and shared tables.", "Book conversations, gallery visits, and new places for coffee.", "English · Hindi", listOf("cafe-circle", "urban"), true)
    "kabir" -> PublicPersonDetails("January 2023", "A maker who loves turning ideas into useful things. I started Tech & Startups Circle to connect local builders, share what we learn, and help each other get started.", "Creative collaborators, project feedback, and an occasional morning run.", "English · Hindi", listOf("tech", "runners"), false)
    else -> PublicPersonDetails("June 2020", "I love the stories behind a city: its music, neighbourhoods, and people. As the founder of Urban Explorers, I organise easygoing walks that help us see familiar streets in a new way.", "Live music, city walks, and people who enjoy exploring somewhere new.", "English · Italian", listOf("urban"), true)
}

fun CityScreens.publicPersonAbout(person: CityItem) {
    val details = publicPersonDetails(person.id)
    ui.section(body, "About ${person.title.substringBefore(' ')}")
    ui.card(body) { box ->
        box.addView(ui.text(details.about, 15))
        ui.divider(box)
        box.addView(ui.text("Here to connect over", 17, true)); ui.gap(box, 6)
        box.addView(ui.text(details.lookingFor, 15, color = ui.muted))
    }
    ui.factTiles(body, listOf(Triple(R.drawable.ic_pin, "Rome", "Home city"), Triple(R.drawable.ic_calendar, details.since.substringAfter(' '), "Member since"), Triple(R.drawable.ic_users, "${details.clubs.size} clubs", "Communities")))
    detailSection("A little more", "Member since" to details.since, "Languages" to details.languages)
    ui.section(body, "Interests")
    val interests = person.subtitle.split('·').map(String::trim)
    ui.chips(body, interests, interests.first()) { interest -> toast(interest) }
    val shared = interests.filter { it in s.interests }
    if (shared.isNotEmpty()) ui.action(body, "${shared.size} shared interests", shared.joinToString(" · "), R.drawable.ic_users) { go("shared", person.id) }
    ui.action(body, if (details.verified) "Verified profile" else "Profile not verified", "About this profile’s verification status", R.drawable.ic_shield) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(host)
            .setTitle(if (details.verified) "Verified profile · Mock status" else "Profile not verified")
            .setMessage(if (details.verified) "This fictional profile demonstrates a verification badge. In the live app, verification must be backed by an identity review. It does not guarantee someone’s behaviour or endorse everything they share." else "This fictional profile has not been marked verified. Public information is still available, but no identity review is represented.")
            .setPositiveButton("Got it", null).show()
    }
    ui.section(body, "Communities")
    details.clubs.forEach { id -> CityData.clubs.firstOrNull { it.id == id }?.let { club ->
        val founder = details.clubs.first() == id
        ui.action(body, club.title, "${if (founder) "Founder" else "Member"}${if (id in s.joined) " · You’re both members" else ""}", R.drawable.ic_users) { go("club", id) }
    } }
    ui.note(body, "Only public profile details are shown. Phone numbers, email addresses and exact personal locations stay private.")
}
