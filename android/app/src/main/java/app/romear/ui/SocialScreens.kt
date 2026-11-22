package app.romear.ui

import android.view.Gravity
import androidx.core.view.setPadding
import android.widget.LinearLayout
import android.widget.HorizontalScrollView
import app.romear.R
import app.romear.data.*
import java.util.UUID

fun CityScreens.home() {
    val tab = draft("tab", "For you")
    ui.chips(body, listOf("For you", "Following", "Reels", "Nearby"), tab) { save("tab", it); refresh() }
    if (tab == "Nearby") { nearbyMap(); return }
    if (tab == "Reels") {
        ui.section(body, "Reels")
        s.posts.filter { it.image?.startsWith("video:") == true }.asReversed().forEach { post ->
            ui.action(body, post.text, s.name, R.drawable.ic_video) { go("reel", post.id) }
        }
        ui.button(body, "Open reel viewer", icon = R.drawable.ic_video) { go("reel", "sample") }
        ui.button(body, "Create a reel", false) { go("compose") }
        return
    }
    val storyRow = ui.row()
    listOf("Your city" to R.drawable.photo_river, "Riya" to R.drawable.avatar_riya, "Aarav" to R.drawable.avatar_aarav, "Cafés" to R.drawable.photo_cafe, "Weekend" to R.drawable.photo_market).forEachIndexed { index, (name, image) ->
        storyRow.addView(ui.column().apply {
            layoutParams = LinearLayout.LayoutParams(ui.dp(74), -2)
            gravity = Gravity.CENTER
            addView(ui.avatar(image, 56).apply { setPadding(ui.dp(3)); background = ui.shape(ui.sand, 30, ui.rust) })
            ui.gap(this, 6); addView(ui.text(name, 11).apply { gravity = Gravity.CENTER })
            setOnClickListener { go("story", index.toString()) }
        })
    }
    body.addView(HorizontalScrollView(host).apply { isHorizontalScrollBarEnabled = false; addView(storyRow) })
    ui.gap(body, 20)
    val filter = draft("filter", "All")
    ui.chips(body, listOf("All", "Running", "Cafés", "Culture"), filter) { save("filter", it); refresh() }
    if (tab == "Nearby") ui.note(body, "Rome collection · Distances and location are illustrative.")
    if (tab == "Following" && s.following.isEmpty() && s.joined.isEmpty()) {
        ui.empty(body, "A feed that feels like you", "Follow people or join a club to start your own circle.")
        ui.button(body, "Find your people") { go("people") }
    } else {
        s.posts.filter { filter == "All" || it.category == filter }.reversed().forEach { local ->
            ui.card(body) { card ->
                card.addView(ui.text(s.name, 17, true)); card.addView(ui.text("Your post · ${local.category}", 12, color = ui.muted)); ui.gap(card, 12)
                local.image?.let { key -> addPostMedia(card, key); ui.gap(card, 12) }
                card.addView(ui.text(local.text))
                ui.button(card, "Delete post", false) { confirm("Delete this post?", "This removes the local post from this device.", "Delete") { repo.update { it.copy(posts = it.posts.filterNot { p -> p.id == local.id }) } } }
            }
        }
        val posts = CityData.posts.filter { filter == "All" || it.category == filter }.filter { post ->
            tab != "Following" || (post.id == "sunset" && ("riya" in s.following || "runners" in s.joined)) ||
                (post.id == "coffee" && "cafe-circle" in s.joined) || (post.id == "weekend" && "urban" in s.joined)
        }
        if (posts.isEmpty() && s.posts.isEmpty()) ui.empty(body, "A little quiet here", "Try another interest or follow more people.")
        posts.forEach { feedPost(it) }
    }
    ui.action(body, "Something real this weekend", "Browse events around the city", R.drawable.ic_calendar) { go("events") }
}

fun CityScreens.feedPost(post: CityItem) {
    val author = when (post.id) {
        "sunset" -> CityData.people.first { it.id == "riya" }
        "weekend" -> CityData.people.first { it.id == "meera" }
        else -> null
    }
    ui.card(body, 0) { card ->
        card.addView(ui.row().apply {
            setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12))
            addView(ui.avatar(author?.image ?: R.drawable.photo_cafe, 38))
            val byline = if (post.id == "weekend") "Meera Joshi · ${post.subtitle}" else post.subtitle
            addView(ui.text(byline, 12, true).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = ui.dp(10) } })
            setOnClickListener { if (author != null) go("person", author.id) else go("venue", "riverside") }
        })
        card.addView(ui.postPhoto(if (post.id == "coffee") R.drawable.story_cafe else post.image, crop = true).apply { setOnClickListener { go("post", post.id) }; contentDescription = post.title })
        val inside = ui.column(14)
        inside.addView(ui.text(post.title, 19, true)); ui.gap(inside, 5); inside.addView(ui.text(post.description, 14))
        ui.gap(inside, 8)
        inside.addView(ui.row().apply {
            addView(ui.icon(if (post.id in s.liked) R.drawable.ic_check else R.drawable.ic_heart, "${if (post.id in s.liked) "Unlike" else "Like"} post") { repo.toggle(post.id, "liked") })
            addView(ui.text("${142 + if (post.id in s.liked) 1 else 0}", 13).apply { layoutParams = LinearLayout.LayoutParams(-2, -2) })
            addView(ui.icon(R.drawable.ic_chat, "Comments") { go("post", post.id) })
            addView(ui.text("${repo.state.value.messages["post-${post.id}"].orEmpty().size}", 13).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
            addView(ui.icon(if (post.id in s.saved) R.drawable.ic_check else R.drawable.ic_bookmark, "Save ${post.title}") { repo.toggle(post.id, "saved") })
            addView(ui.icon(R.drawable.ic_share, "Share ${post.title}") { share("${post.title}\n${post.description}\n${post.location} · ROMEAR") })
        })
        card.addView(inside)
    }
}

fun CityScreens.post() {
    val post = CityData.posts.firstOrNull { it.id == route.id } ?: return
    feedPost(post)
    detailSection("About this moment", "Shared in" to post.location, "Interest" to post.category, "The story" to post.description)
    ui.action(body, "Discover the community", "Find clubs with similar interests", R.drawable.ic_users) { go("club", if (post.category == "Running") "runners" else if (post.category == "Cafés") "cafe-circle" else "urban") }
    ui.section(body, "Comments")
    val messages = s.messages["post-${post.id}"].orEmpty()
    if (messages.isEmpty()) ui.note(body, "Be the first to add a little to the conversation.")
    messages.forEach { message -> ui.card(body, 12) { box -> box.addView(ui.text(s.name, 14, true)); box.addView(ui.text(message.text, 15)) } }
    val comment = ui.input(body, "Add a comment", draft("comment_${post.id}")) { save("comment_${post.id}", it) }
    ui.button(body, "Post comment") {
        val value = comment.text.toString().trim()
        if (value.isBlank()) comment.error = "Write a comment first"
        else if (value.length > 1000) comment.error = "Keep comments under 1,000 characters"
        else { save("comment_${post.id}", ""); repo.send("post-${post.id}", value) }
    }
}

fun CityScreens.compose() {
    ui.heading(body, "A moment worth\nsharing.", "Tell your city what you’ve been up to.")
    ui.note(body, "Local post · visible only on this device")
    val text = ui.input(body, "What’s your city story?", draft("text"), true) { save("text", it) }
    val category = draft("category", "Running")
    ui.chips(body, listOf("Running", "Cafés", "Culture", "Travel"), category) { save("category", it); refresh() }
    val imageKey = draft("image").takeIf(String::isNotBlank)
    imageKey?.let { addPostMedia(body, it); ui.gap(body, 16) }
    ui.button(body, if (imageKey == null) "Choose photo" else "Change photo", false, R.drawable.ic_photo) {
        host.postPhotoPicker.launch(arrayOf("image/*"))
    }
    ui.button(body, "Choose reel video", false, R.drawable.ic_video) { host.postPhotoPicker.launch(arrayOf("video/*")) }
    ui.button(body, "Sample photos", false) {
        val choices = listOf("Run by the river" to "run", "Portrait café story" to "portrait", "Night market" to "market", "No photo" to "")
        choose("Choose a sample photo", choices.map { it.first }) { label -> save("image", choices.first { it.first == label }.second); refresh() }
    }
    ui.button(body, "Share post", icon = R.drawable.ic_arrow) {
        val value = text.text.toString().trim()
        if (value.isEmpty()) text.error = "Add a few words to your post"
        else if (value.length > 2000) text.error = "Keep posts under 2,000 characters"
        else {
            val post = LocalPost(UUID.randomUUID().toString(), value, category, imageKey)
            save("text", ""); save("image", "")
            repo.update { it.copy(posts = it.posts + post) }; host.navigate("home", root = true)
        }
    }
}

fun CityScreens.story() = renderStoryMicro()

fun CityScreens.saved() {
    ui.heading(body, "Keep the good stuff.", "A little collection of things to come back to.")
    val saved = (CityData.all + localEvents).distinctBy { it.id }.filter { it.id in s.saved }
    if (saved.isEmpty()) {
        ui.empty(body, "Your next favourite goes here", "Tap a bookmark on a post, club, event or café.")
        ui.button(body, "Explore your city") { go("explore") }
    } else saved.forEach { itemCard(it) }
}
