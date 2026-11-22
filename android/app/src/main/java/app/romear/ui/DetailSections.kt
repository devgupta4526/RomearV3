package app.romear.ui

import app.romear.R
import app.romear.data.CityData
import app.romear.data.CityItem
import app.romear.data.Kind

/** Consistent information hierarchy for the fictional catalogue, not live availability or policies. */
fun CityScreens.detailSection(title: String, vararg fields: Pair<String, String>) {
    ui.section(body, title)
    ui.card(body, 16) { box -> fields.forEachIndexed { index, (label, value) ->
        if (index > 0) { ui.gap(box, 16) }
        detailRow(box, label, value, title)
    } }
}

fun CityScreens.poolInformation(item: CityItem) {
    val own = s.pools.firstOrNull { it.id == item.id }
    val hostId = when(item.id) { "cycling" -> "aarav"; "study" -> "riya"; else -> "kabir" }
    val hostName = if (own != null) s.name else CityData.people.first { it.id == hostId }.title
    ui.action(body, "Organised by $hostName", if (own != null) "You organise this activity" else "View organiser profile and interests", R.drawable.ic_user) { if (own != null) go("profile") else go("person", hostId) }
    val schedule = when(item.id) { "table-tennis" -> "Today, 7:00–8:30 PM"; "cycling" -> "Tomorrow, 6:30–8:00 AM"; "study" -> "Tomorrow, 4:00–6:00 PM"; else -> "${own?.date.orEmpty()} · ${own?.time?.takeIf { it.isNotBlank() } ?: "Time to be confirmed"}" }
    val capacity = when(item.id) { "table-tennis" -> "2 of 5 places filled · 3 places available"; "cycling" -> "1 of 3 places filled · 2 places available"; "study" -> "2 of 4 places filled · 2 places available"; else -> "You + up to ${(own?.capacity ?: 2) - 1} participants" }
    detailSection("Activity details", "When" to schedule, "Meeting point" to item.location, "Group size" to capacity, "Experience level" to "Beginners welcome · A relaxed, social activity")
    detailSection("What to expect", "The plan" to when(item.id) {
        "table-tennis" -> "Meet at reception, warm up together, then rotate partners for friendly matches. No tournament ranking or competitive selection."
        "cycling" -> "Meet by the riverside entrance, check bikes together, then ride a relaxed loop with a café stop. The group stays together."
        "study" -> "Introduce yourself, agree on a focus goal, and work in two quiet blocks with a short break to share progress."
        else -> "Meet the organiser at the listed place. Confirm timing and activity expectations together before joining."
    }, "Bring along" to when(item.id) { "table-tennis" -> "Comfortable sportswear, indoor shoes, and water. Spare paddles are available in this sample activity."; "cycling" -> "Your own bicycle, helmet, water, and a puncture kit. Check your brakes before leaving."; "study" -> "Laptop or books, charger, headphones, and your own study materials."; else -> "Ask the organiser what equipment or preparation is needed." }, "Cost" to if (own != null) "Not specified · Agree with the organiser before joining" else "No organiser fee. Venue purchases, rentals or transport are paid separately.")
    detailSection("Joining & participation", "Approval" to if (item.id in s.requested) "Your request is pending. You are not counted as a confirmed participant yet." else "Request a place. The organiser reviews requests before confirming the group.", "Cancellation" to "Withdraw your request if plans change. For a confirmed place, let the organiser know as early as possible.", "Community expectations" to "Be punctual and respectful. Ask before taking photos, respect boundaries, and do not share someone else’s contact details.", "Changes or bad weather" to "Confirm the final meeting point with the organiser. Outdoor activities may be rescheduled.")
    ui.action(body, "Ask the organiser", "Clarify timing, equipment or accessibility before joining", R.drawable.ic_chat) { if (own != null) toast("You are the organiser of this pool") else go("chat", hostId) }
    ui.action(body, "Participants", if (own != null) "Your new group is waiting for people" else "Meet people with similar interests", R.drawable.ic_users) { go("people") }
}

fun CityScreens.experienceInformation(item: CityItem) {
    when(item.kind) {
        Kind.EVENT -> {
            val running = item.id == "sunday-run"
            val clubId = if (running) "runners" else if (item.id == "coffee-meet") "cafe-circle" else "urban"
            val club = CityData.clubs.first { it.id == clubId }
            val ownEvent = s.events.any { it.id == item.id }
            ui.action(body, "Hosted by ${if (ownEvent) s.name else club.title}", if (ownEvent) "Your event draft" else "Meet the host community", R.drawable.ic_users) { if (ownEvent) go("profile") else go("club", clubId) }
            detailSection("Event information", "Schedule" to item.subtitle, "Where to meet" to item.location, "Who it’s for" to if (running) "All running levels. Choose a comfortable pace; nobody needs to race." else "People looking to meet their city community. Solo visitors and groups are welcome.", "Duration" to if (running) "About 90 minutes, including warm-up and a coffee stop" else "Approximately two hours. Arrival details appear on your reservation.", "Group size" to if (running) "30 sample places · Small pace groups" else "Limited places per session · Up to 8 guests per reservation")
            detailSection("What’s happening", "Arrival" to "Check in with the host 15 minutes before the start. Introduce yourself and meet the group.", "Main activity" to item.description, "Afterwards" to if (running) "Cool down together and join an optional café stop." else "Stay for a conversation and discover the next community meetup.")
            detailSection("Tickets & important details", "Admission" to "General entry is free in this mock event. Food, drinks, transport and optional purchases are not included.", "What to bring" to if (running) "Running shoes, water and weather-appropriate clothing." else "Your reservation reference and anything you need for a comfortable visit.", "Cancellation" to "You can cancel your mock reservation from My Plans. No payment or refund is involved.", "Accessibility" to "Contact the host before booking to confirm step-free access and individual requirements. Do not assume every route or venue is accessible.", "Updates" to "Check the event and host conversation before leaving. Outdoor plans may change with weather.")
            ui.action(body, "Ask the host", if (ownEvent) "You organise this event" else "Questions about the event or joining alone?", R.drawable.ic_chat) { if (ownEvent) go("profile") else go("chat", clubId) }
        }
        Kind.VENUE -> {
            val workspace = item.id == "studio"
            detailSection("About the venue", "The atmosphere" to item.description, "Best for" to if (workspace) "Focused work, small creative teams and casual professional meetups" else "Coffee with friends, a relaxed meal and small community gatherings", "Location" to item.location, "Opening hours" to if (workspace) "Monday–Friday, 9:00 AM–7:00 PM" else "Daily, 9:00 AM–10:30 PM")
            detailSection("Facilities & services", "Amenities" to if (workspace) "Wi-Fi, shared desks, charging points and bookable meeting tables" else "Indoor and outdoor seating, Wi-Fi, table service and charging points", "Menu & pricing" to if (workspace) "Desk and meeting-space pricing is agreed with the venue. The mock booking does not charge for a space." else "Coffee, tea, seasonal food and vegetarian options. Food and drinks are ordered and paid for at the venue.", "Dietary needs" to "Ask the venue about allergens and current dietary options before ordering.", "Accessibility" to "Step-free access, accessible toilets and seating arrangements must be confirmed with the venue.", "Pets & families" to if (workspace) "Check with the host before bringing pets or children to shared work sessions." else "Family-friendly seating. Ask the venue about pet seating and high chairs.")
            detailSection("Before reserving", "Reservation" to "Choose your date, time and party size. This sample flow supports 1–8 guests.", "Table duration" to "Allow approximately 90 minutes. Ask the venue if you need a longer visit.", "Late arrivals" to "Contact the venue if delayed. A real venue would confirm its table-hold policy.", "Changes & cancellation" to "Cancel the local reservation in My Plans. Real availability and venue policies are not connected.")
            ui.action(body, "Hosted community", "Discover people who meet here", R.drawable.ic_users) { go("club", if (workspace) "tech" else "cafe-circle") }
        }
        Kind.PLACE -> {
            detailSection("About this place", "Why visit" to if (item.id == "colosseum") "Explore a landmark of Rome’s architectural heritage and make time to observe the surrounding streets and public spaces." else "A green setting for a quiet walk, meeting friends and taking a slower break from the city.", "Suggested visit" to "Allow 1–2 hours, plus travel time. Plan around the weather and your own pace.", "Best for" to "City exploration, photography and discovering a new neighbourhood", "Location" to "Rome · See the illustrated experience map for sample context")
            detailSection("Plan your visit", "Access & tickets" to "Check the official operator for opening hours, tickets, closures and entry rules. This prototype does not issue admission tickets.", "Accessibility" to "Confirm step-free routes, toilets and assistance with the operator before travelling.", "What to bring" to "Comfortable shoes, water and weather-appropriate clothing.", "Visitor etiquette" to "Respect site rules and other visitors. Protect historic and natural features and ask before photographing people.")
            ui.action(body, "Explore with a community", "Find a group for your next city walk", R.drawable.ic_users) { go("club", "urban") }
        }
        else -> Unit
    }
}

fun CityScreens.reservationInformation(item: CityItem) {
    detailSection("Before you go", "Arrival" to "Arrive 10–15 minutes early and check in with the host or venue team.", "Meeting point" to item.location, "What to bring" to if (item.kind == Kind.EVENT) "Your plan reference and the items listed in the event details." else "Your plan reference and the name used for the reservation.", "Need to change plans?" to "Cancel from this screen and create a new local reservation with your preferred details. Cancelled plans remain in My Plans.")
    ui.action(body, "View full ${if (item.kind == Kind.EVENT) "event" else "venue"} details", "Schedule, facilities, participation and policies", R.drawable.ic_arrow) { open(item) }
}

fun CityScreens.ownerProfileInformation() {
    detailSection("About you", "Your introduction" to s.bio, "Home city" to s.city, "Interests" to s.interests.joinToString(" · ").ifBlank { "Add interests to help people find common ground" }, "What visitors can see" to "Your public introduction, interests, communities and shared activity. Your phone number and email are not displayed.")
}
