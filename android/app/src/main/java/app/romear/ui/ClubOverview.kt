package app.romear.ui

import app.romear.R
import app.romear.data.CityItem

private data class ClubStory(val founder: String, val personId: String, val founded: String, val purpose: String, val activities: String, val schedule: String, val expectations: String, val verified: Boolean)

fun CityScreens.clubAbout(club: CityItem) {
    val info = when (club.id) {
        "runners" -> ClubStory("Aarav Mehta", "aarav", "March 2021", "We make running a social habit. Our community brings people of every pace together to explore Rome, build confidence, and find friends beyond the finish line.", "Weekly social runs, beginner-friendly routes, weekend long runs, and coffee after the finish.", "Wednesday evenings and Sunday mornings", "All abilities welcome. Bring water, arrive on time, and respect everyone’s pace.", true)
        "cafe-circle" -> ClubStory("Riya Sharma", "riya", "September 2022", "A community for discovering independent cafés and making meaningful connections over a shared table.", "Café discoveries, tasting sessions, book conversations, and relaxed neighbourhood meetups.", "Saturday mornings, with monthly tasting sessions", "Come curious. Everyone orders individually; ask before photographing other members.", true)
        "tech" -> ClubStory("Kabir Singh", "kabir", "January 2023", "A welcoming space for local builders, founders, and curious minds to exchange ideas and help each other grow.", "Project demos, coworking sessions, founder conversations, and peer feedback.", "Thursday evenings and a monthly demo night", "All experience levels welcome. Share generously and avoid unsolicited sales pitches.", false)
        else -> ClubStory("Meera Joshi", "meera", "June 2020", "We uncover the stories behind Rome’s streets, landmarks, and overlooked corners, one walk at a time.", "Neighbourhood walks, architecture trails, museum visits, and photography outings.", "Two Sunday walks each month", "Comfortable shoes recommended. Respect local residents and protect the places we visit.", true)
    }
    ui.section(body, "About the club")
    ui.card(body) { box ->
        box.addView(ui.text("Our story", 19, true))
        ui.gap(box, 8); box.addView(ui.text(info.purpose, 15))
        ui.divider(box)
        box.addView(ui.text("What we do", 17, true)); ui.gap(box, 6)
        box.addView(ui.text(info.activities, 15, color = ui.muted))
    }
    ui.action(body, "Founded by ${info.founder}", "Meet the person behind the community", R.drawable.ic_user) { go("person", info.personId) }
    detailSection("Community at a glance", "Founded" to info.founded, "Based in" to "${club.location}, Rome", "When we meet" to info.schedule, "Who can join" to "Open to everyone", "Membership" to "Free to join. Venue purchases and separately ticketed activities are paid separately.")
    ui.action(body, if (info.verified) "Verified club" else "Verification pending", if (info.verified) "About this community’s verification" else "This club has not been verified yet", R.drawable.ic_shield) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(host)
            .setTitle(if (info.verified) "Verified club · Mock profile" else "Verification pending")
            .setMessage(if (info.verified) "In the live app, this badge would indicate reviewed organiser details, a complete club profile, verified founding members, and agreement to community guidelines. This prototype uses fictional verification status." else "This mock club is shown without a verified badge. A live verification review would be required before public event publishing.")
            .setPositiveButton("Got it", null).show()
    }
    ui.card(body) { box ->
        box.addView(ui.text("Before you join", 18, true)); ui.gap(box, 8)
        box.addView(ui.text(info.expectations, 15))
    }
}

fun isMockClubVerified(id: String) = id in setOf("runners", "cafe-circle", "urban")
