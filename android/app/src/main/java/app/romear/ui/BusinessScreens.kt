package app.romear.ui

import app.romear.R
import org.json.JSONObject
import app.romear.data.LocalEvent
import com.google.android.material.materialswitch.MaterialSwitch
import java.time.LocalDate
import java.util.UUID

fun CityScreens.premium() {
    ui.gap(body, 12)
    body.addView(ui.icon(R.drawable.ic_crown, "ROMEAR Premium"))
    ui.heading(body, "ROMEAR Premium", "More possibilities. More of your city.")
    val plan = draft("plan", "Monthly")
    ui.chips(body, listOf("Monthly", "Annual"), plan) { save("plan", it); refresh() }
    ui.card(body) { box ->
        box.addView(ui.text("A little more room to explore", 22, true)); ui.gap(box, 16)
        listOf("Advanced discovery filters", "Unlimited saved collections", "Monthly activity recaps", "Extra profile customisation", "An ad-free experience").forEach { feature ->
            box.addView(ui.text("✓  $feature", 16)); ui.gap(box, 12)
        }
    }
    ui.note(body, "Membership preview · Pricing and subscriptions are not configured. No purchase, free trial, or paid feature unlock is available in this version.")
    ui.button(body, "Continue exploring") { host.navigate("explore", root = true) }
}

fun CityScreens.business() {
    ui.heading(body, "Business Tools", "A workspace for the people behind the places.")
    val tab = draft("workspace", "Club tools")
    ui.chips(body, listOf("Club tools", "Venue tools"), tab) { save("workspace", it); refresh() }
    ui.note(body, "Local business preview · No organisation account, public listing or live booking access.")
    if (tab == "Club tools") {
        ui.stats(body, listOf(s.events.size.toString() to "Event drafts", s.approved.size.toString() to "Approved", (3 - s.approved.size - s.declined.size).coerceAtLeast(0).toString() to "Sample requests"))
        ui.button(body, "Create an event draft", icon = R.drawable.ic_plus) { go("create-event") }
        ui.action(body, "Member approvals", if (model.activeCommunity) "Review requests to join your pools" else "Try a sample organiser queue", R.drawable.ic_users) { go("approvals") }
        ui.action(body, "Club verification", "Understand the publishing requirements", R.drawable.ic_shield) { go("verification") }
        ui.section(body, "Your event drafts")
        if (s.events.isEmpty()) ui.note(body, "Bring your first idea to life. Event drafts are saved on this device.")
        localEvents.forEach { itemCard(it) }
    } else {
        ui.stats(body, listOf(s.bookings.count { !it.cancelled }.toString() to "Demo bookings", s.offers.size.toString() to "Offer drafts", "0" to "Live listings"))
        ui.button(body, "Create an offer draft", icon = R.drawable.ic_plus) { go("create-offer") }
        ui.action(body, "Booking management", "View local demo reservations", R.drawable.ic_calendar) { go("plans") }
        ui.section(body, "Your offer drafts")
        if (s.offers.isEmpty()) ui.note(body, "Create a little reason for your community to come by.")
        s.offers.forEach { offer -> ui.action(body, offer.substringBefore('\n'), offer.substringAfter('\n', ""), R.drawable.ic_ticket) {
            confirm("Remove this offer draft?", "This only removes the draft stored on your device.", "Remove") { repo.update { it.copy(offers = it.offers - offer) } }
        } }
    }
    ui.action(body, "Promote an event", "Preview audience and budget settings", R.drawable.ic_star) { go("promote") }
}

fun CityScreens.approvals() {
    if (model.activeCommunity) {
        ui.heading(body, "Good communities\nstart with people.", "Requests to join your pools.")
        if (model.applicants.isEmpty()) ui.empty(body, "No requests yet", "Requests from members will appear here.")
        model.applicants.forEach { applicant ->
            fun review(status: String) {
                val path = "pools/${applicant.pool}/requests/${applicant.account}"
                model.updateCommunity("PUT", path, JSONObject().put("status", status), ::toast) { refresh() }
            }
            ui.card(body) { box ->
                box.addView(ui.text(applicant.name, 20, true)); ui.gap(box, 8)
                box.addView(ui.text(applicant.title, 14, color = ui.muted))
                if (applicant.status == "pending") {
                    ui.button(box, "Approve request") { review("approved") }
                    ui.button(box, "Decline", false) { review("declined") }
                } else {
                    box.addView(ui.text(applicant.status.replaceFirstChar { it.uppercase() }, 14, true))
                    ui.button(box, "Undo", false) { review("pending") }
                }
            }
        }
        return
    }
    ui.heading(body, "Good communities\nstart with people.", "Sample requests for City Runners Club.")
    ui.note(body, "Practice queue · These are fictional requests. Decisions are saved only on this device.")
    listOf("aarav" to "Aarav Mehta", "riya" to "Riya Sharma", "kabir" to "Kabir Singh").forEach { (id, name) ->
        ui.card(body) { box ->
            box.addView(ui.text(name, 20, true)); ui.gap(box, 8)
            when(id) {
                in s.approved -> { box.addView(ui.text("Approved locally", 14, true, ui.forest)); ui.button(box, "Undo", false) { repo.update { it.copy(approved = it.approved - id) } } }
                in s.declined -> { box.addView(ui.text("Declined locally", 14, true, ui.rust)); ui.button(box, "Undo", false) { repo.update { it.copy(declined = it.declined - id) } } }
                else -> {
                    box.addView(ui.text("Interested in joining the next community run.", 14, color = ui.muted)); ui.gap(box, 12)
                    ui.button(box, "Approve sample request") { repo.update { it.copy(approved = it.approved + id) } }
                    ui.button(box, "Decline", false) { repo.update { it.copy(declined = it.declined + id) } }
                }
            }
        }
    }
}

fun CityScreens.verification() {
    ui.heading(body, "Real communities.\nA little more trust.", "Public events should come from verified clubs.")
    ui.card(body) { box ->
        box.addView(ui.text("Before a club can publish", 21, true)); ui.gap(box, 16)
        listOf("At least 3–4 phone-verified members", "A complete club profile and description", "A clear organiser and contact details", "Agreement to community guidelines").forEachIndexed { index, line ->
            box.addView(ui.text("${index + 1}. $line", 16)); ui.gap(box, 14)
        }
    }
    ui.note(body, "Club verification requires a connected authentication and review service. This demo cannot verify identities, accept applications or publish events. You can prepare a local event draft in Business Tools.")
    ui.button(body, "Open business workspace") { go("business") }
}

fun CityScreens.businessEditor() {
    val event = route.page == "create-event"
    val existing = s.events.firstOrNull { it.id == route.id }
    ui.heading(body, if (event) "Make something\nworth showing up for." else "Give them a reason\nto drop by.")
    val title = ui.input(body, if (event) "Event title" else "Offer title", draft("title", existing?.name.orEmpty())) { save("title", it) }
    val description = ui.input(body, "Description", draft("description", existing?.description.orEmpty()), true) { save("description", it) }
    val place = if (event) ui.input(body, "Meeting place", draft("place", existing?.location.orEmpty())) { save("place", it) } else null
    val date = draft("date", existing?.date ?: LocalDate.now().plusDays(7).toString())
    ui.action(body, if (event) "Event date" else "Valid until", date, R.drawable.ic_calendar) { pickDate(date) { save("date", it); refresh() } }
    val discount = if (!event) ui.input(body, "Discount percentage", draft("discount", "10"), type = android.text.InputType.TYPE_CLASS_NUMBER) { save("discount", it) } else null
    ui.note(body, "Draft only · Saved on this device. No public event, discount or business listing will be published.")
    ui.button(body, "Save ${if (event) "event" else "offer"} draft") {
        when {
            title.text.toString().trim().length !in 3..80 -> title.error = "Use 3–80 characters"
            description.text.toString().trim().length !in 10..2000 -> description.error = "Use 10–2,000 characters"
            event && place!!.text.toString().trim().length !in 3..120 -> place!!.error = "Use 3–120 characters"
            !event && (discount!!.text.toString().toIntOrNull() ?: 0) !in 1..90 -> discount!!.error = "Choose a value from 1 to 90"
            LocalDate.parse(date).isBefore(LocalDate.now()) -> toast("Choose today or a future date")
            else -> {
                if (event) {
                    val entry = LocalEvent(existing?.id ?: "local-${UUID.randomUUID()}", title.text.toString().trim(), description.text.toString().trim(), place!!.text.toString().trim(), date)
                    repo.update { it.copy(events = it.events.filterNot { e -> e.id == entry.id } + entry) }
                } else {
                    val offer = "${title.text.toString().trim()}\n${discount!!.text}% off · Until $date\n${description.text.toString().trim()}"
                    repo.update { it.copy(offers = it.offers + offer) }
                }
                save("title", ""); save("description", ""); save("place", ""); go("business")
            }
        }
    }
    if (existing != null) ui.button(body, "Remove local event draft", false) {
        confirm("Remove this event draft?", "This removes the event and any demo reservations for it from this device.", "Remove") {
            repo.update { it.copy(events = it.events.filterNot { e -> e.id == existing.id }, bookings = it.bookings.filterNot { b -> b.itemId == existing.id }, saved = it.saved - existing.id) }
            go("business")
        }
    }
}

fun CityScreens.promote() {
    ui.heading(body, "Reach the people\nwho’ll love it.", "Preview an event promotion.")
    val event = draft("event", "Riverside Night Market")
    ui.action(body, "Event", event, R.drawable.ic_calendar) {
        choose("Choose an event", (app.romear.data.CityData.events + localEvents).map { it.title }) { save("event", it); refresh() }
    }
    val audience = draft("audience", "In my city")
    ui.action(body, "Audience", audience, R.drawable.ic_users) { choose("Audience", listOf("In my city", "People who like running", "Café communities", "Culture & arts")) { save("audience", it); refresh() } }
    ui.section(body, "Duration")
    ui.chips(body, listOf("3 days", "7 days", "14 days"), draft("duration", "3 days")) { save("duration", it); refresh() }
    ui.section(body, "Example budget")
    ui.chips(body, listOf("€10", "€20", "€50"), draft("budget", "€20")) { save("budget", it); refresh() }
    ui.note(body, "Planning preview only. Audience size, delivery, payments and advertising analytics are not connected. No reach or RSVP results are promised.")
    ui.button(body, "Share promotion brief", icon = R.drawable.ic_share) {
        share("ROMEAR promotion brief\nEvent: $event\nAudience: $audience\nDuration: ${draft("duration", "3 days")}\nBudget concept: ${draft("budget", "€20")}\nDraft only — no advertising campaign has been started.")
    }
}
