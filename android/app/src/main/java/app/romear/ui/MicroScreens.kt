package app.romear.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.core.view.setPadding
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.romear.R
import app.romear.data.CityData
import app.romear.databinding.ScreenChatBinding
import app.romear.databinding.ScreenStoryBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun CityScreens.renderChatMicro() {
    val target = CityData.find(route.id) ?: return
    val b = ScreenChatBinding.inflate(host.layoutInflater, host.binding.microHost, true)
    val group = target.kind == app.romear.data.Kind.CLUB
    b.chatHeader.addView(ui.icon(R.drawable.ic_back, "Back to conversations") { host.back() })
    b.chatHeader.addView(ui.avatar(target.image, 36))
    b.chatHeader.addView(ui.column().apply {
        layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = ui.dp(10) }
        addView(ui.text(if (target.id == "runners") "Riverside Runners" else target.title, 16, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
        addView(ui.text(if (group) "32 members · Club" else "● Online", 11, color = ui.forest))
        setOnClickListener { go(if (group) "club" else "person", target.id) }
    })
    b.chatHeader.addView(ui.icon(R.drawable.ic_video, "Preview video call") { toast("Video call preview") })
    b.chatHeader.addView(ui.icon(R.drawable.ic_more, "Conversation actions") {
        choose("Conversation", listOf("View profile", "Shared photos", "Clear my messages")) { action ->
            when(action) {
                "View profile" -> go(if(group) "club" else "person", target.id)
                "Shared photos" -> toast("Tap the photo in the conversation to view the venue")
                else -> confirm("Clear your messages?", "The sample conversation stays. Your messages in this chat will be removed.", "Clear") { repo.update { it.copy(messages = it.messages - target.id) } }
            }
        }
    })
    if (group) ui.chips(b.messages, listOf("Chat", "Events", "Members"), "Chat") { tab -> if (tab == "Events") go("events") else if (tab == "Members") go("people") }
    b.messages.addView(ui.text("Today", 11, color = ui.muted).apply { gravity = Gravity.CENTER; setPadding(ui.dp(8)); background = ui.shape(ui.sand, 16); layoutParams = LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = ui.dp(12) } })
    fun bubble(text: String, mine: Boolean, time: String, photo: Boolean = false, author: String? = null) {
        val box = ui.column(if (photo) 5 else 10).apply {
            layoutParams = LinearLayout.LayoutParams(if (photo) ui.dp(250) else -2, -2).apply {
                gravity = if (mine) Gravity.END else Gravity.START
                if (mine) marginStart = ui.dp(55) else marginEnd = ui.dp(55)
                bottomMargin = ui.dp(10)
            }
            background = ui.shape(if (mine) Color.rgb(215, 228, 195) else Color.rgb(239, 232, 222), 15)
        }
        if (author != null) box.addView(ui.text(author, 12, true, ui.forest))
        if (photo) box.addView(ui.image(if (group) target.image else R.drawable.chat_coffee, 138).apply { contentDescription = "Shared community photograph"; setOnClickListener { if (group) go("club", target.id) else go("venue", "riverside") } })
        if (text.isNotBlank()) box.addView(ui.text(text, 15))
        box.addView(ui.text("$time${if (mine) "  ✓✓" else ""}", 10, color = ui.muted).apply { gravity = Gravity.END; setPadding(0, ui.dp(4), ui.dp(2), 0) })
        b.messages.addView(box)
    }
    if (group) {
        bubble(if (target.id == "runners") "Reminder: Sunday’s long run is at 7:00 AM from Ponte Milvio. New route (12 km)!" else "Welcome to ${target.title}! Share your questions about the next meetup here so everyone can join the plan.", false, "09:12", author = "Sam Verdi")
        bubble("", false, "09:14", photo = true)
        bubble("I’m in! Bringing a friend too.", false, "09:18", author = "Lena B.")
        bubble("Great! Weather looks perfect.", false, "09:21", author = "Marco R.")
    } else {
        bubble("Hey! Are you still going to the Riverside Café meetup tomorrow?", false, "09:20")
        bubble("Yes! Definitely. Are we meeting at 10 AM as planned?", true, "09:23")
        bubble("Exactly. They just confirmed a new seasonal menu too!", false, "09:26")
        bubble("", false, "09:27", photo = true)
        bubble("Nice! Can’t wait. See you there!", true, "09:31")
    }
    s.messages[target.id].orEmpty().forEach { message ->
        bubble(if (message.text == "[photo:cafe]") "" else message.text, message.mine, SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.time)), photo = message.text == "[photo:cafe]")
    }
    b.messageInput.setText(draft("message_${target.id}"))
    b.messageInput.setSelection(b.messageInput.length())
    b.messageInput.doAfterTextChanged { save("message_${target.id}", it?.toString().orEmpty()) }
    b.send.setOnClickListener {
        val value = b.messageInput.text.toString().trim()
        if (value.isBlank()) return@setOnClickListener
        if (value.length > 2000) { b.messageInput.error = "Keep messages under 2,000 characters"; return@setOnClickListener }
        host.restoreChatFocus = b.messageInput.hasFocus()
        save("message_${target.id}", ""); repo.send(target.id, value)
    }
    b.attach.setOnClickListener { choose("Add to conversation", listOf("Café photo", "Share an event", "Suggest a venue")) { option -> when(option) {
        "Café photo" -> repo.send(target.id, "[photo:cafe]")
        "Share an event" -> go("events")
        else -> go("venues")
    } } }
    if (host.restoreChatFocus) { b.messageInput.requestFocus(); host.restoreChatFocus = false }
    if (s.messages[target.id].orEmpty().isNotEmpty()) b.messagesScroll.post { b.messagesScroll.smoothScrollTo(0, b.messages.height) }
}

fun CityScreens.renderStoryMicro() {
    val index = (route.id.toIntOrNull() ?: 0).coerceIn(0, 4)
    val b = ScreenStoryBinding.inflate(host.layoutInflater, host.binding.microHost, true)
    val photos = listOf(R.drawable.story_cafe, R.drawable.photo_run, R.drawable.photo_runners, R.drawable.photo_cafe, R.drawable.photo_market)
    val captions = listOf("Perfect coffee stop\nbefore the afternoon run", "The city feels different on a morning run.", "Good routes. Better company.", "A little time for the good things.", "Meet you under the lights.")
    val authors = listOf("Giulia Moretti", "Riya Sharma", "Riverside Runners", "Riverside Café", "Urban Explorers")
    var paused = false
    fun change(delta: Int) {
        val next = index + delta
        if (next > 4) { host.back(); return }
        if (next < 0) { save("progress", "0"); refresh(); return }
        model.route = Route("story", next.toString()); host.render()
    }
    val segments = (0..4).map { n -> ProgressBar(host, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 1000; progress = if (n < index) 1000 else 0
        progressTintList = ColorStateList.valueOf(Color.WHITE); progressBackgroundTintList = ColorStateList.valueOf(0x55FFFFFF)
        layoutParams = LinearLayout.LayoutParams(0, ui.dp(3), 1f).apply { marginEnd = ui.dp(4) }
        b.storyProgress.addView(this)
    } }
    b.storyPhoto.setImageResource(photos[index])
    b.storyPhoto.contentDescription = "Story by ${authors[index]}: ${captions[index]}"
    b.storyHeader.addView(ui.avatar(if (index == 0 || index == 1) R.drawable.avatar_riya else R.drawable.avatar_aarav, 34))
    b.storyHeader.addView(ui.text("${authors[index]}  ·  2h", 14, true, Color.WHITE).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = ui.dp(10) } })
    b.storyHeader.addView(ui.icon(R.drawable.ic_more, "Story options") {
        paused = true
        com.google.android.material.dialog.MaterialAlertDialogBuilder(host).setItems(arrayOf("View profile", "Share story", "Close story")) { _, n ->
            when(n) { 0 -> go("person", "riya"); 1 -> share("${captions[index]} · ROMEAR"); else -> host.back() }
        }.setOnDismissListener { paused = false }.show()
    }.apply { imageTintList = ColorStateList.valueOf(Color.WHITE) })
    b.storyHeader.addView(ui.icon(R.drawable.ic_close, "Close story") { host.back() }.apply { imageTintList = ColorStateList.valueOf(Color.WHITE) })
    b.placeSticker.text = if (index == 0 || index == 3) "Riverside Café" else "Rome · Riverside"
    b.placeSticker.setOnClickListener { go(if (index == 0 || index == 3) "venue" else "event", if (index == 0 || index == 3) "riverside" else "sunday-run") }
    b.storyCaption.text = captions[index]
    val liked = "story-$index" in s.liked
    b.storyLike.setImageResource(R.drawable.ic_heart)
    b.storyLike.imageTintList = ColorStateList.valueOf(if (liked) ui.rust else Color.WHITE)
    b.storyLike.contentDescription = if (liked) "Unlike story" else "Like story"
    b.storyLike.setOnClickListener { repo.toggle("story-$index", "liked") }
    b.storyReply.setText(draft("reply"))
    b.storyReply.doAfterTextChanged { save("reply", it?.toString().orEmpty()) }
    fun reply() {
        val value = b.storyReply.text.toString().trim()
        if (value.isBlank()) { b.storyReply.requestFocus(); return }
        save("reply", ""); repo.send("riya", value); go("chat", "riya")
    }
    b.storySend.setOnClickListener { reply() }
    b.storyReply.setOnEditorActionListener { _, action, _ -> if (action == EditorInfo.IME_ACTION_SEND) { reply(); true } else false }
    var startX = 0f; var startY = 0f; var downAt = 0L
    b.storyTouch.setOnTouchListener { view, event ->
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { startX = event.x; startY = event.y; downAt = android.os.SystemClock.uptimeMillis(); paused = true }
            MotionEvent.ACTION_UP -> {
                paused = false
                val dx = event.x - startX; val dy = event.y - startY
                when {
                    dy > ui.dp(70) -> host.back()
                    dx < -ui.dp(50) -> change(1)
                    dx > ui.dp(50) -> change(-1)
                    android.os.SystemClock.uptimeMillis() - downAt < 300 -> { if (event.x < view.width * .33f) change(-1) else view.performClick() }
                }
            }
            MotionEvent.ACTION_CANCEL -> paused = false
        }; true
    }
    b.storyTouch.setOnClickListener { change(1) }
    androidx.core.view.ViewCompat.addAccessibilityAction(b.storyTouch, "Previous story") { _, _ -> change(-1); true }
    b.storyTouch.isFocusable = true
    b.storyTouch.setOnKeyListener { _, code, event ->
        if (event.action == android.view.KeyEvent.ACTION_UP && code in listOf(android.view.KeyEvent.KEYCODE_DPAD_RIGHT, android.view.KeyEvent.KEYCODE_DPAD_LEFT)) { change(if (code == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1); true } else false
    }
    host.microJob = host.lifecycleScope.launch {
        host.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var progress = draft("progress", "0").toIntOrNull() ?: 0
            while (progress < 1000) {
                delay(60)
                if (!paused && !b.storyReply.hasFocus()) {
                    progress += 10; segments[index].progress = progress; save("progress", progress.toString())
                }
            }
            save("progress", "0"); change(1)
        }
    }
}
