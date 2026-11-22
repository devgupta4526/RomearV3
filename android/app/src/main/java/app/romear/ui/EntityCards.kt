package app.romear.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.setPadding
import app.romear.R
import app.romear.data.CityItem

private fun CityUi.saveControl(item: CityItem, saved: Boolean, action: () -> Unit) = icon(if (saved) R.drawable.ic_check else R.drawable.ic_bookmark, "${if (saved) "Unsave" else "Save"} ${item.title}", action)

fun CityUi.eventCard(parent: LinearLayout, item: CityItem, saved: Boolean, onSave: () -> Unit, onOpen: () -> Unit) {
    card(parent, 0) { box ->
        box.addView(FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(136))
            addView(image(item.image, 136), FrameLayout.LayoutParams(-1, -1))
            val date = runCatching { java.time.LocalDate.parse(item.subtitle.take(10)) }.getOrNull()
            val day = date?.dayOfMonth?.toString() ?: when(item.id) { "market" -> "28"; "sunday-run", "coffee-meet" -> "29"; else -> "—" }
            addView(column(10).apply {
                gravity = Gravity.CENTER; background = shape(cream, 14)
                addView(text(date?.format(java.time.format.DateTimeFormatter.ofPattern("MMM", java.util.Locale.ENGLISH))?.uppercase() ?: "NOV", 10, true, rust).apply { gravity = Gravity.CENTER; letterSpacing = .1f })
                addView(text(day, 27, true).apply { gravity = Gravity.CENTER })
            }, FrameLayout.LayoutParams(dp(62), -2, Gravity.BOTTOM or Gravity.START).apply { leftMargin = dp(14); bottomMargin = dp(12) })
            addView(saveControl(item, saved, onSave).apply { background = shape(cream, 26) }, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP or Gravity.END).apply { topMargin = dp(8); rightMargin = dp(8) })
        })
        box.addView(column(16).apply {
            addView(row().apply {
                addView(text("CITY EVENT", 10, true, rust).apply { letterSpacing = .12f; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
                addView(text("Free entry", 11, true, forest).apply { layoutParams = LinearLayout.LayoutParams(-2, -2) })
            })
            gap(this, 5); addView(text(item.title, 21, true)); gap(this, 8)
            addView(text(item.location, 13, color = muted))
            val reservationStatus = item.subtitle.substringAfter('\n', "").takeIf { it != item.location && it.isNotBlank() }
            if (reservationStatus != null) { gap(this, 6); addView(text(reservationStatus, 12, true, if (reservationStatus.contains("Cancelled")) rust else forest)) }
            gap(this, 12)
            addView(row().apply {
                addView(icon(R.drawable.ic_calendar, "Event time").apply { layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)); setPadding(dp(2)) })
                addView(text(item.subtitle.substringBefore('\n'), 12, true).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(6) } })
                addView(text(if (reservationStatus?.contains("guests") == true) "View reservation" else "View event & tickets", 12, true, forest).apply { layoutParams = LinearLayout.LayoutParams(-2, dp(48)); gravity = Gravity.CENTER; setOnClickListener { onOpen() } })
            })
        })
    }.setOnClickListener { onOpen() }
}

fun CityUi.poolCard(parent: LinearLayout, item: CityItem, saved: Boolean, onSave: () -> Unit, onOpen: () -> Unit) {
    val requested = (context as? app.romear.MainActivity)?.model?.state?.value?.requested?.contains(item.id) == true
    val total = when(item.id) { "cycling" -> 3; "study" -> 4; else -> 5 }
    val filled = if (item.id == "cycling") 1 else 2
    card(parent, 16) { box ->
        box.addView(row().apply {
            addView(column().apply {
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(12) }
                addView(text(if (requested) "REQUEST PENDING" else "OPEN POOL", 10, true, forest).apply { letterSpacing = .12f })
                gap(this, 7); addView(text(item.title, 22, true)); gap(this, 5)
                addView(text(item.category.ifBlank { "Community activity" }, 12, color = muted))
            })
            addView(image(item.image, 72).apply { layoutParams = LinearLayout.LayoutParams(dp(72), dp(72)) })
        })
        gap(box, 14)
        box.addView(row().apply {
            background = shape(0xFFF0EBE1.toInt(), 12); setPadding(dp(10))
            addView(icon(R.drawable.ic_calendar, "When").apply { layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)); setPadding(dp(2)) })
            addView(text(item.subtitle.substringBefore('\n').substringBefore(" · 2 of").substringBefore(" · 1 of"), 13, true).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(7) } })
        })
        gap(box, 12)
        box.addView(row().apply {
            addView(avatar(R.drawable.avatar_aarav, 26))
            if (filled > 1) addView(avatar(R.drawable.avatar_riya, 26).apply { layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply { marginStart = -dp(6) } })
            addView(text("$filled of $total joined", 12, true).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8) } })
            addView(text("${total - filled} spots left", 12, true, forest).apply { layoutParams = LinearLayout.LayoutParams(-2, -2) })
        })
        gap(box, 10)
        box.addView(row().apply { for (n in 0 until total) addView(android.view.View(context).apply { background = shape(if (n < filled) forest else line, 4) }, LinearLayout.LayoutParams(0, dp(4), 1f).apply { marginEnd = dp(4) }) })
        gap(box, 8)
        box.addView(row().apply {
            addView(text(item.location, 12, color = muted).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
            addView(saveControl(item, saved, onSave))
            addView(text("View activity  →", 13, true, forest).apply { layoutParams = LinearLayout.LayoutParams(-2, dp(48)); gravity = Gravity.CENTER; setOnClickListener { onOpen() } })
        })
    }.apply { setCardBackgroundColor(0xFFF1F4EB.toInt()); setOnClickListener { onOpen() } }
}

fun CityUi.clubCard(parent: LinearLayout, item: CityItem, saved: Boolean, onSave: () -> Unit, onOpen: () -> Unit) {
    card(parent, 0) { box ->
        box.addView(FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(122))
            addView(image(item.image, 122), FrameLayout.LayoutParams(-1, -1))
            addView(saveControl(item, saved, onSave).apply { background = shape(cream, 26) }, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP or Gravity.END).apply { topMargin = dp(8); rightMargin = dp(8) })
        })
        box.addView(column(16).apply {
            addView(row().apply {
                addView(icon(if (item.category == "Running") R.drawable.ic_run else if (item.category == "Cafés") R.drawable.ic_coffee else R.drawable.ic_users, item.category).apply {
                    background = shape(0xFFE7EDDF.toInt(), 16); layoutParams = LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginEnd = dp(12); topMargin = -dp(30) }
                })
                addView(text("${item.category.uppercase()} COMMUNITY", 10, true, forest).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f); letterSpacing = .1f })
            })
            gap(this, 6); addView(text(item.title, 21, true))
            gap(this, 7); addView(text(item.description, 13, color = muted).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            gap(this, 12)
            addView(row().apply {
                addView(avatar(R.drawable.avatar_aarav, 25))
                addView(avatar(R.drawable.avatar_riya, 25).apply { layoutParams = LinearLayout.LayoutParams(dp(25), dp(25)).apply { marginStart = -dp(8) } })
                addView(text("${item.members} members", 12, true).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8) } })
                addView(text("Explore club", 12, true, forest).apply { layoutParams = LinearLayout.LayoutParams(-2, dp(48)); gravity = Gravity.CENTER; setOnClickListener { onOpen() } })
            })
        })
    }.setOnClickListener { onOpen() }
}
