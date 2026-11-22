package app.romear.ui

import android.view.Gravity
import android.widget.LinearLayout
import app.romear.R
import app.romear.data.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun CityScreens.inbox() {
    val filter = draft("filter", "All")
    ui.chips(body, listOf("All", "Direct", "Groups", "Events"), filter) { save("filter", it); refresh() }
    val entries = when(filter) { "Groups", "Events" -> listOf(CityData.clubs.first()); "Direct" -> CityData.people; else -> listOf(CityData.clubs.first()) + CityData.people }
    val previews = mapOf("runners" to "Sam: See you at 7 AM!", "aarav" to "Sounds good! Let us meet...", "riya" to "New cafe alert at Trastevere!", "kabir" to "Event update: meeting point...", "meera" to "Thanks! See you there")
    entries.forEachIndexed { index, person ->
        body.addView(ui.row().apply {
            minimumHeight = ui.dp(88)
            addView(ui.avatar(person.image, 50))
            addView(ui.column().apply {
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = ui.dp(12); marginEnd = ui.dp(8) }
                addView(ui.text(if (person.id == "runners") "Riverside Runners" else person.title, 16, true))
                ui.gap(this, 4)
                addView(ui.text(s.messages[person.id].orEmpty().lastOrNull()?.let { "You: " + if (it.text == "[photo:cafe]") "Photo" else it.text } ?: previews[person.id].orEmpty(), 13, color = ui.muted).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            })
            addView(ui.column().apply {
                layoutParams = LinearLayout.LayoutParams(ui.dp(48), -2); gravity = Gravity.END
                addView(ui.text(if (index < 2) "09:${38 + index * 3}" else "Yesterday", 10, color = ui.muted).apply { gravity = Gravity.END })
                ui.gap(this, 8)
                if (index < 2) addView(ui.text("1", 10, true, android.graphics.Color.WHITE).apply { gravity = Gravity.CENTER; background = ui.shape(ui.forest, 12); layoutParams = LinearLayout.LayoutParams(ui.dp(18), ui.dp(18)).apply { gravity = Gravity.END } })
            })
            contentDescription = "Open conversation with ${person.title}"
            setOnClickListener { go("chat", person.id) }
        })
        body.addView(android.view.View(host).apply { setBackgroundColor(ui.line) }, LinearLayout.LayoutParams(-1, ui.dp(1)).apply { marginStart = ui.dp(62) })
    }
}

fun CityScreens.chat() = renderChatMicro()

fun CityScreens.notifications() {
    ui.heading(body, "Notifications", "Updates from your local activity.")
    val active = s.bookings.filterNot { it.cancelled }
    if (active.isEmpty() && s.joined.isEmpty() && s.following.isEmpty()) {
        ui.empty(body, "You’re all caught up", "New local plans, clubs and connections will appear here.")
    }
    active.reversed().forEach { booking ->
        val item = findItem(booking.itemId)
        ui.action(body, "Plan saved", "${item?.title.orEmpty()} · ${booking.date}", R.drawable.ic_calendar) { go("pass", booking.id) }
    }
    s.joined.forEach { id -> ui.action(body, "A new community", "You joined ${CityData.find(id)?.title.orEmpty()}", R.drawable.ic_users) { go("club", id) } }
    s.following.forEach { id -> ui.action(body, "Connection made", "You followed ${CityData.find(id)?.title.orEmpty()}", R.drawable.ic_user) { go("person", id) } }
    ui.note(body, "No push notifications are sent by this offline version.")
}
