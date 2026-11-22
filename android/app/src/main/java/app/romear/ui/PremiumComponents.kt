package app.romear.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import app.romear.R
import app.romear.data.CityItem
import androidx.core.view.setPadding

fun CityUi.hero(parent: LinearLayout, item: CityItem, eyebrow: String = item.category) {
    val frame = FrameLayout(context).apply {
        layoutParams = LinearLayout.LayoutParams(-1, dp(246)).apply { bottomMargin = dp(20) }
        background = shape(forest, 26); clipToOutline = true
    }
    frame.addView(image(item.image, 246), FrameLayout.LayoutParams(-1, -1))
    frame.addView(View(context).apply { background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.TRANSPARENT, 0xDF0C2118.toInt())) }, FrameLayout.LayoutParams(-1, dp(190), Gravity.BOTTOM))
    frame.addView(text(eyebrow.ifBlank { "YOUR CITY, TOGETHER" }.uppercase(), 10, true, Color.WHITE).apply {
        letterSpacing = .13f; setPadding(dp(12), dp(8), dp(12), dp(8)); background = shape(0x77152F23, 30)
    }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { setMargins(dp(16), dp(16), dp(16), 0) })
    frame.addView(column(20).apply {
        addView(text(item.title, 29, true, Color.WHITE).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) })
        gap(this, 5)
        addView(text(item.subtitle, 13, color = 0xFFE8EEE6.toInt()))
    }, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
    parent.addView(frame)
}

fun CityUi.factTiles(parent: LinearLayout, values: List<Triple<Int, String, String>>) {
    parent.addView(row().apply {
        values.forEachIndexed { index, (resource, value, label) ->
            addView(column(12).apply {
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { if (index < values.lastIndex) marginEnd = dp(8) }
                background = shape(if (index == 0) 0xFFE7EDDF.toInt() else 0xFFF0EBE1.toInt(), 18)
                addView(icon(resource, label).apply { layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)); setPadding(dp(2)) })
                gap(this, 9)
                addView(text(value, 16, true))
                gap(this, 2); addView(text(label, 11, color = muted))
            })
        }
    }); gap(parent, 20)
}

fun CityUi.socialProof(parent: LinearLayout, title: String, subtitle: String, filled: Int = 2, capacity: Int = 5, showProgress: Boolean = true) {
    parent.addView(row().apply {
        addView(FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(86), dp(42))
            listOf(R.drawable.avatar_aarav, R.drawable.avatar_riya, R.drawable.avatar_aarav).take(filled.coerceIn(1, 3)).forEachIndexed { n, image ->
                addView(avatar(image, 34).apply { setPadding(dp(2)); background = shape(cream, 30) }, FrameLayout.LayoutParams(dp(34), dp(34)).apply { leftMargin = dp(n * 23); topMargin = dp(4) })
            }
        })
        addView(column().apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            addView(text(title, 14, true)); addView(text(subtitle, 12, color = muted))
        })
    }); gap(parent, 12)
    if (showProgress) parent.addView(row().apply {
        for (n in 0 until capacity.coerceIn(1, 12)) addView(View(context).apply { background = shape(if (n < filled) forest else line, 4) }, LinearLayout.LayoutParams(0, dp(4), 1f).apply { marginEnd = dp(4) })
    }); gap(parent, 22)
}

fun CityScreens.detailRow(parent: LinearLayout, label: String, value: String, section: String) {
    val glyph = when {
        label.contains("when", true) || label.contains("schedule", true) || label.contains("hours", true) || label.contains("date", true) || label.contains("duration", true) || label.contains("arrival", true) -> R.drawable.ic_calendar
        label.contains("location", true) || label.contains("point", true) || label.contains("where", true) || label.contains("city", true) || label.contains("based", true) -> R.drawable.ic_pin
        label.contains("group", true) || label.contains("who", true) || label.contains("level", true) || label.contains("visitors", true) -> R.drawable.ic_users
        label.contains("cost", true) || label.contains("admission", true) || label.contains("pricing", true) || label.contains("ticket", true) -> R.drawable.ic_ticket
        label.contains("menu", true) || label.contains("diet", true) -> R.drawable.ic_coffee
        else -> R.drawable.ic_shield
    }
    val key = "detail-$section-$label"
    var expanded = draft(key) == "true"
    val expandable = value.length > 100
    val content = ui.text(value, 14, color = ui.muted).apply {
        if (expandable && !expanded) maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    val mark = ui.text(if (expanded) "−" else "+", 20, color = ui.forest).apply { gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(ui.dp(32), ui.dp(32)) }
    val row = ui.row().apply {
        gravity = Gravity.TOP
        addView(ui.icon(glyph, label).apply { layoutParams = LinearLayout.LayoutParams(ui.dp(34), ui.dp(34)).apply { marginEnd = ui.dp(12) }; setPadding(ui.dp(8)); background = ui.shape(0xFFEAF0E4.toInt(), 11) })
        addView(ui.column().apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            addView(ui.text(label, 14, true)); ui.gap(this, 4); addView(content)
        })
        if (expandable) {
            addView(mark)
            minimumHeight = ui.dp(48)
            contentDescription = "$label. $value. ${if (expanded) "Collapse" else "Expand"} details"
            setOnClickListener {
                if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
                    android.transition.TransitionManager.beginDelayedTransition(parent, android.transition.AutoTransition().apply { duration = 180L })
                }
                expanded = !expanded; save(key, expanded.toString()); content.maxLines = if (expanded) Int.MAX_VALUE else 2
                mark.text = if (expanded) "−" else "+"
                contentDescription = "$label. $value. ${if (expanded) "Collapse" else "Expand"} details"
            }
        }
    }
    parent.addView(row)
}
