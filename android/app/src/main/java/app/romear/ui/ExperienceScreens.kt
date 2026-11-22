package app.romear.ui

import android.app.DatePickerDialog
import android.widget.LinearLayout
import app.romear.R
import app.romear.data.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import org.json.JSONObject

fun CityScreens.listing() {
    val venues = route.page == "venues"
    ui.heading(body, if (venues) "Cafés & Spaces" else "Events", if (venues) "Cafés, shared tables, and spaces to belong." else "The city is better when you’re part of it.")
    if (!venues) {
        val tab = draft("events-tab", "Events")
        ui.chips(body, listOf("Events", "Small pools"), tab) { save("events-tab", it); refresh() }
        if (tab == "Small pools") { pools(showHeading = false); return }
    }
    val filter = draft("filter", "For you")
    ui.chips(body, if (venues) listOf("For you", "Cafés", "Workspaces") else listOf("For you", "Running", "Cafés", "Food & culture"), filter) { save("filter", it); refresh() }
    val catalogue = if (venues) CityData.venues else CityData.events + if (model.activeCommunity) emptyList() else localEvents
    catalogue.filter { filter == "For you" || it.category == filter }.forEach { itemCard(it) }
    ui.button(body, "View my plans", false, R.drawable.ic_calendar) { go("plans") }
    ui.note(body, "Reference collection · Event schedules, venue details and availability are demo content.")
}

fun CityScreens.experience() {
    val item = findItem(route.id) ?: return
    ui.hero(body, item, if (item.kind == Kind.EVENT) "CITY EXPERIENCES" else if (item.kind == Kind.VENUE) "A PLACE TO BELONG" else "EXPLORE ROME")
    ui.action(body, item.location, "View meeting area", R.drawable.ic_pin) { go("map") }
    if (item.kind == Kind.EVENT) ui.factTiles(body, listOf(Triple(R.drawable.ic_calendar, if (item.id == "sunday-run") "6:00 AM" else "5:00 PM", "Starts at"), Triple(R.drawable.ic_ticket, "Free entry", "Admission"), Triple(R.drawable.ic_users, "All levels", "Welcome")))
    else if (item.kind == Kind.VENUE) ui.factTiles(body, listOf(Triple(R.drawable.ic_coffee, if (item.id == "studio") "Workspace" else "Café", "Venue type"), Triple(R.drawable.ic_users, "1–8 guests", "Group size"), Triple(R.drawable.ic_shield, "Wi-Fi", "Amenities")))
    ui.section(body, when(item.kind) { Kind.EVENT -> "About the experience"; Kind.VENUE -> "Inside the venue"; else -> "Why visit" })
    body.addView(ui.text(item.description.ifBlank { "One of the city’s remarkable places. Make time to look around, take the slow route, and find something new." })); ui.gap(body, 20)
    experienceInformation(item)
    if (item.kind != Kind.PLACE) {
        ui.card(body) { box -> box.addView(ui.text(if (item.kind == Kind.EVENT) "Make it a plan" else "A table with your name on it", 19, true)); ui.gap(box, 8)
            box.addView(ui.text("Try the reservation flow using local demo data. No real ticket or table will be reserved.", 14, color = ui.muted)) }
        ui.button(body, if (item.kind == Kind.EVENT) "Continue to Checkout" else "Book a Table", icon = R.drawable.ic_ticket) { go("booking", item.id) }
    }
    ui.button(body, if (item.id in s.saved) "Saved · Remove" else "Save for later", false, R.drawable.ic_bookmark) { repo.toggle(item.id, "saved") }
    ui.button(body, "Share", false, R.drawable.ic_share) { share("${item.title}\n${item.description}\n${item.location} · ROMEAR") }
    if (item.kind == Kind.EVENT) ui.action(body, "Meet the community", "Discover the clubs behind the city", R.drawable.ic_users) { go("clubs") }
    if (businessEvents.any { it.id == item.id }) ui.button(body, "Edit event draft", false) { go("create-event", item.id) }
}

fun CityScreens.pickDate(current: String, selected: (String) -> Unit) {
    val day = runCatching { LocalDate.parse(current) }.getOrElse { LocalDate.now() }
    DatePickerDialog(host, { _, year, month, date -> selected(LocalDate.of(year, month + 1, date).toString()) }, day.year, day.monthValue - 1, day.dayOfMonth).apply {
        datePicker.minDate = LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.show()
}

fun CityScreens.booking() {
    val item = findItem(route.id) ?: return
    val date = draft("date_${item.id}", businessEvents.firstOrNull { it.id == item.id }?.date ?: LocalDate.now().plusDays(1).toString())
    val time = draft("time_${item.id}", if (item.kind == Kind.EVENT) "5:00 PM" else "1:30 PM")
    val guests = draft("guests_${item.id}", "2").toIntOrNull()?.coerceIn(1, 8) ?: 2
    val live = model.activeCommunity && CityData.find(item.id) != null
    ui.heading(body, if (item.kind == Kind.EVENT) "Checkout" else "Book a Table", item.title)
    ui.note(body, if (live) "1  Details    →    2  Save plan" else "1  Details    →    2  Local confirmation")
    ui.action(body, "Select a date", LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")), R.drawable.ic_calendar) {
        pickDate(date) { save("date_${item.id}", it); refresh() }
    }
    ui.action(body, if (item.kind == Kind.EVENT) "Tickets" else "Party size", "$guests ${if (guests == 1) "guest" else "guests"}", R.drawable.ic_users) {
        choose("How many people?", (1..8).map { "$it" }) { save("guests_${item.id}", it); refresh() }
    }
    ui.section(body, "Choose a time")
    val times = if (item.kind == Kind.EVENT) listOf("5:00 PM", "6:00 PM", "7:00 PM") else listOf("12:00 PM", "12:30 PM", "1:00 PM", "1:30 PM", "2:00 PM", "2:30 PM")
    ui.chips(body, times, time) { save("time_${item.id}", it); refresh() }
    ui.card(body) { box ->
        box.addView(ui.text("Your plan", 20, true)); ui.gap(box, 10)
        box.addView(ui.text("${item.title}\n${item.location}\n$date · $time\n$guests guests", 15))
        ui.divider(box); box.addView(ui.text("Total: €0.00", 18, true, ui.forest))
        ui.gap(box, 8); box.addView(ui.text(if (live) "Saved to your account as a personal plan. No table or ticket will be reserved." else "This is a local planning example. It does not check live inventory, charge a payment method or reserve a real place.", 13, color = ui.muted))
    }
    ui.button(body, if (live) "Save plan" else "Confirm Booking") {
        if (LocalDate.parse(date).isBefore(LocalDate.now())) { toast("Choose today or a future date"); return@button }
        val existing = visiblePlans().firstOrNull { !it.cancelled && it.itemId == item.id && it.date == date && it.time == time }
        if (existing != null) { toast("You already have this plan"); go("pass", existing.id) }
        else if (live) {
            val key = "plan_${item.id}_${date}_${time}_$guests"
            val id = draft(key).ifBlank { UUID.randomUUID().toString().also { save(key, it) } }
            val payload = JSONObject().put("id", id).put("item_id", item.id).put("day", date)
                .put("time_label", time).put("guests", guests)
            model.updateCommunity("PUT", "plans", payload, ::toast) {
                save(key, "")
                go("pass", id)
            }
        } else { val id = repo.book(item.id, date, time, guests); go("pass", id) }
    }
    detailSection("Booking information", "What’s included" to when {
        live -> "Your selected date, time and party size will appear in My Plans."
        item.kind == Kind.EVENT -> "Entry for your selected number of guests. Optional purchases are separate."
        else -> "A sample table reservation for your party. Food and drinks are separate."
    }, "Changes & cancellation" to if (live) "You can cancel a saved plan in My Plans." else "You can cancel in My Plans. There is no payment or refund in this mock flow.", "Before confirming" to "Check the date, time, guest count and meeting place. Accessibility or dietary arrangements must be agreed with the host separately.")
}

internal fun CityScreens.visiblePlans(): List<Booking> = model.personalPlans + s.bookings

fun CityScreens.plans() {
    ui.heading(body, "My Plans", if (model.activeCommunity) "Your personal plans, all in one place." else "Your local reservations, all in one place.")
    val filter = draft("filter", "Upcoming")
    ui.chips(body, listOf("Upcoming", "Past", "Cancelled"), filter) { save("filter", it); refresh() }
    val bookings = visiblePlans().filter { b -> when (filter) { "Cancelled" -> b.cancelled; "Past" -> !b.cancelled && LocalDate.parse(b.date).isBefore(LocalDate.now()); else -> !b.cancelled && !LocalDate.parse(b.date).isBefore(LocalDate.now()) } }
    if (bookings.isEmpty()) {
        ui.empty(body, "A little space for possibility", "Your ${filter.lowercase()} plans will appear here.")
        ui.button(body, "Find an experience") { go("events") }
    }
    bookings.forEach { b -> findItem(b.itemId)?.let { item ->
        val status = if (b.cancelled) "Cancelled" else if (b in model.personalPlans) "Plan saved" else "Confirmed"
        ui.photo(body, item.copy(subtitle = "${b.date} · ${b.time}\n${b.guests} guests · $status"), item.id in s.saved, { repo.toggle(item.id, "saved") }, { go("pass", b.id) })
    } }
}

fun CityScreens.pass() {
    val b = visiblePlans().firstOrNull { it.id == route.id } ?: run { ui.empty(body, "Plan unavailable", "This plan could not be loaded."); return }
    val live = b in model.personalPlans
    val item = findItem(b.itemId) ?: return
    body.addView(ui.image(item.image, 210)); ui.gap(body, 20)
    ui.heading(body, item.title, if (b.cancelled) "Cancelled" else if (live) "Plan saved" else "Confirmed")
    ui.card(body, 20) { box ->
        box.addView(ui.text(if (live) "PERSONAL PLAN" else "BOOKING PASS", 11, true, ui.rust).apply { letterSpacing = .12f }); ui.gap(box, 16)
        box.addView(ui.text("${b.date}\n${b.time}\n${item.location}", 18, true)); ui.gap(box, 16)
        box.addView(ui.text("${b.guests} guests · ${s.name}", 15)); ui.divider(box)
        box.addView(ui.text("RM-${b.id.take(8).uppercase()}", 24, true, ui.forest)); ui.gap(box, 8)
        box.addView(ui.text("For your planning only. This reference is not a valid entry ticket, café reservation, or payment receipt.", 14, color = ui.muted))
    }
    ui.button(body, "Share plan", false, R.drawable.ic_share) { share("Let’s go to ${item.title}!\n${b.date} · ${b.time}\n${item.location}\nPlanning example from ROMEAR; no real reservation.") }
    if (!b.cancelled) ui.button(body, if (live) "Cancel plan" else "Cancel demo reservation", false) {
        confirm("Cancel this plan?", if (live) "This marks the personal plan as cancelled in your account." else "Only this local demo reservation will be cancelled.", "Cancel plan") {
            if (live) model.updateCommunity("DELETE", "plans/${b.id}", null, ::toast) { refresh() }
            else repo.update { state -> state.copy(bookings = state.bookings.map { if (it.id == b.id) it.copy(cancelled = true) else it }) }
        }
    }
    ui.button(body, "View all plans") { go("plans") }
    reservationInformation(item)
}

fun CityScreens.pools(showHeading: Boolean = true) {
    if (showHeading) ui.heading(body, "Pools", "Find someone to play, ride, learn, or explore with.")
    val tab = draft("filter", "Open")
    ui.chips(body, listOf("Open", "My requests", "Created by me"), tab) { save("filter", it); refresh() }
    if (model.activeCommunity) {
        val rows = model.sharedPools.filter {
            when (tab) { "Created by me" -> it.mine; "My requests" -> it.status.isNotBlank(); else -> !it.mine && !LocalDate.parse(it.pool.date).isBefore(LocalDate.now()) }
        }
        if (rows.isEmpty()) ui.empty(body, "No pools yet", "Create a pool or explore another day.")
        rows.forEach { itemCard(it.item) }
        ui.button(body, "Create a pool", icon = R.drawable.ic_plus) { go("create-pool") }
        return
    }
    if (tab != "Created by me") {
        val pools = CityData.pools.filter { tab == "Open" || it.id in s.requested }
        if (pools.isEmpty()) ui.empty(body, "No requests yet", "Open a pool and request to join.")
        pools.forEach { itemCard(it) }
    } else {
        if (s.pools.isEmpty()) ui.empty(body, "Start something small", "A game, a study session, a walk. Make the first plan.")
        s.pools.forEach { p -> ui.action(body, p.name, "${p.date} · ${p.location}\n${p.capacity} places · Your local pool", R.drawable.ic_users) { go("pool", p.id) } }
    }
    ui.button(body, "Create a pool", icon = R.drawable.ic_plus) { go("create-pool") }
}

fun CityScreens.pool() {
    val shared = model.sharedPools.firstOrNull { it.pool.id == route.id }
    val own = s.pools.firstOrNull { it.id == route.id }
    val p = shared?.item ?: CityData.pools.firstOrNull { it.id == route.id } ?: own?.let { CityItem(it.id, it.name, "${it.date} · ${it.capacity} places", R.drawable.photo_park, Kind.POOL, it.description.ifBlank { "A community activity organised by ${s.name}." }, it.location) } ?: return
    ui.hero(body, p.copy(image = if (p.id == "table-tennis") R.drawable.photo_table_tennis else p.image), "SMALL PLANS. GOOD COMPANY.")
    val start = shared?.pool?.time ?: when (p.id) { "table-tennis" -> "7:00 PM"; "cycling" -> "6:30 AM"; "study" -> "4:00 PM"; else -> own?.time?.ifBlank { "Confirm time" }.orEmpty() }
    val room = shared?.let { "${(it.pool.capacity - it.joined).coerceAtLeast(0)} spots" } ?: when (p.id) {
        "table-tennis" -> "3 spots"
        "cycling", "study" -> "2 spots"
        else -> "${own?.capacity ?: 4} places"
    }
    ui.factTiles(body, listOf(Triple(R.drawable.ic_calendar, start, "Start time"), Triple(R.drawable.ic_users, room, "Room to join"), Triple(R.drawable.ic_shield, "All levels", "Welcome")))
    ui.socialProof(body, "A small group. A real connection.", "Meet your next good company", shared?.joined ?: if (p.id == "cycling" || own != null) 1 else 2, shared?.pool?.capacity ?: if (p.id == "cycling") 3 else if (p.id == "study") 4 else own?.capacity ?: 5)
    body.addView(ui.text(p.description)); ui.gap(body, 18)
    if (shared == null) poolInformation(p)
    ui.action(body, p.location, "Meeting point", R.drawable.ic_pin) { toast("Demo meeting point · Rome") }
    if (shared != null) {
        ui.note(body, when (shared.status) {
            "pending" -> "Waiting for the organiser to approve your request."
            "approved" -> "The organiser approved your request."
            "declined" -> "The organiser declined your request."
            else -> "The organiser approves requests."
        })
        if (shared.mine) {
            ui.button(body, "Remove your pool", false) {
                confirm("Remove ${p.title}?", "This also closes its join requests.", "Remove") {
                    model.updateCommunity("DELETE", "pools/${p.id}", null, ::toast) { go("pools") }
                }
            }
        } else {
            val requested = shared.status.isNotBlank()
            ui.button(body, if (requested) "Withdraw request" else "Request to join", !requested) {
                model.updateCommunity(if (requested) "DELETE" else "PUT", "pools/${p.id}/request", null, ::toast) { refresh() }
            }
        }
        ui.button(body, "Share pool", false, R.drawable.ic_share) { share("${p.title}\n${p.subtitle}\n${p.location}\nROMEAR") }
        return
    }
    ui.note(body, "The organiser approves requests. This offline demo saves the request on your device and does not contact the organiser.")
    if (own == null) ui.button(body, if (p.id in s.requested) "Withdraw local request" else "Request to join", p.id !in s.requested) { repo.toggle(p.id, "requested") }
    else ui.button(body, "Remove your pool", false) { confirm("Remove ${p.title}?", "This removes your locally created pool.", "Remove") { repo.update { it.copy(pools = it.pools.filterNot { pool -> pool.id == p.id }) }; go("pools") } }
    ui.button(body, "Share pool", false, R.drawable.ic_share) { share("${p.title}\n${p.subtitle}\n${p.location}\nA demo activity on ROMEAR.") }
}

fun CityScreens.createPool() {
    ui.heading(body, "Create Pool", "You bring the idea. Your city brings the people.")
    val title = ui.input(body, "Pool name", draft("name")) { save("name", it) }
    val location = ui.input(body, "Meeting place", draft("location")) { save("location", it) }
    val description = ui.input(body, "Describe the activity, plan and what to bring", draft("description"), true) { save("description", it) }
    val date = draft("date", LocalDate.now().plusDays(1).toString())
    ui.action(body, "Date", date, R.drawable.ic_calendar) { pickDate(date) { save("date", it); refresh() } }
    val startTime = draft("start-time", "6:00 PM")
    ui.action(body, "Start time", startTime, R.drawable.ic_calendar) { choose("Choose a start time", listOf("6:00 AM", "8:00 AM", "10:00 AM", "12:00 PM", "2:00 PM", "4:00 PM", "6:00 PM", "8:00 PM")) { save("start-time", it); refresh() } }
    val capacity = draft("capacity", "4")
    ui.action(body, "Places available", capacity, R.drawable.ic_users) { choose("Group size", (2..12).map(Int::toString)) { save("capacity", it); refresh() } }
    val live = model.activeCommunity
    ui.note(body, if (live) "Members in your city can find this pool and ask to join." else "This creates a pool on your device. Public discovery and organiser requests need a connected service.")
    ui.button(body, if (live) "Create pool" else "Create local pool") {
        when {
            title.text.toString().trim().length !in 3..80 -> title.error = "Use 3–80 characters"
            location.text.toString().trim().length !in 3..120 -> location.error = "Use 3–120 characters"
            description.text.toString().trim().length !in 20..2000 -> description.error = "Use 20–2,000 characters so people know what to expect"
            LocalDate.parse(date).isBefore(LocalDate.now()) -> toast("Choose today or a future date")
            else -> {
                val name = title.text.toString().trim()
                val meetingPlace = location.text.toString().trim()
                val details = description.text.toString().trim()
                if (live) {
                    val payload = JSONObject().put("title", name).put("location", meetingPlace).put("description", details)
                        .put("day", date).put("capacity", capacity.toInt()).put("time_label", startTime)
                    val signature = payload.toString()
                    val previous = draft("pendingPool")
                    val id = previous.substringBefore('\n').takeIf { it.isNotBlank() && previous.substringAfter('\n') == signature }
                        ?: UUID.randomUUID().toString()
                    save("pendingPool", "$id\n$signature")
                    model.updateCommunity("PUT", "pools", payload.put("id", id), ::toast) {
                        save("pendingPool", ""); save("name", ""); save("location", ""); save("description", "")
                        go("pools")
                    }
                } else {
                    val pool = LocalPool(UUID.randomUUID().toString(), name, meetingPlace, date, capacity.toInt(), details, startTime)
                    save("name", ""); save("location", ""); save("description", "")
                    repo.update { it.copy(pools = it.pools + pool) }; go("pools")
                }
            }
        }
    }
}
