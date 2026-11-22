package app.romear.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import app.romear.R
import app.romear.data.CityData
import app.romear.data.CommunityPost
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal fun CityScreens.retryId(key: String, signature: String): String {
    val previous = draft(key)
    return previous.substringBefore('\n').takeIf { it.isNotBlank() && previous.substringAfter('\n') == signature }
        ?: UUID.randomUUID().toString().also { save(key, "$it\n$signature") }
}

internal suspend fun CityScreens.encodeCommunityPhoto(key: String): String = withContext(Dispatchers.IO) {
    val uri = Uri.parse(key)
    val resolver = host.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("This photo could not be read")
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 960) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: throw IOException("This photo could not be read")
    try {
        for (quality in listOf(85, 65, 45, 25)) {
            val output = ByteArrayOutputStream()
            if (bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output) && output.size() <= 256 * 1024) {
                return@withContext Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
            }
        }
        throw IOException("Choose a smaller photo")
    } finally {
        bitmap.recycle()
    }
}

private fun CityScreens.addCloudPhoto(parent: LinearLayout, post: CommunityPost) {
    val photo = ImageView(host).apply { portraitPostFrame(); contentDescription = "Photo by ${post.author}" }
    parent.addView(photo)
    model.postPhotos.get(post.id)?.let { photo.setImageBitmap(it); return }
    host.lifecycleScope.launch {
        val bitmap = try {
            val data = model.auth.community("GET", "posts/${post.id}/photo").getString("jpeg")
            withContext(Dispatchers.Default) {
                val bytes = Base64.decode(data, Base64.DEFAULT)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth !in 1..1920 || bounds.outHeight !in 1..1920) null
                else BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (_: IOException) {
            null
        }
        if (bitmap != null && model.activeCommunity) {
            model.postPhotos.put(post.id, bitmap)
            if (photo.isAttachedToWindow) photo.setImageBitmap(bitmap)
        }
    }
}

internal fun CityScreens.communityPostCard(post: CommunityPost) {
    ui.card(body) { card ->
        card.addView(ui.text(post.author, 17, true).apply {
            setOnClickListener { if (post.mine) go("profile") else go("person", post.owner) }
        })
        card.addView(ui.text("${post.city} · ${post.category}", 12, color = ui.muted))
        ui.gap(card, 8)
        if (post.photo) addCloudPhoto(card, post)
        else CityData.referencePhoto(post.sample)?.let { card.addView(ui.postPhoto(it)) }
        card.addView(ui.text(post.body, 15).apply { setOnClickListener { go("post", post.id) } })
        card.addView(ui.row().apply {
            addView(ui.icon(if (post.liked) R.drawable.ic_check else R.drawable.ic_heart, if (post.liked) "Unlike post" else "Like post") {
                model.updateCommunity(if (post.liked) "DELETE" else "PUT", "posts/${post.id}/reaction/like", null, ::toast) { refresh() }
            })
            addView(ui.text(post.likes.toString(), 13))
            addView(ui.icon(R.drawable.ic_chat, "Comments") { go("post", post.id) })
            addView(ui.text(post.comments.toString(), 13))
            addView(ui.icon(if (post.saved) R.drawable.ic_check else R.drawable.ic_bookmark, if (post.saved) "Remove bookmark" else "Save post") {
                model.updateCommunity(if (post.saved) "DELETE" else "PUT", "posts/${post.id}/reaction/save", null, ::toast) { refresh() }
            })
            addView(ui.icon(R.drawable.ic_share, "Share post") { share("${post.body}\n${post.city} · ROMEAR") })
        })
        if (post.mine && route.page == "post") ui.button(card, "Delete post", false) {
            confirm("Delete this post?", "Its photo and comments will also be removed.", "Delete") {
                model.updateCommunity("DELETE", "posts/${post.id}", null, ::toast) { go("home") }
            }
        }
    }
}

internal fun CityScreens.renderCommunityPost() {
    val id = route.id
    val post = model.postDetails?.takeIf { it.id == id }
        ?: model.publicFeed.firstOrNull { it.id == id }
        ?: model.cloudSavedPosts.firstOrNull { it.id == id }
        ?: model.authorPosts.values.asSequence().flatten().firstOrNull { it.id == id }
    if (post == null) { ui.note(body, "Loading post"); return }
    communityPostCard(post)
    ui.section(body, "Comments")
    if (model.postDetails?.id == id) model.postComments.forEach { comment ->
        ui.card(body, 12) { card ->
            card.addView(ui.text(comment.author, 14, true))
            card.addView(ui.text(comment.body, 15))
        }
    }
    val comment = ui.input(body, "Add a comment", draft("comment")) { save("comment", it) }
    ui.button(body, "Post comment") {
        val body = comment.text.toString().trim()
        if (body.length !in 1..1000) comment.error = "Use 1–1,000 characters"
        else {
            val payload = JSONObject().put("id", retryId("commentRetry", body)).put("body", body)
            model.updateCommunity("PUT", "posts/$id/comments", payload, ::toast) {
                if (draft("comment").trim() == body) save("comment", "")
                save("commentRetry", "")
                refresh()
            }
        }
    }
}
